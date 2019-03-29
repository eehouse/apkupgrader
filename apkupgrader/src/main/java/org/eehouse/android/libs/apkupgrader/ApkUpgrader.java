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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class ApkUpgrader {
    private static final String TAG = ApkUpgrader.class.getSimpleName();
    private static final String HIDDEN_PREFS = TAG + ".hidden";

    public static enum Interval { NEVER, HOURLY, DAILY, WEEKLY, MONTHLY, }
    public static enum Scheme { HTTPS, HTTP, }

    private static enum Key { SCHEME, HOST, PATH, INTERVAL, APPID, VERSIONCODE,
                              BUILDTYPE, FLAVOR, CHANNELID, ICONID,
    }

    public static class Config {
        private Context mContext;

        private Config( Context context ) { mContext = context; }

        public Config setScheme( Scheme scheme )
        {
            store( mContext, Key.SCHEME, scheme.ordinal() );
            return this;
        }

        public Config setHost( String host )
        {
            store( mContext, Key.HOST, host );
            return this;
        }

        public Config setPath( String path )
        {
            store( mContext, Key.PATH, path );
            return this;
        }

        public Config setInterval( Interval interval )
        {
            store( mContext, Key.INTERVAL, interval.ordinal() );
            return this;
        }

        public Config setAppInfo( String appID, int versionCode,
                                  String buildType, String flavor )
        {
            store( mContext, Key.APPID, appID );
            store( mContext, Key.VERSIONCODE, versionCode );
            store( mContext, Key.BUILDTYPE, buildType );
            store( mContext, Key.FLAVOR, flavor );
            return this;
        }

        public Config setForNotifications( String channelID, int iconID )
        {
            store( mContext, Key.CHANNELID, channelID );
            store( mContext, Key.ICONID, iconID );
            return this;
        }
    }

    public static Config getConfig(Context context )
    {
        return new Config(context);
    }

    public static void checkNow( Context context )
    {
        String appID = loadString( context, Key.APPID );
        int versionCode = loadInt( context, Key.VERSIONCODE, 0 );
        String buildType = loadString( context, Key.BUILDTYPE );
        String flavor = loadString( context, Key.FLAVOR );
        String channelID = loadString( context, Key.CHANNELID );
        int iconID = loadInt( context, Key.ICONID, 0 );

        Updater.run( context, appID, versionCode, buildType, flavor, channelID, iconID, true );
    }

    protected static String getUrl( Context context )
    {
        Scheme scheme = Scheme.values()[loadInt(context, Key.SCHEME, 0)];
        Uri uri = new Uri.Builder()
            .scheme(scheme == Scheme.HTTPS ? "https" : "http")
            .authority(loadString(context, Key.HOST))
            .appendPath(loadString(context, Key.PATH))
            .appendPath("apk_updater.py")
            .appendPath("check" )
            .build();
        String result = uri.toString();
        // Log.d( TAG, "getUrl() => " + result );
        return result;
    }


    private static void store( Context context, Key key, String val )
    {
        context.getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .edit()
            .putString( key.toString(), val )
            .apply()
            ;
    }

    private static void store( Context context,  Key key, int val )
    {
        context.getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .edit()
            .putInt( key.toString(), val )
            .apply()
            ;
    }

    private static String loadString( Context context, Key key )
    {
        String result = context
            .getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .getString( key.toString(), "" );
        return result;
    }

    private static int loadInt( Context context, Key key, int dflt )
    {
        int result = context
            .getSharedPreferences( HIDDEN_PREFS, Context.MODE_PRIVATE )
            .getInt( key.toString(), dflt );
        return result;
    }
}
