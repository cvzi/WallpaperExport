# <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Launcher icon" height="48"> WallpaperExport

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3%20or%20later-a32d2a?logo=GNU)](https://www.gnu.org/licenses/gpl-3.0)
[![F-Droid](https://img.shields.io/f-droid/v/com.github.cvzi.wallpaperexport.svg?logo=f-droid)](https://f-droid.org/packages/com.github.cvzi.wallpaperexport/)
[![Download APK file](https://img.shields.io/github/release/cvzi/WallpaperExport.svg?label=Download%20apk&logo=android&color=3d8)](https://github.com/cvzi/WallpaperExport/releases/latest)
[![Gradle CI](https://img.shields.io/github/workflow/status/cvzi/WallpaperExport/%F0%9F%94%A8%20Gradle%20Build%20CI?logo=github)](https://github.com/cvzi/WallpaperExport/actions/workflows/gradleCI.yml)

The sole purpose of this app is to back up the current wallpaper of an Android device.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.github.cvzi.wallpaperexport/)

This app was created because on Android 13 “Tiramisu” it is necessary to request the `MANAGE_EXTERNAL_STORAGE` permission to read the current wallpaper. This permission allows read and write access to all user files on the device and is mostly used by file manager apps. Google's Play Store does not allow this permission for this use-case of accessing the wallpaper. [More information](https://developer.android.com/training/data-storage/manage-all-files).

I did not want to include this permission in my app [DarkModeLiveWallpaper](https://github.com/cvzi/darkmodewallpaper/), instead I created this separate app that can be used by anyone who still wants to export their wallpaper.

## Screenshots

| <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Main Activity" /> | <img src="images/PermissionScreen.png" alt="Permission Screen"/> |
| --- | ---- |
