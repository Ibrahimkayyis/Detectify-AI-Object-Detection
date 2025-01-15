package com.dicoding.picodiploma.mycamera

import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dicoding.picodiploma.mycamera.data.DatabaseProvider
import com.dicoding.picodiploma.mycamera.data.DetectionEntity
import com.dicoding.picodiploma.mycamera.databinding.ActivityCameraBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.task.gms.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    // Gunakan kamera belakang secara default
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Untuk deteksi objek di setiap frame
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    // Tambahkan ImageCapture untuk mengambil foto
    private var imageCapture: ImageCapture? = null

    // DAO untuk DB
    private val detectionDao by lazy {
        DatabaseProvider.getDatabase(this).detectionDao()
    }

    // Variabel untuk menyimpan ukuran frame analisis (agar bounding box presisi).
    // Akan kita perbarui di onResults().
    private var latestImageWidth = 0
    private var latestImageHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tombol simpan dan history
        binding.btnSaveDetection.setOnClickListener {
            saveCurrentDetection()
        }
        binding.btnViewHistory.setOnClickListener {
            openHistoryFragment()
        }
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUI()
        startCamera()
    }

    /**
     * Menginisialisasi CameraX: Preview, ImageAnalysis, dan ImageCapture.
     */
    private fun startCamera() {
        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            detectorListener = object : ObjectDetectorHelper.DetectorListener {
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(
                    results: MutableList<Detection>?,
                    inferenceTime: Long,
                    imageHeight: Int,
                    imageWidth: Int
                ) {
                    runOnUiThread {
                        // Simpan ukuran image analyzer
                        latestImageWidth = imageWidth
                        latestImageHeight = imageHeight

                        results?.let {
                            if (it.isNotEmpty() && it[0].categories.isNotEmpty()) {
                                // Tampilkan bounding box, label, dsb di overlay
                                binding.overlay.setResults(it, imageHeight, imageWidth)

                                val builder = StringBuilder()
                                for (result in it) {
                                    val displayResult =
                                        "${result.categories[0].label} " +
                                                NumberFormat.getPercentInstance().format(
                                                    result.categories[0].score
                                                ).trim()
                                    builder.append("$displayResult \n")
                                }

                                binding.tvResult.text = builder.toString()
                                binding.tvInferenceTime.text = "$inferenceTime ms"
                            } else {
                                binding.overlay.clear()
                                binding.tvResult.text = ""
                                binding.tvInferenceTime.text = ""
                            }
                        }

                        binding.overlay.invalidate()
                    }
                }
            }
        )

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Persiapkan Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Persiapkan ImageAnalysis
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                // Jalankan deteksi di setiap frame
                objectDetectorHelper.detectObject(image)
            }

            // Persiapkan ImageCapture
            val imageCaptureBuilder = ImageCapture.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)

            imageCapture = imageCaptureBuilder.build()

            try {
                // Pastikan unbind terlebih dahulu
                cameraProvider.unbindAll()
                // Bind: Preview, ImageAnalysis, dan ImageCapture
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(
                    this@CameraActivity, "Gagal memunculkan kamera.", Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "startCamera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Ketika tombol "Save Detection" ditekan:
     * 1. Cek apakah ada hasil deteksi.
     * 2. Jika ada, tangkap foto dengan ImageCapture (OnImageCapturedCallback).
     * 3. Setelah dapat imageProxy, konversi ke Bitmap.
     * 4. Gambar bounding box (seluruh objek) ke atas Bitmap.
     * 5. Simpan Bitmap final ke file.
     * 6. Simpan URI file ke database.
     */
    private fun saveCurrentDetection() {
        val results = binding.overlay.getCurrentResults()
        if (results.isNullOrEmpty()) {
            runOnUiThread {
                Toast.makeText(this, "No detections to save!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val detections = results
        val currentCapture = imageCapture ?: return

        currentCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val originalBitmap = imageProxy.toBitmap()
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close()

                    if (originalBitmap == null) {
                        runOnUiThread {
                            Toast.makeText(
                                this@CameraActivity,
                                "Failed to get Bitmap!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    // Rotasi agar orientasi benar
                    val correctedBitmap = originalBitmap.rotate(rotationDegrees)

                    // Gambar bounding box ke correctedBitmap
                    val finalBitmap = correctedBitmap.drawDetections(
                        detections = detections,
                        imageWidth = correctedBitmap.width,
                        imageHeight = correctedBitmap.height
                    )

                    // Simpan finalBitmap ke file
                    val photoFile = createFile()
                    val success = finalBitmap.saveToFile(photoFile)
                    if (!success) {
                        runOnUiThread {
                            Toast.makeText(
                                this@CameraActivity,
                                "Failed saving final image!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    // Simpan ke DB
                    val joinedLabels = detections.joinToString { it.categories[0].label }
                    val maxScore = detections.maxOf { it.categories[0].score }

                    CoroutineScope(Dispatchers.IO).launch {
                        detectionDao.insertDetection(
                            DetectionEntity(
                                imageUri = photoFile.toUri().toString(),
                                label = joinedLabels,
                                score = maxScore,
                                boundingBox = "Multiple bounding boxes"
                            )
                        )
                        detectionDao.deleteOldDetections()
                    }

                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            "Detection & photo saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "onError: ${exception.message}", exception)
                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            "Failed capturing photo!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }


    /**
     * Membuat file baru dengan format nama "IMG-yyyyMMdd-HHmmss.jpg" di folder cache (contoh).
     * Anda bisa mengubahnya ke folder eksternal/DCIM, dsb. sesuai kebutuhan & permission.
     */
    private fun createFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val fileName = "IMG_$timeStamp.jpg"

        val storageDir: File? = cacheDir
        return File(storageDir, fileName)
    }

    private fun openHistoryFragment() {
        binding.viewFinder.visibility = View.GONE
        binding.overlay.visibility = View.GONE
        binding.btnSaveDetection.visibility = View.GONE
        binding.btnViewHistory.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HistoryFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onBackPressed() {
        // Jika sedang di fragment history, kembali ke kamera
        if (binding.fragmentContainer.visibility == View.VISIBLE) {
            binding.fragmentContainer.visibility = View.GONE
            binding.viewFinder.visibility = View.VISIBLE
            binding.overlay.visibility = View.VISIBLE
            binding.btnSaveDetection.visibility = View.VISIBLE
            binding.btnViewHistory.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        supportActionBar?.hide()
    }

    companion object {
        private const val TAG = "CameraActivity"
        const val CAMERAX_RESULT = 200
        const val EXTRA_CAMERAX_IMAGE = "extra_camerax_image"
    }

    // ------------------------------------------------------------
    //               EXTENSION / UTILITY FUNGSI
    // ------------------------------------------------------------

    /**
     * Konversi ImageProxy (JPEG) ke Bitmap.
     * Catatan: Pastikan format keluaran ImageCapture adalah JPEG.
     * Jika menggunakan YUV_420_888, perlu konversi manual (YuvImage).
     */
    private fun ImageProxy.toBitmap(): Bitmap? {
        // Pastikan planes.size > 0
        val planeProxy = planes.getOrNull(0) ?: return null
        val buffer = planeProxy.buffer
        buffer.rewind()

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Gambar bounding box (label & skor) ke Bitmap dan kembalikan hasilnya.
     * [imageWidth], [imageHeight] adalah ukuran Bitmap ini (bisa pakai bitmap.width, bitmap.height).
     */
    private fun Bitmap.drawDetections(
        detections: List<Detection>,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap {
        // Salin bitmap agar yang asli tidak terganggu
        val tempBitmap = copy(config, true)
        val canvas = Canvas(tempBitmap)

        // Paint untuk bounding box
        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        // Paint untuk teks label
        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 40f
            style = Paint.Style.FILL
        }

        // Asumsikan boundingBox di [0..imageWidth, 0..imageHeight].
        // Jika bounding box hasil deteksi beda resolusi, Anda harus scale.

        for (det in detections) {
            val rect = det.boundingBox // android.graphics.RectF
            // Gambar bounding box
            canvas.drawRect(rect, boxPaint)

            // Tulis label + skor. Ambil kategori pertama saja (bisa kembangkan sendiri).
            val category = det.categories[0]
            val labelText = "${category.label} ${(category.score * 100).toInt()}%"
            // Tulis di atas left-top bounding box
            canvas.drawText(labelText, rect.left, rect.top - 10, textPaint)
        }

        return tempBitmap
    }

    /**
     * Simpan Bitmap ke file JPEG.
     */
    private fun Bitmap.saveToFile(file: File): Boolean {
        return try {
            FileOutputStream(file).use { fos ->
                compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun Bitmap.rotate(deg: Int): Bitmap {
        if (deg == 0) return this
        val matrix = Matrix()
        matrix.postRotate(deg.toFloat())
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

}
