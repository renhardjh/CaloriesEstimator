package com.renhard.caloriesestimator.module.camera.view

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.AudioManager
import android.media.MediaActionSound
import android.os.Bundle
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.renhard.caloriesestimator.animation.Animation.animateArrow
import com.renhard.caloriesestimator.databinding.FragmentCameraBinding
import com.renhard.caloriesestimator.module.camera.model.BoundingBox
import com.renhard.caloriesestimator.module.camera.viewmodel.CameraViewModel
import com.renhard.caloriesestimator.module.main.adapter.MainAdapter
import com.renhard.caloriesestimator.module.main.adapter.MainAdapterCallback
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel
import com.renhard.caloriesestimator.util.Constants.LABELS_PATH
import com.renhard.caloriesestimator.util.Constants.MODEL_PATH
import com.renhard.caloriesestimator.util.Detector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.schedule


class CameraFragment : Fragment(), Detector.DetectorListener, OnChartValueSelectedListener,
    MainAdapterCallback {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val isFrontCamera = false
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mainAdapter: MainAdapter
    var viewModel = CameraViewModel()
    private lateinit var pieChart: PieChart
    private var isResultState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(requireContext(), MODEL_PATH, LABELS_PATH, this) {
                toast(it)
            }
        }

        mainAdapter = MainAdapter(this)
        val layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = mainAdapter

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setBottomSheet()
        setupChart()

        bindListeners()
    }

    private fun bindListeners() {
        binding.apply {
            cbGPU.setOnCheckedChangeListener { _, isChecked ->
                cameraExecutor.submit {
                    detector?.restart(isGpu = isChecked)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector?.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }
    }

    private fun reloadData() {
        lifecycleScope.launch(Dispatchers.Main) {
            val pagingData = PagingData.from(viewModel.predictList)
            mainAdapter.submitData(pagingData)
            mainAdapter.notifyDataSetChanged()
        }
        setChartData()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            if(!isResultState) {
                startCamera()
            }
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
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

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        requireActivity().runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }

        viewModel.predictList = boundingBoxes
            .map { CaloriePredictModel(it.clsName, it.calorie, 1f) }
            .groupBy { it.foodName }
            .values
            .map {
                it.reduce { acc, item ->
                    CaloriePredictModel(acc.foodName, acc.calorie, acc.weight + item.weight)
                }
            }
        reloadData()
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

        binding.btnCapture.setOnClickListener {
            reloadData()
            binding.btnCapture.visibility = View.GONE
            binding.ivReCapture.visibility = View.VISIBLE
            cameraProvider?.unbindAll()
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
            when (audio?.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    val sound = MediaActionSound()
                    sound.play(MediaActionSound.SHUTTER_CLICK)
                }

                AudioManager.RINGER_MODE_SILENT -> {}
                AudioManager.RINGER_MODE_VIBRATE -> {}
            }
            Timer().schedule(2000){
                activity?.runOnUiThread{
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    bottomSheetBehavior.isDraggable = true
                    pieChart.spin(1500, 0f, 180f, Easing.EaseInOutQuad)
                }
            }
            isResultState = true
        }

        binding.ivReCapture.setOnClickListener {
            if (allPermissionsGranted()){
                startCamera()
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
}