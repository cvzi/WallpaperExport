/*  WallpaperExport github.com/cvzi/WallpaperExport
    Copyright © 2022 cuzi@openmail.cc

    This file is part of WallpaperExport.

    WallpaperExport is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    WallpaperExport is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with WallpaperExport.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.github.cvzi.wallpaperexport

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.github.cvzi.wallpaperexport.databinding.ActivityAboutBinding
import com.github.cvzi.wallpaperexport.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*


typealias StringRes = Int

enum class IntentType {
    SEND, VIEW, SAVE
}

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val DRAWABLES_INDEX = R.string.tag_drawables_index
        private const val INTENT_TYPE = R.string.tag_share_type
        private const val ORIGINAL_TEXT = R.string.tag_original_text
        private const val FILE_PROVIDER = R.string.file_provider_authority
    }

    private lateinit var binding: ActivityMainBinding

    private val drawables: Array<Drawable?> = Array(3) { null }
    private var askedForPermission = false
    private var currentDrawable: Drawable? = null
    private var currentButton: Button? = null
    private val mainLooper = Handler(Looper.getMainLooper())
    private val fileNameSuggestion =
        arrayOf("system_wallpaper.png", "builtin_wallpaper.png", "lockscreen_wallpaper.png")

    @RequiresApi(Build.VERSION_CODES.R)
    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            Log.d(TAG, "manageStoragePermission granted")
        } else {
            Log.d(TAG, "manageStoragePermission NOT granted")
        }
    }
    private val requestPermissionLauncher = registerForActivityResult(
        RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Permission granted")
        } else {
            Log.d(TAG, "Permission NOT granted")
        }
    }
    private val createDocumentLauncher =
        registerForActivityResult(CreateDocument("image/png")) { documentUri ->
            if (documentUri != null) {
                currentDrawable?.let {
                    onDocumentCreated(it, documentUri)
                }
            } else {
                Log.e(TAG, "No document created")
                revertOriginalText(currentButton)
            }
        }

    private val startAbout = { _: View ->
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private val permissionCheckerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!periodicPermissionCheck()) {
                mainLooper.postDelayed(this, 1000)
            }
        }
    }
    private val onShareButtonClick = View.OnClickListener {
        if (it is Button) {
            currentButton = it
            it.setTag(ORIGINAL_TEXT, it.text.toString())
            it.setText(R.string.opening)
        }

        val intentType = it.getTag(INTENT_TYPE) as IntentType
        val drawablesIndex = it.getTag(DRAWABLES_INDEX) as Int
        val drawable = drawables.getOrNull(drawablesIndex)
        val fileName = fileNameSuggestion.getOrNull(drawablesIndex) ?: ""

        if (drawable == null) {
            Log.e(TAG, "onShareButtonClick: Drawable is null")
            toastMessage(R.string.failed_to_extract_wallpaper)
            revertOriginalText(it)
            return@OnClickListener
        }

        if (intentType == IntentType.SAVE) {
            currentDrawable = drawable
            createDocumentLauncher.launch(fileName)
            return@OnClickListener
        }

        CoroutineScope(Dispatchers.Default).launch {
            val uri = createTemporaryFile(drawable, "wallpaper_$drawablesIndex")
            runOnUiThread {
                when (intentType) {
                    IntentType.VIEW -> {
                        shareUri(uri, Intent.ACTION_VIEW, it)
                    }
                    IntentType.SEND -> {
                        shareUri(uri, Intent.ACTION_SEND, it)
                    }
                    else -> {
                    }
                }

            }
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initShareButton(binding.buttonShareLeft, 0, IntentType.SEND)
        initShareButton(binding.buttonShareMiddle, 1, IntentType.SEND)
        initShareButton(binding.buttonShareRight, 2, IntentType.SEND)

        initShareButton(binding.buttonSaveLeft, 0, IntentType.SAVE)
        initShareButton(binding.buttonSaveMiddle, 1, IntentType.SAVE)
        initShareButton(binding.buttonSaveRight, 2, IntentType.SAVE)

        initShareButton(binding.imageViewLeft, 0, IntentType.VIEW)
        initShareButton(binding.imageViewMiddle, 1, IntentType.VIEW)
        initShareButton(binding.imageViewRight, 2, IntentType.VIEW)


        binding.textViewAbout.setOnClickListener(startAbout)
        binding.imageButtonAbout.setOnClickListener(startAbout)

        binding.switchMissingPermission.setOnClickListener {
            askForPermission(alwaysAsk = true)
        }
    }


    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        mainLooper.removeCallbacks(permissionCheckerRunnable)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            binding.switchMissingPermission.visibility = View.GONE
        }

        hasPermissions({
            binding.switchMissingPermission.isChecked = true
            CoroutineScope(Dispatchers.Default).launch {
                loadWallpapers()
            }
        }, {
            binding.switchMissingPermission.isChecked = false
            askForPermission()
        })
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            mainLooper.postDelayed(permissionCheckerRunnable, 1000)
            mainLooper.postDelayed({
                mainLooper.removeCallbacks(permissionCheckerRunnable)
            }, 60000)
        }
    }


    private fun shareUri(uri: Uri?, intentType: String, view: View) {
        if (uri != null) {
            val intent = Intent.createChooser(Intent(intentType).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                type = "image/png"
            }, getString(R.string.sharing_chooser))

            addPermissionsToChooser(intent, uri)

            if (intent.resolveActivity(packageManager) != null) {
                if (view is Button) {
                    view.postDelayed({
                        revertOriginalText(view)
                    }, 2000)
                }
                startActivity(intent)
            } else {
                toastMessage(R.string.failed_no_app_found)
                revertOriginalText(view)
            }
        } else {
            Log.e(TAG, "onShareButtonClick: uri is null")
            toastMessage(R.string.failed_to_save_image)
            revertOriginalText(view)
        }
    }

    private fun revertOriginalText(view: Any?) {
        if (view is Button) {
            (view.getTag(ORIGINAL_TEXT) as? String?)?.let {
                view.text = it
            }
        }
    }

    private fun onDocumentCreated(drawable: Drawable, documentUri: Uri) {
        CoroutineScope(Dispatchers.Default).launch {
            val success = storeToUri(drawable, documentUri)
            runOnUiThread {
                revertOriginalText(currentButton)
                if (success) {
                    toastMessage(R.string.wallpaper_saved)
                } else {
                    toastMessage(R.string.failed_to_save_wallpaper)
                }
            }
        }
    }

    @Suppress("Deprecation")
    private fun addPermissionsToChooser(intent: Intent, uri: Uri) {
        for (resolveInfo in packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_ALL
        )) {
            grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun initShareButton(shareButton: View, drawablesIndex: Int, intentType: IntentType) {
        shareButton.setTag(DRAWABLES_INDEX, drawablesIndex)
        shareButton.setTag(INTENT_TYPE, intentType)
        shareButton.setOnClickListener(onShareButtonClick)
    }

    private suspend fun createTemporaryFile(drawable: Drawable, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            val directory = File(cacheDir, "shared_wallpapers")
            var fileOutputStream: FileOutputStream? = null
            try {
                directory.mkdirs()
                val file = File(directory, "$fileName.png")
                fileOutputStream = FileOutputStream(file)

                saveDrawable(drawable, fileOutputStream)

                fileOutputStream.close()
                return@withContext FileProvider.getUriForFile(
                    this@MainActivity, getString(FILE_PROVIDER), file
                )
            } catch (e: IOException) {
                Log.e(
                    TAG, "createTemporaryFile() Error saving to bitmap: ${e.stackTraceToString()}"
                )
            } finally {
                fileOutputStream?.close()
            }
            return@withContext null
        }
    }


    private suspend fun storeToUri(drawable: Drawable, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            val outputStream = contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                try {
                    saveDrawable(drawable, outputStream)
                    return@withContext true
                } catch (e: IOException) {
                    Log.e(
                        TAG,
                        "storeToUri() Error saving drawable: ${e.stackTraceToString()}"
                    )
                } finally {
                    outputStream.close()
                }
            } else {
                Log.e(TAG, "storeToUri() openOutputStream returned null")
            }
            return@withContext false
        }
    }


    private fun saveDrawable(drawable: Drawable, outputStream: OutputStream) {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val bufferedOutputStream = outputStream.buffered()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bufferedOutputStream)
        bufferedOutputStream.flush()
        bufferedOutputStream.close()
    }

    private fun askForPermission(alwaysAsk: Boolean = false) {
        if (askedForPermission && !alwaysAsk) {
            return
        }
        askedForPermission = true
        AlertDialog.Builder(this)
            .setTitle(R.string.ask_for_permission_title)
            .setMessage(R.string.ask_for_permission_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manageStoragePermissionLauncher.launch(
                        Intent(
                            ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                        )
                    )
                    dialog.dismiss()
                } else {
                    requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE)
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun hasPermissions(ok: (() -> Unit), error: (() -> Unit)) {
        if (checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())) {
            ok()
        } else {
            error()
        }
    }

    @RequiresPermission(READ_EXTERNAL_STORAGE)
    private suspend fun loadWallpapers() {
        withContext(Dispatchers.IO) {
            val wallpaperManager = WallpaperManager.getInstance(this@MainActivity)

            drawables[0] = wallpaperManager.drawable
            drawables[1] = wallpaperManager.getBuiltInDrawable(
                30000, 30000, false, 0.5f, 0.5f
            )
            drawables[2] = wallpaperManager.getBuiltInDrawable(FLAG_LOCK)

            runOnUiThread {
                displayWallpapers(drawables[0], drawables[1], drawables[2])
            }
        }
    }

    private fun displayWallpapers(
        drawable: Drawable?, builtInDrawable: Drawable?, builtInLockDrawable: Drawable?
    ) {
        binding.textViewInfoLeft.text = if (drawable != null) {
            binding.linearLayoutLeft.visibility = View.VISIBLE
            "${drawable.intrinsicWidth}x${drawable.intrinsicHeight}"
        } else {
            binding.linearLayoutLeft.visibility = View.GONE
            getString(R.string.unavailable)
        }
        binding.textViewInfoMiddle.text = if (builtInDrawable != null) {
            binding.linearLayoutMiddle.visibility = View.VISIBLE
            "${builtInDrawable.intrinsicWidth}x${builtInDrawable.intrinsicHeight}"
        } else {
            binding.linearLayoutMiddle.visibility = View.GONE
            getString(R.string.unavailable)
        }
        binding.textViewInfoRight.text = if (builtInLockDrawable != null) {
            binding.linearLayoutRight.visibility = View.VISIBLE
            "${builtInLockDrawable.intrinsicWidth}x${builtInLockDrawable.intrinsicHeight}"
        } else {
            binding.linearLayoutRight.visibility = View.GONE
            getString(R.string.unavailable)
        }

        binding.imageViewLeft.setImageDrawable(drawable)
        binding.imageViewMiddle.setImageDrawable(builtInDrawable)
        binding.imageViewRight.setImageDrawable(builtInLockDrawable)
    }

    private fun periodicPermissionCheck(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            startActivity(Intent(this, this::class.java))
            return true
        }
        return false
    }


    private fun toastMessage(message: StringRes) = toastMessage(getString(message))

    private fun toastMessage(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

class AboutActivity : ComponentActivity() {
    companion object {
        private const val LICENSE_REPORT = "file:///android_asset/open_source_licenses.html"
    }

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setHtmlText(binding.textViewAboutLicense, R.string.about_license)

        binding.buttonAboutOpenSourceLicenses.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.about_open_source_licenses))
                .setView(WebView(this).apply {
                    loadUrl(LICENSE_REPORT)
                })
                .show()
        }

        setHtmlText(
            binding.textViewAppVersion,
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.BUILD_TYPE
        )

        setHtmlText(binding.textViewIssues, R.string.about_issues)
    }

    private fun setHtmlText(
        textView: TextView,
        stringRes: StringRes,
        vararg formatArgs: Any?
    ): TextView {
        return setHtmlText(textView, getString(stringRes, *formatArgs))
    }

    private fun setHtmlText(textView: TextView, htmlString: String): TextView {
        return textView.apply {
            movementMethod = LinkMovementMethod()
            text = Html.fromHtml(
                htmlString,
                Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV
            )
        }
    }
}