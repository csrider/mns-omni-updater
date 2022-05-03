package com.messagenetsystems.evolutionupdater.threads;

/** SendAjaxRequest
 * Sends an AJAX request.
 * Simply provide everything necessary during instantiation and execution.
 *
 * Revisions:
 *  2018.10.19  Chris Rider     Created (modeled after evolution app's RequestActiveMessagesRecnos).
 */

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.messagenetsystems.evolutionupdater.SystemFunctions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SendAjaxRequest extends AsyncTask<Object, Void, Void> {
    private final String TAG = this.getClass().getSimpleName();

    private Context mContext;
    private SystemFunctions systemFunctions;

    private final int volleyTimeoutSeconds = 10;

    private String remoteHost;      //remote ajax host (e.g. the server hosting the ajax)
    private String remoteAjax;      //remote ajax target (e.g. the executable ajax file smajax.cgi)

    /** Constructor */
    public SendAjaxRequest(final Context context) {
        mContext = context;

        systemFunctions = new SystemFunctions(context);
    }

    /* VOLLEY METHOD - sometimes causes fatal exception "No implementation found for long com.android.tools.profiler.support.network.HttpTracker$Connection.nextId()..." */
    /* To fix, you have to turn off advanced profiling in your Android Studio settings */
    @Override
    protected Void doInBackground(Object... objects) {
        final String TAGG = "doInBackground: ";
        Log.v(TAG, TAGG+"Invoked.");

        final Context context = (Context) objects[0];
        mContext = context;

        final String ajaxCommand = "";
        final String url = "http://"+ remoteHost + remoteAjax +"?"+ ajaxCommand;

        final RequestQueue queue = Volley.newRequestQueue(context);
        queue.start();

        // Request a string response from the server's AJAX program
        // (this implicitly sends our trigger, we only care about response for status and logging)
        Log.d(TAG, TAGG+"Setting up an AJAX request to the server ("+url+") to trigger it to send us messages...");
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    JSONObject jsonServerResponse = null;
                    JSONArray jsonServerResponse_activeMessages = null;
                    int msgRecno = 0;

                    @Override
                    public void onResponse(String response) {
                        Log.v(TAG, TAGG+"Server AJAX response is:\n"+ response);

                        //do something with response here

                        queue.stop();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, TAGG+"Server AJAX didn't work! "+error.getMessage());
                        queue.stop();
                    }
                });
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                volleyTimeoutSeconds * 1000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        return null;
    }

    private JSONObject parseJsonResponse(String rawJson) {
        final String TAGG = "parseJsonResponse: ";
        JSONObject jsonObject = null;

        try {
            jsonObject = new JSONObject(rawJson);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return jsonObject;
    }
}
