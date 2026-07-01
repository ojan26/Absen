package com.example.ui.screens

import android.Manifest
import android.graphics.ImageFormat
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import com.example.ui.ScanResultState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun InAppScannerDialog(
    type: String,
    mode: String,
    defaultCamera: String,
    scanResult: ScanResultState?,
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
    onClearResult: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Izin kamera diperlukan untuk memindai QR Code.", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val initialLensFacing = remember(defaultCamera) {
        if (defaultCamera == "FRONT") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    if (hasCameraPermission) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("in_app_scanner_dialog"),
                color = Color.Black
            ) {
                ScannerView(
                    type = type,
                    mode = mode,
                    initialLensFacing = initialLensFacing,
                    scanResult = scanResult,
                    onDismiss = onDismiss,
                    onCodeScanned = onCodeScanned,
                    onClearResult = onClearResult
                )
            }
        }
    }
}

@Composable
fun ScannerView(
    type: String,
    mode: String,
    initialLensFacing: Int,
    scanResult: ScanResultState?,
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
    onClearResult: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera Selector State: Default initialized with the saved preference
    var lensFacing by remember { mutableStateOf(initialLensFacing) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isScanningActive by remember { mutableStateOf(true) }

    LaunchedEffect(isScanningActive) {
        if (!isScanningActive) {
            kotlinx.coroutines.delay(3500)
            isScanningActive = true
            onClearResult()
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Rebind camera whenever lensFacing changes
    LaunchedEffect(lensFacing) {
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, QrCodeAnalyzer { qrText ->
            if (isScanningActive) {
                isScanningActive = false
                // Execute on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onCodeScanned(qrText)
                }
            }
        })

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Control flashlight
            if (camera.cameraInfo.hasFlashUnit()) {
                camera.cameraControl.enableTorch(isFlashOn)
            }
        } catch (e: Exception) {
            Log.e("ScannerView", "Use case binding failed", e)
        }
    }

    // Toggle flashlight whenever isFlashOn state changes (if current camera has flash)
    LaunchedEffect(isFlashOn, lensFacing) {
        try {
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            
            // Check if bound to active camera control
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector)
            if (camera.cameraInfo.hasFlashUnit()) {
                camera.cameraControl.enableTorch(isFlashOn)
            }
        } catch (e: Exception) {
            // Ignore if camera is not fully loaded yet
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Translucent background overlay with a transparent hole for viewfinder
        ScannerOverlay()

        // 3. UI Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Group Top Bar and Notification Card together
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Bar with Close and Camera Switch buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Scanner Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Scanner",
                            tint = Color.White
                        )
                    }

                    // Title Label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PEMINDAI QR",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Absen ${type.uppercase()} ($mode)",
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }

                    // Camera Switch (Front / Back Toggle)
                    IconButton(
                        onClick = {
                            isFlashOn = false // Reset flash on switch
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("scanner_switch_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Ganti Kamera",
                            tint = Color.White
                        )
                    }
                }

                // Elegant inline scan result banner
                if (scanResult != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (scanResult.success) Color(0xFF047857) else Color(0xFFB91C1C)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (scanResult.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scanResult.title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = scanResult.message,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // Central text guidance
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        "Kamera Depan Aktif\nDekatkan QR code Anda ke layar"
                    } else {
                        "Arahkan kamera belakang ke QR Code"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Bottom Bar with flash switch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                // Flashlight Toggle (Only show for back camera)
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    IconButton(
                        onClick = { isFlashOn = !isFlashOn },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (isFlashOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .testTag("scanner_flash_button")
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Senter",
                            tint = if (isFlashOn) MaterialTheme.colorScheme.onPrimary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val lineProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Viewfinder Dimensions (Square box in the middle)
        val boxSize = width * 0.70f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val right = left + boxSize
        val bottom = top + boxSize

        val rect = Rect(left, top, right, bottom)
        val roundRect = RoundRect(rect, CornerRadius(24.dp.toPx(), 24.dp.toPx()))

        val path = Path().apply {
            addRoundRect(roundRect)
        }

        // Clip path to draw semi-transparent background around the viewfinder box
        clipPath(path = path, clipOp = ClipOp.Difference) {
            drawRect(
                color = Color.Black.copy(alpha = 0.7f),
                size = size
            )
        }

        // Draw modern neon viewfinder frame corners
        val strokeWidth = 4.dp.toPx()
        val cornerLength = 32.dp.toPx()
        val cornerColor = Color(0xFF10B981) // Neon Green

        // Draw corners manually for modern, polished high-contrast look
        // Top Left
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

        // Top Right
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)

        // Bottom Left
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)

        // Bottom Right
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)

        // Draw elegant thin outline border of the square
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // Draw sliding laser scanner line
        val scanLineY = top + (boxSize * lineProgress)
        drawLine(
            color = Color(0xFF10B981),
            start = Offset(left + 8.dp.toPx(), scanLineY),
            end = Offset(right - 8.dp.toPx(), scanLineY),
            strokeWidth = 3.dp.toPx()
        )

        // Glow effect below the laser line
        val glowHeight = 16.dp.toPx()
        drawRect(
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            topLeft = Offset(left + 8.dp.toPx(), scanLineY - glowHeight / 2),
            size = Size(boxSize - 16.dp.toPx(), glowHeight)
        )
    }
}

class QrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        if (image.format == ImageFormat.YUV_420_888) {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val width = image.width
            val height = image.height

            // PlanarYUVLuminanceSource constructor
            val source = PlanarYUVLuminanceSource(
                data, width, height, 0, 0, width, height, false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(binaryBitmap)
                onQrCodeDetected(result.text)
            } catch (e: Exception) {
                // Fallback attempt for rotated images if needed, or just let the next frame try
            } finally {
                image.close()
            }
        } else {
            image.close()
        }
    }
}
