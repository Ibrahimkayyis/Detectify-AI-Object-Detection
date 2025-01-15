package com.dicoding.picodiploma.mycamera

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dicoding.picodiploma.mycamera.data.DatabaseProvider
import com.dicoding.picodiploma.mycamera.data.DetectionDao
import com.dicoding.picodiploma.mycamera.data.DetectionEntity
import com.dicoding.picodiploma.mycamera.data.HistoryAdapter
import com.dicoding.picodiploma.mycamera.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter by lazy { HistoryAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val detectionDao = DatabaseProvider.getDatabase(requireContext()).detectionDao()

        // Load semua data saat awal
        loadAllDetections(detectionDao)

        // Pencarian
        binding.searchView.setOnQueryTextListener(
            object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { searchDetections(detectionDao, it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { searchDetections(detectionDao, it) }
                    return true
                }
            }
        )

        // Tambahkan listener untuk tombol Delete All
        binding.btnDeleteAll.setOnClickListener {
            confirmDeleteAll(detectionDao)
        }

        // Tambahkan listener untuk hapus item (long click di RecyclerView)
        setupAdapterItemDelete(detectionDao)
    }

    private fun loadAllDetections(detectionDao: DetectionDao) {
        lifecycleScope.launch {
            detectionDao.getAllDetections().collect { detections ->
                adapter.setData(detections)
            }
        }
    }

    private fun searchDetections(detectionDao: DetectionDao, query: String) {
        lifecycleScope.launch {
            detectionDao.searchByLabel(query).collect { detections ->
                adapter.setData(detections)
            }
        }
    }

    /**
     * Memunculkan dialog konfirmasi untuk hapus semua data
     */
    private fun confirmDeleteAll(detectionDao: DetectionDao) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All History")
            .setMessage("Are you sure you want to delete all history?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    detectionDao.deleteAllDetections() // Fungsi ini perlu ditambahkan di DetectionDao
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Mengatur aksi long-click pada setiap item di RecyclerView untuk menghapus item tertentu.
     */
    private fun setupAdapterItemDelete(detectionDao: DetectionDao) {
        adapter.setOnItemLongClickListener { detection ->
            // Tampilkan konfirmasi
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this detection?")
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch {
                        detectionDao.deleteDetection(detection)
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
