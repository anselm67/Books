package com.anselm.books.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentScanBinding
import com.anselm.books.databinding.RecyclerviewScanIsbnBinding
import com.anselm.books.ui.widgets.BookFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment: BookFragment() {
    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var adapter: IsbnArrayAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentScanBinding.inflate(inflater, container, false)

        // Sets up the recycler view.
        adapter = IsbnArrayAdapter()
        binding.idRecycler.adapter = adapter
        binding.idRecycler.layoutManager = LinearLayoutManager(binding.idRecycler.context)

        // Checks permissions and sets up the camera.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if ( it ) {
                    startCamera()
                } else {
                    app.toast(R.string.request_camera_permission)
                }
            }
            launcher.launch(Manifest.permission.CAMERA)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        return binding.root
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Sets up the preview use case.
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.idViewFinder.surfaceProvider)
                }

            // Sets up the image analyzer user case.
            val barcodeAnalyzer = BarcodeAnalyzer(binding.idOverlay) {
                handleISBN(it)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, barcodeAnalyzer)
                }

            try {
                // Binds our use cases to the camera after clearing out any previous binding.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
                barcodeAnalyzer.scaleFor(imageAnalyzer.resolutionInfo!!, binding.idViewFinder)
            } catch(e: Exception) {
                Log.e(TAG, "Failed to bind the camera", e)
                app.toast(getString(R.string.bind_camera_failed, e.message))
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleISBN(isbn: String) {
        playSound()
        adapter.insertFirst(isbn)
    }

    private fun playSound() {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(requireContext(), notification)
        ringtone.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}

private class IsbnArrayAdapter: RecyclerView.Adapter<IsbnArrayAdapter.ViewHolder>() {
    private val dataSource = mutableListOf<String>()
    inner class ViewHolder(
        val binding: RecyclerviewScanIsbnBinding,
    ): RecyclerView.ViewHolder(binding.root) {

        fun bind(isbn: String) {
            binding.idISBNText.text = isbn
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewScanIsbnBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    fun insertFirst(isbn: String) {
        dataSource.add(0, isbn)
        notifyItemInserted(0)
    }
}
