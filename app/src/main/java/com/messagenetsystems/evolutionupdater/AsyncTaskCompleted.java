package com.messagenetsystems.evolutionupdater;

/** AsyncTaskCompleted
 *
 * Interface for custom "callback" capability for when an AsyncTask completes.
 *
 * Note: This is not technically a pure style of callback, but essentially allows us to use it like one.
 *       For simplicity, in this documentation, we just use the word "callback," though.
 *
 * Usage:
 *  In your custom class that extends AsyncTask (such as downloading a file), accept the callback as a parameter...
 *      AsyncTaskCompleted onDownloadCompletedListener;                                             //declare an instance of this interface in the main class' scope
 *      this.onDownloadCompletedListener = onDownloadCompletedListener;                             //initialize in constructor (onDownloadCompletedListener is passed in to constructor during instantiation)
 *      onDownloadCompletedListener.onAsyncTaskCompleted(result);                                   //call the specified callback and return result in onPostExecute (which is synonymous with AsyncTask completion)
 *
 *  In the code in which you want to implement an AsyncTask and on-completion callback/listener...
 *      Create your custom callback...
 *          public class MyCustomCallback implements AsyncTaskCompleted {
 *              @Override
 *              public void onAsyncTaskCompleted(String result) {
 *                  //do whatever you want here
 *              }
 *          }
 *      Use your custom callback...
 *          MyCustomCallback myCustomCallback;
 *          myCustomCallback = new MyCustomCallback();
 *          DownloadFileInBackground downloadFile;
 *          downloadFile = new DownloadFileInBackground(appContext, maxRetries, myCustomCallback);
 *          downloadFile.execute(fileUrlToDownload);
 *
 * Revisions:
 *  2019.02.11-12   Chris Rider     Created.
 */

public interface AsyncTaskCompleted {
    void onAsyncTaskCompleted(String result);
}
