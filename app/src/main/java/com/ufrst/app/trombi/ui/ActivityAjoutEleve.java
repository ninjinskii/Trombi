package com.ufrst.app.trombi.ui;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ufrst.app.trombi.R;
import com.ufrst.app.trombi.database.Eleve;
import com.ufrst.app.trombi.database.EleveGroupeJoin;
import com.ufrst.app.trombi.database.EleveWithGroups;
import com.ufrst.app.trombi.database.Groupe;
import com.ufrst.app.trombi.database.TrombiViewModel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_ID;
import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_ID_E;
import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_NOM_E;
import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_PHOTO_E;

public class ActivityAjoutEleve extends AppCompatActivity {

    private ExtendedFloatingActionButton fab;
    private TrombiViewModel trombiViewModel;
    private TextInputEditText inputEditText;
    private MaterialButton buttonGroup;
    private TextView emptyTextView;
    private ChipGroup chipGroup;
    private Toolbar toolbar;

    private String nomPrenomEleve;
    private String photoPath;
    boolean isEditMode = false;                 // false: ajout, true: modification élève
    private long idTrombi;
    private long idEleve;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        if(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES){
            setTheme(R.style.AppThemeDark);
        } else{
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajout_eleve);

        getExtras();
        findViews();
        setListeners();
        getGroupesForEleve();
        updateData();

