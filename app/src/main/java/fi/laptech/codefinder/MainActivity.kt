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

    // Store raw detected corners
    var rawDetectedCorners by remember { mutableStateOf<List<TextCorners>>(emptyList()) }

    // Derive the state to reduce unnecessary recompositions
    val detectedTextCorners by remember { 
        derivedStateOf { 
            // Only trigger recomposition if the number of corners or their positions change significantly
            rawDetectedCorners.takeIf { it.isNotEmpty() } ?: emptyList()
        } 
    }

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
                        // Set target resolution to 1080p for better performance
                        .setTargetResolution(android.util.Size(1080, 1920))
                        // Set target frame rate to 15 fps for better performance
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, TextAnalyzer(searchText) { corners ->
                                rawDetectedCorners = corners
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
            // Use remember with key to avoid recalculating on every recomposition
            val optimizedCorners = remember(detectedTextCorners) {
                detectedTextCorners.map { textCorners ->
                    // Add padding to make the box larger than the text
                    val padding = 0.01f // 1% of the screen size as padding

                    // Calculate the center of the text once
                    val centerX = textCorners.points.map { it.x }.average().toFloat()
                    val centerY = textCorners.points.map { it.y }.average().toFloat()

                    // Process all points at once
                    val processedPoints = textCorners.points.map { point ->
                        // Calculate vector from center to point
                        val vectorX = point.x - centerX
                        val vectorY = point.y - centerY

                        // Extend the point outward by padding amount
                        val extendedX = centerX + vectorX * (1 + padding)
                        val extendedY = centerY + vectorY * (1 + padding)

                        // Store the processed point
                        PointF(extendedX, extendedY)
                    }

                    // Return the processed corners
                    TextCorners(processedPoints)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Use a more efficient drawing approach
                optimizedCorners.forEach { textCorners ->
                    // Create a path for more efficient drawing
                    val path = androidx.compose.ui.graphics.Path()

                    // Convert normalized corner points to canvas coordinates
                    val points = textCorners.points.map { point ->
                        Offset(
                            point.x * size.width,
                            point.y * size.height
                        )
                    }

                    // If we have points, start the path
                    if (points.isNotEmpty()) {
                        // Move to the first point
                        path.moveTo(points.first().x, points.first().y)

                        // Add lines to each subsequent point
                        points.drop(1).forEach { point ->
                            path.lineTo(point.x, point.y)
                        }

                        // Close the path
                        path.close()

                        // Draw the path as a stroke
                        drawPath(
                            path = path,
                            color = Color.Green,
                            style = Stroke(width = 4f)
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

    // Throttling mechanism
    private var lastAnalysisTimestamp = 0L
    private val analysisCooldownMs = 150L // Analyze at most every 150ms (approx. 6-7 fps)

    // Caching mechanism
    private var lastProcessedCorners: List<TextCorners> = emptyList()
    private var frameSkipCounter = 0
    private val MAX_FRAME_SKIP = 10 // Process at least every 10th frame even if no changes

    companion object {
        // Store both width and height of the preview view
        var previewWidth: Float = 0f
        var previewHeight: Float = 0f

        // Store the image analysis dimensions
        var imageAnalysisWidth: Float = 0f
        var imageAnalysisHeight: Float = 0f

        // Store the display metrics for more accurate scaling
        var displayDensity: Float = 1f

        // Maximum number of matches to find before early termination
        const val MAX_MATCHES = 5
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Apply throttling: skip processing if not enough time has passed since last analysis
        if (currentTimestamp - lastAnalysisTimestamp < analysisCooldownMs) {
            // Return the cached results if we have them
            if (lastProcessedCorners.isNotEmpty()) {
                onTextDetected(lastProcessedCorners)
            }
            imageProxy.close()
            return
        }

        // Apply frame skipping: process at least every MAX_FRAME_SKIP frames
        if (lastProcessedCorners.isNotEmpty() && frameSkipCounter < MAX_FRAME_SKIP) {
            frameSkipCounter++
            onTextDetected(lastProcessedCorners)
            imageProxy.close()
            return
        }

        // Reset counter and update timestamp when we actually process a frame
        frameSkipCounter = 0
        lastAnalysisTimestamp = currentTimestamp

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

        // Early termination flag
        var shouldTerminateEarly = false

        blockLoop@ for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    // Check if the element contains the search text (case insensitive)
                    if (element.text.contains(searchText, ignoreCase = true)) {
                        // Get the corner points
                        val cornerPoints = element.cornerPoints
                        if (cornerPoints != null && cornerPoints.size == 4) {
                            // Pre-calculate values that are used multiple times
                            val previewAspectRatio = if (previewHeight > 0) previewWidth / previewHeight else 0f

                            // Calculate scaling factors once
                            // Using 0.5625f (9:16 aspect ratio) as the reference value for 1080x1920 resolution
                            // For square-ish screens, use a more moderate scaling approach

                            // Reference aspect ratio (9:16)
                            val referenceAspectRatio = 0.5625f

                            // Calculate how far the current aspect ratio is from the reference
                            val aspectRatioDifference = if (previewAspectRatio < referenceAspectRatio) {
                                referenceAspectRatio / previewAspectRatio - 1f
                            } else {
                                previewAspectRatio / referenceAspectRatio - 1f
                            }

                            // Apply a dampening factor for square-ish screens (aspect ratio close to 1:1)
                            // The closer to 1:1, the more we dampen the scaling
                            val squarenessFactor = kotlin.math.max(0f, 1f - kotlin.math.abs(previewAspectRatio - 1f))
                            val dampeningFactor = 1f - (squarenessFactor * 0.5f) // Reduce scaling by up to 50% for perfect squares

                            // Log the factors for debugging
                            Log.d("TextAnalyzer", "Preview aspect ratio: $previewAspectRatio")
                            Log.d("TextAnalyzer", "Squareness factor: $squarenessFactor, Dampening factor: $dampeningFactor")

                            // Calculate the final scaling factors with dampening applied
                            val horizontalScalingFactor = if (previewAspectRatio < referenceAspectRatio) {
                                1f + (aspectRatioDifference * dampeningFactor)
                            } else {
                                1.0f
                            }

                            val verticalScalingFactor = if (previewAspectRatio > referenceAspectRatio) {
                                1f + (aspectRatioDifference * dampeningFactor)
                            } else {
                                1.0f
                            }

                            // Log the final scaling factors
                            Log.d("TextAnalyzer", "Horizontal scaling factor: $horizontalScalingFactor, Vertical scaling factor: $verticalScalingFactor")

                            // Transform the coordinates to match the preview view
                            val transformedPoints = cornerPoints.map { point ->
                                // Handle the camera sensor orientation by swapping x and y
                                // Also invert the x-coordinate to fix horizontal flipping
                                val cameraX = point.y.toFloat()
                                val cameraY = point.x.toFloat()

                                // Normalize to 0-1 range in the camera coordinate space
                                val normalizedX = 1f - (cameraX / height)
                                val normalizedY = cameraY / width

                                // Apply scaling from center point (0.5)
                                val scaledX = 0.5f + (normalizedX - 0.5f) * horizontalScalingFactor
                                val scaledY = 0.5f + (normalizedY - 0.5f) * verticalScalingFactor

                                // Log the coordinate transformation for debugging
                                Log.d("TextAnalyzer", "Coordinate transformation: ($normalizedX, $normalizedY) -> ($scaledX, $scaledY)")

                                // Return the normalized and scaled point
                                PointF(scaledX, scaledY)
                            }

                            detectedCorners.add(TextCorners(transformedPoints))

                            // Check if we've found enough matches
                            if (detectedCorners.size >= MAX_MATCHES) {
                                shouldTerminateEarly = true
                                break@blockLoop
                            }
                        }
                    }
                }
            }
        }

        // Update the cache with the new results
        lastProcessedCorners = detectedCorners

        // Send the results to the callback
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
