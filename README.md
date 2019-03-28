# apkupgrader
library for checking server for newer apk then downloading and installing it

<h2>What problem does this solve?</h2>
<p>I have several apps now that are available 
from the Google Play Store <em>and</em> from other places like github.
The Play Store does a good job of upgrading, but the others don't.
This library addresses that: it lets my app periodically check for upgrades
and offer to install them when found.
</p>

<h2>Assumptions</h2>
<ul>
<li>You have a server. That's where your new .apks will live. Without that this library doesn't help. (I'd love 
to be able to make it pick up new .apks on GitHub, say, but haven't figured that out. Got a solution? Merge request, 
or just email me.)</li>

<li>Your server does mod_python, or you're willing to script the server-side yourself. (There's a 
Python script provided with the library that does the server end of the upgrade conversation. You should
be able to just drop it into a mod_python environment. Otherwise you can use it as sample code and 
write your own backend.)</li>

<li>Your server has aapt on it. Hey, aapt's in Debian now so why not?</li>

</ul>

<h2>Design</h2>
<p>Basically, you have on your server a directory tree where you put .apks as you release them, organizing them
in directories by appID, release/debug and variant/flavor (optional). The client side sends a json including
these fields along with the versionCode and md5sum of the currently installed .apk. The server looks at all .apks
that match, and returns the newest that has at least as high a versionCode and whose md5sum does not match.
The client library then posts a notification that when selected downloads the .apk and passes it to the installer,
which will in turn prompt the user to enable side-loads and then install the downloaded .apk.</p>

<p>(The server-side script returns an error message when the directory that corresponds to an upgrade request
doesn't exist, so there's no mystery what directories to create as you're setting up.)</p>

<p>I do builds using Travis which is configured to upload each new build to the right directory on my server.
So I do a push to github and five minutes later this library will, if invoked, notice the new .apk. (By default
it will only check once/week, but there's also support for a manual Check Now option.)</p>
