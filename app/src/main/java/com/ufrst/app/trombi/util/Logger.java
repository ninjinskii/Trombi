package com.ufrst.app.trombi.util;

import android.util.Log;

public class Logger {
    public static void handleException(Throwable throwable){
        Log.e("_______________E_______________", "Une exception s'est produite: \n\n", throwable);
    }

    public static void logV(String clue, String message){
        Log.v("__________" + clue + "__________", message);
    }

    public static void logV(String message){
        Log.v("____________________", message);
    }
}
