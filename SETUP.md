# Setup

## Setup for older versions
See [SETUP-old.md](/SETUP-old.md)

## Giphy
See [here](/GIPHY.md).

## Reddit Client ID
A Reddit Client ID is needed to access Reddit from 3rd party clients.

### Reddit Client ID creation steps
![Create application](assets/screenshots/create_application.png)

1. Go to [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) and login if
necessary
2. Click `create another app...`. Do not re-use any Client ID for any app other
than Continuum.
3. Set the name to Continuum
4. Set the type to `installed app`
5. Set redirect uri to `continuum://localhost`. If the redirect uri is set
incorrectly it won't work.
6. Complete the `reCAPTCHA`
7. Click `create app`
8. Copy the Client ID of your newly created app. It is recommended to save it
in the notes of your entry for Reddit in your password manager.

![Client ID](assets/screenshots/client_id.png)

> [!NOTE]
>
> This is just an example Client ID. It was created and deleted. Keep
> yours private.
>
> *It is reccomended to create the Client ID using a non banned account.*

### Adding a Reddit Client ID to Continuum
The method of adding a Client ID to Continuum depends on whether this is the
first time the app is being set up.

**Initial setup:**
1. Open Continuum and press `GET STARTED`
2. Select your theme colors, if you like, and press `DONE`
3. Enter your Client ID and press `OK`
4. Wait for Continuum to restart

**Changing the Client ID:**
1. Go to `Settings in the side bar`
<img src="/assets/screenshots/settings.png" alt="drawing" width="189.5" height="400" style="object-fit: contain;"/>
  
2. Select `Reddit API Client ID`
<img src="/assets/screenshots/continuum_client_id1.png" alt="drawing" width="189.5" height="400" style="object-fit: contain;"/>
  
3. Press `Reddit API Client ID`
<img src="/assets/screenshots/continuum_client_id2.png" alt="drawing" width="189.5" height="400" style="object-fit: contain;"/>
  
4. Enter your `Client ID`
<img src="/assets/screenshots/enter_client_id.png" alt="drawing" width="189.5" height="400" style="object-fit: contain;"/>
It is best to copy and paste it.

6. Press `OK` and Wait for Continuum to restart

> [!NOTE]
>
> *This is only needed once, even if you have multiple accounts.*

  
# Common errors


> [!NOTE]
>
> *The most likely cause for this is the `redirect uri` is set incorrectly. The
> big tell is if you can view Reddit in guest mode, aka without logging in.*


### Correct username and password does not work
Continuum depends on
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
by default for logging into Reddit. So if having the login issue, you can as a workaround use the login button located at the bottom of your screen which opens up reddit in your default browser for you to login. Keep in mind this method tends to not work with hardened browsers like Fennec. Here are some of the browsers which are guaranteed to work with this method; 

 <img src="/assets/screenshots/login_button.png">

- Brave

- Chrome

Alternatively, you can update to the latest version of Android
possible, and then the latest version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
possible.

Reddit's login password now requires
[XHR](https://en.wikipedia.org/wiki/XMLHttpRequest) to work. Older versions of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
don't support [XHR](https://en.wikipedia.org/wiki/XMLHttpRequest).

The current and known good version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
is `131.0.6778.135`.

#### WebView updating
Updating
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
can be tricky. You likely can't search and see it in the
[Google Play Store](https://play.google.com/store/games) app on your phone.

The best way is to find the app in the `Apps` section of `Settings`. The search
box in the top right can make it easier to find in the long list of apps. Once
you select
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
go to the bottom. You can see your version. Click on `App details`. This will
take you directly to
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
listing in the [Google Play Store](https://play.google.com/store/games) app. If
there is an update available it will be shown.

#### Alternative versions of WebView
There are
[Dev](https://play.google.com/store/apps/details?id=com.google.android.webview.dev),
[Beta](https://play.google.com/store/apps/details?id=com.google.android.webview.beta),
and
[Canary](https://play.google.com/store/apps/details?id=com.google.android.webview.canary)
versions of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview).
These aren't recommended, but under rare circumstances they might be useful to
get a newer version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview).

Once installed you need to enable
[Developer options](https://developer.android.com/studio/debug/dev-options),
you can go to them in `Settings`. Within is an option called
`WebView implementation` where you can pick which `WebView` is active.
