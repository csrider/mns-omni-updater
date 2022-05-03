package com.messagenetsystems.evolutionupdater.receivers;

/** BackgroundStopGettingUpdatesReceiver
 * Handles receiving request to stop a background download of updates.
 *
 * Revisions:
 *  2018.10.26  Chris Rider     Created.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.threads.DownloadFileInBackground;

public class BackgroundStopGettingUpdatesReceiver extends BroadcastReceiver {
    private static final String TAG = BackgroundStopGettingUpdatesReceiver.class.getSimpleName();

    protected Context appContext;

    /** Constructor */
    public BackgroundStopGettingUpdatesReceiver(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        final String intentAction = context.getResources().getString(R.string.intentAction_triggerOmniUpdater_stopGettingUpdatesBackground);

        if (intent.getAction() != null
                && intent.getAction().equals(intentAction)) {

            // Set flag in DownloadFileInBackground to stop
            // (the function sets the flag, which is checked every download iteration)
            DownloadFileInBackground.cancelDownload();

        } else {
            Log.w(TAG, TAGG+"Intent action not available or did not match conditions needed to start updater activity.");
        }
    }
}
