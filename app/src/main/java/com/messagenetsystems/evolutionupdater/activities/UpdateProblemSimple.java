package com.messagenetsystems.evolutionupdater.activities;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.R;

public class UpdateProblemSimple extends Activity {
    private static final String TAG = UpdateProblemSimple.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_problem_simple);
        final String TAGG = "onCreate: ";
        Log.v(TAG, TAGG+"Invoked.");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        Log.v(TAG, TAGG+"Invoked.");
    }
}
