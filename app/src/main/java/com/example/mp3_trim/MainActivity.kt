package com.example.mp3_trim

//import android.net.Uri
//import android.os.Build
//import android.os.Environment
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File


class MainActivity : AppCompatActivity() {

    //UI Views

    private companion object {
        //PERMISSION request constant, assign any value
        private const val TAG = "PERMISSION_TAG"
        private const val PICK_AUDIO_FILE_CODE = 101
        private var selectedFilePath: String? = null
        private const val STORAGE_PERMISSION_REQUEST_CODE = 100

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init UI Views
        val checkboxPermission = findViewById<CheckBox>(R.id.checkbox_permission)
        val buttonChooseAudio = findViewById<Button>(R.id.button_choose_audio)
        val buttonStart = findViewById<Button>(R.id.button_start)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val itemContainer = findViewById<LinearLayout>(R.id.itemContainer)
        val addItemButton = findViewById<Button>(R.id.addItemButton)
        addItemButton.setOnClickListener {
            val inflater = LayoutInflater.from(this)
            val itemView = inflater.inflate(R.layout.list_item, itemContainer, false)

            // Find the remove button within the newly inflated item view
            val removeButton = itemView.findViewById<Button>(R.id.removeItemButton)

            // Set the click listener for the remove button
            removeButton.setOnClickListener {
                // Remove the item view from the container
                itemContainer.removeView(itemView)
            }
            itemContainer.addView(itemView)
            print("add ")
        }
        // Check if the permission is already granted and update the checkbox
        if (checkPermission()) {
            checkboxPermission.isChecked = true
        } else {
            requestPermission()
        }

        // Choose an audio file
        buttonChooseAudio.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "audio/*"
            startActivityForResult(intent, PICK_AUDIO_FILE_CODE)

        }

        // Start trimming process

