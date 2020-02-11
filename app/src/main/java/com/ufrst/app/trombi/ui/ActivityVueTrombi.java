package com.ufrst.app.trombi.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.ufrst.app.trombi.R;
import com.ufrst.app.trombi.database.Eleve;
import com.ufrst.app.trombi.database.Groupe;
import com.ufrst.app.trombi.database.GroupeWithEleves;
import com.ufrst.app.trombi.database.TrombiViewModel;
import com.ufrst.app.trombi.util.HTMLProvider;
import com.ufrst.app.trombi.util.ImageUtil;
import com.ufrst.app.trombi.util.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_DESC;
import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_ID;
import static com.ufrst.app.trombi.ui.ActivityMain.EXTRA_NOM;
import static com.ufrst.app.trombi.ui.ActivityMain.PREFS_NBCOLS;

public class ActivityVueTrombi extends AppCompatActivity {

    private BottomSheetBehavior bottomSheetBehavior;
    private CoordinatorLayout coordinatorLayout;
    private RelativeLayout relativeLayout;
    private LinearLayout linearLayout;
    private ProgressBar progressBar;
    private ChipGroup chipGroup;
    private TextView tvNbCols;
    private Switch switchDesc;
    private View bottomSheet;
    private WebView webView;
    private Toolbar toolbar;
    private SeekBar seekBar;

    private Observer<List<Eleve>> observerEleve;
    private TrombiViewModel trombiViewModel;
    private boolean observingGroups = false;            // Sert à savoir si on met le groupe pour le titre du PDF
    private boolean isLoading = false;                  // Le chargement d'une webview est en cours
    private List<Eleve> listeEleves;
    private SharedPreferences prefs;
    private boolean withDesc = true;
    private String descTrombi;
    private String nomTrombi;
    private long idTrombi;
    private int nbCols;                                 // Nombre de colonnes à afficher dans le webview