        // Toolbar
        setSupportActionBar(toolbar);
        setTitle(R.string.AJOUTELEVE_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void getExtras(){
        Intent intent = getIntent();
        idTrombi = intent.getLongExtra(EXTRA_ID, -1);

        if(intent.hasExtra(EXTRA_NOM_E) && intent.hasExtra(EXTRA_ID_E)
                && intent.hasExtra(EXTRA_PHOTO_E)){
            isEditMode = true;
            nomPrenomEleve = intent.getStringExtra(EXTRA_NOM_E);
            idEleve = intent.getLongExtra(EXTRA_ID_E, -1);
            photoPath = intent.getStringExtra(EXTRA_PHOTO_E);
        } else{
            nomPrenomEleve = "";
            photoPath = "";
        }
    }

    private void findViews(){
        buttonGroup = findViewById(R.id.AJOUTELEVE_buttonGroup);
        inputEditText = findViewById(R.id.AJOUTELEVE_entrerNom);
        chipGroup = findViewById(R.id.AJOUTELEVE_chipsGroup);
        emptyTextView = findViewById(R.id.AJOUTELEVE_empty);
        toolbar = findViewById(R.id.AJOUTELEVE_toolbar);
        fab = findViewById(R.id.AJOUTELEVE_btnValider);
    }

    private void setListeners(){
        fab.setOnClickListener(view -> saveEleve());

        buttonGroup.setOnClickListener(view -> {
            Intent intent = new Intent(ActivityAjoutEleve.this, ActivityListGroupe.class);
            intent.putExtra(EXTRA_ID, idTrombi);

            startActivity(intent);
        });
    }

    // Récupère les groupes auxquels l'élève appartient et déclenche l'observation des groupes
    private void getGroupesForEleve(){
        trombiViewModel = ViewModelProviders.of(this).get(TrombiViewModel.class);

        // CompletableFuture qui va récupérer de manière asynchrone les groupes de l'élève
        CompletableFuture.supplyAsync(() -> trombiViewModel.getEleveByIdWithGroupsNotLive(idEleve))
                .thenApply(EleveWithGroups::getGroupes)                                         // Récupérer les groupes de l'objet EleveWithGroupes
                .exceptionally(throwable -> null)                                               // Si erreur (cas de l'ajout, l'id -1 n'existe pas) alors on continue avec null en tant que liste de groupes
                .thenAccept(groups -> runOnUiThread(() -> observeGroupesForTrombi(groups)));   // Déclencher l'observation des groupes du trombi en passant la liste des groupes
    }

    // GetGroupesForEleve appelle cette fonction, et lui passe la liste des groupes
    // auxquels l'élève appartient
    private void observeGroupesForTrombi(@Nullable List<Groupe> groupsToCheck){
        trombiViewModel.getGroupesByTrombi(idTrombi).observe(this, new Observer<List<Groupe>>() {
            @Override
            public void onChanged(List<Groupe> groupes){
                // Supprime les potentielles précédentes chips
                chipGroup.post(() -> chipGroup.removeAllViews());

                // La liste des groupes n'est pas vide
                if(groupes.size() != 0){
                    emptyTextView.setVisibility(View.GONE);

                    Executors.newSingleThreadExecutor().execute(() ->
                            setChips(groupes, groupsToCheck));
                } else{
                    emptyTextView.setVisibility(View.VISIBLE);
                }

                // On a besoin des valeurs une seule fois
                trombiViewModel.getGroupesByTrombi(idTrombi).removeObserver(this);
            }
        });
    }

    // Appellé par observeGroupesForTrombi en passant la liste des groupes du trombi
    // et la liste de groupes auxquels l'élève appartient
    // Ne pas exécuter sur le ThreadUI
    private void setChips(List<Groupe> groups, @Nullable List<Groupe> groupsToCheck){
        for(Groupe g : groups){
            // Inflate la chips d'après mon layout customisé, afin de pouvoir changer l'apparence de la chips lors de la sélection
            Chip c = (Chip) getLayoutInflater()
                    .inflate(R.layout.chips_choice, chipGroup, false);

            // Ajout d'un tag pour lier un objet à cette vue
            c.setTag(R.string.TAG_CHIPS_ID, g);

            // Check la chips si l'élève appartient au groupe
            if(groupsToCheck != null && groupsToCheck.contains(g)){
                c.post(() -> c.setChecked(true));  // View#post permet de donner un executable dans le thread UI
            }

            // Texte de la chips
            c.setText(g.getNomGroupe());

            // Ajout de la chips dans le groupe de chips
            chipGroup.post(() -> chipGroup.addView(c));
        }
    }

    private void updateData(){
        // Cas de la création d'élève puis de la modification
        if(nomPrenomEleve == null){
            fab.setText(R.string.U_valider);
        } else{
            inputEditText.setText(nomPrenomEleve);
        }
    }

    // Enregistre un nouvel élève dans la BD, avec ses groupes s'il y en a
    private void saveEleve(){
        String nom = inputEditText.getText().toString();

        if(nom.trim().isEmpty()){
            inputEditText.setError(getResources().getString(R.string.AJOUTELEVE_empty));
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            // Instanciation d'un élève
            Eleve eleve = new Eleve(nom, idTrombi, "");

            // Cas de l'ajout de l'élève, puis de la modification
            if(!isEditMode){
                // Insertion d'un élève et récupération de son ID dans la BD
                long idEleve = trombiViewModel.insertAndRetrieveId(eleve);
                setGroupForEleve(idEleve);
            } else{
                eleve.setIdEleve(idEleve);
                eleve.setPhoto(photoPath);
                trombiViewModel.update(eleve);
                setGroupForEleve(idEleve);
            }
        });

        // Retour à l'activité appelante
        Intent data = new Intent();
        setResult(RESULT_OK, data);

        finish();
    }

    // Alimente la cross ref entre groupe et élèves
    private void setGroupForEleve(long idEleve){
        // On récupère les groupes associés aux chips sélectionnées et insère des XRef dans la BD
        for(int i = 0; i < chipGroup.getChildCount(); i++){
            Chip chip = (Chip) chipGroup.getChildAt(i);
            Groupe g = (Groupe) chip.getTag(R.string.TAG_CHIPS_ID);

            if(g != null){
                // Ajout de la XRef pour toutes les chips checkées (si il existe déja il sera ignoré
                // cf EleveGroupJoin#insert pour plus de détails
                // puis retrait de la XRef pour toutes les chips pas checkées
                // si Room ne trouve pas d'item à supprimer, il ne fait rien
                if(chip.isChecked()){
                    trombiViewModel.insert(new EleveGroupeJoin(idEleve, g.getIdGroupe(), idTrombi));
                } else{
                    trombiViewModel.delete(new EleveGroupeJoin(idEleve, g.getIdGroupe(), idTrombi));
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return super.onSupportNavigateUp();
    }
}
