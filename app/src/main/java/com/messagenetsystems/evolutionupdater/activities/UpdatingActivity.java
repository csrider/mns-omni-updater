package com.messagenetsystems.evolutionupdater.activities;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class UpdatingActivity extends Activity {
    private static final String TAG = UpdatingActivity.class.getSimpleName();

    private Context appContext;
    private TextView statusText2;
    private ProgressBar progressBar;

    private SystemFunctions systemFunctions;

    private String serverPath;
    private String localPath;

    private volatile boolean doneDownloadingAllFiles = false;

    private UpdateFileList updateFileList;
    private static volatile int updateFileCounter = 0;

    private static volatile String currentlyDownloadingFilename = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updating);

        final String TAGG = "onCreate: ";

        appContext = getApplicationContext();
        systemFunctions = new SystemFunctions(appContext);

        statusText2 = (TextView) findViewById(R.id.tvStatus2);
        statusText2.setText("loading...");

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);
        progressBar.setProgress(0);

        serverPath = appContext.getResources().getString(R.string.updatePackageServerPath);
        localPath = appContext.getResources().getString(R.string.updateFileDownloadPath);

        updateFileList = new UpdateFileList();
        updateFileList.add(new UpdateFile(appContext.getResources().getString(R.string.packageFilename_evolution), appContext.getResources().getString(R.string.appPackageName_evolution)));
        //updateFileList.add(new UpdateFile(appContext.getResources().getString(R.string.packageFilename_evolutionWatchdog), appContext.getResources().getString(R.string.appPackageName_evolutionWatchdog)));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";

        // Start the first file downloading...
        // When it's finished, it will initiate any further files necessary.
        initiateDownload(updateFileList.get(updateFileCounter));
    }

    private void initiateDownload(UpdateFile updateFile) {
        final String TAGG = "initiateDownload: ";
        Log.v(TAG, TAGG+"Invoked.");

        // Update globals
        currentlyDownloadingFilename = updateFile.filename;

        // Start the download
        new DownloadFileFromURL().execute("http://" + systemFunctions.serverIP + "/" + serverPath + "/" + updateFile.filename);
    }

    private class UpdateFile {
        String filename;
        String filehash;
        String packageName;
        boolean hasDownloaded;
        boolean isPackageFile;

        UpdateFile(String filename, String packagename) {
            this.filename = filename;
            this.filehash = "";
            this.packageName = packagename;
            this.hasDownloaded = false;
            this.isPackageFile = isPackageFile;
        }
    }
    private class UpdateFileList extends ArrayList<UpdateFile> {
        ArrayList<UpdateFile> updateFileList;

        UpdateFileList() {
        }
    }

    //<params, progress, result>
    private class DownloadFileFromURL extends AsyncTask<String, Integer, String> {
        final String TAGG = "DownloadFileFromURL: ";

        String fileURL = "";

        /* Before starting background thread */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(TAG, TAGG+"Starting download");

            statusText2.setText("Downloading: "+currentlyDownloadingFilename+"...");

            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
        }

        /* Downloading file in background thread
        * Note: Don't do any UI tasks here! */
        @Override
        protected String doInBackground(String... f_url) {
            fileURL = f_url[0];
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(f_url[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(localPath+"/"+currentlyDownloadingFilename);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            progressBar.setIndeterminate(false);    //if we get here, then we know size
            progressBar.setMax(100);
            progressBar.setProgress(progress[0]);
        }

        /* After completing background task */
        @Override
        protected void onPostExecute(String result) {
            Log.i(TAG, TAGG + "Download of \"" + fileURL + "\" complete.");
            statusText2.setText("Finished: " + currentlyDownloadingFilename);

            // Verify checksum to determine how to proceed from here...
            //  If matches what's expected, continue to next file (if there are more)
            //  Else try again because we apparently got a bad download

            // First make a request to get the checksum from server asynchronously and wait until we get it
            // Once we get it, continue

            new Thread(new Runnable(){
            //runOnUiThread(new Runnable(){
                public void run(){
                    try {
                        String checksumExpected;
                        systemFunctions.requestChecksumForPackageUpdateFileFromServer(updateFileList.get(updateFileCounter).packageName);
                        Log.v(TAG, TAGG+"Waiting for expected checksum from server...");
                        while (MainUpdaterService.serverChecksumRequestStatus.equals(SystemFunctions.CHECKSUM_REQUEST_SUBMITTED)) {}
                        Log.v(TAG, TAGG+"Got expected checksum from server ("+MainUpdaterService.serverChecksumRequestStatus+").");
                        checksumExpected = MainUpdaterService.serverChecksumRequestStatus;
                        MainUpdaterService.serverChecksumRequestStatus = "";    //reset it

                        // Second, calculate checksum of local just-downloaded file
                        String checksumActual = systemFunctions.calculateChecksumForLocalFile(localPath + "/" + currentlyDownloadingFilename);

                        // Now, compare them to see if we succeeded in getting the whole file
                        if (checksumExpected.equals(checksumActual)) {
                            Log.d(TAG, TAGG+"Checksum matches (file downloaded properly).");

                            //do the install of the succesfully downloaded package
                            systemFunctions.installPackage(updateFileList.get(updateFileCounter).packageName);

                            //initiate next file update
                            updateFileCounter++;
                            if (updateFileCounter < updateFileList.size()) {
                                initiateDownload(updateFileList.get(updateFileCounter));
                            }
                        } else {
                            Log.w(TAG, TAGG+"Checksum did not match (need to try again).");
                            initiateDownload(updateFileList.get(updateFileCounter));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
                    }
                }
            }).start();


            /*
            String checksumExpected = systemFunctions.requestChecksumForPackageUpdateFileFromServer(updateFileList.get(updateFileCounter).packageName);
            while (MainUpdaterService.serverChecksumRequestStatus.equals(SystemFunctions.CHECKSUM_REQUEST_SUBMITTED)) {}
            checksumExpected = MainUpdaterService.serverChecksumRequestStatus;
            MainUpdaterService.serverChecksumRequestStatus = "";    //reset it

            // Second, calculate checksum of local just-downloaded file
            String checksumActual = systemFunctions.calculateChecksumForLocalFile(localPath + "/" + currentlyDownloadingFilename);

            // Now, compare them to see if we succeeded in getting the whole file
            if (checksumExpected.equals(checksumActual)) {
                Log.d(TAG, TAGG+"Checksum matches (file downloaded properly).");
                updateFileCounter++;
                if (updateFileCounter < updateFileList.size()) {
                    initiateDownload(updateFileList.get(updateFileCounter));
                }
            } else {
                Log.w(TAG, TAGG+"Checksum did not match (need to try again).");
                initiateDownload(updateFileList.get(updateFileCounter));
            }
            */
        }
    }
}
