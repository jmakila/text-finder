package fi.laptech.codefinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fi.laptech.codefinder.ui.theme.IDFinderTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Data class to hold the normalized corner points of a text element
data class TextCorners(val points: List<PointF>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IDFinderTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "input",
        modifier = modifier
    ) {
        composable("input") {
            InputScreen(
                onNavigateToCamera = { text ->
                    navController.navigate("camera/$text")
                }
            )
        }
        composable("camera/{searchText}") { backStackEntry ->
            val searchText = backStackEntry.arguments?.getString("searchText") ?: ""
            CameraScreen(
                searchText = searchText,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun InputScreen(
    onNavigateToCamera: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.enter_text_to_find)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (text.isNotBlank()) {
                        onNavigateToCamera(text)
                    }
                }
            )
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                if (text.isNotBlank()) {
                    onNavigateToCamera(text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.search_button))
        }
    }
}

@Composable
fun CameraScreen(
    searchText: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // Keep screen on
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Handle back button
    BackHandler(onBack = onNavigateBack)

    // Check camera permission
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    var permissionGranted by remember { mutableStateOf(hasPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionGranted) {
            CameraPreview(
                searchText = searchText,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionRequest(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }

        // Overlay showing the search text
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ) {
            Text(
                text = stringResource(R.string.searching_for, searchText),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraPreview(
    searchText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var detectedTextCorners by remember { mutableStateOf<List<TextCorners>>(emptyList()) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Store a reference to the PreviewView
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Store the actual dimensions and position of the PreviewView
    var previewBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                // Store the reference to the PreviewView
                previewView = view

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview use case
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    // Image analysis use case
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, TextAnalyzer(searchText) { corners ->
                                detectedTextCorners = corners
                            })
                        }

                    try {
                        // Unbind all use cases before rebinding
                        cameraProvider.unbindAll()

                        // Bind use cases to camera
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )

                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                view
            },
            modifier = Modifier.fillMaxSize(),
            // Update the bounds of the PreviewView after layout
            update = { view ->
                // Get the actual dimensions and position of the PreviewView
                view.post {
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    val x = location[0].toFloat()
                    val y = location[1].toFloat()
                    val width = view.width.toFloat()
                    val height = view.height.toFloat()

                    // Update the bounds
                    previewBounds = androidx.compose.ui.geometry.Rect(x, y, x + width, y + height)

                    // Update the preview dimensions in the TextAnalyzer companion object
                    TextAnalyzer.previewWidth = width
                    TextAnalyzer.previewHeight = height

                    // Get the display density from the context
                    TextAnalyzer.displayDensity = context.resources.displayMetrics.density

                }
            }
        )

        // Overlay for detected text boxes - positioned to match the PreviewView exactly
        previewBounds?.let { bounds ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                detectedTextCorners.forEach { textCorners ->
                    // Add padding to make the box larger than the text
                    val padding = 0.01f // 1% of the screen size as padding

                    // Convert normalized corner points to canvas coordinates with padding
                    val canvasPoints = textCorners.points.map { point ->
                        // Calculate the center of the text
                        val centerX = textCorners.points.map { it.x }.average().toFloat()
                        val centerY = textCorners.points.map { it.y }.average().toFloat()

                        // Calculate vector from center to point
                        val vectorX = point.x - centerX
                        val vectorY = point.y - centerY

                        // Extend the point outward by padding amount
                        val extendedX = centerX + vectorX * (1 + padding)
                        val extendedY = centerY + vectorY * (1 + padding)

                        // Convert to canvas coordinates - use the actual PreviewView dimensions
                        Offset(
                            extendedX * size.width,
                            extendedY * size.height
                        )
                    }

                    // Add the first point again to close the shape
                    val drawPoints = canvasPoints + canvasPoints.first()

                    // Draw the polygon
                    for (i in 0 until drawPoints.size - 1) {
                        drawLine(
                            color = Color.Green,
                            start = drawPoints[i],
                            end = drawPoints[i + 1],
                            strokeWidth = 4f
                        )
                    }
                }
            }
        }
    }
}

class TextAnalyzer(
    private val searchText: String,
    private val onTextDetected: (List<TextCorners>) -> Unit
) : ImageAnalysis.Analyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        // Store both width and height of the preview view
        var previewWidth: Float = 0f
        var previewHeight: Float = 0f

        // Store the image analysis dimensions
        var imageAnalysisWidth: Float = 0f
        var imageAnalysisHeight: Float = 0f

        // Store the display metrics for more accurate scaling
        var displayDensity: Float = 1f
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    processTextRecognitionResult(visionText, imageProxy.width, imageProxy.height)
                }
                .addOnFailureListener { e ->
                    Log.e("TextAnalyzer", "Text recognition failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processTextRecognitionResult(text: Text, width: Int, height: Int) {
        // Store the image analysis dimensions in the companion object
        imageAnalysisWidth = width.toFloat()
        imageAnalysisHeight = height.toFloat()

        val detectedCorners = mutableListOf<TextCorners>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    // Check if the element contains the search text (case insensitive)
                    if (element.text.contains(searchText, ignoreCase = true)) {
                        // Get the corner points
                        val cornerPoints = element.cornerPoints
                        if (cornerPoints != null && cornerPoints.size == 4) {
                            // Transform the coordinates to match the preview view
                            val transformedPoints = cornerPoints.map { point ->
                                // Handle the camera sensor orientation by swapping x and y
                                // Also invert the x-coordinate to fix horizontal flipping
                                val cameraX = point.y.toFloat()
                                val cameraY = point.x.toFloat()

                                // Normalize to 0-1 range in the camera coordinate space
                                val normalizedX = 1f - (cameraX / height)
                                val normalizedY = cameraY / width

                                // Calculate the aspect ratio of the preview screen (width/height)
                                val previewAspectRatio = if (previewHeight > 0) previewWidth / previewHeight else 0f

                                // Apply scaling based on aspect ratio
                                // For narrower screens (aspect ratio < 0.75), scale horizontal position
                                // For wider screens (aspect ratio > 0.75), scale vertical position
                                // Calculate scaling factors
                                val horizontalScalingFactor = if (previewAspectRatio < 0.75f) {
                                    val factor = (1f / previewAspectRatio) * 0.75f
                                    factor
                                } else {
                                    1.0f
                                }

                                val verticalScalingFactor = if (previewAspectRatio > 0.75f) {
                                    val factor = previewAspectRatio / 0.75f
                                    factor
                                } else {
                                    1.0f
                                }

                                // Apply scaling from center point (0.5)
                                val scaledX = 0.5f + (normalizedX - 0.5f) * horizontalScalingFactor
                                val scaledY = 0.5f + (normalizedY - 0.5f) * verticalScalingFactor

                                // Return the normalized and scaled point
                                PointF(scaledX, scaledY)
                            }

                            detectedCorners.add(TextCorners(transformedPoints))
                        }
                    }
                }
            }
        }

        onTextDetected(detectedCorners)
    }
}

@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
fun BackHandler(onBack: () -> Unit) {
    // Handle back button press
    androidx.activity.compose.BackHandler {
        onBack()
    }
}
