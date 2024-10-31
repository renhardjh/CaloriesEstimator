package com.renhard.caloriesestimator.module.sharedcamera.view

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.SharedCamera
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.renhard.caloriesestimator.animation.Animation.animateArrow
import com.renhard.caloriesestimator.databinding.FragmentSharedCameraBinding
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.camera.model.Success
import com.renhard.caloriesestimator.module.camera.viewmodel.CameraViewModel
import com.renhard.caloriesestimator.module.main.adapter.MainAdapter
import com.renhard.caloriesestimator.module.main.adapter.MainAdapterCallback
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel
import com.renhard.caloriesestimator.module.sharedcamera.helpers.DisplayRotationHelper
import com.renhard.caloriesestimator.module.sharedcamera.helpers.FullScreenHelper
import com.renhard.caloriesestimator.module.sharedcamera.helpers.TapHelper
import com.renhard.caloriesestimator.module.sharedcamera.helpers.TrackingStateHelper
import com.renhard.caloriesestimator.module.sharedcamera.rendering.BackgroundRenderer
import com.renhard.caloriesestimator.module.sharedcamera.rendering.ObjectRenderer
import com.renhard.caloriesestimator.module.sharedcamera.rendering.PlaneRenderer
import com.renhard.caloriesestimator.module.sharedcamera.rendering.PointCloudRenderer
import com.renhard.caloriesestimator.util.Constants.LABELS_PATH
import com.renhard.caloriesestimator.util.Constants.MODEL_PATH
import com.renhard.caloriesestimator.util.DetectorInstanceSegment
import com.renhard.caloriesestimator.util.DrawImages
import com.renhard.caloriesestimator.util.ImageUtils.pxToCm
import com.renhard.caloriesestimator.util.InstanceSegmentation
import com.renhard.caloriesestimator.util.YuvToRgbConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Arrays
import java.util.EnumSet
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt


