package com.messagenetsystems.evolutionupdater.threads;

/** DownloadFileInBackground
 *
 * Downloads a file in a background thread. That's all.
 * It makes no effort to check whether succeeded or not.
 *
 * Revisions:
 *  2018.10.18  Chris Rider     Created.
 *  2018.10.19  Chris Rider     Added integrity checking.
 *  2018.02.11  Chris Rider     Added "callback" parameter capability. If provided, we will execute it when file is finished.
 *  2018.02.12  Chris Rider     Added custom timeout capability.
 *  2019.04.05  Chris Rider     Deprecated! (replaced with Android DownloadManager)
 */

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.AsyncTaskCompleted;
import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

public class DownloadFileInBackground extends AsyncTask<String, Integer, String> {
    private String TAG = DownloadFileInBackground.class.getSimpleName();

    // Constant values...
    public static final int RETRIES_FOREVER = Integer.MAX_VALUE;
    public static final int RETRIES_VERY_LONG = Integer.MAX_VALUE - 1;
    private static final int FILETYPE_APK = 0;
    private static final int FILETYPE_MD5 = 1;
    public static final String RESULT_UNKNOWN = "Unknown";
    public static final String RESULT_ALREADY_DOWNLOADING = "Already downloading";
    public static final String RESULT_DOWNLOADING = "Downloading";
    public static final String RESULT_ALREADY_DOWNLOADED = "Already downloaded";
    public static final String RESULT_DOWNLOADED = "Downloaded";
    public static final String RESULT_DOWNLOAD_CANCELLED = "Download cancelled";

    private final ThreadLocal<Context> mContext = new ThreadLocal<>();
    private String serverIP;
    private String serverPath;
    private String localPath;
    private String filename;
    private String fileURL;
    private int fileType;
    private String packageName;
    private String packageName_short;
    private String packageName_evolution;
    private String packageName_evolutionupdater;
    private String packageName_evolutionwatchdog;
    private SystemFunctions systemFunctions;
    private int retriesRequested;
    private int retriesRemaining;
    private int timeoutSeconds;
    private String notifText_updateReady;
    private String notifText_updateDownloading;
    private String notifText_updateRetrying;
    private String notifText_updateDownloadFailed;
    private String notifText_updateDownloadCancelled;
    private int prevProgress = 0;
    private static boolean doStopDownload;
    private String whatToNotifyWhenDone;

    private AsyncTaskCompleted onDownloadCompletedListener;

    /* Constructor */
    public DownloadFileInBackground(Context context, int retries, @Nullable AsyncTaskCompleted onDownloadCompletedListener) {
        serverIP = MainUpdaterService.serverIP;
        serverPath = MainUpdaterService.serverPath;
        localPath = MainUpdaterService.localPath;
        systemFunctions = new SystemFunctions(context);
        retriesRequested = retries;
        retriesRemaining = retriesRequested;
        doStopDownload = false;
        timeoutSeconds = 2 * 60; //default timeout to use

        mContext.set(context);  //thread context

        this.onDownloadCompletedListener = onDownloadCompletedListener;

        notifText_updateReady = context.getResources().getString(R.string.notification_text_updateReady);
        notifText_updateDownloading = context.getResources().getString(R.string.notification_text_updateDownloading);
        notifText_updateRetrying = context.getResources().getString(R.string.notification_text_updateDownloadRetrying);
        notifText_updateDownloadFailed = context.getResources().getString(R.string.notification_text_updateDownloadFailed);
        notifText_updateDownloadCancelled = context.getResources().getString(R.string.notification_text_updateDownloadCancelled);

        packageName_evolution = context.getResources().getString(R.string.appPackageName_evolution);
        packageName_evolutionupdater = context.getResources().getString(R.string.appPackageName_evolutionUpdater);
        packageName_evolutionwatchdog = context.getResources().getString(R.string.appPackageName_evolutionWatchdog);

        this.whatToNotifyWhenDone = null;
    }

