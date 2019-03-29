/* -*- compile-command: "find-and-gradle.sh inXw4dDeb"; -*- */
/*
 * Copyright 2009-2012 by Eric House (xwords@eehouse.org).  All rights
 * reserved.
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

public class DwnldActivity extends Activity {
    private static final String TAG = DwnldActivity.class.getSimpleName();

    private static final String KEY_URL = "url";
    private static final String KEY_APPID = "appid";
    private static final String KEY_APPNAME = "name";

    public static Intent makeIntent(Context context, String url, String appID, String appName )
    {
        Intent intent = new Intent( context, DwnldActivity.class )
            .putExtra( KEY_URL, url )
            .putExtra( KEY_APPID, appID )
            .putExtra( KEY_APPNAME, appName )
            ;
        return intent;
    }

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        // requestWindowFeature( Window.FEATURE_LEFT_ICON );
        // getWindow().setFeatureDrawableResource( Window.FEATURE_LEFT_ICON,
        //                                                    R.drawable.icon48x48 );

        super.onCreate( savedInstanceState );

        setContentView( R.layout.download );

        startDownload();
    }

    private void startDownload()
    {
        Intent intent = getIntent();
        String url = intent.getStringExtra(KEY_URL);
        Log.d( TAG, "got url: " + url );
        String appID = intent.getStringExtra(KEY_APPID);
        Log.d( TAG, "got appID: " + appID );
        String appName = intent.getStringExtra( KEY_APPNAME );

        TextView label = (TextView)findViewById(R.id.dwnld_message);
        label.setText( getString( R.string.downloading_title_fmt, appName ) );

        new DownloadFilesTask(url, appID).execute();
    }

    private class DownloadFilesTask extends AsyncTask<Void, Void, File> {
        private ProgressBar mProgressBar;
        private Uri mURI;
        private String mAppID;

        public DownloadFilesTask( String uri, String appID )
        {
            mURI = Uri.parse( uri );
            mAppID = appID;

            mProgressBar = (ProgressBar)findViewById( R.id.progress_bar );
        }

        @Override
        protected File doInBackground( Void... unused )
        {
            File result = null;
            Log.d( TAG, "doInBackground()" );
            try {
                URI jUri = new URI( mURI.getScheme(),
                                    mURI.getSchemeSpecificPart(),
                                    mURI.getFragment() );
                URLConnection conn = jUri.toURL().openConnection();
                final int fileLen = conn.getContentLength();

                runOnUiThread( new Runnable() {
                        public void run() {
                            mProgressBar.setMax( fileLen );
                        }
                    });

                InputStream is = conn.getInputStream();
                String name = new File(mURI.getPath()).getName();
                result = saveToDownloads( is, name );
                is.close();
            } catch ( java.net.URISyntaxException use ) {
                Log.e( TAG, use.getMessage() );
            } catch ( java.net.MalformedURLException mue ) {
                Log.e( TAG, mue.getMessage() );
            } catch ( java.io.IOException ioe ) {
                Log.e( TAG, ioe.getMessage() );
            }
            return result;
        }

        @Override
        protected void onPostExecute( File apkFile )
        {
            if ( apkFile != null ) {
                Intent intent = makeInstallIntent( apkFile );
                Log.d( TAG, "calling startActivity(" + intent + ")");
                startActivity( intent );
            }
            finish();
        }

        private Intent makeInstallIntent( File apkFile )
        {
            Uri uri = FileProvider
                .getUriForFile( DwnldActivity.this, mAppID + ".provider", apkFile );
            Intent intent = new Intent( Intent.ACTION_VIEW )
                .setDataAndType( uri, "application/vnd.android.package-archive" )
                .addFlags( Intent.FLAG_ACTIVITY_NEW_TASK
                           | Intent.FLAG_GRANT_READ_URI_PERMISSION );
            return intent;
        }

        private int mTotalRead = 0;
        private void progressMade( int nRead )
        {
            mTotalRead += nRead;
            runOnUiThread( new Runnable() {
                    public void run() {
                        mProgressBar.setProgress( mTotalRead );
                    }
                });
        }

        private File saveToDownloads(InputStream is, String name ) throws IOException
        {
            Log.d( TAG, "saveToDownloads(" + name + ")");
            boolean success = false;
            File appFile = new File( getDownloadDir(), name );

            byte[] buf = new byte[1024*4];
            try {
                FileOutputStream fos = new FileOutputStream( appFile );
                boolean cancelled = false;
                for ( ; ; ) {
                    cancelled = isCancelled();
                    if ( cancelled ) {
                        break;
                    }
                    int nRead = is.read( buf, 0, buf.length );
                    if ( 0 > nRead ) {
                        break;
                    }
                    fos.write( buf, 0, nRead );
                    progressMade( nRead );
                }
                fos.close();
                success = !cancelled;
            } catch ( java.io.FileNotFoundException fnf ) {
                Log.e( TAG, fnf.getMessage() );
            } catch ( java.io.IOException ioe ) {
                Log.e( TAG, ioe.getMessage() );
            }

            if ( !success ) {
                appFile.delete();
                appFile = null;
            }
            return appFile;
        }

        private File getDownloadDir()
        {
            Log.d( TAG, "getDownloadDir()" );
            File result = null;
            for ( int attempt = 0; attempt < 3; ++attempt ) {
                Log.d( TAG, "attempt: " + attempt );
                switch (attempt) {
                case 0:
                    result = Environment.
                        getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    break;
                case 1:
                case 2:
                    String state = Environment.getExternalStorageState();
                    if ( !state.equals( Environment.MEDIA_MOUNTED ) ) {
                        continue;
                    }
                    result = Environment.getExternalStorageDirectory();
                    if ( 1 == attempt && result != null ) {
                        result = new File( result, "download/" );
                    }
                    break;
                }

                if ( null != result ) {
                    if ( !result.exists()) {
                        Log.d( TAG, result + " does not exist" );
                    } else if (! result.isDirectory()) {
                        Log.d( TAG, result + " not a directory" );
                    } else if (! result.canWrite() ) {
                        Log.d( TAG, result + " not writeable" );
                    } else {
                        // All's well
                        break;
                    }

                    result = null;
                }
            }
            Log.d( TAG, "getDownloadDir() => " + result );
            return result;
        }
    }

}
