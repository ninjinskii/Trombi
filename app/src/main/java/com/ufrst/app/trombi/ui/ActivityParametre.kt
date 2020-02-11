package com.ufrst.app.trombi.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.RelativeLayout
import android.widget.Switch
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.chip.ChipGroup
import com.ufrst.app.trombi.R
import com.ufrst.app.trombi.ui.ActivityMain.*
import com.ufrst.app.trombi.util.Logger

class ActivityParametre : AppCompatActivity() {

    private val switchRatioLayout : RelativeLayout by bind(R.id.PARA_switchNbColLayout)
    private val switchQualityLayout : RelativeLayout by bind(R.id.PARA_switchQualityLayout)
    private val switchFixedRatio : Switch by bind(R.id.PARA_switchFixedRatio)
    private val switchQuality : Switch by bind(R.id.PARA_switchQuality)
    private val numberPicker : NumberPicker by bind(R.id.PARA_npCol)
    private val toolbar : Toolbar by bind(R.id.PARA_toolbar)
    private lateinit var prefs : SharedPreferences

    private var nbCols = -1
    private var isFixedRatio = true
    private var qualityOrLatency = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parametre)

        retrieveSharedPreferences()
        initializeComponent()
        setListeners()
        
        // Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setTitle(R.string.PARA_title)
    }

    private fun retrieveSharedPreferences(){
        prefs = getSharedPreferences("com.ufrst.app.trombi", Context.MODE_PRIVATE)

        with(prefs) {
            nbCols = getInt(PREFS_NBCOLS, 4)
            isFixedRatio = getBoolean(PREFS_FIXED_RATIO, true)
            Logger.logV("PARA, retrieveSharedPrefs", "isFixedRatio: $isFixedRatio")
            qualityOrLatency = getBoolean(PREFS_QUALITY_OR_LATENCY, false)
        }
    }

    // Actualise la valeur des composants selon les SharedPreferences
    private fun initializeComponent(){
        with(numberPicker){
            minValue = 1
            maxValue = 6
            value = nbCols
        }

        switchFixedRatio.isChecked = isFixedRatio
        switchQuality.isChecked = qualityOrLatency
    }

    private fun setListeners(){
        switchRatioLayout.setOnClickListener {
            Logger.logV("CLick", "ClickListenerSwitchRatio")
            switchFixedRatio.isChecked = !switchFixedRatio.isChecked
        }

        switchQualityLayout.setOnClickListener {
            switchQuality.isChecked = !switchQuality.isChecked
        }
    }

    // Fonction d'extention simplifiant les findViewById et permet de rendre les composants lazy-evaluated
    private fun <T : View> Activity.bind(@IdRes res : Int) : Lazy<T> {
        return lazy { findViewById<T>(res) }
    }

    override fun onPause() {
        prefs.edit().apply{
            putInt(PREFS_NBCOLS, numberPicker.value)
            Logger.logV("PARA, onPause", "switchFixedRatio: " + switchFixedRatio.isChecked)
            putBoolean(PREFS_FIXED_RATIO, switchFixedRatio.isChecked)
            putBoolean(PREFS_QUALITY_OR_LATENCY, switchQuality.isChecked)
            apply()
        }

        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return super.onSupportNavigateUp()
    }
}