package com.dicoding.picodiploma.mycamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dicoding.picodiploma.mycamera.CameraActivity.Companion.CAMERAX_RESULT
import com.dicoding.picodiploma.mycamera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Permintaan izin kamera
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                showToast("Permission request denied")
            }
        }

    // Mengecek apakah permission sudah diberikan
    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            this,
            REQUIRED_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pastikan permission kamera sudah disetujui
        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(REQUIRED_PERMISSION)
        }

        // Tombol untuk membuka CameraActivity (CameraX)
        binding.cameraXButton.setOnClickListener {
            startCameraX()
        }

        // Tombol untuk membuka HistoryFragment
        binding.viewHistoryButton.setOnClickListener {
            openHistoryFragment()
        }
    }

    /**
     * Membuka CameraActivity (fitur CameraX).
     */
    private fun startCameraX() {
        val intent = Intent(this, CameraActivity::class.java)
        // Jalankan intent dengan ActivityResult
        launcherIntentCameraX.launch(intent)
    }

    /**
     * Callback setelah kembali dari CameraActivity (bila perlu).
     */
    private val launcherIntentCameraX = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CAMERAX_RESULT) {
            val uriString = it.data?.getStringExtra(CameraActivity.EXTRA_CAMERAX_IMAGE)
            // Jika suatu saat Anda ingin menampilkan gambar di sini, Anda bisa pakai uriString
        }
    }

    /**
     * Membuka HistoryFragment di dalam fragmentContainer.
     * Menyembunyikan CardView tombol utama untuk mencegah elemen terlihat saat di HistoryFragment.
     */
    private fun openHistoryFragment() {
        binding.cardViewButtons.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HistoryFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onBackPressed() {
        // Jika pengguna kembali dari HistoryFragment, tampilkan kembali CardView tombol utama
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            binding.cardViewButtons.visibility = View.VISIBLE
            binding.fragmentContainer.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUIRED_PERMISSION = Manifest.permission.CAMERA
    }
}
