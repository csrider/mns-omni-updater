package com.messagenetsystems.evolutionupdater.receivers;

/** BackgroundGetUpdatesReceiver
 * Handles receiving request to begin a background download of updates.
 *
 * When we receive the broadcast (containing intent extras defining what to download), we simply
 * enqueue the requested download into the Android DownloadManager, which handles everything for us.
 * This is a much more reliable way of doing it than the home-brewed solution.
 *
 * Revisions:
 *  2018.10.18  Chris Rider     Created.
 *  2018.10.30  Chris Rider     Begun prototyping a monitoring thread.
 *  2018.04.05  Chris Rider     Deprecated home-brewed AsyncTask download method, in favor of no longer downloading MD5 file and using Android DownloadManager for APK.
 */

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.threads.CheckForUpdatesThread;

import java.io.File;

public class BackgroundGetUpdatesReceiver extends BroadcastReceiver {
    private static final String TAG = BackgroundGetUpdatesReceiver.class.getSimpleName();

    protected Context appContext;
    final boolean DO_RESET_MAIN_FLAGS = true;

    /** Constructor */
    public BackgroundGetUpdatesReceiver(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        final String intentAction = context.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground);
        String filename;

        if (intent.getAction() != null
                && intent.getAction().equals(intentAction)) {

            // Get app package name to download from intent extras
            String appPackageName = intent.getStringExtra("appPackageName");

            // Get any other flags/params from intent
            String notifyWhenDone = intent.getStringExtra("notifyWhenDone");    //get the resource to notify, if provided with one

            // Construct a filename to pass to the download routine and do it
            filename = appPackageName+".apk";
            MainUpdaterService.downloadRetriesAttempted = 0;

            // Setup the file source
            Uri fileUri = Uri.parse("http://"+MainUpdaterService.serverIP+"/"+MainUpdaterService.serverPath+"/"+filename);

            // Setup the DownloadManager instance
            DownloadManager.Request request = new DownloadManager.Request(fileUri);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalPublicDir("/", filename);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);

            // Setup the file destination
            try {
                File destFile = new File(MainUpdaterService.localPath + "/" + filename);
                if (destFile.exists()) {
                    if (destFile.delete()) {
                        Log.d(TAG, TAGG+"Already-existing / older APK file successfully removed in preparation for downloading newer one.");
                    } else {
                        Log.e(TAG, TAGG + "Already-existing APK file could not be deleted. New file will download with different filename and update may not work!");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught trying to cleanup file download destination: "+e.getMessage());
            }

            // Instantiate DownloadManager and enqueue our file in it
            // (once that's done, it should start downloading it on its own)
            DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                final long downloadID = downloadManager.enqueue(request);

                if (downloadID > -1) {
                    // Update this package's download status, so we don't try to enqueue it again while in-progress
                    CheckForUpdatesThread.setPackageDownloadStatus(appPackageName, CheckForUpdatesThread.STATUS_DOWNLOAD_QUEUED);

                    Log.d(TAG, TAGG + "DownloadManager enqueued \"" + filename + "\" with id " + downloadID + " (it should now start downloading automatically).");

                    // Register a broadcast receiver to handle what happens when download is completed
                    // (note: we must do this after enqueueing so we know our download-ID to check for)
                    final BroadcastReceiver onDownloadComplete = new DownloadManagerCompletedReceiver(appContext, downloadID, appPackageName, filename, DO_RESET_MAIN_FLAGS, notifyWhenDone);
                    appContext.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));    //NOTE: this will attempt to self-unregister when complete
                } else {
                    // Reset the package's download status, since we apparently failed to enqueue it... this way it may be retried
                    CheckForUpdatesThread.setPackageDownloadStatus(appPackageName, CheckForUpdatesThread.STATUS_DOWNLOAD_UNKNOWN);

                    Log.w(TAG, TAGG+"DownloadManager did not return a download ID. Download most likely was not queued!");
                }
            } else {
                Log.e(TAG, TAGG+"Failed to create a DownloadManager instance!");
            }

        } else {
            Log.w(TAG, TAGG+"Intent action not available or did not match conditions needed to start updater activity.");
        }
    }
}