    private Chip checked;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vue_trombi);

        getExtras();
        retrieveSharedPrefs();
        findViews();
        initializeWebView();
        getGroupesAndEleves();
        setListeners();

        // Toolbar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(nomTrombi);

        // Met en place la Bottom Sheet persistante, qui contiendra les filtres
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        // Applique la valeur par défaut de nbCols à la SeekBar
        seekBar.setProgress(nbCols);
        tvNbCols.setText(String.valueOf(nbCols + 1));

    }

    // Récupération des extras
    private void getExtras(){
        Intent intent = getIntent();
        idTrombi = intent.getLongExtra(EXTRA_ID, -1);
        nomTrombi = intent.getStringExtra(EXTRA_NOM);
        descTrombi = intent.getStringExtra(EXTRA_DESC);
    }

    // Récupération des valeures prédéfinies par l'utilisateur
    private void retrieveSharedPrefs(){
        prefs = getSharedPreferences("com.ufrst.app.trombi", Context.MODE_PRIVATE);
        nbCols = prefs.getInt(PREFS_NBCOLS, 4) -1;
    }

    // Désérialise les vues dont on aura besoin depuis le XML
    private void findViews(){
        relativeLayout = findViewById(R.id.VUETROMBI_switchDescLayout);
        coordinatorLayout = findViewById(R.id.VUETROMBI_coordinator);
        linearLayout = findViewById(R.id.VUETROMBI_layoutParam);
        progressBar = findViewById(R.id.VUETROMBI_progressBar);
        bottomSheet = findViewById(R.id.VUETROMBI_bottomSheet);
        switchDesc = findViewById(R.id.VUETROMBI_switchDesc);
        chipGroup = findViewById(R.id.VUETROMBI_chipsGroup);
        tvNbCols = findViewById(R.id.VUETROMBI_nbCols);
        seekBar = findViewById(R.id.VUETROMBI_seekBar);
        toolbar = findViewById(R.id.VUETROMBI_toolbar);
        webView = findViewById(R.id.VUETROMBI_webView);
    }

    // Définit les paramètres de la WebView
    private void initializeWebView(){
        webView.setInitialScale(1);
        webView.getSettings().setUseWideViewPort(true);
    }

    // Récupère les informations sur le trombinoscope sélectionné dans l'activité précédente
    private void getGroupesAndEleves(){
        // Création et référenciation d'un observeur qui sera supprimé/ajouté lors de la sélection
        // ou déselection d'un groupe
        observerEleve = eleves -> {
            listeEleves = eleves;
            showHTML();
        };

        trombiViewModel = ViewModelProviders.of(this).get(TrombiViewModel.class);
        trombiViewModel.getElevesByTrombi(idTrombi).observe(this, observerEleve);
        trombiViewModel.getGroupesByTrombi(idTrombi).observe(this, groupes -> {
            // Insertion des chips pour le choix des groupes a afficher
            Executors.newSingleThreadExecutor().execute(() -> {
                for(Groupe g : groupes){
                    setChips(g);
                }
            });
        });

        // Observation des élèves d'un des groupes (voir TrombiViewModel)
        trombiViewModel.groupesWithEleves
                .observe(ActivityVueTrombi.this, groupeWithEleves -> {
                    listeEleves = groupeWithEleves.getEleves();
                    showHTML();
                });
    }

    // Applique des listeners sur certains éléments
    private void setListeners(){
        // Seekbar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b){
                tvNbCols.setText(String.valueOf(i + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar){
                if(seekBar.getProgress() != nbCols && !isLoading){
                    nbCols = seekBar.getProgress();
                    showHTML();
                }
            }
        });

        // Groupe de chips
        chipGroup.setOnCheckedChangeListener((chipGroup, i) -> {
            //
            // SOLUTION TEMPORAIRE au problème du changement d'id des chips lors de la reprise de l'activité
            //
            /*int pos = i - 1;    // Représente la position du groupe dans la liste listeGroupe

            while(pos > chipGroup.getChildCount() - 1){
                pos -= chipGroup.getChildCount();
            }*/

            // Une chips est sélectionnée
            if(i != ChipGroup.NO_ID){
                // Récupération du groupe associé à la chips: cf setChips()
                Chip c = findViewById(i);

                checked = c;

                // Récupération du groupe associé à la chip
                Groupe currentGroupe = (Groupe) c.getTag(R.string.TAG_CHIPS_ID);

                observingGroups = true;

                // Changement de l'ID voulu pour la récupération de la méthode TrombiViewModel#getGroupByIdWithEleves
                trombiViewModel.setIdGroup(currentGroupe.getIdGroupe());
            } else{
                observingGroups = false;

                // On observe la liste de tous les élèves du trombi à nouveau
                trombiViewModel.getElevesByTrombi(idTrombi)
                        .observe(ActivityVueTrombi.this, observerEleve);
            }
        });

        // Switch afficher description
        relativeLayout.setOnClickListener(view -> {
            if(!isLoading){
                switchDesc.setChecked(!switchDesc.isChecked());
                withDesc = switchDesc.isChecked();

                showHTML();
            }
        });
    }

    // Instancie positionne, et met en place les chips dans la bottom sheet.
    private void setChips(Groupe g){
        // Inflate la chips d'après mon layout customisé, afin de pouvoir changer l'apparence de la chips lors de la sélection
        Chip c = (Chip) getLayoutInflater()
                .inflate(R.layout.chips_choice, chipGroup, false);

        // Ajout d'un tag pour lier un objet à cette vue
        c.setTag(R.string.TAG_CHIPS_ID, g);

        // Texte de la chips
        c.setText(g.getNomGroupe()); //A changer: enlever + idGroupe

        // Ajout de la chips dans le groupe de chips
        chipGroup.post(() -> chipGroup.addView(c));
    }

    // Insère le HTML dans la WebView de manière asynchrone (évite les freezes)
    private void showHTML(){
        if(!isLoading){
            setLoadingState(true);

            HTMLProvider htmlProvider = new HTMLProvider.Builder()
                    .setNomTrombi(nomTrombi)
                    .setDescTrombi(descTrombi)
                    .setListeEleves(listeEleves)
                    .setNbCols(nbCols)
                    .hasDescription(withDesc)
                    .build();

            // Génère le HTML dans un autre Thread, puis l'affiche dans le ThreadUI (obligatoire)
            CompletableFuture.supplyAsync(htmlProvider::doHTML)
                    .thenAccept(htmlText ->
                            runOnUiThread(() -> webView.loadDataWithBaseURL(
                                    null,
                                    htmlText,
                                    "text/html; charset=UTF-8",
                                    "UTF-8",
                                    null)))
                    .exceptionally(throwable -> null)
                    .thenRun(() -> setLoadingState(false));
        }
    }

    // Active ou désactive des composants si la génération du html est en cours ou non
    private void setLoadingState(boolean isLoading){
        runOnUiThread(() -> {
            if(isLoading){
                chipGroup.setVisibility(View.INVISIBLE);
                seekBar.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
            } else{
                chipGroup.setVisibility(View.VISIBLE);
                seekBar.setEnabled(true);
                progressBar.setVisibility(View.GONE);
            }
        });

        this.isLoading = isLoading;
    }

    // Demande à l'utilisateur si la liste doit être exportée
    // Un fichier correspondant à cette liste peut déjà exister
    private void checkWriteExportedList(){
        DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        Calendar calendar = Calendar.getInstance();
        final String filename = nomTrombi + "-" + df.format(calendar.getTime());

        // Observation des élèves du trombi à exporter
        trombiViewModel.getElevesByTrombi(idTrombi).observe(this, eleves -> {
            File file = new File(getExternalFilesDir(null) + "/" + filename + ".txt");

            // Si le fichier existe, on demande à le remplacer, sinon on le créer.
            if(file.exists()){
                // Snackbar avec action
                Snackbar.make(coordinatorLayout, R.string.VUETROMBI_fichierExiste, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.U_remplacer, new View.OnClickListener() {
                            @Override
                            public void onClick(View v){
                                writeExportedList(filename, eleves, true);
                            }
                        })
                        .setActionTextColor(ContextCompat.getColor(ActivityVueTrombi.this, R.color.colorAccent))
                        .setDuration(8000)
                        .show();
            } else{
                writeExportedList(filename, eleves, false);
            }
        });

        trombiViewModel.getElevesByTrombi(idTrombi).removeObservers(this);
    }

    // Ecrit une liste d'élèves dans un fichier situé dans le stockage externe de l'app
    private void writeExportedList(String filename, List<Eleve> eleves, boolean doErase){
        // Le fichier est réécrit, on doit supprimer son contenu actuel
        if(doErase){
            try{
                PrintWriter writer = new PrintWriter(getExternalFilesDir(null) +
                        "/" + filename + ".txt");
                writer.print("");
                writer.close();
            } catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }

        // Ecriture du fichier
        try(BufferedWriter writer =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(getExternalFilesDir(null) +
                                            "/" + filename + ".txt",
                                            true),
                                    StandardCharsets.UTF_8)
                    )
        ){
            for(Eleve eleve : eleves){
                writer.write(eleve.getNomPrenom() + "\n");
            }

            Snackbar.make(coordinatorLayout, R.string.VUETROMBI_listeExportee, Snackbar.LENGTH_LONG)
                    .show();
        } catch(IOException e){
            Snackbar.make(coordinatorLayout,
                    R.string.VUETROMBI_listeExporteeErr,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    // Lance un Intent pour le PrintManager qui permet d'exporter en PDF
    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);

        StringBuilder filename = new StringBuilder(nomTrombi);

        if(observingGroups && trombiViewModel.groupesWithEleves.getValue() != null){
            filename.append(" - ").append(trombiViewModel.groupesWithEleves.getValue().getGroupe().getNomGroupe());
        }

        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(filename.toString());

        if(printManager != null){
            printManager.print(filename.toString(), printAdapter, new PrintAttributes.Builder().build());
        }

    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.export_trombi_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item){
        switch(item.getItemId()){
            case R.id.VUETROMBI_exporterTrombi:
                return true;

            case R.id.VUETROMBI_exporterPdf:
                createWebPrintJob(webView);
                return true;

            case R.id.VUETROMBI_exporterImg:
                CompletableFuture
                        .supplyAsync(() -> ImageUtil.saveImageFromWebview(webView,
                                getExternalFilesDir(null).getPath(),
                                nomTrombi))
                        .thenAccept(isSaved -> Logger.logV("IMG", "Image sauvée ?: " + isSaved));
                return true;

            case R.id.VUETROMBI_exporterListe:
                checkWriteExportedList();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
