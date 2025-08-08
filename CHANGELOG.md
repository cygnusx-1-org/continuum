# CHANGELOG

---

7.5.1.2 / 2025-8-8
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Added contains subreddit and users post filters #112
* Added thumbnails for crossposts
* Fixed crash when trying to select the camera with no permission
* Fixed Can't post my own GIFs #100
* Fixed Reduce the size of the placeholder preview image #53
  - If you don't like the divide line for posts in the compact mode, "Settings | Interface | Post | Compact Layout | Show Divider"
* Fixed "Remove Alls" -> "Remove All" #81
* Fixed Swap taps and long press in comments #111
* Fixed Duplicate image title downloads #101

7.5.1.2 / 2025-6-16
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Ability to hide user prefix in comments
* Fixed Make it so the user can choose the password for the backup and enter it for the restore #83
* Removed padding from comments to make them more compact
* Merged "Keep screen on when autoplaying videos" from upstream
* Fixed Download issue #98
* Fixed crash from not having a video location set

7.5.1.2 / 2025-6-16
============

* Ability to hide user prefix in comments
* Fixed Make it so the user can choose the password for the backup and enter it for the restore #83
* Removed padding from comments to make them more compact
* Merged "Keep screen on when autoplaying videos" from upstream
* Fixed Download issue #98
* Fixed crash from not having a video location set

7.5.1.1 / 2025-5-28
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Synced with upstream to 7.5.1
* Fixed Bug: Failed to download Tumblr videos #48
* Fxied App crashes while trying to download an image #78
* Fixed Crash when trying to download an image without the download location set #77
* Fixed 4x All as default #75

7.5.0.2 / 2025-5-24
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Reverted "* Fixed Reduce the size of the placeholder preview image #53"
* Fixed NSFW toggle button #73 via reverting the change by Infinity
* Fixed Please add the ability to have extra tabs at the top of the main page #64
* Changing app/build.gradle to build v7a and v8a builds

7.5.0.1 / 2025-5-12
============
* Fixed Downloaded video contains no sound #57
* Fixed Reduce the size of the placeholder preview image #53
* Synced with upstream to 7.5.0

7.4.4.4 / 2025-4-20
============
* Added support for inputting the client ID via a QR code
* Added child comment count next to comment score when the comment is collapsed
* Fixed Make the comments more compact #18
* Enabled "Swap Tap and Long Press Comments" by default
* Fixed Incorrect FAB icon and action in bottom bar customization #39
* Fixed Sensible download names #13  
* Fixed Simplify download path #38

7.4.4.3 / 2025-4-16
===================
* Changed internal name to org.cygnusx1.continuum #7
* Added support for Giphy API Key #20
* Changed the beginning of the backup filename to Continuum
* Fixed Rename all instances of Sensitive Content to NSFW #19
* Removed toggle to allow not backing up accounts and api keys #21 #22
* Fixed “Swipe vertically to go back” still active on gifs and videos when disabled #6
* Removed all references to random since Reddit removed it #11

7.4.4.2 / 2025-4-14
===================
* Added support for backing up and restoring all settings other than Security
* Added toggle to backup accounts and the client id, and it is enabled by default
* Removed rate this app in About
* Fixed link to subreddit in About
* Removed more branding

7.4.4.1 / 2025-4-10
===================
* Initial release based on Infinity for Reddit
* Removed most of the Infinity for Reddit branding
* Added a new icon
* Changed the user agent and redirect URL
* Added a dynamic Client ID setting
* Added Solarized Amoled theme
