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
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
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
import androidx.core.view.DragStartHelper
import com.github.cvzi.wallpaperexport.databinding.ActivityAboutBinding
import com.github.cvzi.wallpaperexport.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


typealias StringRes = Int

enum class IntentType {
    SEND, VIEW, SAVE
}

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private val DRAWABLES_INDEX = R.string.tag_drawables_index
        private val INTENT_TYPE = R.string.tag_share_type
        private val ORIGINAL_TEXT = R.string.tag_original_text
        private val FILE_PROVIDER = R.string.file_provider_authority
    }

    private lateinit var binding: ActivityMainBinding

    private val drawables: Array<Drawable?> = Array(3) { null }
    private val temporaryFiles: HashMap<String, Uri> = HashMap(3)
    private var askedForPermission = false
    private var currentDrawable: Drawable? = null
    private var currentButton: Button? = null
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
                Handler(Looper.getMainLooper()).postDelayed(this, 1000)
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

    private val onImageClick = View.OnClickListener { view ->
        // Create temporary file for better drag'n'drop experience
        Toast.makeText(this@MainActivity, "\uD83D\uDCA1 drag and drop", Toast.LENGTH_SHORT).show()
        val drawablesIndex = view.getTag(DRAWABLES_INDEX) as Int
        drawables.getOrNull(drawablesIndex)?.let { drawable ->
            CoroutineScope(Dispatchers.Default).launch {
                createTemporaryFile(drawable, "wallpaper_$drawablesIndex")
            }
        }
    }

    private val onDragStartListener = OnDragStartListener@{ view: View, _: DragStartHelper ->
        val imageView = view as? ImageView?

        val drawablesIndex = view.getTag(DRAWABLES_INDEX) as Int
        val drawable = drawables.getOrNull(drawablesIndex)
        val fileName = fileNameSuggestion.getOrNull(drawablesIndex) ?: ""

        if (drawable == null) {
            Log.e(TAG, "onDragStartListener: Drawable is null")
            toastMessage(R.string.failed_to_extract_wallpaper)
            return@OnDragStartListener false
        }

        imageView?.setColorFilter(Color.argb(80, 70, 180, 255))

        CoroutineScope(Dispatchers.Default).launch {
            val uri = createTemporaryFile(drawable, "wallpaper_$drawablesIndex") ?: return@launch
            runOnUiThread {
                val clipData = ClipData(
                    ClipDescription(fileName, arrayOf("image/*")),
                    ClipData.Item(uri)
                )
                imageView?.colorFilter = null
                view.startDragAndDrop(
                    clipData,
                    View.DragShadowBuilder(view),
                    null,
                    View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                )
            }
        }

        return@OnDragStartListener true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(ActivityMainBinding.inflate(layoutInflater)) {
            binding = this
            setContentView(root)

            initShareButton(buttonShareLeft, 0, IntentType.SEND)
            initShareButton(buttonShareMiddle, 1, IntentType.SEND)
            initShareButton(buttonShareRight, 2, IntentType.SEND)

            initShareButton(buttonSaveLeft, 0, IntentType.SAVE)
            initShareButton(buttonSaveMiddle, 1, IntentType.SAVE)
            initShareButton(buttonSaveRight, 2, IntentType.SAVE)

            initDragDropImage(imageViewLeft, 0)
            initDragDropImage(imageViewMiddle, 1)
            initDragDropImage(imageViewRight, 2)

            textViewAbout.setOnClickListener(startAbout)
            imageButtonAbout.setOnClickListener(startAbout)

            switchMissingPermission.setOnClickListener {
                askForPermission(alwaysAsk = true)
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        Handler(Looper.getMainLooper()).removeCallbacks(permissionCheckerRunnable)

        binding.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                switchMissingPermission.visibility = View.GONE
            }
            hasPermissions({
                switchMissingPermission.isChecked = true
                CoroutineScope(Dispatchers.Default).launch {
                    loadWallpapers()
                }
            }, {
                switchMissingPermission.isChecked = false
                askForPermission()
            })
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Handler(Looper.getMainLooper()).apply {
                postDelayed(permissionCheckerRunnable, 1000)
                postDelayed({
                    removeCallbacks(permissionCheckerRunnable)
                }, 60000)
            }
        }
    }

    override fun onDestroy() {
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
        super.onDestroy()
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
                view.postDelayed({
                    revertOriginalText(view)
                }, 2000)
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
        (view as? Button?)?.apply {
            (getTag(ORIGINAL_TEXT) as? String?)?.let {
                text = it
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
        shareButton.apply {
            setTag(DRAWABLES_INDEX, drawablesIndex)
            setTag(INTENT_TYPE, intentType)
            setOnClickListener(onShareButtonClick)
        }
    }

    private fun initDragDropImage(imageView: ImageView, drawablesIndex: Int) {
        imageView.apply {
            setTag(DRAWABLES_INDEX, drawablesIndex)
            DragStartHelper(imageView, onDragStartListener).attach()
            setOnClickListener(onImageClick)
        }
    }

    private suspend fun createTemporaryFile(drawable: Drawable, fileName: String): Uri? {
        if (temporaryFiles.containsKey(fileName)) {
            return temporaryFiles[fileName]
        }
        return withContext(Dispatchers.IO) {
            val directory = File(cacheDir, "shared_wallpapers")
            var fileOutputStream: FileOutputStream? = null
            try {
                directory.mkdirs()
                val file = File(directory, "$fileName.png")
                fileOutputStream = FileOutputStream(file)

                saveDrawable(drawable, fileOutputStream)

                fileOutputStream.close()
                temporaryFiles[fileName] = FileProvider.getUriForFile(
                    this@MainActivity, getString(FILE_PROVIDER), file
                )
                return@withContext temporaryFiles[fileName]
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
            val outputStream = try {
                contentResolver.openOutputStream(uri)
            } catch (exception: FileNotFoundException) {
                Log.e(TAG, "storeToUri() Error opening output stream", exception)
                return@withContext false
            }
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
        Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        ).let { bitmap ->
            Canvas(bitmap).let { canvas ->
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
            outputStream.buffered().apply {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                try {
                    flush()
                    close()
                } catch (exception: IOException) {
                    Log.e(TAG, "saveDrawable() Failed to write file", exception)
                }

            }
        }
    }

    private fun askForPermission(alwaysAsk: Boolean = false) {
        if (askedForPermission && !alwaysAsk) {
            return
        }
        askedForPermission = true
        var askForPermissionMessage = getString(R.string.ask_for_permission_message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForPermissionMessage += "\n\nYou need to grant two permissions:"
            if (checkSelfPermission(READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    askForPermissionMessage += "\n\nMake sure to accept \"Allow all\"!\n"
                }
                askForPermissionMessage += "\n[ ] READ_MEDIA_IMAGES"
            } else {
                askForPermissionMessage += "\n[✓] READ_MEDIA_IMAGES"
            }
            askForPermissionMessage += if (!Environment.isExternalStorageManager()) {
                "\n[ ] All files access"
            } else {
                "\n[✓] All files access"
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ask_for_permission_title)
            .setMessage(askForPermissionMessage)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(
                            READ_MEDIA_IMAGES
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissionLauncher.launch(READ_MEDIA_IMAGES)
                        askedForPermission = false
                    } else if (!Environment.isExternalStorageManager()) {
                        manageStoragePermissionLauncher.launch(
                            Intent(
                                ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                            )
                        )
                        dialog.dismiss()
                    }
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
        if (checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && Environment.isExternalStorageManager()
                    && checkSelfPermission(READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    && Environment.isExternalStorageManager())
        ) {
            ok()
        } else {
            error()
        }
    }

    @RequiresPermission(READ_EXTERNAL_STORAGE)
    private suspend fun loadWallpapers() {
        withContext(Dispatchers.IO) {
            temporaryFiles.clear()

            val wallpaperManager = WallpaperManager.getInstance(this@MainActivity)
            drawables[0] = wallpaperManager.drawable
            drawables[1] = wallpaperManager.getBuiltInDrawable(
                30000, 30000, false, 0.5f, 0.5f
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {



            } else {

                wallpaperManager.getWallpaperFile(FLAG_LOCK)?.use {
                    drawables[2] =
                        BitmapDrawable(resources, BitmapFactory.decodeFileDescriptor(it.fileDescriptor))
                }
                if (drawables[2] == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    drawables[2] = wallpaperManager.getDrawable(FLAG_LOCK)
                }
                if (drawables[2] == null) {
                    drawables[2] = wallpaperManager.getBuiltInDrawable(FLAG_LOCK)
                }
            }
            runOnUiThread {
                binding.displayWallpapers()
            }
        }
    }

    private fun ActivityMainBinding.displayWallpapers() {
        val drawable: Drawable? = drawables[0]
        val builtInDrawable: Drawable? = drawables[1]
        val lockDrawable: Drawable? = drawables[2]

        textViewInfoLeft.text = if (drawable != null) {
            linearLayoutLeft.visibility = View.VISIBLE
            "${drawable.intrinsicWidth}x${drawable.intrinsicHeight}"
        } else {
            linearLayoutLeft.visibility = View.GONE
            getString(R.string.unavailable)
        }
        textViewInfoMiddle.text = if (builtInDrawable != null) {
            linearLayoutMiddle.visibility = View.VISIBLE
            "${builtInDrawable.intrinsicWidth}x${builtInDrawable.intrinsicHeight}"
        } else {
            linearLayoutMiddle.visibility = View.GONE
            getString(R.string.unavailable)
        }
        textViewInfoRight.text = if (lockDrawable != null) {
            linearLayoutRight.visibility = View.VISIBLE
            "${lockDrawable.intrinsicWidth}x${lockDrawable.intrinsicHeight}"
        } else {
            linearLayoutRight.visibility = View.GONE
            getString(R.string.unavailable)
        }

        imageViewLeft.setImageDrawable(drawable)
        imageViewMiddle.setImageDrawable(builtInDrawable)
        imageViewRight.setImageDrawable(lockDrawable)
    }

    private fun periodicPermissionCheck(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // Open Main Activity again
            Intent(this, this::class.java).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    PendingIntent.getActivity(this, 0, it.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }, PendingIntent.FLAG_IMMUTABLE).send()
                } else {
                    startActivity(it)
                }
            }
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

        with(ActivityAboutBinding.inflate(layoutInflater)) {
            binding = this
            setContentView(root)

            setHtmlText(textViewAboutLicense, R.string.about_license)

            buttonAboutOpenSourceLicenses.setOnClickListener {
                AlertDialog.Builder(it.context)
                    .setTitle(getString(R.string.about_open_source_licenses))
                    .setView(WebView(it.context).apply {
                        loadUrl(LICENSE_REPORT)
                    })
                    .show()
            }

            setHtmlText(
                textViewAppVersion,
                R.string.about_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.BUILD_TYPE
            )

            setHtmlText(textViewIssues, R.string.about_issues)

            setHtmlText(textViewDonate, R.string.about_donate)
        }
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