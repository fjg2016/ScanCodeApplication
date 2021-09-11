package com.fengnan.code

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var mResultLayout: LinearLayout
    private lateinit var mResultTv: TextView
    private lateinit var mCopyBtn: Button
    private lateinit var mScanIv: ImageView
    private lateinit var mFlashIv: ImageView
    private lateinit var mPreviewView: PreviewView
    private lateinit var mCameraExecutor: ExecutorService

    private var mCameraProvider: ProcessCameraProvider? = null
    private var mPreView: Preview? = null
    private var mCamera: Camera? = null

    private var mImageAnalysis: ImageAnalysis? = null

    private var mBackCamera = true
    private var mFlashLightOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mResultLayout = findViewById(R.id.llResult)
        mResultTv = findViewById(R.id.tvResult)
        mCopyBtn = findViewById(R.id.btnCopy)
        mScanIv = findViewById(R.id.ivScan)
        mFlashIv = findViewById(R.id.ivFlash)
        mPreviewView = findViewById(R.id.previewView)

        mCopyBtn.setOnClickListener {
            copyText(mResultTv.text.toString())
        }
        mFlashIv.setOnClickListener {
            mFlashLightOn = !mFlashLightOn
            mCamera?.cameraControl?.enableTorch(mFlashLightOn)
            mFlashIv.isSelected = mFlashLightOn
        }

        mCameraExecutor = Executors.newSingleThreadExecutor()

        mPreviewView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(p0: View?) {
                checkCameraAndStoragePermission()
            }

            override fun onViewDetachedFromWindow(p0: View?) {

            }
        })
    }

    private fun checkCameraAndStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            mRequestPermission.launch(
                arrayOf(
                    Manifest.permission.CAMERA
                )
            )
            return
        }
        setupCamera(mPreviewView)
    }

    private fun setupCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this@MainActivity)
        cameraProviderFuture.addListener({
            try {
                mCameraProvider = cameraProviderFuture.get()
                mBackCamera = hasBackCamera()
                bindPreview(mCameraProvider!!, previewView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this@MainActivity))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider, previewView: PreviewView) {
        if (!mBackCamera) {
            if (!hasFrontCamera()) {
                showToast(getString(R.string.camera_not_support))
                return
            }
        }
        mPreView = Preview.Builder().build()
        mImageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setTargetResolution(Size(360, 540))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(mCameraExecutor, mImageAnalyzer)
            }
        val cameraSelector =
            if (mBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        cameraProvider.unbindAll()
        mCamera = cameraProvider.bindToLifecycle(this, cameraSelector, mPreView, mImageAnalysis)
        mPreView!!.setSurfaceProvider(previewView.surfaceProvider)
        mScanIv.visibility = View.VISIBLE
    }

    private fun hasBackCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private val mAnalyzerCallback = object : ImageAnalyzerCallback {
        override fun analyzerResult(result: String) {
            showAnalyzerResult(result)
            stopAnalysis()
        }
    }

    private fun showAnalyzerResult(result: String) {
        mResultLayout.visibility = View.VISIBLE
        mResultTv.text = result
    }

    private val mImageAnalyzer = BarCodeImageAnalyzer(mAnalyzerCallback)

    private fun stopAnalysis() {
        mImageAnalysis?.clearAnalyzer()
        mCameraProvider?.unbindAll()
        mScanIv.visibility = View.GONE
    }

    private val mRequestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            var hasAllPermission = true
            for (item in result) {
                if (item.key == Manifest.permission.CAMERA) {
                    if (item.value) {
                        setupCamera(mPreviewView)
                    } else {
                        hasAllPermission = false
                    }
                }
            }
            if (!hasAllPermission) {
                showToast(getString(R.string.not_allowed_camera_permission))
            }
        }

    private fun showToast(content: String) {
        Toast.makeText(this@MainActivity, content, Toast.LENGTH_SHORT).show()
    }

    /**
     * 复制文本
     */
    private fun copyText(content: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val mClipData = ClipData.newPlainText("Label", content)
            cm.setPrimaryClip(mClipData)
            showToast(getString(R.string.copy_success))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBackPressed() {
        if (mResultLayout.visibility == View.VISIBLE) {
            mResultLayout.visibility = View.GONE
            setupCamera(mPreviewView)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCameraExecutor.shutdown()
    }

    interface ImageAnalyzerCallback {
        fun analyzerResult(result: String)
    }

    private class BarCodeImageAnalyzer(callback: ImageAnalyzerCallback? = null) :
        ImageAnalysis.Analyzer {

        private val mCallback: ImageAnalyzerCallback? = callback

        private val mScanning = BarcodeScanning.getClient()

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            if (mCallback == null) {
                imageProxy.close()
                return
            }
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                mScanning.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val result = barcode.rawValue
                            if (result != null) {
                                mCallback.analyzerResult(result)
                                break
                            }
                        }
                    }
                    .addOnCanceledListener {
                        imageProxy.close()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

}