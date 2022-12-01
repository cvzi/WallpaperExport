# <img src="images/ic_launcher-playstore.png" alt="Launcher icon" height="48"> WallpaperExport

The sole purpose of this app is to back up the current wallpaper of an Android device.

This app was created because on Android 13 “Tiramisu” it is necessary to request the `MANAGE_EXTERNAL_STORAGE` permission to read the current wallpaper. This permission allows read and write access to all user files on the device and is mostly used by file manager apps. Google's Play Store does not allow this permission for this use-case of accessing the wallpaper. [More information](https://developer.android.com/training/data-storage/manage-all-files).

I did not want to include this permission in my app [DarkModeLiveWallpaper](https://github.com/cvzi/darkmodewallpaper/), instead I created this separate app that can be used by anyone who still wants to export their wallpaper.

## Screenshots

| <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" alt="Main Activity" /> | <img src="images/PermissionScreen.png" alt="Permission Screen"/> |
| --- | ---- |
