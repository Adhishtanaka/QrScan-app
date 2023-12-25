package Adhishtanaka.qrscan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_CAMERA_REQUEST = 1
        private const val IMAGE_PICKER_REQUEST_CODE = 2
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }



    private var isDialogShowing = false
    private lateinit var pvScan: androidx.camera.view.PreviewView
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private val screenAspectRatio: Int
        get() {
            val metrics = DisplayMetrics().also { pvScan.display?.getRealMetrics(it) }
            return aspectRatio(metrics.widthPixels, metrics.heightPixels)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContentView(R.layout.activity_main)



        pvScan = findViewById(R.id.scanPreview)

        setupCamera()

        val switch = findViewById<SwitchMaterial>(R.id.flashSwitch)

        switch.setOnCheckedChangeListener { _, isChecked ->
            flash(isChecked)
        }

        val chooseImageButton = findViewById<ImageButton>(R.id.chooseImageButton)

        chooseImageButton.setOnClickListener {
            openImagePicker()
        }


        val openHistoryButton = findViewById<ImageButton>(R.id.historyButton)


        openHistoryButton.setOnClickListener {
            val intent = Intent(this@MainActivity, HistoryActivity::class.java)
            startActivity(intent)
        }

        val zoomSlider = findViewById<SeekBar>(R.id.zoomSlider)
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val zoomLevel = progress / 100.0f
                setZoom(zoomLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICKER_REQUEST_CODE)
    }


    private fun flash(b: Boolean) {
        if (cameraProvider != null) {
            val camera = cameraProvider?.bindToLifecycle(
                this,
                cameraSelector!!,
                previewUseCase,
                analysisUseCase
            )
            val cameraControl = camera?.cameraControl

            if (b) {
                cameraControl?.enableTorch(true)
            } else {
                cameraControl?.enableTorch(false)
            }
        }
    }

    private fun setupCamera() {
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                if (isCameraPermissionGranted()) {
                    bindCameraUseCases()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(
                            arrayOf(Manifest.permission.CAMERA),
                            PERMISSION_CAMERA_REQUEST
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun setZoom(zoomLevel: Float) {
        if (cameraProvider != null) {
            val camera = cameraProvider?.bindToLifecycle(
                this,
                cameraSelector!!,
                previewUseCase,
                analysisUseCase
            )
            val cameraControl = camera?.cameraControl
            val cameraInfo = camera?.cameraInfo

            val maxZoomRatio = cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1.0f

            val targetZoomRatio = 1.0f + zoomLevel * (maxZoomRatio - 1.0f)

            cameraControl?.setZoomRatio(targetZoomRatio)
        }
    }

    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider?.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(pvScan.display.rotation)
            .build()

        previewUseCase?.setSurfaceProvider(pvScan.surfaceProvider)

        cameraSelector?.let {
            cameraProvider?.bindToLifecycle(this, it, previewUseCase)
        }
    }

    private fun bindAnalyseUseCase() {
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider?.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(pvScan.display.rotation)
            .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(cameraExecutor, { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)
        })

        cameraSelector?.let {
            cameraProvider?.bindToLifecycle(
                this,
                it, analysisUseCase
            )
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun processImageProxy(barcodeScanner: BarcodeScanner, imageProxy: ImageProxy) {
        if (imageProxy.image == null || isDialogShowing) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.getOrNull(0)
                barcode?.rawValue?.let { code ->
                    if (!isDialogShowing) {
                        val content = barcode?.rawValue
                        val category = determineCategory(content)
                        val calendar = Calendar.getInstance()
                        val datetime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time)

                        // Common function to handle duplicates
                        handleDuplicateAndInsert(code, datetime, category)
                    }
                }
            }
            .addOnFailureListener {

            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
    private fun handleDuplicateAndInsert(code: String, datetime: String, category: String) {

        val isDuplicate = isDuplicate(code)

        if (isDuplicate) {

            deleteDuplicates(code)
        }


        insertScannedData(code, datetime, category)


        showDialogA(code, category)
        isDialogShowing = true
    }


    private fun isDuplicate(code: String): Boolean {
        val dbHelper = QRCodeDatabaseHelper(this@MainActivity)
        val database = dbHelper.readableDatabase

        val cursor = database.query(
            QRCodeDatabaseHelper.TABLE_QR,
            arrayOf(QRCodeDatabaseHelper.KEY_ID),
            "${QRCodeDatabaseHelper.KEY_Details} = ?",
            arrayOf(code),
            null,
            null,
            null
        )

        val isDuplicate = cursor.count > 0
        cursor.close()

        return isDuplicate
    }

    private fun deleteDuplicates(code: String) {
        val dbHelper = QRCodeDatabaseHelper(this@MainActivity)
        val database = dbHelper.writableDatabase

        database.delete(
            QRCodeDatabaseHelper.TABLE_QR,
            "${QRCodeDatabaseHelper.KEY_Details} = ?",
            arrayOf(code)
        )
    }



    private fun determineCategory(content: String?): String {
        content?.let { nonNullContent ->
            val websitePattern = Pattern.compile("^(https?|ftp)://.*$")
            val wifiPattern = Pattern.compile("^WIFI:.*$")

            if (websitePattern.matcher(nonNullContent).matches()) {
                return "Website"
            } else if (wifiPattern.matcher(nonNullContent).matches()) {
                return "WiFi"
            }
        }
        return "Text"
    }

    private fun insertScannedData(data: String, datetime: String, category: String) {
        val dbHelper = QRCodeDatabaseHelper(this)
        val database = dbHelper.writableDatabase

        val dateFormat = SimpleDateFormat("yyyy MMM dd", Locale.ENGLISH)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

        val values = ContentValues().apply {
            put(QRCodeDatabaseHelper.KEY_Details, data)

            val calendar = Calendar.getInstance()
            val parsedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).parse(datetime)
            calendar.time = parsedDate

            val formattedDate = dateFormat.format(calendar.time)
            val formattedTime = timeFormat.format(calendar.time)

            put(QRCodeDatabaseHelper.KEY_DateTime, "$formattedDate $formattedTime")
            put(QRCodeDatabaseHelper.KEY_Tag, category)
        }

        database.insert(QRCodeDatabaseHelper.TABLE_QR, null, values)
    }

    private fun showDialogA(code: String, category: String) {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle("QR Scan")


        when (category) {
            "Text" -> {
                dialogBuilder.setMessage(code)
                dialogBuilder.setPositiveButton("Copy") { _, _ ->
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("QR Scan", code)
                    clipboardManager.setPrimaryClip(clipData)

                }
            }
            "Website" -> {
                dialogBuilder.setMessage(code)
                dialogBuilder.setPositiveButton("Open") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(code))
                    startActivity(intent)
                }
                dialogBuilder.setNegativeButton("Copy") { _, _ ->
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("QR Scan", code)
                    clipboardManager.setPrimaryClip(clipData)

                }
            }
            "WiFi" -> {
                val wifiData = parseWiFiQRCodeData(code)
                val ssid = wifiData["ssid"]
                val password = wifiData["password"]

                val dialogMessage = "SSID: $ssid\nPassword: $password"
                dialogBuilder.setMessage(dialogMessage)

                dialogBuilder.setPositiveButton("Copy Password") { _, _ ->
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("WiFi Password", password)
                    clipboardManager.setPrimaryClip(clipData)

                }
            }
        }

        dialogBuilder.setNeutralButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        dialogBuilder.setOnDismissListener {
            isDialogShowing = false
        }

        dialogBuilder.show()
    }

    private fun parseWiFiQRCodeData(qrCodeContent: String): Map<String, String> {
        val wifiData = mutableMapOf<String, String>()

        val ssidPattern = Pattern.compile("S:([^;]+);")
        val passwordPattern = Pattern.compile("P:([^;]+);")

        val ssidMatcher = ssidPattern.matcher(qrCodeContent)
        val passwordMatcher = passwordPattern.matcher(qrCodeContent)

        if (ssidMatcher.find() && passwordMatcher.find()) {
            val ssid = ssidMatcher.group(1)
            val password = passwordMatcher.group(1)

            wifiData["ssid"] = ssid
            wifiData["password"] = password
        }

        return wifiData
    }





    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = Math.max(width, height).toDouble() / Math.min(width, height)
        if (Math.abs(previewRatio - RATIO_4_3_VALUE) <= Math.abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                setupCamera()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean = this.let {
        ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
    } == PackageManager.PERMISSION_GRANTED

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                handleSelectedImage(uri)
            }
        }
    }

    private fun handleSelectedImage(imageUri: Uri) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Processing Image...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            try {
                val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

                barcodeScanner.process(InputImage.fromFilePath(this, imageUri))
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.getOrNull(0)
                        if (barcode != null) {
                            val code = barcode.rawValue
                            val category = determineCategory(code)
                            handler.post {
                                if (code != null) {

                                    if (isDuplicate(code)) {
                                        deleteDuplicates(code)
                                    }


                                    val calendar = Calendar.getInstance()
                                    val datetime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        .format(calendar.time)
                                    insertScannedData(code, datetime, category)

                                    
                                    showDialogA(code, category)
                                }
                            }
                        } else {
                            handler.post {
                                showNoQRCodeFoundMessage()
                            }
                        }
                    }
                    .addOnFailureListener {
                        handler.post {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                "Failed to decode QR code",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
            } catch (e: Exception) {
                handler.post {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Error processing image: ${e.message}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } finally {
                progressDialog.dismiss()
            }
        }
    }


    private fun showNoQRCodeFoundMessage() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle("No QR Code Found")
            .setMessage("The selected image does not contain a QR code.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                isDialogShowing = false
            }

        dialogBuilder.show()
    }

    private fun checkAndAddQRCode(code: String, category: String) {
        val dbHelper = QRCodeDatabaseHelper(this)
        val database = dbHelper.writableDatabase


        val cursor = database.query(
            QRCodeDatabaseHelper.TABLE_QR,
            arrayOf(QRCodeDatabaseHelper.KEY_ID),
            "${QRCodeDatabaseHelper.KEY_Details} = ?",
            arrayOf(code),
            null,
            null,
            null
        )

        if (cursor.count > 0) {

            database.delete(
                QRCodeDatabaseHelper.TABLE_QR,
                "${QRCodeDatabaseHelper.KEY_Details} = ?",
                arrayOf(code)
            )
        }

        val calendar = Calendar.getInstance()
        val datetime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(calendar.time)

        val values = ContentValues().apply {
            put(QRCodeDatabaseHelper.KEY_Details, code)

            val formattedDate = SimpleDateFormat("yyyy MMM dd", Locale.ENGLISH).format(calendar.time)
            val formattedTime = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(calendar.time)

            put(QRCodeDatabaseHelper.KEY_DateTime, "$formattedDate $formattedTime")
            put(QRCodeDatabaseHelper.KEY_Tag, category)
        }

        database.insert(QRCodeDatabaseHelper.TABLE_QR, null, values)

        cursor.close()
    }


    override fun onResume() {
        super.onResume()
        val zoomSlider = findViewById<SeekBar>(R.id.zoomSlider)
        zoomSlider.progress = 0
    }

}
