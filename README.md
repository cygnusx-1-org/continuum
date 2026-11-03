<h1 align="center">Reddit Jr.</h1>

A personal fork of [Continuum](https://github.com/cygnusx-1-org/continuum), an ad-free Reddit client for Android written in Java. Continuum is itself a fork of [Infinity for Reddit](https://github.com/Docile-Alligator/Infinity-For-Reddit).

<br>

<div align="center">

[Releases](https://github.com/jayblah/continuumjr/releases)
[License](https://github.com/jayblah/continuumjr/blob/master/LICENSE)
[Upstream: Continuum](https://github.com/cygnusx-1-org/continuum)

</div>

---
# What is different from Continuum
- **NATO and binary decoder.** Comments and post bodies that spell something in the NATO phonetic alphabet or in 8-bit binary get the decoded text added below the paragraph, with links to copy it and to search for it. Turn it off in `Settings > Miscellaneous > Decode NATO and binary`.
- **In-app updates.** The app checks the [latest release](https://github.com/jayblah/continuumjr/releases/latest) of this repository, at most once a day, and offers to download and install it. Turn it off in `Settings > Miscellaneous > Check for updates automatically`, or check by hand with `Check for updates now`.
- **Installs beside Continuum.** The application ID is `org.cygnusx1.continuumjr` and the app is named Reddit Jr., so it does not replace or share data with Continuum.

Everything else is Continuum. Changes are kept small and in new files where possible so upstream merges stay clean.

# Installation
1. Open the [latest release](https://github.com/jayblah/continuumjr/releases/latest) on your phone.
2. Download the APK with `arm64-v8a` in its name. That is the 64-bit build and the right choice for almost every phone. Use `armeabi-v7a` only on an old 32-bit phone.
3. Open the file and allow installs from your browser when Android asks.

After that the app updates itself from new releases.

## Verification
org.cygnusx1.continuumjr 1B:6C:E8:BD:8A:42:C4:9D:98:20:D5:A5:DF:D6:53:1A:D2:06:BD:81:51:0C:6D:42:FE:AC:81:FD:42:CE:71:9B

You can verify it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

# Setup
Reddit Jr. needs your own Reddit `Client ID`, like Continuum. See [SETUP.md](/SETUP.md).

# FAQ
See [FAQ.md](/FAQ.md)

# Debugging
See [DEBUGGING.md](/DEBUGGING.md)

# Releasing
Releases are built and signed by GitHub Actions ([build.yml](/.github/workflows/build.yml)).

1. Raise `versionName` and `versionCode` in `app/build.gradle`.
2. Commit and push to `master`.
3. Tag the commit with `v` plus the version name and push the tag, for example `git tag v8.3.3.9.1.3 && git push origin v8.3.3.9.1.3`.

The workflow checks that the tag matches `versionName`, builds the APKs, and publishes the release. It needs the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Every release must be signed with the same key, or Android will refuse to install it over the previous one. The version must also be higher than the last release, because the in-app updater compares version numbers.

# Staying current with Continuum
The `upstream` remote points at Continuum.

```
git fetch upstream
git merge upstream/master
```

Expect conflicts in `app/build.gradle` (keep your version, based on theirs, and keep the `applicationId` and app name) and in `.github/workflows/build.yml` (keep this fork's).

# License
Distributed under the AGPL-3.0 License. See [LICENSE](/LICENSE) for more information. The decoder code is by wchill ([patcheddit](https://github.com/wchill/patcheddit)) and carries its own notice in the file headers.

# Credits and upstream
- [Continuum](https://github.com/cygnusx-1-org/continuum) by u/edgan, whose [Discord](https://discord.gg/vDuSpJEDrW) and [subreddit](https://old.reddit.com/r/continuumreddit/) are the place for questions about the parts this fork did not change.
- [Infinity for Reddit](https://github.com/Docile-Alligator/Infinity-For-Reddit) by Docile-Alligator.

<details>
<summary>Features from <b>Infinity</b></summary>

* Lazy mode: Automatic scrolling of posts enables you to enjoy amazing posts without moving your thumb.
* Browsing posts
* View comments
* Expand and collapse comments section
* Vote posts and comments
* Save posts
* Write comments
* Edit comments and delete comments
* Submit posts (text, link, image and video)
* Edit posts (mark and unmark NSFW and spoiler and edit flair) and delete posts
* See all the subscribed subreddits and followed users
* View the messages
* Get notifications of unread messages
* etc...

</details>
