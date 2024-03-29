package com.ufrst.app.trombi.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

// Pour les requêtes nécessitant une jointure
@Dao
public interface EleveGroupeJoinDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(EleveGroupeJoin eleveGroupeJoin);

    @Update
    void update(EleveGroupeJoin eleveGroupeJoin);

    @Delete
    void delete(EleveGroupeJoin eleveGroupeJoin);

    // GroupeWitheEleves sans Annotations (@Relation / @Junction)
    // représentant chaque groupe et sa liste d'élèves (sans les élèves soft deleted)
    @Transaction
    @Query("SELECT table_eleve.id_trombi, id_groupe, nom_groupe, table_eleve.is_deleted " +
            "FROM eleve_x_group " +
            "INNER JOIN table_eleve ON table_eleve.id_eleve = eleve_x_group.join_id_eleve " +
            "INNER JOIN table_groupe ON table_groupe.id_groupe = eleve_x_group.join_id_groupe " +
            "WHERE table_eleve.is_deleted = 0 " +
            "ORDER BY table_eleve.nom_prenom")
    LiveData<List<GroupeWithEleves>> getGroupesWithEleves();

    // Retourne un objet GroupeWithEleve avec un groupe d'un certain id
    // Comme Room utilisera deux requêtes pour nous dans les coulisses, on annote une Transaction pour s'assurer que cela se passe atomiquement
    @Transaction
    @Query("SELECT * FROM table_groupe WHERE id_groupe=:idGroupe")
    LiveData<GroupeWithEleves> getGroupeByIdWithEleves(long idGroupe);

    // Retourne un objet EleveWithGroups avec un eleve d'un certain id
    @Transaction
    @Query("SELECT * FROM table_eleve WHERE id_eleve=:idEleve")
    LiveData<EleveWithGroups> getEleveByIdWithGroups(long idEleve);

    @Transaction
    @Query("SELECT * FROM table_eleve WHERE id_eleve=:idEleve")
    EleveWithGroups getEleveByIdWithGroupsNotLive(long idEleve);

    @Transaction
    @Query("SELECT * FROM table_eleve WHERE id_trombi=:idTrombi")
    List<EleveWithGroups> getEleveWithGroupsByTrombiNotLive(long idTrombi);

    // Supprime les cross refs d'un trombi
    @Query("UPDATE eleve_x_group SET is_deleted=:isDeleted WHERE join_id_trombi=:idTrombi")
    void softDeleteXRefsByTrombi(long idTrombi, int isDeleted);

    // Soft delete les cross refs d'un groupe
    @Query("UPDATE eleve_x_group SET is_deleted=:isDeleted WHERE join_id_groupe=:idGroupe")
    void softDeleteXRefsByGroupe(long idGroupe, int isDeleted);

    // Soft delete les cross refs d'un eleve
    @Query("UPDATE eleve_x_group SET is_deleted=:isDeleted WHERE join_id_eleve=:idEleve")
    void softDeleteXRefsByEleve(long idEleve, int isDeleted);

    // Supprime réellement les XRefs soft deleted
    @Query("DELETE FROM eleve_x_group WHERE is_deleted = 1")
    void deleteSoftDeletedXRefs();

}
