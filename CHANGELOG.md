# CHANGELOG

---

8.3.3.9.1.25-beta.1 / 2026-11-5
============
* Removed Compact Layout and Card Layout 2. A feed or setting that used Compact Layout now uses Compact Layout 2, and one that used Card Layout 2 now uses Card Layout
* Renamed Card Layout to Card Bottom and Card Layout 3 to Card Top
* The per-layout column counts for the removed layouts are gone from `Settings > Post > The Number of Columns in Post Feed`

8.3.3.9.1.24-beta.1 / 2026-11-5
============
* Internal clean-up with no change in what you see: the compact post rows (Compact, Compact 2 and the Card 2 link rows) now live in their own files

8.3.3.9.1.23-beta.2 / 2026-11-5
============
* Internal clean-up with no change in what you see: the shared base of every post row (voting, saving, sharing, tapping names) now lives in its own file

8.3.3.9.1.22 / 2026-11-5
============
* Internal clean-up with no change in what you see: the post list code uses one named variable per row type instead of repeating the same cast several hundred times, and post filters split their comma-separated lists once instead of for every post

8.3.3.9.1.21 / 2026-11-5
============
* Internal clean-up with no change in what you see: dates are formatted with a reused formatter instead of a new one for every post and comment, the "how long ago or the date" choice lives in one place, and the backup code is shorter

8.3.3.9.1.20 / 2026-11-4
============
* Internal clean-up with no change in what you see: one shared JSON reader instead of a new one for every call, filter regexes compiled once instead of for every post, and shorter code for the feed settings that redraw the list

8.3.3.9.1.19 / 2026-11-4
============
* Decoder: added base64. It decodes only when the bytes are valid UTF-8 and free of control characters, so ordinary long words and IDs are left alone. The box is labelled `B64`
* Decoder: a paragraph with binary shows only the binary, and base64 is tried only when binary gave nothing. A text already shown anywhere in a comment is not shown again, whichever decoder found it
* Decoder: the search buttons now look for the `@handles` in the text if it has any, otherwise the names left once its links are removed, and open one search for each name (up to four)
* Decoder: the label never wraps, and the decoded text is one line that scrolls sideways. Multi-line results keep their lines for Copy
* The setting is now called `Decode binary and base64`

8.3.3.9.1.18 / 2026-11-4
============
* Undoing a hide now puts the post back in the feed straight away, like hiding takes it out. In the Hidden list it works the other way round: unhiding takes the post out of the list
* Post details: a new image button beside Save opens every image and GIF in the comments as the vertical list. Each one is captioned with the commenter
* Added `Settings > Interface > Post > Hide Vote Buttons` and the same under `Post Details`, which hide the upvote and downvote buttons and the vote count. Swipe actions that vote still work

8.3.3.9.1.17 / 2026-11-3
============
* Renamed the remaining Continuum names in the code: the lint checks, the post filter code, the comments, the release scripts and the Gradle release settings
* The login redirect entry for custom API keys is now `redditjr://localhost` instead of `continuum://localhost`. The built-in RedReader login is unchanged
* Renamed the `r/ContinuumAll` feed to `r/RedditJrAll`. Saved post filters are moved to the new name, and the old name still opens the same feed
* Decoder: a link with a trailing slash, such as `https://link.me/name/`, now searches for `name` instead of the whole link
* Decoder buttons are now `TITLE`, `ALL` and `SOCIALS`

8.3.3.9.1.16 / 2026-11-3
============
* The card border gradient on NSFW posts no longer depends on `Hide NSFW Badge`
* Removed `Show NSFW in the Post Type Badge`. The NSFW badge is a badge of its own again, and `Hide NSFW Badge` still hides it
* Removed the unused Recently Visited and API recording settings files from Backup & Restore and the account settings. Backups that contain them still restore

8.3.3.9.1.15 / 2026-11-3
============
* Removed the API statistics feature: the screen, the code that recorded API calls, and its database table
* Removed the `recently_visited` database table. Recents is unchanged, since it is kept in settings and never used that table. Old backups that contain the table still restore, and the table is simply skipped

8.3.3.9.1.14 / 2026-11-3
============
* The Post Type Card Border no longer depends on `Hide Post Type`
* On NSFW posts the card border is a gradient from the post type's color on the left to the NSFW color on the right
* The vertical gallery list now plays GIFs when `Video Autoplay` allows it, and preloads the next pictures
* Removed `Settings > Debug`
* `Privacy Policy` now opens a real policy, PRIVACY.md in the repository
* With `Collapse Stickied Posts` on, tap the pin on an opened-up stickied post to fold it back into one line
* Removed Recently Visited: the sidebar row, its screen and its Settings screen. Recents covers it. Your Backup & Restore files still work
* GIF cards in the feed stop animating while a gallery or other screen is open over it

8.3.3.9.1.13 / 2026-11-3
============
* Hiding a post now takes it out of the feed straight away, instead of leaving it there until you refresh
* Added `Settings > Post > Collapse Stickied Posts`, which shows a subreddit's stickied posts as a single line. Tap one to open it
* Added `Settings > Post > Post Type Card Border`, a thin line across the top of Card Layout 1 and 3 cards in the post type's color
* Card Layout 1 and 3 have a smaller gap between cards
* The vertical gallery list loads screen-sized pictures instead of full-size ones, and shows GIFs as stills, so it scrolls smoothly. Tap one to see it play
* Collapsed comments with the old green background, saved in your applied theme, now show no background too
* `Check for updates` moved out of About to the top level of Settings

