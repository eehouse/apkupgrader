/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2019 by Eric House (eehouse@eehouse.org).  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.android.libs.apkupgrader;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class Updater {
    private static final String TAG = Updater.class.getSimpleName();

    /**
     * @param(appID) your app's appID (likely BuildConfig.APPLICATION_ID)
     *
     * @param(versionCode) your app's versionCode (likely BuildConfig.VERSION_CODE)
     *
     * @param(buildType) your app's versionCode (likely BuildConfig.BUILD_TYPE)
     *
     * @param(flavor) your app's versionCode (likely BuildConfig.FLAVOR)
     */
    
    public static void run( Context context, String appID, int versionCode,
                            String buildType, String flavor, String channelID, int iconID )
    {
        PackageManager  pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo( appID, 0 );
            String sum = figureMD5( new File( pi.applicationInfo.sourceDir ) );
            Log.d( TAG, "got sum for " + pi.applicationInfo.sourceDir + ": " + sum );

            new FetchTask(context, appID, versionCode, buildType, flavor, sum, channelID, iconID)
                .execute();
        } catch ( PackageManager.NameNotFoundException e ) {
        }
    }

    // https://stackoverflow.com/questions/13152736/how-to-generate-an-md5-checksum-for-a-file-in-android
    private static String figureMD5( File updateFile )
    {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch ( NoSuchAlgorithmException e ) {
            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Exception while getting FileInputStream", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int nRead;
        try {
            while ((nRead = is.read(buffer)) > 0) {
                digest.update( buffer, 0, nRead );
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception on closing MD5 input stream", e);
            }
        }
    }

    private static class FetchTask extends AsyncTask<Void, Void, String> {
        private Context mContext;
        private String mAppID;
        private int mVersionCode;
        private String mBuildType;
        private String mFlavor;
        private String mMd5Sum;
        private String mChannelID;
        private int mIconID;
        private String mAppName;

        public FetchTask( Context context, String appID, int versionCode, String buildType,
                          String flavor, String md5Sum, String channelID, int iconID )
        {
            mContext = context;
            mAppID = appID;
            mAppName = nameFor( context, appID );
            mVersionCode = versionCode;
            mBuildType = buildType;
            mFlavor = flavor;
            mMd5Sum = md5Sum;
            mChannelID = channelID;
            mIconID = iconID;
        }

        @Override
        protected String doInBackground( Void... unused )
        {
            String result = null;
            try {
                JSONObject params = new JSONObject();
                params.put("appID", mAppID);
                params.put("variant", mFlavor);
                params.put("versionCode", mVersionCode);
                params.put("type", mBuildType);
                params.put("md5sum", mMd5Sum);
                String paramsStr = params.toString();

                Map<String, String> paramsMap = new HashMap<String, String>();
                paramsMap.put( "params", paramsStr );
                paramsStr = getPostDataString( paramsMap );
            
                String url = "https://eehouse.org/apk_updater.py/check";
                HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();

                conn.setRequestMethod( "POST" );
                conn.setDoInput( true );
                conn.setDoOutput( true );
                conn.setFixedLengthStreamingMode( paramsStr.length() );

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer
                    = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write( paramsStr );
                writer.flush();
                writer.close();
                os.close();

                int responseCode = conn.getResponseCode();
                if ( HttpsURLConnection.HTTP_OK == responseCode ) {
                    InputStream is = conn.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream( is );

                    ByteArrayOutputStream bas = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    for ( ; ; ) {
                        int nRead = bis.read( buffer );
                        if ( 0 > nRead ) {
                            break;
                        }
                        bas.write( buffer, 0, nRead );
                    }
                    result = new String( bas.toByteArray() );
                } else {
                    Log.w( TAG, "runConn: responseCode: " + responseCode
                           + "/" + conn.getResponseMessage() + " for url: "
                           + conn.getURL() );
                    logErrorStream( conn.getErrorStream() );
                }

                Log.d( TAG, "doInBackground() => " + result );
            } catch (JSONException jex) {
            } catch (MalformedURLException mex) {
            } catch (IOException ioe) {
            }
            return result;
        }

        @Override
        protected void onPostExecute( String jsonStr )
        {
            Log.d( TAG, "got " + jsonStr);
            if ( null != jsonStr ) {
                try {
                    JSONObject json = new JSONObject( jsonStr );
                    if ( json.optBoolean( "success", false ) ) {
                        String url = json.optString("url", null);
                        if ( url == null ) { // nothing there!
                            String msg = mContext.getString(R.string.toast_nothing_newer_fmt, "foo");
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                        } else {
                            Intent intent = DwnldActivity.makeIntent( mContext, url, mAppID, mAppName );
                            postLaunchNotification( intent );
                        }
                    }
                } catch (JSONException ex) {
                }
            }
        }

        private void postLaunchNotification( Intent intent )
        {
            PendingIntent pi = PendingIntent
                .getActivity( mContext, 1000, intent, PendingIntent.FLAG_ONE_SHOT );

            Notification notification =
                new NotificationCompat.Builder( mContext, mChannelID )
                .setContentIntent( pi )
                .setSmallIcon( mIconID )
                .setContentTitle( mContext.getString(R.string.notify_title_fmt, mAppName) )
                .setContentText( mContext.getString(R.string.notify_body) )
                .setAutoCancel( true )
                .build();

            NotificationManager nm = (NotificationManager)
                mContext.getSystemService( Context.NOTIFICATION_SERVICE );
            nm.notify( mIconID, notification );
        }
    }

    private static String getPostDataString( Map<String, String> params )
    {
        String result = null;
        try {
            ArrayList<String> pairs = new ArrayList<String>();
            // StringBuilder sb = new StringBuilder();
            // String[] pair = { null, null };
            for ( Map.Entry<String, String> entry : params.entrySet() ){
                pairs.add( URLEncoder.encode( entry.getKey(), "UTF-8" )
                           + "="
                           + URLEncoder.encode( entry.getValue(), "UTF-8" ) );
            }
            result = TextUtils.join( "&", pairs );
        } catch ( java.io.UnsupportedEncodingException uee ) {
            Log.e( TAG, "unsupported encoding: " + uee );
        }

        return result;
    }

    private static void logErrorStream( InputStream is )
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for ( ; ; ) {
                int length = is.read( buffer );
                if ( length == -1 ) {
                    break;
                }
                baos.write( buffer, 0, length );
            }
            Log.e( TAG, baos.toString() );
        } catch (Exception ex) {
            Log.e( TAG, ex.getMessage() );
        }
    }

    private static String nameFor( Context context, String appID )
    {
        String result = null;
        PackageManager pm = context.getPackageManager();
        try {
            result = pm.getApplicationInfo(appID, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
        }
        return result;
    }
}