class SharedCameraFragment : Fragment(), DetectorInstanceSegment.DetectorListener, OnChartValueSelectedListener,
    MainAdapterCallback, SurfaceTexture.OnFrameAvailableListener, GLSurfaceView.Renderer,
    ImageReader.OnImageAvailableListener {
    private var _binding: FragmentSharedCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var surfaceView: GLSurfaceView
    private var sharedSession: Session? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var sharedCamera: SharedCamera? = null
    private lateinit var cameraId: String
    private var shouldUpdateSurfaceTexture = AtomicBoolean(false)
    private var arcoreActive = false
    private var surfaceCreated = false
    private var errorCreatingSession = false
    private lateinit var previewCaptureRequestBuilder: CaptureRequest.Builder
    private var cpuImageReader: ImageReader? = null
    private var cpuImagesProcessed = 0
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var trackingStateHelper: TrackingStateHelper
    private lateinit var tapHelper: TapHelper
    private val backgroundRenderer = BackgroundRenderer()
    private val virtualObject = ObjectRenderer()
    private val virtualObjectShadow = ObjectRenderer()
    private val planeRenderer = PlaneRenderer()
    private val pointCloudRenderer = PointCloudRenderer()
    private val anchorMatrix = FloatArray(16)
    private val DEFAULT_COLOR: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
    private var distanceCm: Float = 0f
    private var focalLength: Float = 0f
    private var magnification: Float = 0f
    private val anchors = ArrayList<BlankAnchor>()
    private var isFinalResult = false

    // Prevent any changes to camera capture session after CameraManager.openCamera() is called, but
    // before camera device becomes active.
    private var captureSessionChangesPossible = true

    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private val safeToExitApp = ConditionVariable()

    private val isFrontCamera = false
    private var detector: DetectorInstanceSegment? = null
    private lateinit var mainAdapter: MainAdapter
    private var viewModel = CameraViewModel()
    private lateinit var pieChart: PieChart
    private var isResultState = false
    private var instanceSegmentation: InstanceSegmentation? = null

    class BlankAnchor(val anchor: Anchor, val color: FloatArray)

    // Camera device state callback.
    private val cameraDeviceCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d(
                TAG,
                "Camera device ID " + cameraDevice.id + " opened."
            )
            this@SharedCameraFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onClosed(cameraDevice: CameraDevice) {
            Log.d(
                TAG,
                "Camera device ID " + cameraDevice.id + " closed."
            )
            this@SharedCameraFragment.cameraDevice = null
            safeToExitApp.open()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.w(
                TAG,
                "Camera device ID " + cameraDevice.id + " disconnected."
            )
            cameraDevice.close()
            this@SharedCameraFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Log.e(
                TAG,
                "Camera device ID " + cameraDevice.id + " error " + error
            )
            cameraDevice.close()
            this@SharedCameraFragment.cameraDevice = null
            // Fatal error. Quit application.
            activity?.finish()
        }
    }

    // Repeating camera capture session state callback.
    var cameraSessionStateCallback: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
        // Called when the camera capture session is first configured after the app
        // is initialized, and again each time the activity is resumed.
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(
                TAG,
                "Camera capture session configured."
            )
            captureSession = session
            setRepeatingCaptureRequest()
        }

        override fun onSurfacePrepared(
            session: CameraCaptureSession, surface: Surface
        ) {
            Log.d(
                TAG,
                "Camera capture surface prepared."
            )
        }

        override fun onReady(session: CameraCaptureSession) {
            Log.d(
                TAG,
                "Camera capture session ready."
            )
        }

        override fun onActive(session: CameraCaptureSession) {
            Log.d(
                TAG,
                "Camera capture session active."
            )
            if (!arcoreActive) {
                resumeARCore()
            }
            synchronized(this@SharedCameraFragment) {
                captureSessionChangesPossible = true
                (this@SharedCameraFragment as Object).notify()
            }
        }

        override fun onCaptureQueueEmpty(session: CameraCaptureSession) {
            Log.w(
                TAG,
                "Camera capture queue empty."
            )
        }

        override fun onClosed(session: CameraCaptureSession) {
            Log.d(
                TAG,
                "Camera capture session closed."
            )
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(
                TAG,
                "Failed to configure camera capture session."
            )
        }
    }

    // Repeating camera capture session capture callback.
    private val cameraCaptureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            shouldUpdateSurfaceTexture.set(true)
        }

        override fun onCaptureBufferLost(
            session: CameraCaptureSession,
            request: CaptureRequest,
            target: Surface,
            frameNumber: Long
        ) {
            Log.e(
                TAG,
                "onCaptureBufferLost: $frameNumber"
            )
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            Log.e(
                TAG,
                "onCaptureFailed: " + failure.frameNumber + " " + failure.reason
            )
        }

        override fun onCaptureSequenceAborted(
            session: CameraCaptureSession, sequenceId: Int
        ) {
            Log.e(
                TAG,
                "onCaptureSequenceAborted: $sequenceId $session"
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSharedCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainAdapter = MainAdapter(this)
        val layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = mainAdapter

        // GL surface view that renders camera preview image.
        surfaceView = binding.glsurfaceview
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY


        // Helpers, see hello_ar_java sample to learn more.
        displayRotationHelper = DisplayRotationHelper(context)
        tapHelper = TapHelper(context)
        surfaceView.setOnTouchListener(tapHelper)
        trackingStateHelper = TrackingStateHelper(activity)

        resumeARCore()

        setBottomSheet()
        setupChart()

        instanceSegmentation = InstanceSegmentation(
            context = requireContext(),
            modelPath = MODEL_PATH,
            labelPath = LABELS_PATH
        ) {
            toast(it)
        }

//        if (OpenCVLoader.initLocal()) {
//            Log.i("OpenCV", "OpenCV successfully loaded.")
//            getContourArea()
//        }

        view.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            FullScreenHelper.setFullScreenOnWindowFocusChanged(activity, hasFocus)
        }

        detector = DetectorInstanceSegment(requireContext(), MODEL_PATH, LABELS_PATH ?: "", this) {
            toast(it)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            sharedSession?.setCameraTextureName(backgroundRenderer.textureId)
            sharedCamera?.surfaceTexture?.setOnFrameAvailableListener(this)

            // Create an ARCore compatible capture request using `TEMPLATE_RECORD`.
            previewCaptureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            // Build surfaces list, starting with ARCore provided surfaces.
            val surfaceList = sharedCamera?.arCoreSurfaces

            // Add a CPU image reader surface. On devices that don't support CPU image access, the image
            // may arrive significantly later, or not arrive at all.
            surfaceList?.add(cpuImageReader?.surface)

            // Surface list should now contain three surfaces:
            // 0. sharedCamera.getSurfaceTexture()
            // 1. …
            // 2. cpuImageReader.getSurface()

            // Add ARCore surfaces and CPU image surface targets.
            for (surface in surfaceList!!) {
                previewCaptureRequestBuilder.addTarget(surface)
            }

            // Wrap our callback in a shared camera callback.
            val wrappedCallback =
                sharedCamera?.createARSessionStateCallback(
                    cameraSessionStateCallback,
                    backgroundHandler
                )

            // Create camera capture session for camera preview using ARCore wrapped callback.
            cameraDevice!!.createCaptureSession(surfaceList, wrappedCallback!!, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(
                TAG,
                "CameraAccessException",
                e
            )
        }
    }

    private fun setRepeatingCaptureRequest() {
        try {
            captureSession?.setRepeatingRequest(
                previewCaptureRequestBuilder.build(), cameraCaptureCallback, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(
                TAG,
                "Failed to set repeating request",
                e
            )
        }
    }

    private fun resumeARCore() {
        // Ensure that session is valid before triggering ARCore resume. Handles the case where the user
        // manually uninstalls ARCore while the app is paused and then resumes.
        if (sharedSession == null) {
            return
        }

        if (!arcoreActive) {
            try {
                // To avoid flicker when resuming ARCore mode inform the renderer to not suppress rendering
                // of the frames with zero timestamp.
                backgroundRenderer.suppressTimestampZeroRendering(false)
                // Resume ARCore.
                sharedSession?.resume()
                arcoreActive = true

                // Set capture session callback while in AR mode.
                sharedCamera?.setCaptureCallback(cameraCaptureCallback, backgroundHandler)
            } catch (e: CameraNotAvailableException) {
                Log.e(
                    TAG,
                    "Failed to resume ARCore session",
                    e
                )
                return
            }
        }
    }

    private fun processSuccessResult(original: Bitmap, success: Success) {
        viewModel.updatePredictionList(success.results)
        val drawImages = DrawImages(requireContext())
        val result = drawImages.invoke(
            original = original,
            success = success,
            isSeparateOut = false,
            isMaskOut = false
        )
        val areaSegmentation = result.areaSegmentation.groupBy { it }
            .values
            .map {
                it.reduce { acc, item ->
                    acc + item
                }
            }
        areaSegmentation.forEachIndexed { index, it ->
            val height = CaloriesTableModel().getHeightByClass(viewModel.predictList[index].foodName)
            val realAreaSize = it * distanceCm / focalLength
            val totalVolume = realAreaSize * 2 * height
            viewModel.predictList[index].weight = totalVolume
            Log.d("WeightX ${viewModel.predictList[index].foodName}:", "${totalVolume} cm3, ${height}, ${realAreaSize}")
        }

        requireActivity().runOnUiThread {
            binding.ivImage.visibility = View.VISIBLE
            binding.ivImage.setImageBitmap(result.bitmapSegmentation.firstOrNull()?.second!!)
            reloadData()
        }

    }

    private fun clearOutput(error: String) {
        requireActivity().runOnUiThread {
            binding.ivImage.visibility = View.GONE
            binding.ivImage.setImageBitmap(null)
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun reloadData() {
        lifecycleScope.launch(Dispatchers.Main) {
            val pagingData = PagingData.from(viewModel.predictList)
            mainAdapter.submitData(pagingData)
            mainAdapter.notifyDataSetChanged()
        }
        viewModel.predictList.forEach {
            Log.d("reloadDataX:", "${it.weight}")
        }
        setChartData()
    }

    override fun onDestroy() {
        if (sharedSession != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            sharedSession?.close()
            sharedSession = null
        }
        super.onDestroy()
        detector?.close()
    }

    @Synchronized
    private fun waitUntilCameraCaptureSessionIsActive() {
        while (!captureSessionChangesPossible) {
            try {
                (this as Object).wait()
            } catch (e: InterruptedException) {
                Log.e(
                    TAG,
                    "Unable to wait for a safe time to make changes to the capture session",
                    e
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCamera()
    }

    override fun onPause() {
        pauseCamera()
        super.onPause()
    }

    private fun pauseARCore() {
        if (arcoreActive) {
            // Pause ARCore.
            sharedSession?.pause()
            arcoreActive = false
        }
    }

    private fun pauseCamera() {
        shouldUpdateSurfaceTexture.set(false)
        surfaceView.onPause()
        waitUntilCameraCaptureSessionIsActive()
        displayRotationHelper.onPause()
        pauseARCore()
        closeCamera()
        stopBackgroundThread()
    }

    private fun resumeCamera() {
        waitUntilCameraCaptureSessionIsActive()
        startBackgroundThread()
        surfaceView.onResume()


        // When the activity starts and resumes for the first time, openCamera() will be called
        // from onSurfaceCreated(). In subsequent resumes we call openCamera() here.
        if (surfaceCreated) {
            try {
                if (allPermissionsGranted()){
                    if(!isResultState) {
                        openCamera()
                    }
                } else {
                    requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
                }
            } catch (e: Exception) {

            }
        }

        displayRotationHelper.onResume()
    }

    private fun closeCamera() {
        if (captureSession != null) {
            captureSession?.close()
            captureSession = null
        }
        if (cameraDevice != null) {
            waitUntilCameraCaptureSessionIsActive()
            safeToExitApp.close()
            cameraDevice!!.close()
            safeToExitApp.block()
        }
        if (cpuImageReader != null) {
            cpuImageReader?.close()
            cpuImageReader = null
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("sharedCameraBackground")
        backgroundThread?.start()
        backgroundThread?.looper?.let {
            backgroundHandler = Handler(it)
        }
    }

    private fun stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread?.quitSafely()
            try {
                backgroundThread?.join()
                backgroundThread = null
                backgroundHandler = null
            } catch (e: InterruptedException) {
                Log.e(
                    TAG,
                    "Interrupted while trying to join background handler thread",
                    e
                )
            }
        }
    }

    companion object {
        private const val TAG = "CameraXYZ"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        requireActivity().runOnUiThread {
            binding.overlay.clear()
        }
    }

    override fun onDetect(segmentResult: List<SegmentationResult>, inferenceTime: Long) {
        requireActivity().runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(segmentResult)
                invalidate()
            }
        }

        if(!isResultState) {
            viewModel.updatePredictionList(segmentResult)
            reloadData()
        }
    }

    private fun toast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.llSheet)
        bottomSheetBehavior.isDraggable = false

        val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
        bottomSheetBehavior.peekHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 90f, metrics).toInt()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        animateArrow(binding.ivArrow, 180f, 0f)
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        animateArrow(binding.ivArrow, 0f, 180f)
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.llSheet.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

//        binding.btnCapture.setOnClickListener {
//            binding.ivImage.visibility = if(binding.ivImage.visibility == View.VISIBLE) View.GONE else View.VISIBLE
//        }
//        binding.btnCapture.setOnClickListener {
//            didCaptureResult()
//        }

        binding.ivReCapture.setOnClickListener {
            if (allPermissionsGranted()){
                resumeCamera()
                binding.ivImage.visibility = View.GONE
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            binding.ivReCapture.visibility = View.GONE
            binding.btnCapture.visibility = View.VISIBLE
            bottomSheetBehavior.isDraggable = false
            isResultState = false
        }
    }

    private fun setupChart() {
        pieChart = binding.pieChart
        pieChart.setUsePercentValues(true)
        pieChart.getDescription().setEnabled(false)
        pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
        pieChart.setDragDecelerationFrictionCoef(0.95f)

//        pieChart.setCenterTextTypeface(tfLight)
        pieChart.setCenterTextColor(Color.BLACK)
        pieChart.setCenterTextSize(14f)
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD)

        pieChart.setDrawHoleEnabled(true)
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setTransparentCircleColor(Color.WHITE)
        pieChart.setTransparentCircleAlpha(110)

        pieChart.setHoleRadius(58f)
        pieChart.setTransparentCircleRadius(61f)
        pieChart.setDrawCenterText(true)
        pieChart.setRotationAngle(0f)

        // enable rotation of the chart by touch
        pieChart.setRotationEnabled(true)
        pieChart.setHighlightPerTapEnabled(true)

        // add a selection listener
        pieChart.setOnChartValueSelectedListener(this)

        // pieChart.spin(2000, 0f, 360f, Easing.EaseInOutQuad)

        // entry label styling
        pieChart.setEntryLabelColor(Color.DKGRAY)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.legend.isEnabled = false
    }

    private fun setChartData() {
        val totalCalorie = String.format("%.1f", viewModel.predictList.sumOf { it.calorie.toDouble() * it.weight.toDouble() })
        pieChart.setCenterText("$totalCalorie kkal\nTotal Kalori")
        val entries = ArrayList<PieEntry>()

        viewModel.predictList.forEach {
            entries.add(
                PieEntry(
                    it.calorie.toFloat() * it.weight,
                    it.foodName
                )
            )
        }
        val dataSet = PieDataSet(entries, "Total Kalori")
        dataSet.setDrawIcons(false)

        dataSet.sliceSpace = 3f
        dataSet.iconsOffset = MPPointF(0f, 40f)
        dataSet.selectionShift = 5f


        // add a lot of colors
        val colors = ArrayList<Int>()

        for (c in ColorTemplate.VORDIPLOM_COLORS) colors.add(c)

        for (c in ColorTemplate.JOYFUL_COLORS) colors.add(c)

        for (c in ColorTemplate.COLORFUL_COLORS) colors.add(c)

        for (c in ColorTemplate.LIBERTY_COLORS) colors.add(c)

        for (c in ColorTemplate.PASTEL_COLORS) colors.add(c)

        colors.add(ColorTemplate.getHoloBlue())

        dataSet.colors = colors


        //dataSet.setSelectionShift(0f);
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.DKGRAY)
//        data.setValueTypeface(tfLight)
        pieChart.setData(data)

        pieChart.highlightValues(null)
        val h = arrayOfNulls<Highlight>(1)
        val highgestId = viewModel.predictList.indices.maxBy { (viewModel.predictList.get(it).calorie * viewModel.predictList.get(it).weight) }
        h[0] = Highlight(highgestId.toFloat(), 0, 0)
        pieChart.highlightValue(h[0])
        pieChart.invalidate()
    }

    private fun openCamera() {
        // Don't open camera if already opened.
        if (cameraDevice != null) {
            return
        }

        // Make sure that ARCore is installed, up to date, and supported on this device.
        if (!isARCoreSupportedAndUpToDate()) {
            return
        }

        if (sharedSession == null) {
            try {
                // Create ARCore session that supports camera sharing.
                sharedSession = Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA))
            } catch (e: Exception) {
                errorCreatingSession = true
                toast("Failed to create ARCore session that supports camera sharing")
                Log.e(
                    TAG,
                    "Failed to create ARCore session that supports camera sharing",
                    e
                )
                return
            }

            errorCreatingSession = false

            // Enable auto focus mode while ARCore is running.
            val config = sharedSession?.config
            config?.setFocusMode(Config.FocusMode.AUTO)
            sharedSession?.configure(config)
        }

        // Store the ARCore shared camera reference.
        sharedCamera = sharedSession?.sharedCamera

        // Store the ID of the camera used by ARCore.
        cameraId = sharedSession?.cameraConfig?.cameraId ?: ""

        // Use the currently configured CPU image size.
        val desiredCpuImageSize = sharedSession?.cameraConfig?.imageSize
        desiredCpuImageSize?.let {
            cpuImageReader =
                ImageReader.newInstance(
                    it.width,
                    it.height,
                    ImageFormat.YUV_420_888,
                    2
                )
        }
        cpuImageReader?.setOnImageAvailableListener(this, backgroundHandler)

        // When ARCore is running, make sure it also updates our CPU image surface.
        sharedCamera?.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReader?.surface))

        try {
            // Wrap our callback in a shared camera callback.

            val wrappedCallback =
                sharedCamera?.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler)

            // Store a reference to the camera system service.
            cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Get the characteristics for the ARCore camera.
            val characteristics = cameraManager.getCameraCharacteristics(this.cameraId)

            // Prevent app crashes due to quick operations on camera open / close by waiting for the
            // capture session's onActive() callback to be triggered.
            captureSessionChangesPossible = false

            // Open the camera device using the ARCore wrapped callback.
            cameraManager.openCamera(cameraId, wrappedCallback!!, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(
                TAG,
                "Failed to open camera",
                e
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                TAG,
                "Failed to open camera",
                e
            )
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Failed to open camera",
                e
            )
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {

    }

    override fun onNothingSelected() {

    }

    override fun onEditWeight(position: Int) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle("Masukan Berat ${viewModel.predictList[position].foodName} (gram)")

        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)
        builder.setPositiveButton("OK", { dialog, which ->
            val weight = input.text.toString().toFloatOrNull() ?: 1f
            viewModel.predictList[position].weight = weight
            reloadData()
            pieChart.spin(1500, 0f, 180f, Easing.EaseInOutQuad)
        })
        builder.setNegativeButton("Batalkan", { dialog, which -> dialog.cancel() })

        builder.show()
    }

    private fun imageToBitmap(image: Image, newWidth: Int, newHeight: Int): Bitmap {
        val yuvToRgbConverter = YuvToRgbConverter(requireContext())
        var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image, bitmap)

        val matrix = Matrix().apply {
            postRotate(displayRotationHelper.getCameraSensorToDisplayRotation(cameraId).toFloat())
        }
        bitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            matrix, true
        )

        val sourceWidth: Int = bitmap.width
        val sourceHeight: Int = bitmap.height

        val xScale = newWidth.toFloat() / sourceWidth
        val yScale = newHeight.toFloat() / sourceHeight
        val scale = max(xScale.toDouble(), yScale.toDouble()).toFloat()

        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight

        val left: Float = (newWidth - scaledWidth) / 2
        val top: Float = (newHeight - scaledHeight) / 2

        val targetRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        val newBitmap = Bitmap.createBitmap(newWidth, newHeight, bitmap.config)
        val canvas = Canvas(newBitmap)
        canvas.drawBitmap(bitmap, null, targetRect, null)

        return newBitmap
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {

    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        surfaceCreated = true

        // Set GL clear color to black.
        GLES20.glClearColor(0f, 0f, 0f, 1.0f)

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the camera preview image texture. Used in non-AR and AR mode.
            backgroundRenderer.createOnGlThread(context)
            planeRenderer.createOnGlThread(context, "models/trigrid.png")
            pointCloudRenderer.createOnGlThread(context)

            virtualObject.createOnGlThread(context, "models/andy.obj", "models/andy.png")
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f)

            virtualObjectShadow.createOnGlThread(
                context, "models/andy_shadow.obj", "models/andy_shadow.png"
            )
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow)
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f)

            if (allPermissionsGranted()) {
                openCamera()
            } else {
                ActivityCompat.requestPermissions(requireActivity(),
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Failed to read an asset file",
                e
            )
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Use the cGL clear color specified in onSurfaceCreated() to erase the GL surface.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (!shouldUpdateSurfaceTexture.get()) {
            // Not ready to draw.
            return
        }

        // Handle display rotations.
        displayRotationHelper.updateSessionIfNeeded(sharedSession)

        try {
            onDrawFrameARCore()
        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(
                TAG,
                "Exception on the OpenGL thread",
                t
            )
        }
    }

    @Throws(CameraNotAvailableException::class)
    fun onDrawFrameARCore() {
        if (!arcoreActive) {
            // ARCore not yet active, so nothing to draw yet.
            return
        }

        if (errorCreatingSession) {
            // Session not created, so nothing to draw.
            return
        }

        // Perform ARCore per-frame update.
        val frame = sharedSession!!.update()
        val camera = frame.camera

        // Handle screen tap.
        handleTap(frame, camera)

        // If frame is ready, render camera preview image to the GL surface.
        backgroundRenderer.draw(frame)

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }

        // Get projection matrix.
        val projmtx = FloatArray(16)
        camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)

        // Get camera matrix and draw.
        val viewmtx = FloatArray(16)
        camera.getViewMatrix(viewmtx, 0)

        // Compute lighting from average intensity of the image.
        // The first three components are color scaling factors.
        // The last one is the average pixel intensity in gamma space.
        val colorCorrectionRgba = FloatArray(4)
        frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)

        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewmtx, projmtx)
        }

        // Visualize planes.
        planeRenderer.drawPlanes(
            sharedSession?.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projmtx
        )

        // Visualize anchors created by touch.
        val scaleFactor = 1.0f
        for (coloredAnchor in anchors) {
            if (coloredAnchor.anchor.trackingState != TrackingState.TRACKING) {
                continue
            }
            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to sharedSession.update() as ARCore refines its estimate of the world.
            coloredAnchor.anchor.pose.toMatrix(anchorMatrix, 0)

            // Update and draw the model and its shadow.
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor)
            virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color)
            virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color)
        }