8.3.3.9.1.12 / 2026-11-3
============
* Collapsed comments no longer have a bright green background. The built-in themes now give them none, since the collapsed row and its text already say so. A custom theme you made keeps its own color, which you can change under the theme's `Fully Collapsed Comment Background`
* Shadowbox now always autoplays its videos and GIFs, whatever `Video Autoplay` is set to. `Mute Autoplaying Videos` still applies
* Added TODO.md, which tracks what is left to rename and the features put off

8.3.3.9.1.11 / 2026-11-3
============
* Gallery posts opened from the feed or a post now show every image in one continuous vertical list. Tap an image to open the swipe viewer on it, with zoom, download and share, and press back to return to the list
* Fixed `Hide Subreddit Icon`, which left the icon in place
* Removed the remaining Continuum names, links and art from the app: the About screen's email, Reddit account and subreddit rows, the splash screen astronaut, and the name in the text
* Removed the Patcheddit notice, since the decoder is original work

8.3.3.9.1.10 / 2026-11-3
============
* Added reverse image search (Google, Yandex or both) to the post options menu for image, GIF and gallery posts, so it works from the feed
* Recents: each row has a pin button, to keep it at the top, and an X button, to remove it. `Settings > Navigation Drawer > Number of Recents` sets how many of the rest are shown
* Added `Settings > Interface > Hide Subreddit Icon`
* The decoder now only decodes binary. NATO phonetic decoding is gone, and the setting is now `Decode binary`

8.3.3.9.1.9 / 2026-11-3
============
**Installs as a new app, again.** The application ID changed from `org.cygnusx1.redditjr` to `io.github.jayblah.redditjr`, which is yours instead of Continuum's namespace. Android treats it as a different app, so it does not update the previous one. Back up your settings in the old app under `Settings > Backup & Restore`, install this, and restore them.

8.3.3.9.1.8 / 2026-11-3
============
**Installs as a new app.** The application ID changed from `org.cygnusx1.continuumjr` to `org.cygnusx1.redditjr`, and the repository from `continuumjr` to `redditjr`. Android treats it as a different app, so it does not update the old one. Back up your settings in the old app under `Settings > Backup & Restore`, install this, and restore them. APK files are now named `redditjr-<abi>-<version>.apk`.

* The update dialog now renders the release notes instead of showing their Markdown markup, such as `**Full Changelog**`
* Release notes now come from this changelog, so the update dialog shows what changed instead of a list of pull requests

8.3.3.9.1.7 / 2026-11-3
============
* Added `Settings > Post > Hide NSFW Badge`, which never shows the NSFW badge in the feed or the post
* Fixed compact posts with no thumbnail, such as text posts, showing no post type at all when the Post Type Triangle Indicator is on. They now keep the text badge
* Update dialog now shows download progress with a Cancel button
* Added `Settings > About > Include pre-release versions`. A release tag with a suffix, such as `-beta.1`, is published as a pre-release
* Recents now also lists users, and remembers whether the section is collapsed
* The decoder remembers its results for recent comments, so scrolling back over a comment does not scan it again
* Release builds skip the lint-vital pass and use the Gradle build cache, which makes them faster
* Added a CI workflow that compiles every push to master and runs the unit tests

8.3.3.9.1.6 / 2026-11-3
============
* NSFW now shows inside the post type badge ("Image · NSFW") instead of as a badge of its own, in the feed and in the post. Added `Settings > Post > Show NSFW in the Post Type Badge` to turn it off
* Tapping the toolbar now scrolls to top on post and comment screens, like a long press already did
* Larger text on the decoder card's buttons, which now wrap on a narrow screen

8.3.3.9.1.5 / 2026-11-3
============
* Fixed the update dialog's button text being hard to read in Dark Mode
* Settings is now pinned at the bottom of the navigation drawer instead of being in the Preferences section
* Renamed Show Subreddit Banner to Hide Subreddit Banner and moved it above Hide Subreddit Description
* Update settings moved from Miscellaneous to About
* New icon: solid orange background and a smaller alien

8.3.3.9.1.4 / 2026-11-3
============
Renamed to Reddit Jr. with a new icon. The application ID is unchanged, so this updates the existing install.

* Decoder results are now a card with a Copy button and SimpCity and social search buttons, instead of a quote with emoji links
* Decoder only runs under NSFW posts
* Decoder search links now use only the handle, dropping a link's host and path or a leading label such as "TikTok and IG:". Copy still copies the full decoded text
* Decoder now reads 8-bit binary split across lines
* Added reverse image search (Google, Yandex or both) to the bottom bar of the image, gallery and Imgur viewers, for images and GIFs
* AutoModerator and stickied comments now start collapsed. Added `Settings > Miscellaneous > Collapse AutoModerator and stickied comments`
* Added `Settings > Interface > Show subreddit banner`. With it off, the header keeps only the strip under the toolbar
* Added a Recents section to the navigation drawer with the last 5 subreddits visited. Added `Collapse Recents Section` and `Hide Recents Section` to `Settings > Navigation Drawer`