        buttonStart.setOnClickListener {
            val requests = ArrayList<TrimRequest>()
            for (i in 0 until itemContainer.childCount) {
                val itemView = itemContainer.getChildAt(i)
                val etStartTime = itemView.findViewById<EditText>(R.id.edittext_start_time)
                val etEndTime = itemView.findViewById<EditText>(R.id.edittext_end_time)
                val startTime = etStartTime.text.toString()
                val endTime = etEndTime.text.toString()
                if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                    requests.add(TrimRequest(startTime = startTime, endTime = endTime))
                }
            }
            if (selectedFilePath != null && requests.isNotEmpty()) {
                // Show loading, disable button
                progressBar.visibility = View.VISIBLE
                buttonStart.isEnabled = false
                trimMp3List(selectedFilePath!!, requests) {
                    progressBar.visibility = View.GONE
                    buttonStart.isEnabled = true
                }
            } else {
                Toast.makeText(
                    this,
                    "Please choose a file and fill in both start and end times",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        } else {
//            //Android is below 11(R)
//            val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
            return false

        }
    }


    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            try {
                Log.d(TAG, "requestPermission: try")
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "requestPermission: ", e)
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)

            }
        } else {
            //Android is below 11(R)
//            ActivityCompat.requestPermissions(this,
//                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
//                STORAGE_PERMISSION_CODE
//            )
        }
    }

    private val storageActivityResultLauncher = this.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "storageActivityResultLauncher: ")
        //here we will handle the result of our intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            if (Environment.isExternalStorageManager()) {
                //Manage External Storage Permission is granted
                Log.d(
                    TAG,
                    "storageActivityResultLauncher: Manage External Storage Permission is granted"
                )
                val checkboxPermission = findViewById<CheckBox>(R.id.checkbox_permission)
                checkboxPermission.isChecked = true
            } else {
                //Manage External Storage Permission is denied....
                Log.d(
                    TAG,
                    "storageActivityResultLauncher: Manage External Storage Permission is denied...."
                )
                toast("Manage External Storage Permission is denied....")
            }
        } else {
            //Android is below 11(R)
        }
    }


    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, you can read/write to storage

            } else {
                // Permission denied, show a message to the user
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class TrimRequest(val startTime: String, val endTime: String)

    private fun trimMp3List(
        inputPath: String,
        requests: List<TrimRequest>,
        onTrimCompleted: () -> Unit
    ) {
        requests.listIterator().forEach {
            trimMp3(inputPath = inputPath, startTime = it.startTime, endTime = it.endTime) {
                //do handle when a trim file finished
            }
        }
        onTrimCompleted();
    }

    private fun trimMp3(
        inputPath: String,
        startTime: String,
        endTime: String,
        onTrimComplete: () -> Unit
    ) {
        // Extract the file name from the input path and append '_trimmed'
        val fileNameWithoutExtension = inputPath.substringBeforeLast(".")
        val fileExtension = inputPath.substringAfterLast(".", "")
        val outputFileName =
            "${fileNameWithoutExtension}_${startTime}_${endTime}_trimmed.$fileExtension"

        val endTimeInSecond = convertTimeToSecond(endTime);
        val startTimeInSecond = convertTimeToSecond(startTime);
        val duration = (endTimeInSecond - startTimeInSecond).toString()


        // Delete the output file if it exists
        val outputFile = File(outputFileName)
        if (outputFile.exists()) {
            val deleted = outputFile.delete()
            if (deleted) {
                Log.d("TrimMp3", "Existing output file deleted: $outputFileName")
            } else {
                Log.w("TrimMp3", "Failed to delete existing output file: $outputFileName")
            }
        }

        // FFmpeg command to trim the MP3
        val command = "-i $inputPath -ss $startTime -t $duration -acodec copy $outputFileName"
        Log.d(TAG, "trimMp3: command $command")
        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode

            Handler(Looper.getMainLooper()).post {
                if (ReturnCode.isSuccess(returnCode)) {
                    Toast.makeText(
                        this,
                        "MP3 trimmed: ${startTime}:${endTime} successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Error trimming MP3 ${startTime}:${endTime}, errorCode: $returnCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onTrimComplete() // Call the completion callback after processing
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val textviewFilePath = findViewById<TextView>(R.id.textview_file_path)
        var outputTextview = findViewById<TextView>(R.id.outputTextview)
        if (requestCode == PICK_AUDIO_FILE_CODE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                selectedFilePath = getRealPathFromUri(this, uri)
                textviewFilePath.text = "Input: $selectedFilePath"

                val fileNameWithoutExtension = selectedFilePath?.substringBeforeLast(".")
                val fileExtension = selectedFilePath?.substringAfterLast(".", "")
                val outputFileName = "${fileNameWithoutExtension}_trimmed.$fileExtension"
                outputTextview.text = "Output: $outputFileName"
            }
        }

    }

    fun convertTimeToSecond(time: String): Int {
        // split the time string by colon (:)
        val timeParts = time.split(":")

        return when (timeParts.size) {
            3 -> {

                //Parse hours, minutes and seconds
                val hours = timeParts[0].toInt()
                val minutes = timeParts[1].toInt()
                val seconds = timeParts[2].toInt()

                //Convert the time to total seconds
                hours * 3600 + minutes * 60 + seconds
            }

            2 -> {
                val minutes = timeParts[0].toInt()
                val seconds = timeParts[1].toInt()

                //Convert the time to total seconds
                minutes * 60 + seconds
            }

            1 -> {
                val seconds = timeParts[0].toInt()
                seconds
            }

            else -> throw IllegalArgumentException("Invalid time format")
        }
    }

}


fun getRealPathFromUri(context: Context, uri: Uri): String? {
    // Document Provider
    if (DocumentsContract.isDocumentUri(context, uri)) {
        when {
            isExternalStorageDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
                // Handle non-primary volumes (SD cards, etc.) here if necessary
            }

            isDownloadsDocument(uri) -> {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) {
                    return id.substring(4)
                }
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    id.toLong()
                )
                return getDataColumn(context, contentUri, null, null)
            }

            isMediaDocument(uri) -> {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
    }
    // MediaStore (and general)
    else if ("content".equals(uri.scheme, ignoreCase = true)) {
        return getDataColumn(context, uri, null, null)
    }
    // File
    else if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }
    return null
}

// Helper method to extract data from Uri
private fun getDataColumn(
    context: Context,
    uri: Uri?,
    selection: String?,
    selectionArgs: Array<String>?
): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)
    try {
        cursor = uri?.let {
            context.contentResolver.query(
                it,
                projection,
                selection,
                selectionArgs,
                null
            )
        }
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(index)
        }
    } finally {
        cursor?.close()
    }
    return null
}

// Checks if the Uri authority is ExternalStorageProvider
private fun isExternalStorageDocument(uri: Uri): Boolean {
    return "com.android.externalstorage.documents" == uri.authority
}

// Checks if the Uri authority is DownloadsProvider
private fun isDownloadsDocument(uri: Uri): Boolean {
    return "com.android.providers.downloads.documents" == uri.authority
}

// Checks if the Uri authority is MediaProvider
private fun isMediaDocument(uri: Uri): Boolean {
    return "com.android.providers.media.documents" == uri.authority
}