//        val focalLength = camera.imageIntrinsics.focalLength[0].toInt()
//        val cameraId = cameraId
//        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
//
//        val maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!
//        val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
//        val w = size?.width ?: 0f
//        val h = size?.height ?: 0f
//
//        val metrics = Resources.getSystem().displayMetrics
//        val fovW = Math.toDegrees(2 * Math.atan(w / (maxFocus[0] * 2.0)))
//        val fovH = Math.toDegrees(2 * Math.atan(h / (maxFocus[0] * 2.0)))
//        val screenHMeasuring = (metrics.heightPixels.toFloat() / metrics.densityDpi.toFloat()) * 2.54f
//        magnification = fovH.toFloat() / screenHMeasuring
//        Log.d("MagnificationX:", "${screenHMeasuring} ${magnification}")

//        val characteristics = cameraManager.getCameraCharacteristics(this.cameraId)
//        val sensorPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
//        val sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
//        focalLength = camera.imageIntrinsics.focalLength[0]
//        Log.d("MagnificationX:", "F: ${focalLength}, P: ${sensorPhysicalSize}, W: ${sensorArraySize}")
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = tapHelper.poll()
        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                val trackable = hit.trackable
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && (PlaneRenderer.calculateDistanceToPlane(
                        hit.hitPose,
                        camera.pose
                    ) > 0))
                    || (trackable is Point
                            && (trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL))
                ) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size >= 20) {
                        anchors[0].anchor.detach()
                        anchors.removeAt(0)
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    val objColor = if (trackable is Point) {
                        floatArrayOf(66.0f, 133.0f, 244.0f, 255.0f)
                    } else if (trackable is Plane) {
                        floatArrayOf(139.0f, 195.0f, 74.0f, 255.0f)
                    } else {
                        DEFAULT_COLOR
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
//                    val anchor =
//                        BlankAnchor(
//                            hit.createAnchor(),
//                            objColor
//                        )
//                    anchors.add(anchor)

                    val startPose = camera.pose
                    val endPose = hit.hitPose

                    // Compute the difference vector between the two hit locations.
                    val dx = startPose.tx() - endPose.tx()
                    val dy = startPose.ty() - endPose.ty()
                    val dz = startPose.tz() - endPose.tz()

                    // Compute the straight-line distance.
                    distanceCm = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat() * 100f
                    // Count focal length in mm
                    val characteristics = cameraManager.getCameraCharacteristics(this.cameraId)
                    val sensorPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    focalLength = camera.imageIntrinsics.focalLength[0] * sensorPhysicalSize!!.width / sensorArraySize!!.width
                    focalLength = focalLength * 10
                    val metrics = Resources.getSystem().displayMetrics
                    val imageW = metrics.widthPixels.pxToCm(requireContext())
                    magnification = distanceCm * imageW / focalLength
                    Log.d("MagnificationX:", "F: ${focalLength}, P: ${imageW}, W: ${magnification}")
                    requireActivity().runOnUiThread {
                        didCaptureResult()
                    }
                }
            }
        }
    }

    private fun isARCoreSupportedAndUpToDate(): Boolean {
        // Make sure ARCore is installed and supported on this device.
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        when (availability) {
            Availability.SUPPORTED_INSTALLED -> {}
            Availability.SUPPORTED_APK_TOO_OLD, Availability.SUPPORTED_NOT_INSTALLED -> try {
                // Request ARCore installation or update if needed.
                val installStatus =
                    ArCoreApk.getInstance().requestInstall(activity,  /*userRequestedInstall=*/true)
                when (installStatus) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        Log.e(
                            TAG,
                            "ARCore installation requested."
                        )
                        return false
                    }

                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
            } catch (e: UnavailableException) {
                Log.e(
                    TAG,
                    "ARCore not installed",
                    e
                )
                requireActivity().runOnUiThread {
                    toast("ARCore not installed\n$e")
                }
                activity?.finish()
                return false
            }

            Availability.UNKNOWN_ERROR, Availability.UNKNOWN_CHECKING, Availability.UNKNOWN_TIMED_OUT, Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                Log.e(
                    TAG,
                    "ARCore is not supported on this device, ArCoreApk.checkAvailability() returned "
                            + availability
                )
                requireActivity().runOnUiThread {
                    toast("ARCore is not supported on this device, "
                            + "ArCoreApk.checkAvailability() returned "
                            + availability)
                }
                return false
            }
        }
        return true
    }

    override fun onImageAvailable(reader: ImageReader?) {
        val image: Image? = reader?.acquireLatestImage()
        if (image == null) {
            Log.w(
                TAG,
                "onImageAvailable: Skipping null image."
            )
            return
        }

        val bitmap = imageToBitmap(image, surfaceView.width, surfaceView.height)
        detector?.detect(bitmap)
        requireActivity().runOnUiThread {
            if(binding.ivImage.visibility == View.GONE) {
                binding.ivImage.setImageBitmap(bitmap)
            }
        }
        image.close()
        cpuImagesProcessed++
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == false) {
            toast("Camera permission is needed to run this application")
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun didCaptureResult() {
        isResultState = true
        requireActivity().runOnUiThread {
            val ivImage = binding.ivImage
            val bitmap = ivImage.drawable.toBitmap()
            detector?.detect(bitmap)
            instanceSegmentation?.invoke(
                frame = bitmap,
                smoothEdges = true,
                onSuccess = { processSuccessResult(bitmap, it) },
                onFailure = { clearOutput(it) }
            )
        }

        binding.btnCapture.visibility = View.GONE
        binding.ivReCapture.visibility = View.VISIBLE
        pauseCamera()
//        val audio = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
//        when (audio?.ringerMode) {
//            AudioManager.RINGER_MODE_NORMAL -> {
//                val sound = MediaActionSound()
//                sound.play(MediaActionSound.SHUTTER_CLICK)
//            }
//
//            AudioManager.RINGER_MODE_SILENT -> {}
//            AudioManager.RINGER_MODE_VIBRATE -> {}
//        }
        Timer().schedule(2000){
            activity?.runOnUiThread{
                val bottomSheetBehavior = BottomSheetBehavior.from(binding.llSheet)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetBehavior.isDraggable = true
                pieChart.spin(1500, 0f, 180f, Easing.EaseInOutQuad)
            }
        }
    }
}