8.3.3.9.1.2 / 2026-11-3
============
First Continuumjr release, a fork of Continuum 8.3.3.9.1. Note arm64-v8a is the 64-bit build, and should be considered the default choice.

* Added decoding of NATO phonetic alphabet and 8-bit binary in comments and post bodies, with Copy and search links
* Added `Settings > Miscellaneous > Decode NATO and binary` to turn the decoder off
* Added an in-app updater that checks the latest GitHub release of this repository
* Added `Settings > Miscellaneous > Check for updates automatically` and `Check for updates now`
* Changed the application ID to `org.cygnusx1.continuumjr` and the app name to Continuumjr, so it installs beside Continuum
* Replaced the build workflow with one that signs and publishes a release when a version tag is pushed

8.3.3.9.1 / 2026-11-2
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed [BUG] Random Subreddit and Random NSFW Subreddit not working #479
* Fixed logged-in History and Local Saved posts failing on blocked /api/info
* Fixed History stopping early when a read post could not be fetched
* Fixed Local Saved comments by storing each comment's post id
* Converted Use Old Load More Comments Method into global Use /api/info the Old Way
* Greyed out Use /api/info the Old Way unless Enable Overrides is enabled

# Notes
The `Settings > Interface > Comment > Use Old Load More Comments Method` per-account setting became the `Settings > API Keys > Use /api/info the Old Way` global setting.

8.3.3.9 / 2026-11-2
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented allow automatic bullet pointing #447
* Enabled gallery grids by default
* Changed gallery grids to use a 1x2 or 2x2 grid, with +X on the fourth cell
* Fixed low-res and stuck feed images, sharper galleries on wide screens

# Notes
Gallery grids with five or more images get a +X marker on the fourth image. If the you press the fourth image you get taken to the fourth image.

8.3.3.8 / 2026-10-31
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Added Anonymous comment saving
* Fixed Saved posts for Anonymous

8.3.3.7 / 2026-10-31
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Load more comments fails with 403 "blocked by network security" #475
* Fixed Issues with using Top Day/Week in multireddit #469
* Fixed OAuth login CSRF via hardcoded state in exported deep-link redirect handler

8.3.3.6 / 2026-10-21
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Gallery posting is broken #464
* Fixed History Limit doesn't declare the minimum #461

8.3.3.5 / 2026-10-20
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Some posts are missing image attachments #459
* Fixed Flairs with images don't work #458

8.3.3.4 / 2026-10-18
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Expand the "optionally use old reddit url for sharing" option into fully customizable sharing domain #453
* Fixed TTS Button Does Nothing #456
* Fixed Importing anon/backup from Infinity doesn't work #457
* Fixed All tab showing Saved tab Contents #455

