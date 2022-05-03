package com.messagenetsystems.evolutionupdater;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

public class StartupActivity extends Activity {
    private final static String TAG = StartupActivity.class.getSimpleName();

    private static final int MY_PERMISSIONS_EXT_WRITE = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String TAGG = "onCreate: ";
        Log.d(TAG, TAGG+"Invoked.");

        setContentView(R.layout.activity_startup);

        // Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "External storage write permission not granted, requesting now...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_EXT_WRITE);
        } else {
            Log.d(TAG, "All permissions have been granted.");
        }

        // Start our app's main service...
        Log.d(TAG, TAGG+"Starting MainService...");
        startService(new Intent(getApplicationContext(), MainUpdaterService.class));               //make the call to actually start the service

        // Close out this activity
        Log.d(TAG, TAGG+"Stopping activity...");
        finish();
    }

    @Override
    protected void onDestroy() {
        final String TAGG = "onDestroy: ";
        Log.d(TAG, TAGG+"Invoked.");

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        final String TAGG = "onRequestPermissionsResult: ";

        switch (requestCode) {
            case MY_PERMISSIONS_EXT_WRITE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission granted, allow continue
                    Log.i(TAG, TAGG + "Permission (WRITE_EXTERNAL_STORAGE) granted.");
                } else {
                    //permission denied, disallow continue
                    Log.w(TAG, TAGG + "Permission (WRITE_EXTERNAL_STORAGE) not granted. This is necessary for downloading update files.");
                }
                return;
            }
            //other 'case' lines to check for other permissions this app might request
        }
    }
}