    /* Before starting background thread */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        final String TAGG = "onPreExecute: ";
        Log.v(TAG, TAGG+"Invoked");
    }

    /* Downloading file in background thread.
     * Whatever you return here will be passed to result of onPostExecute.
     * Note: Don't do any UI tasks here! */
    @Override
    protected String doInBackground(String... fileToDownload) {
        final String TAGG = "doInBackground ("+fileToDownload[0]+"): ";
        Log.v(TAG, TAGG+"Invoked");

        filename = fileToDownload[0];
        fileURL = "http://"+serverIP+"/"+serverPath+"/"+filename;
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;

        // Figure out package name from filename
        if (filename.contains(".apk")) {
            packageName = filename.replace(".apk", "");
        } else if (filename.contains(".md5")) {
            packageName = filename.replace(".md5", "");
        }
        packageName_short = packageName.replace("com.messagenetsystems.","");
        Log.d(TAG, TAGG+"Package name determined: "+packageName+" (short: "+packageName_short+")");

        // Figure out type of file
        if (filename.contains(".apk")) {
            fileType = FILETYPE_APK;
        } else if (filename.contains(".md5")) {
            fileType = FILETYPE_MD5;
        }

        // If we're downloading a package file that is currently in the process of downloading, skip doing it again
        // DEV-NOTE: Do this before the pre-existing check below, so it doesn't try to check an in-progress file.
        // DEV-NOTE: This probably isn't really needed, as these AsyncTasks will queue up and the check below will catch any duplicate attempts.
        if (fileType == FILETYPE_APK && (
                (packageName.equals(packageName_evolution) && MainUpdaterService.flag_isDownloading_evolution)
                || (packageName.equals(packageName_evolutionupdater) && MainUpdaterService.flag_isDownloading_evolutionupdater)
                || (packageName.equals(packageName_evolutionwatchdog) && MainUpdaterService.flag_isDownloading_evolutionwatchdog) )){
            Log.i(TAG, TAGG+"The package file to download is already currently being downloaded. No need to start a new download at this time.");
            return RESULT_ALREADY_DOWNLOADING;
        }

        // If we're downloading a package file that already exists on the device, avoid downloading it again.
        // DEV-NOTE: This depends on there already being an MD5 file downloaded, so make sure that has always happened, first.
        // DEV-NOTE: Do this after the isDownloading check above, so we don't try to check an in-progress file.
        if (fileType == FILETYPE_APK
                && systemFunctions.localPackageFileExists(packageName)
                && systemFunctions.localPackageFileIsValid(packageName)) {
            Log.i(TAG, TAGG + "The package file on the device is already-current. No need to download again.");
            systemFunctions.updateNotificationWithText(notifText_updateReady+" ("+packageName_short+")");
            return RESULT_ALREADY_DOWNLOADED;
        }

        // Try to actually start the download
        try {
            URL url = new URL(fileURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutSeconds*1000);
            connection.connect();

            // Expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    Log.w(TAG, TAGG + "HTTP reports 404 file not found.");
                    systemFunctions.updateNotificationWithText(notifText_updateDownloadFailed + " ('"+fileURL+"' not found)");
                    return "HTTP 404 file-not-found " + connection.getResponseCode() + ": " + connection.getResponseMessage();
                } else {
                    Log.w(TAG, TAGG + "HTTP reports something other than 200 (" + String.valueOf(connection.getResponseCode()) + ").");
                    systemFunctions.updateNotificationWithText(notifText_updateDownloadFailed + " (HTTP error)");
                    return "Unexpected HTTP response-code " + connection.getResponseCode() + ": " + connection.getResponseMessage();
                }
            }

            // If we got to here, then we will actually start to download...
            Log.i(TAG, TAGG+"Downloading: "+fileURL);

            // Set flags
            MainUpdaterService.flag_isDownloading = true;                                           //set global generic flag for some file is downloading
            MainUpdaterService.currentDownloadProgress = 0;                                         //initialize global generic progress variable
            MainUpdaterService.getTimeOfLastDownloadProgressUpdate_raw = 0;                         //initialize
            systemFunctions.updateNotificationWithText(notifText_updateDownloading+" ("+packageName_short+")");                //update notification
            if (fileType == FILETYPE_APK) {                                                         //update discrete package isdownloading flags, depending on what we're downloading now
                if (packageName.equals(packageName_evolution)) {
                    MainUpdaterService.flag_isDownloading_evolution = true;
                } else if (packageName.equals(packageName_evolutionupdater)) {
                    MainUpdaterService.flag_isDownloading_evolutionupdater = true;
                } else if (packageName.equals(packageName_evolutionwatchdog)) {
                    MainUpdaterService.flag_isDownloading_evolutionwatchdog = true;
                } else {
                    Log.e(TAG, TAGG + "Unhandled case, trying to determine whether to set discrete package download flag.");
                }
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            output = new FileOutputStream(localPath+"/"+filename);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            prevProgress = 0;   //initialize
            while ((count = input.read(data)) != -1) {
                // allow canceling of asynctask gracefully
                if (doStopDownload || isCancelled()) {
                    input.close();
                    systemFunctions.updateNotificationWithText(notifText_updateDownloadCancelled+" ("+packageName_short+")");
                    return RESULT_DOWNLOAD_CANCELLED;
                }
                total += count;

                // publishing the progress....
                if (fileLength > 0) {// only if total length is known
                    publishProgress((int) (total * 100 / fileLength));
                }

                output.write(data, 0, count);
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            return "Exception: "+e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
                Log.e(TAG, TAGG+"Exception caught (can ignore?): "+ignored.getMessage());
            }

            if (connection != null)
                connection.disconnect();
        }
        return localPath+"/"+filename;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        final String TAGG = "onProgressUpdate: ";

        /*
        message = MainUpdaterService.msgHandler.obtainMessage();

        if (message != null) {
            Bundle msgObj = new Bundle();
            msgObj.putString(bundle_keyName_command, bundle_command_updateDownloadProgress);
            msgObj.putInt(bundle_keyName_progressInt, progress[0]);
            message.obj = msgObj;
            msgHandler_MainService.sendMessage(message);
        } else {
            Log.e(TAG, TAGG+"Could not get Message from Handler in MainUpdaterService, to send message. Aborting.");
        }
        */

        MainUpdaterService.getTimeOfLastDownloadProgressUpdate_raw = new Date().getTime();

        //just to make sure we don't spam resources
        if (progress[0] != prevProgress) {
            MainUpdaterService.currentDownloadProgress = progress[0];
            if (MainUpdaterService.downloadRetriesAttempted > 0) {
                systemFunctions.updateNotificationWithText(notifText_updateDownloading+" ("+packageName_short+")" + " " + progress[0] + "%, retry #"+MainUpdaterService.downloadRetriesAttempted);
            } else {
                systemFunctions.updateNotificationWithText(notifText_updateDownloading+" ("+packageName_short+")" + " " + progress[0] + "%");
            }
        }
        prevProgress = progress[0];
    }

    /* After completing background task */
    @Override
    protected void onPostExecute(String result) {
        final String TAGG = "onPostExecute: ";
        Log.v(TAG, TAGG+"Invoked");

        if (onDownloadCompletedListener != null)
            onDownloadCompletedListener.onAsyncTaskCompleted(result);

        // Reset global vars
        MainUpdaterService.flag_isDownloading = false;
        MainUpdaterService.currentDownloadProgress = 0;
        MainUpdaterService.getTimeOfLastDownloadProgressUpdate_raw = 0;
        MainUpdaterService.flag_isDownloading_evolution = false;
        MainUpdaterService.flag_isDownloading_evolutionupdater = false;
        MainUpdaterService.flag_isDownloading_evolutionwatchdog = false;

        Log.i(TAG, TAGG + "Download attempt of \"" + fileURL + "\" finished.");

        if (fileType == FILETYPE_APK) {
            Log.i(TAG, TAGG+"Checking downloaded package file's integrity against MD5 which should have already been downloaded...");
            if (systemFunctions.localPackageFileIsValid(packageName)) {
                Log.i(TAG, TAGG + "Downloaded package file is valid.");
                systemFunctions.updateNotificationWithText(notifText_updateReady+" ("+packageName_short+")");

                // Check for any resources we've been requested to update about being done
                if (whatToNotifyWhenDone == null) {
                    //nothing to notify
                } else if (whatToNotifyWhenDone.equals("checkForUpdatesThread")) {
                    Log.d(TAG, TAGG+"It was specified to notify CheckForUpdatesThread that we're done.");
                    //CheckForUpdatesThread.packageIsDownloading = null;   //this is how we tell this thread that we're done
                    MainUpdaterService.flag_isDownloadingPackage = null;
                }
            } else {
                Log.w(TAG, TAGG + "Downloaded package file is invalid.");

                // since we had some problem, let's figure out our retries...
                if (retriesRequested == RETRIES_FOREVER) {
                    retriesRemaining = retriesRequested;    //retries remaining never decrements
                } else {
                    retriesRemaining--;
                }

                // depending on retries left, proceed or don't
                if (retriesRemaining > -1) {
                    Log.i(TAG, TAGG+"Retrying download ("+retriesRemaining+" retries remaining).");
                    DownloadFileInBackground downloadFileInBackground_retry = new DownloadFileInBackground(mContext.get(), retriesRemaining, onDownloadCompletedListener);
                    MainUpdaterService.downloadRetriesAttempted++;
                    downloadFileInBackground_retry.execute(filename);

                    systemFunctions.updateNotificationWithText(notifText_updateRetrying+" ("+packageName_short+")");
                } else {
                    Log.w(TAG, TAGG+"No retries remaining.");
                    systemFunctions.updateNotificationWithText(notifText_updateDownloadFailed+" ("+packageName_short+")" +" "+MainUpdaterService.downloadRetriesAttempted+" retries");
                    MainUpdaterService.downloadRetriesAttempted = 0;
                }
            }
        }

        mContext.set(null);
    }

    public static void cancelDownload() {
        doStopDownload = true;
    }

    int setTimeoutSeconds(int timeoutSeconds) {
        final String TAGG = "setTimeoutSeconds: ";
        Log.v(TAG, TAGG+"Invoked with "+String.valueOf(timeoutSeconds)+" seconds.");

        if (timeoutSeconds == 0) {
            Log.w(TAG, TAGG+"Invalid value provided, not updating timeout.");
        } else {
            this.timeoutSeconds = timeoutSeconds;
        }

        Log.v(TAG, TAGG+"Set value to and returning "+String.valueOf(this.timeoutSeconds)+" seconds.");
        return this.timeoutSeconds;
    }

    String getCurrentDownloadFilename() {
        final String TAGG = "getCurrentDownloadFile: ";
        Log.v(TAG, TAGG+"Invoked.");
        return filename;
    }

    public void setWhatToNotifyWhenDone(String whatToNotify) {
        this.whatToNotifyWhenDone = whatToNotify;
    }
}