# Notes
`Settings > Miscellaneous > Domain for Shared Links` now lets you set different
[embed fixer](https://github.com/seriaati/embed-fixer) hostnames, and custom
hostnames.

`Read Aloud` now supports third-party text-to-speech engines.

`Infinity for Reddit` backups work again, but only for the `Anonymous` account. If
you want to have the settings on more than the `Anonymous` account, use
`Settings > Account Settings Management > Copy Settings from Another Account`.
The reason is that most settings are global in `Infinity for Reddit`.

8.3.3.3 / 2026-10-10
=============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Hide read posts setting only works after restarting the app #442
* Made every setting but Post Filters apply without an app restart
* Fixed Editing an existing comment sometimes doesn't show anything, but the input box #310
* Fixed Random SFW/NSFW subreddit option keeps returning the same subreddits across sessions #441
* Added Read Aloud to the Bottom Sheet menu in the feed

8.3.3.2 / 2026-10-3
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Hide read posts setting only works after restarting the app #442
* Made every setting but Post Filters apply without an app restart
* Fixed Editing an existing comment sometimes doesn't show anything, but the input box #310
* Fixed Random SFW/NSFW subreddit option keeps returning the same subreddits across sessions #441
* Added Read Aloud to the Bottom Sheet menu in the feed

8.3.3.1 / 2026-10-1
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Updated to `upstream` version 8.3.3
* Implemented Support for stickers in comments #432
* Implemented Kill momentum when pressing floating button to go to next comment #433
* Fixed Comment gestures trigger even if the option is disabled #431
* Fixed Comments containing only 9 or less digits followed by a period appear invisible #430

8.3.1.9 / 2026-9-24
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Improve searching comments #427
* Fixed Issues with post filters related to wildcard rules and Applied to choices #426
* Fixed Tab strip jumps to last tabs after unsubscribing a middle subreddit tab #405
* Fixed Collapsing a comment mutes post video and makes it lag for a moment #423

8.3.1.8 / 2026-9-19
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed default Comment Swipe Actions

8.3.1.7 / 2026-9-19
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Gesture to read/unread a post #411
* Improved the consistency of Resume where I left off
* Fixed Creating a MultiReddit shows a "Cannot create this multireddit" error even though it succeeds #422
* Changed the layout of video media view buttons to be consistent with the new image media view layout

8.3.1.6 / 2026-9-17
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented View image index in the gallery view #419
* Fixed Cannot filter out pinned posts with post filters #420
* Implemented Highlight followed users #415
* Implemented Set custom tags for other users like in RES #413

8.3.1.5 / 2026-9-16
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Support for more image embeds in comments #412
* Preloaded feed card previews at their bound size to remove pop-in
* Fixed Emojis shown in spoilered comments even when not pressed #416
* Fixed Videos in Shadowbox Mode Failing Randomly #407
* Fixed Tab strip jumps to last tabs after unsubscribing a middle subreddit tab #405

8.3.1.4 / 2026-9-14
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Update all the translations #404
* Selftext posts with no preview image use the first image in the post as the preview image
* Sized body images in post details and comments to the width of their block
* Letterboxed autoplaying GIF cards in Fixed Height in Card mode to match video cards

8.3.1.3 / 2026-9-13
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Inline video autoplay intermittently fails until the post is scrolled off and back; GIFs load much slower than videos #403

8.3.1.2 / 2026-9-12
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Bug: Crashes on setting restoration #401
* Preloaded compact thumbnails so they appear without pop-in
* Reverted "Keep screen on when autoplaying videos." from upstream
* Implemented Configurable thumbnail size for compact layout #355

8.3.1.1 / 2026-9-11
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Updated to `upstream` version `8.3.1`
* Added `Shadowbox Mode`
* Fixed `Settings | Video | Autoplay GIFs in Comments`
* Added inline playback for `dropr`, `dubz`, `mlb`, `streamain`, `streamff`, and `streamin` links
* Added `mlb` to the `Data Saving` default list

`Shadowbox Mode` is a new choice in the `overflow`/`...` menus of various feeds. It is a fullscreen mode with a hidable bottom bar. It lets you horizontally swipe to other posts. For `Reddit` galleries it lets you swipe down to see the images.

There is a new setting `Settings | Interface | Hide Text Posts and Posts with No Preview` to make it more like a fullscreen version of the `Gallery Layout`.

8.3.0.4 / 2026-9-8
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Resume where I left off
* Added a quicktoggle for Resume, and it defaults to off
* Fixed Not able to set video quality in NSFW subs #388
* Renamed This Account to Account, and All Accounts to Global in Settings
* Moved Theme from the Account section of Settings to a new Themes section
* Fixed the color of the Read Aloud icon
* Moved the random subreddit options in search to their own screen behind the dice icon
* The incognito button state in the search screen is now persistent till the app is restarted
* Moved API Statistics into Debug within Settings

Resume where I left off, when on(off by default), remembers exactly where you
are at in the app when you close it. In a video, back to the video and at the
same timestamp. Looking at a certain preview image in a feed, pixel perfect
return to that position in the feed. Looking at a certain post in a subreddit,
back to that position in that subreddit.

Its location is `Settings | Miscellaneous | Resume where I left off`.

8.3.0.3 / 2026-9-4
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Per-account app settings #185
* Implemented Subreddit's "Since" display broken #391
* Fixed Comment embedded videos can't be downloaded. #389
* Added an "Open Reddit Link in Clipboard" feature to the feed overflow menu
* Fixed missing Download Image in the Bottom Sheet menu for posts of type Image
* Implemented make recovered flair follow theme #387

TLDR: The Settings screen has been restructured in relation to the per-account settings.

Most settings are now per account. Settings is broken into "This Account" and "All Accounts" sections to be able to tell the per account settings from the global settings.

Layout choices and the bottom bar settings now are per account.

New accounts get default settings. Existing accounts get a copy of the existing settings.

There is a new screen in Settings, "Account Settings Management". It has options to "Copy Settings from Another Account" and "Reset This Account's Settings".

Miscellaneous is in the "This Account" section of Settings, but the settings that stayed global where added to the new Device screen in Settings.

Themes are global, but theme choice is per account.

"Backup and Restore" got broken out of Advanced as their own screen in Settings

Backups and restores still include everything, including all accounts, as it was before.

"Forever Disable NSFW Content" has been removed. It was a useless setting given how easily it could be bypassed.

Advanced in Settings became "Global Settings Management"

"Crash Reports" was moved to the bottom of Settings under Debug.

8.3.0.2 / 2026-8-31
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Search links support #381
* Implemented Add option to hide non media posts in Gallery view #377
* Implemented Auto play gifs in a Reddit Gallery, without having to open the gallery #382
* Fixed some comments have incorrect space between upvote button and count #385
* Implemented Option To Toggle off Random Subreddit Feature. #386
* Described and enabled by default Click to Show Media in Gallery Layout
* Added an edited marker and edit time to the post detail header

8.3.0.1 / 2026-8-29
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Updated to 8.3.0
* Implemented Recover deleted comments automatically #372
* Fixed Media downloads occasionally take a long time #378

8.2.5.9.9.9.7 / 2026-8-26
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Misaligned middle columns with fixed height enabled #373
* Implemented Random subreddit feature #86

r/random is SFW(Safe for Work) content, but there is no guarantee. The moderators have to have not marked the subreddit NSFW(Not Safe for Work). There are over 300k subreddits in this list.

r/randnsfw is NSFW subreddits. These are subreddits where the moderators did mark it as NSFW. It can be anything text to images to video. It is also ALL the orientations and fetishes. There are over 70k subreddits in this list.

r/myrandom is a random subscribed subreddit from your own subscriptions on that account.

Subscriptions's FAB button and Go to Subreddit will take you to a random subreddit, but you can't refresh to get another random subreddit. Adding the virtual subreddits as a tab will let you refresh to get a new random subreddit.

Subreddits with no posts are skipped.

See [Issue 86](https://github.com/cygnusx-1-org/continuum/issues/86) for more details.

8.2.5.9.9.9.6 / 2026-8-25
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Post Filter bug #370
* Implemented Add support for incognito keyboard #134
* Implemented Search history for users that are not logged in #115

8.2.5.9.9.9.5 / 2026-8-22
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed viewing my/other profile autoshutdowns/kills the app #369
* Implemented New Windows or Tab feature #327

There is a new Open in New Window feature in the long press/Bottom Sheet menu for posts. Though the issue title mentioned tabs it is a new window only feature.

8.2.5.9.9.9.4 / 2026-8-21
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Feature to Show and Keep Track of Visited Subreddits and Users. #363
* Added the ability to save users to Subscriptions | Users
* Fixed Follow swipe between post on posts page #126
* Fixed Inbox unread count unsynced between *main page* and *other pages* #361

8.2.5.9.9.9.3 / 2026-8-18
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Replaced the subreddit filter prefix/suffix toggles with per-rule keyword matching on r/ContinuumAll
* Rebuilt the Post Filter screen around one include/exclude rule list, and merged in Applies to
* Simplified steps of "Add to Post Filter"
* Stopped naming new post and comment filters "New Filter"
* Added a feature where the apps records and lists the subreddits each wildcard rule actually hides

Note the under the hood Post Filters functionality is exactly the same. The changes either extend existing functionality or change the UI/UX. The exception is the removal of the prefix/suffix toggles, which are superceded by the new wildcard feature.

What is r/ContinuumAll? It is an alias of r/all. The reason is to avoid accidental filtering r/all. It is an opt-in, and you have to set it up. I did create r/ContinuumAll on Reddit as a private subreddit as a placeholder.

Steps:
1. Add wildcard filters which are automatcally limited to r/ContinuumAll
2. Remove r/all, titled All by default, from Settings | Interface | Customize Tabs in Main Page | Tabs
3. Add r/ContinuumAll to Settings | Interface | Customize Tabs in Main Page | Tabs, and title it All if you want

See GitHub issue [#358](https://github.com/cygnusx-1-org/continuum/issues/358) for more background.

8.2.5.9.9.9.2 / 2026-8-14
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Stopped comment rows rebinding and shifting on every collapse and update
* Implemented Links to subs without reddit.com aren't clickable #356
* Padded the compact thumbnail block and widened layout snapshot coverage

8.2.5.9.9.9.1 / 2026-8-4
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Making a comment sometimes crashes the app #352
* Implemented Optionally use `old` instead of `www` for sharing #333
* Made Solarized Amoled the default theme and hardened theme management
* Hardened Reddit video downloads against a lost or deleted download folder
* Fixed App is not respecting post's default sort type for comments. #348

8.2.5.9.9.9 / 2026-7-28
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Tapping a post's flair or post type label crashes the app when doing so from the "Saved posts" screen. #350
* Improved Recover Post
* Added Recover Comment
* Fixed NSFW/spoiler blur not updating live in feed and comments
* Improved Fixed Disable Swiping Between Tabs being undone by gallery touches
* Improved Disable Swiping Between Tabs
* Made the main page back button action apply without a restart
* Routed more Reddit deep-link types in-app and honored their sort
* Fixed Blurry Thumbnails in Compact Layout #339
* Improved Inbox
* Massive effort to avoid app crashes via null checks with NullAway

8.2.5.9.9.8 / 2026-7-14
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Replaced all icons with a cleaner versions. Thanks to @98xe!
* Started a new process to make null checks crashes a non-issue within the Continuum code

8.2.5.9.9.7 / 2026-7-11
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed YouTube title suggestion
* Added Quick Toggles settings to hide the theme, NSFW, and thumbnail quick toggles in the navigation drawer
* Added Saved search
* Improved "Recovery Post"

8.2.5.9.9.6 / 2026-7-6
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented Enable subreddit filtering by keyword #326
* Added Local Saved to mirror saves locally and surface ones Reddit drops
* Added removed-post recovery via Arctic Shift API with a "Recover Post" action
* Implemented Restore ability to customize tab names #328
* Changed the "/m/" prefix for multireddit tabs to "m/" for increased consistently with the rest of the app
* Fixed "m//" to "m/" for user multireddit tabs
* Shortened "/m/user/username/m/multireddit-name" to "u/m/multireddit-name" for user multireddit tabs
* Made the main page tab bar always scrollable

"Made the main page tab bar always scrollable" makes the titles not try to span the full width. This change was made to make the behavior consistent. With the fixed(fills the width of the screen) shorter titles could get line wrapped.

8.2.5.9.9.5 / 2026-7-4
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented rearrangeable tabs
* Prefixed multireddit tabs with /m/

8.2.5.9.9.4 / 2026-7-2
===========
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed option to add comment top padding back #113 Thanks @KaKi87
* Fixed Crash on hiding posts twice in a session #321

8.2.5.9.9.3 / 2026-6-30
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Added Translate feature in the overflow menu for posts and comments via Google Translate
* Added Read Aloud feature in the overflow menu for posts and comments

8.2.5.9.9.2 / 2026-6-30
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Added text to the override toggle to make it crystal clear that the default client ID, user agent, and redirect URI are RedReader

8.2.5.9.9.1 / 2026-6-29
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Posts loading very slowly #252
* Fixed Duplicate images shown in posts, first image appears blurry before full resolution #317
* Implemented Cannot change comment sort type in subreddits with a recommended sort type #316
* Renamed "Respect Subreddit Recommended Comment Sort Type" to "Respect Subreddit Recommended Comment Sort Type By Default"
* Fixed Wrong Style in Card Layout #314

We will go back to the old versioning scheme once upstream drops 8.3.0.
Apparently the latest new feature upstream is working on is reminders.
Before that it was video in markdown.

8.2.5.9.9 / 2026-6-28
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Added a setting to enable and disable autoplay of GIFs in comments(`Settings | Video | Autoplay GIFs in Comments`)
* Fixed restore of the the current at the time account in a backup

8.2.5.9.8 / 2026-6-27
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Removed all references except Post Filters to requiring a restart on change
* Convert API Keys and Customize Tabs in Main Page to a method that restarts on back button or back gesture
* Updated dependencies and raised minSdk to 24(Android 7.0)
* Moved the post view sort to a toolbar subtitle under the subreddit name
* Restored the child-count pill on re-collapsed comments via a shared styleChildCountBadge helper
* Aligned the fully-collapsed comment row to the header to remove the username jump and avatar crowding

`Continuum`'s minimum version of `Android` is now `7.0`. The previous minimum
was `5.0`.

This was done to allow the modernization of various libraries. The biggest
being an upgrade of the main video library from 1.8.0 to 1.10.1(latest).

The other big thing in this release is no need to restart the app a lot less.
Most settings now just work. The big exception is `Post Filters`, which would
require a completely rewrite

The restart in `API Keys` is now once for all changes on going back instead of
once per change.

8.2.5.9.7 / 2026-6-25
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed an inset bugs in Subscriptions tabs
* Implemented Feature Request: Add “Other Discussions” #45
* Implemented subreddits and multireddits should be synced automatically with the official reddit app/website #42
* Implemented Improvement: Reduce click depth to choose comment sorting #307
* Fixed API Client ID stays blank even after clicking "OK" #304

8.2.5.9.6 / 2026-6-23
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Reverted the layout changes
* Added usernames to the compact layouts as their own line like "Card layout"

The backstory:
The original goals where to add the usernames to the compact layout, and make
all the card and compact layouts more compact. But it didn't work as intended
as planned on devices with a UI scaling bigger than my phone.

My original fix was going to be a hybrid solution where the new style would
work, but it would work like the old way for most people. The catch is that
the max length of a subreddit is 21 and the max length of a username is 20.
So after doing the math based on those, the font sizes, upper case vs lower
case, the UI scaling, other text, etc it wouldn't work even on my phone in
the worst case scenario.

Examples:
subreddit AAAAAAAAAAAAAAAAAAAAA 21
username  PaleRulerGoingAlone7  20

8.2.5.9.5 / 2026-6-22
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemeneted Show comments in saved tab #61
* Implemented preview images for self-text posts
* Implemented Feature request: Scale embedded images in comments uniformly #4
* Fixed Backup not working. #300
* Fixed Usernames visible in compact view #144
* Moved Usernames to the right of subreddits instead of under them in layouts
* Moved link hostname to the end of the badge row in the card layout

8.2.5.9.4 / 2026-6-21
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Suggestion to Use Color Markers for Differentiating Video and Image Content #169
* New setting, "Settings | Interface | Post | Post Type Triangle Indicator" for compact layouts
* Increased thumbnail size from 96dp to 112dp
* Added slide://localhost and slide://127.0.0.1 to AndroidManifest.xml for external browsers

Post types now have assigned colors for the card layout badge. The triangle
colors match the badge colors.

8.2.5.9.3 / 2026-6-20
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Hardened OAuth login error handling with clear failure feedback
* Registered external browser login redirect URIs for BaconReader, Boost, Continuum, Infinity, Joey, RedReader, Relay, RIF, and Slide
* Convert API Keys and Customize Tabs in Main Page to a method that restarts on back button or back gesture

8.2.5.9.2 / 2026-6-19
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Custom Feeds not visible #299
* Added an Add button to Subreddits and Users in Subscriptions
* Added Show Users MultiReddits" and "Show Favorite Users MultiReddits"
toggles in "Settings | Interface | Customize Tabs in Main Page"
* Fixed tabs to auto-fit the screen width
* Excluded favorites from the non-favorite tabs to avoid duplicate tabs
* Changed the "Multireddits" label to "MultiReddits" in the navigation drawer
* Fixed add button in Subscriptions | Subreddits to say "Add Subreddit"

8.2.5.9.1 / 2026-6-17
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Fixed Force maximum refresh rate #297
* Fixed Unable to select and copy post titles now #296

Used 8.2.5.9.1 instead of 8.2.5.10 to avoid a bug in GitHub's release page
where it will get the order wrong.

8.2.5.9 / 2026-6-15
============
Note v8a is the 64-bit build, and should be considered the default choice.

* Implemented a prefix toggle search of subscriptions
* Fixed gallery layout columns #290
* Added Hide Post Type Icon and Hide Image Count in Gallery options
* Fixed lazy-mode crash with >2 gallery columns by sizing span array to span count

8.2.5.8 / 2026-6-10
============
Note v8a is the 64-bit build, and should be considered the default choose.

* The new default is the RedReader client ID.

* New Enable Overrides toggle, off by default, to still allow overriding the client ID, user agent, and redirect URI


Most users' client IDs have been removed by Reddit. If yours was removed, log out of each account and login again.

Check your https://old.reddit.com/prefs/apps/, some still exist. The override will still let you use it.

8.2.5.7 / 2026-6-6
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Video scrub conflicts with android back gesture #279
* Fixed App crashes when loading certain posts since 8.2.5 #277

8.2.5.6 / 2026-6-6
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Missing child comments indicator #272
* Fixed Hiding comment votes also hides child count if only top level comments are shown #219
* Fixed Downloads name randomizer broken for downloaded images/gifs from comments. #278
* Restored Compact comment defaults
* Restored Hide user prefix in comments
* Restored APNG animated avatars
* Restored Disable profile avatar animation
* Restored Crosspost thumbnails
* Restored Preview quality in split view
* Restored Swap content/comments on Fold
* Restored Remember comment scroll position
* Restored Share as Image with Comments

8.2.5.5 / 2026-6-5
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Comments collapse lag animation #275
* Fixed issue with loading previews of gifs in Reddit Galleries

8.2.5.4 / 2026-6-5
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed loading of first image of Reddit Galleries when horitzonal swiping between posts
* Switched Reddit Gallery static images to using all preview images for the quick swipe viewing in the feed
* Added English descriptions to most sections of Settings
* Fixed video rotation
* Restored the overflow menu media views where appropriate
* Added an API Statistics section to Settings

8.2.5.3 / 2026-6-3
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Swipe action not working #267
* Disabled "Enable Swipe Action" when "Swipe Between Posts" is enabled, because they conflict with each other
* Fixed greyed out toggles showing they are greyed out
* Fixed Navbar background #265
* Fixed login_chrome_required to include mention of Firefox
* Fixed Apply download naming convention to Share Media #268
* Added support for sharing videos
* Removed blur on screenshots for NSFW and Spoiler
* Fixed Crash after playing videos #269
* Fixed IndexOutOfBoundsException opening a post when the setting "show author avatar" is enabled. Docile-Alligator/Infinity-For-Reddit #1926

8.2.5.2 / 2026-6-2
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Added a video scrub with a hortizontal back and forth gesture in videos(Disabled in Galleries)

8.2.5.1 / 2026-6-1
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Updated to 8.2.5

8.2.4.2 / 2026-6-1
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Extended pinch to zoom to gifs
* Deconflicted swipe up/down to dismiss from pinch to zoom
* Removed pinch to zoom preference, it is the new default
* Added rotation support to images, videos, and galleries
* Removed the media view bottom bar option and made it the default

8.2.4.1 / 2026-6-1
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Update to 8.2.4

8.2.1.10 / 2026-5-31
============
Note v8a is the 64-bit build, and should be considered the default choose.

* More Anonymous Home tab fixes

8.2.1.9 / 2026-5-31
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed multiple subreddits for the Anonymous user

8.2.1.8 / 2026-5-30
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Home tab fails to fetch subscriptions in anonymous mode #263
* Blocked subscribing to NSFW subreddits for Anonymous users(Because it doesn't work)
* Fixed crash caused by a permissions problem when trying to record a video for a video post submission
* Fixed issue with uploading video files from an SD card with sanitization.

8.2.1.7 / 2026-5-29
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Posts and Subreddits won't load in Anonymous mode #261
* Fixed exact restoration of the position and layout for Home and Subreddits

8.2.1.6 / 2026-5-29
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Posts and Subreddits won't load in Anonymous mode #261
* Fixed exact restoration of the position and layout for Home and Subreddits

8.2.1.5 / 2026-5-26
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Adaptive Icon #260
* Added ability to long press the back arrow to return to Home
* Implemented Switch accounts by holding profile icon on homepage #237

8.2.1.4 / 2026-5-24
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Replaced more Infinity for Reddit icons with Continuum icons
* Made a subreddit flair required obvious for cross-posts, and all post types
* Disabled the submit button for cross-posts and all posts types when a flair hasn't been set when required
* Feat/specific browser link handler per account

8.2.1.3 / 2026-5-12
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed issue with cookie dialog on the login page when using WebView

8.2.1.2 / 2026-5-11
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed accessing users' multi-reddits in Anonymous mode
* Added Anonymous local data like upvotes, saved, and history to the backup
* Fixed crash with multi-reddits in Anonymous mode

8.2.1.1 / 2026-5-10
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Update to 8.1.2
* Fixed Create multireddit function broken for Anonymous #193
* Fixed Some links don't open inside the app #254
* Fixed crash when opening a saved post in Anonymous mode
* Fixed [Bug] history/saved/upvoted/downvoted posts sometimes don't work in the local "saved on device"

8.1.4.7 / 2026-5-3
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Implemented Add support for ephemeral customTabs #147
* Implemented Feature request: Adjust seek interval for video player controls #239
* Added "Show Thumbnail on the Left" as "Toggle Thumbnail Side" in the "Bottom Navigation Bar"
* Fixed issue with transparent pngs as preview or thumbnails images

8.1.4.6 / 2026-4-18
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed issue with the redirect URI when it defined but blank
* Fixed issue with the user agent when it is defined but blank
* Added whitespace trimming for the client ID and redirect URI
* Added leading and trailing only whitespace trimming to the user agent
* Changed the font used for client IDs to make typos less likely

8.1.4.5 / 2026-4-9
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed issue with the Reddit account's interface language being anything
other than English at WebView login. Thanks to @wchill
* Fixed Jump to Next Top-level Comment #244

8.1.4.4 / 2026-4-3
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Added support for configuring the user agent and redirect uri directly in the app
* Added support for logging in with Firefox as the external browser

8.1.4.3 / 2026-3-31
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed WebView login

8.1.4.2 / 2026-3-27
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Added "Share as Image with Comments" and "Share as Image with this Comment Thread"
* Fixed the colors of the "Share as Image" QR codes to always be white/black for usability with the Android Camera app

8.1.4.1 / 2026-3-27
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Updated to 8.1.4
* Added the ability to search Settings
* Added post ids or post ids + comment ids to saved filenames

8.1.3.1 / 2026-3-16
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Link in comment doesnt render/link properly #238
* Fixed Ability to swap both the content and comments on Fold when unfolded #232
* Improved the user experience when adding a user to a multi-reddit

8.1.2.4 / 2026-3-6
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed issue with preview image quality in the split view

8.1.2.3 / 2026-3-4
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Simplified adding a user profile to a multi-reddit
* Fixed Add a multireddit description page #230
* Moved "Enable folding phone support" to "Settings | Miscellaneous"
* Added "Default Post Layout for a Foldable Unfolded" in "Settings | Interface | Post"
* Fixed Some gifs in comments fail to embed #227
* Fixed bug with uploading an image into a comment

8.1.2.2 / 2026-2-21
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Added retry logic to 500 errors when loading posts from a subreddit
* Made subredditAPICallLimit always return 100
* Reverted halve the limit button

8.1.2.1 / 2026-2-18
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Added feature to allow the user to "Halve the post limit" on failure
* Upgraded to 8.1.2

8.1.1.1 / 2026-2-12
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Gifs no longer showing up in comments #217
* Upgraded to 8.1.1

8.1.0.2 / 2026-1-23
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed crash on start with 8.1.0.1

8.1.0.1 / 2026-1-23
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Added Remember Comment Scroll Position in Settings | Interface | Comment, disabled by default
* Upgraded to 8.1.0

8.0.8.1 / 2025-11-16
=============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Bottom Navigation Bar #196
* Fixed Video player control is not aligned properly #198
* Upgraded to 8.0.8

8.0.7.3 / 2025-11-9
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed App closes when I try to open a post with a video #189
* Fixed issue with post image recycling while scrolling for auto-played video posts

8.0.7.2 / 2025-11-7
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Updated media3-exoplayer to 1.8.0
* Having Video issue on Huawei #186

8.0.7.1 / 2025-11-5
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Fixed Confirm to exit doesn't really exit #154
* Upgraded to 8.0.7

8.0.5.1 / 2025-10-17
=============
Note v8a is the 64-bit build, and should be considered the default choose.

* Make double tapping tabs cause a scroll to the top (in Subreddit view)
* Add contain subreddits/users in postFilter
* Upgraded to 8.0.5

8.0.3.1 / 2025-9-17
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Ensure `mark posts as read` is also turned on before hiding read posts
* Don't show the number of online subscribers in ViewSubredditDetailAct, aka
the comments not loading
* Always show read posts on any users page
* Hide the + button on PostFilterUsageListingActivity as it does nothing
* Upgraded to 8.0.3, aka Redgif sound

8.0.2.1 / 2025-9-8
===========
Note v8a is the 64-bit build, and should be considered the default choose.

* Keep pinned posts pinned even when filtering or hiding read posts
* Make double tapping tabs cause a scroll to the top #148
* Fix the + button in apply post filter not working correctly
* Upgraded to 8.0.2

8.0.0.1 / 2025-8-21
============
Note v8a is the 64-bit build, and should be considered the default choose.

* Re-enabled redgifs
* Upgraded to 8.0.0
* Fix No sound in redgifs #129

7.5.1.3 / 2025-8-8
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
