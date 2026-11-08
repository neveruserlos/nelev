package com.nelev.dev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.nelev.dev.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * QR-сканер (CameraX + ZXing).
 *
 * Все шаги, которые раньше могли уронить приложение (инициализация декодера,
 * выбор разрешения, привязка камеры, обработка кадров), обёрнуты в защиту:
 * при ошибке показывается экран с объяснением и кнопкой «Повторить» вместо
 * краша. Анализ идёт в 1280×720, а не в максимальном разрешении камеры —
 * так меньше памяти и стабильнее распознавание.
 *
 * Декодер ZXing — чистый Java: не зависит от ML Kit, Google Play Services,
 * нативных .so и рефлексии, поэтому одинаково работает и в debug, и в
 * release-сборке после R8.
 */
class BarcodeScannerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QR = "qr_result"
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scanning = AtomicBoolean(false)
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null
    private var previewViewRef: PreviewView? = null
    private var torchEnabled = false

    private val permissionState = mutableStateOf(false)
    private val detectedState = mutableStateOf(false)
    private val torchState = mutableStateOf(false)
    private val exposureState = mutableStateOf(0)
    private val exposureAvailableState = mutableStateOf(false)
    private var exposureMin = 0
    private var effectsEnabled = true
    private var soundClickEnabled = true

    // Переиспользуемый буфер под плоскость Y: на кадр не выделяем новую память.
    private var luminanceBuffer: ByteArray = ByteArray(0)
    private var frameCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Декодер ZXing работает на чистом Java — без ML Kit, нативных
        // библиотек и рефлексии, поэтому не ломается под R8.
        permissionState.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val settings = SettingsStore.load(this)
        val lang = settings.language
        val effects = settings.visualEffectsEnabled
        effectsEnabled = effects
        soundClickEnabled = settings.soundClickEnabled

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ScannerScreen(
                    lang = lang,
                    effectsEnabled = effects,
                    permissionGranted = permissionState.value,
                    detected = detectedState.value,
                    torchOn = torchState.value,
                    exposureEv = exposureState.value,
                    exposureAvailable = exposureAvailableState.value,
                    onClose = { finish() },
                    onToggleTorch = { toggleTorch() },
                    onCycleExposure = { cycleExposure() },
                    onTapFocus = { x, y -> focusAt(x, y) },
                    bindCamera = { preview, onReady, onError ->
                        startCamera(preview, onReady, onError)
                    }
                )
            }
        }

        if (!permissionState.value) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                permissionState.value = true
            } else {
                Toast.makeText(
                    this,
                    if (SettingsStore.load(this).language == "ru")
                        "Без доступа к камере сканер не работает"
                    else "Camera access is required for scanning",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startCamera(
        previewView: PreviewView,
        onReady: (hasFlash: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                previewViewRef = previewView

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    // 1920×1080: заметно больше деталей для мелких QR.
                    // Память не растёт — кадры не копятся (KEEP_ONLY_LATEST)
                    // и буфер под плоскость Y переиспользуется.
                    .setTargetResolution(Size(1920, 1080))
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(executor) { image -> analyzeSafely(image) }
                    }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                boundCamera = camera

                // Диапазон компенсации экспозиции: даём пользователю убрать
                // пересвет на ярком свете.
                runCatching {
                    val exposure = camera.cameraInfo.exposureState
                    exposureMin = exposure.exposureCompensationRange.lower
                    exposureAvailableState.value = exposureMin < 0
                }

                // Стартовая фокусировка по центру кадра (там область сканирования).
                previewView.post {
                    runCatching {
                        val factory = previewView.meteringPointFactory
                        val center = factory.createPoint(
                            previewView.width / 2f,
                            previewView.height / 2f
                        )
                        camera.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                        )
                    }
                }

                val hasFlash = runCatching {
                    camera.cameraInfo.hasFlashUnit()
                }.getOrDefault(false)
                onReady(hasFlash)
            } catch (t: Throwable) {
                onError(t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeSafely(imageProxy: ImageProxy) {
        try {
            if (imageProxy.format != ImageFormat.YUV_420_888) return
            val width = imageProxy.width
            val height = imageProxy.height
            if (width <= 0 || height <= 0) return
            val luminance = yuvLuminance(imageProxy) ?: return

            // Сначала центральная область (совпадает с рамкой на экране) —
            // это быстрее и точнее, чем искать QR по всему кадру.
            // Полный кадр проверяем каждый третий кадр, чтобы не грузить CPU.
            frameCounter++
            val raw = decodeArea(luminance, width, height)
                ?: if (frameCounter % 3 == 0) decodeAll(luminance, width, height) else null
            if (raw != null && scanning.compareAndSet(false, true)) {
                runOnUiThread { handleFound(raw) }
            }
        } catch (_: Throwable) {
            // битый кадр — просто пропускаем
        } finally {
            runCatching { imageProxy.close() }
        }
    }

    /** Берём только яркостную плоскость Y — этого достаточно ZXing. */
    private fun yuvLuminance(imageProxy: ImageProxy): ByteArray? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null

        val data = if (luminanceBuffer.size >= width * height) {
            luminanceBuffer
        } else {
            ByteArray(width * height).also { luminanceBuffer = it }
        }

        if (pixelStride == 1 && rowStride == width) {
            val len = min(buffer.remaining(), data.size)
            buffer.get(data, 0, len)
            return data
        }
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            val toRead = min(rowStride, buffer.remaining())
            if (toRead < width) break
            buffer.get(row, 0, toRead)
            var src = 0
            var dst = y * width
            for (x in 0 until width) {
                data[dst++] = row[src]
                src += pixelStride
            }
        }
        return data
    }

    /** Центральный квадрат кадра — примерно то, что видит пользователь в рамке. */
    private fun decodeArea(data: ByteArray, width: Int, height: Int): String? {
        val side = (min(width, height) * 0.82f).toInt().coerceAtLeast(1)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return tryDecode(PlanarYUVLuminanceSource(data, width, height, left, top, side, side, false))
    }

    private fun decodeAll(data: ByteArray, width: Int, height: Int): String? =
        tryDecode(PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false))

    /**
     * ZXing по очереди пробует:
     *  - HybridBinarizer (неравномерный свет, тени);
     *  - GlobalHistogramBinarizer (пересвет, низкий контраст);
     *  - те же два на инвертированном кадре (белый QR на тёмном фоне).
     */
    private fun tryDecode(source: LuminanceSource): String? {
        val variants = runCatching { listOf(source, source.invert()) }.getOrDefault(listOf(source))
        for (variant in variants) {
            for (binarizer in listOf(
                runCatching { HybridBinarizer(variant) }.getOrNull(),
                runCatching { GlobalHistogramBinarizer(variant) }.getOrNull()
            )) {
                if (binarizer == null) continue
                val result = try {
                    reader.decodeWithState(BinaryBitmap(binarizer))
                } catch (_: Throwable) {
                    null
                } finally {
                    reader.reset()
                }
                if (result != null) return result.text
            }
        }
        return null
    }

    private fun handleFound(raw: String) {
        if (isFinishing) return
        detectedState.value = true
        if (soundClickEnabled) runCatching { ClickPlayer.play(this) }
        lifecycleScope.launch {
            delay(if (effectsEnabled) 160L else 0L)
            deliver(raw)
        }
    }

    private fun deliver(raw: String) {
        if (isFinishing) return
        setResult(RESULT_OK, Intent().putExtra(EXTRA_QR, raw))
        finish()
    }

    private fun toggleTorch() {
        val camera = boundCamera ?: return
        val hasFlash = runCatching { camera.cameraInfo.hasFlashUnit() }.getOrDefault(false)
        if (!hasFlash) return
        torchEnabled = !torchEnabled
        torchState.value = torchEnabled
        runCatching { camera.cameraControl.enableTorch(torchEnabled) }
    }

    /** Фокусировка по тапу: точка в координатах PreviewView. */
    private fun focusAt(x: Float, y: Float) {
        val camera = boundCamera ?: return
        val view = previewViewRef ?: return
        runCatching {
            val factory = view.meteringPointFactory
            val point = factory.createPoint(x, y)
            camera.cameraControl.startFocusAndMetering(
                FocusMeteringAction
                    .Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
            )
        }
    }

    /**
     * Компенсация экспозиции: 0 → −1 → −2 → −3 → 0.
     * Нужна, когда на ярком свете камера «выжигает» QR-код в белое.
     */
    private fun cycleExposure() {
        val camera = boundCamera ?: return
        if (exposureMin >= 0) return
        val current = exposureState.value
        val next = if (current > exposureMin && current > -3) current - 1 else 0
        runCatching { camera.cameraControl.setExposureCompensationIndex(next) }
        exposureState.value = next
    }

    override fun onDestroy() {
        super.onDestroy()
        scanning.set(true)
        runCatching { cameraProvider?.unbindAll() }
        runCatching { executor.shutdown() }
    }
}

private val PINK_START = Color(0xFFFF9BC6)
private val PINK_END = Color(0xFFFF69B4)

private val PINK = Color(0xFFE79BC0)

@Composable
private fun ScannerScreen(
    lang: String,
    effectsEnabled: Boolean,
    permissionGranted: Boolean,
    detected: Boolean,
    torchOn: Boolean,
    exposureEv: Int,
    exposureAvailable: Boolean,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
    onCycleExposure: () -> Unit,
    onTapFocus: (Float, Float) -> Unit,
    bindCamera: (PreviewView, (Boolean) -> Unit, (String) -> Unit) -> Unit
) {
    var hasFlash by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun t(ru: String, en: String, es: String = en, zh: String = en, ja: String = en, ko: String = en, pt: String = en, uk: String = ru): String = when (lang) {
        "ru" -> ru; "es" -> es; "zh" -> zh; "ja" -> ja; "ko" -> ko; "pt" -> pt; "uk" -> uk; else -> en
    }

    LaunchedEffect(permissionGranted, retryKey) {
        if (!permissionGranted) return@LaunchedEffect
        error = null
        starting = true
        bindCamera(
            previewView,
            { flash ->
                hasFlash = flash
                starting = false
            },
            { message ->
                error = message
                starting = false
            }
        )
    }

    val scanProgress: Float
    if (effectsEnabled) {
        val scanAnim = rememberInfiniteTransition(label = "scan_line").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_progress"
        )
        scanProgress = scanAnim.value
    } else {
        scanProgress = 0f
    }
    val flashAlpha by animateFloatAsState(
        targetValue = if (detected) 0.5f else 0f,
        animationSpec = tween(if (effectsEnabled) 120 else 0),
        label = "flash"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (focusPoint != null) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 260 else 0),
        label = "focus_ring_alpha"
    )
    val focusScale by animateFloatAsState(
        targetValue = if (focusPoint != null) 1f else 1.35f,
        animationSpec = tween(if (effectsEnabled) 320 else 0),
        label = "focus_ring_scale"
    )

    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            delay(750)
            focusPoint = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        if (permissionGranted) {
            AndroidView(
                factory = { previewView.also { pv ->
                    pv.setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            onTapFocus(event.x, event.y)
                            focusPoint = Offset(event.x, event.y)
                            focusTick++
                        }
                        true
                    }
                } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Затемнение вокруг рамки + уголки + бегущая линия
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width * 0.72f, size.height - 280.dp.toPx())
                .coerceAtLeast(0f)
            if (side <= 0f) return@Canvas
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f - 24.dp.toPx()
            val right = left + side
            val bottom = top + side
            val corner = CornerRadius(22.dp.toPx(), 22.dp.toPx())

            val mask = Color.Black.copy(alpha = 0.6f)
            drawRect(mask, Offset.Zero, ComposeSize(size.width, top))
            drawRect(mask, Offset(0f, bottom), ComposeSize(size.width, size.height - bottom))
            drawRect(mask, Offset(0f, top), ComposeSize(left, side))
            drawRect(mask, Offset(right, top), ComposeSize(size.width - right, side))

            val frameColor = if (detected) PINK_END else Color.White.copy(alpha = 0.16f)
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(left, top),
                size = ComposeSize(side, side),
                cornerRadius = corner,
                style = Stroke(width = 1.5.dp.toPx())
            )

            val bracket = Brush.linearGradient(
                colors = listOf(PINK_START, PINK_END),
                start = Offset(left, top),
                end = Offset(right, bottom)
            )
            val len = 46.dp.toPx()
            val w = 6.dp.toPx()
            val cap = StrokeCap.Round
            drawLine(bracket, Offset(left, top + len), Offset(left, top), w, cap)
            drawLine(bracket, Offset(left, top), Offset(left + len, top), w, cap)
            drawLine(bracket, Offset(right - len, top), Offset(right, top), w, cap)
            drawLine(bracket, Offset(right, top), Offset(right, top + len), w, cap)
            drawLine(bracket, Offset(left, bottom - len), Offset(left, bottom), w, cap)
            drawLine(bracket, Offset(left, bottom), Offset(left + len, bottom), w, cap)
            drawLine(bracket, Offset(right - len, bottom), Offset(right, bottom), w, cap)
            drawLine(bracket, Offset(right, bottom), Offset(right, bottom - len), w, cap)

            if (!detected) {
                val lineY = top + 26.dp.toPx() +
                        (side - 52.dp.toPx()) * scanProgress
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            PINK.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        startX = left,
                        endX = right
                    ),
                    topLeft = Offset(left + 18.dp.toPx(), lineY - 1.dp.toPx()),
                    size = ComposeSize(side - 36.dp.toPx(), 2.dp.toPx())
                )
            }

            // Кольцо фокусировки по тапу
            val fp = focusPoint
            if (fp != null && focusAlpha > 0.01f) {
                val base = 34.dp.toPx() * focusScale
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f * focusAlpha),
                    radius = base,
                    center = fp,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = PINK.copy(alpha = 0.85f * focusAlpha),
                    radius = base * 0.55f,
                    center = fp,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        if (flashAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
        }

        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleButton(
                size = 44.dp,
                onClick = onClose
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = t("Закрыть", "Close", es = "Cerrar", zh = "关闭", ja = "閉じる", ko = "닫기", pt = "Fechar", uk = "Закрити"),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = t("Сканер QR", "QR Scanner", es = "Escáner QR", zh = "二维码扫描仪", ja = "QRスキャナー", ko = "QR 스캐너", pt = "Leitor QR", uk = "Сканер QR"),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            GlassCircleButton(
                size = 44.dp,
                enabled = hasFlash,
                active = torchOn,
                onClick = onToggleTorch
            ) {
                Icon(
                    imageVector = IconsEx.Lightbulb,
                    contentDescription = t("Фонарик", "Flashlight", es = "Linterna", zh = "手电筒", ja = "懐中電灯", ko = "손전등", pt = "Lanterna", uk = "Ліхтарик"),
                    tint = when {
                        torchOn -> Color(0xFFFFE082)
                        !hasFlash -> Color.White.copy(alpha = 0.3f)
                        else -> Color.White.copy(alpha = 0.85f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Нижняя подсказка + компенсация экспозиции
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hintText = when {
                detected -> t("Готово!", "Done!", es = "¡Listo!", zh = "完成！", ja = "完了！", ko = "완료!", pt = "Pronto!", uk = "Готово!")
                error != null -> t("Камера недоступна", "Camera unavailable", es = "Cámara no disponible", zh = "摄像头不可用", ja = "カメラが利用できません", ko = "카메라를 사용할 수 없습니다", pt = "Câmera indisponível", uk = "Камера недоступна")
                starting -> t("Запуск камеры…", "Starting camera…", es = "Iniciando cámara…", zh = "启动摄像头…", ja = "カメラを起動中…", ko = "카메라 시작 중…", pt = "Iniciando câmera…", uk = "Запуск камери…")
                else -> t("Наведи на QR-код · тап — фокус", "Point at the QR code · tap to focus", es = "Apunta al código QR · toca para enfocar", zh = "指向二维码·点击对焦", ja = "QRコードを指す·タップしてフォーカス", ko = "QR 코드를 가리키세요 · 탭하여 포커스", pt = "Aponte para o código QR · toque para focar", uk = "Наведи на QR-код · тап — фокус")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (detected) PINK.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = hintText,
                        color = if (detected) PINK else Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                if (exposureAvailable) {
                    val exposureActive = exposureEv != 0
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(
                                1.dp,
                                if (exposureActive) PINK.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(50)
                            )
                            .clickable { onCycleExposure() }
                            .padding(horizontal = 13.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = if (exposureEv == 0) "EV 0" else "EV \u2212${-exposureEv}",
                            color = if (exposureActive) PINK else Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Экран ошибки
        AnimatedVisibility(
            visible = error != null && !starting,
            enter = fadeIn(tween(if (effectsEnabled) 220 else 0)) +
                    scaleIn(tween(if (effectsEnabled) 220 else 0), initialScale = 0.96f),
            exit = fadeOut(tween(if (effectsEnabled) 160 else 0)) +
                    scaleOut(tween(if (effectsEnabled) 160 else 0), targetScale = 0.97f)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xF0121114))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PINK.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconsEx.QrCodeScanner,
                            contentDescription = null,
                            tint = PINK,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = t("Камера недоступна", "Camera unavailable", es = "Cámara no disponible", zh = "摄像头不可用", ja = "カメラが利用できません", ko = "카메라를 사용할 수 없습니다", pt = "Câmera indisponível", uk = "Камера недоступна"),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        text = t(
                            "Не удалось открыть камеру. Попробуй ещё раз или закрой сканер.",
                            "Could not open the camera. Try again or close the scanner.",
                            es = "No se pudo abrir la cámara. Intenta de nuevo o cierra el escáner.",
                            zh = "无法打开摄像头。请重试或关闭扫描仪。",
                            ja = "カメラを開くことができませんでした。もう一度試すか、スキャナーを閉じてください。",
                            ko = "카메라를 열 수 없습니다. 다시 시도하거나 스캐너를 닫으세요.",
                            pt = "Não foi possível abrir a câmera. Tente novamente ou feche o leitor.",
                            uk = "Не вдалось відкрити камеру. Спробуй ще раз або закрий сканер."
                        ),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = error.orEmpty(),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .clickable { onClose() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t("Закрыть", "Close", es = "Cerrar", zh = "关闭", ja = "閉じる", ko = "닫기", pt = "Fechar", uk = "Закрити"),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(PINK_START, PINK_END)))
                                .clickable {
                                    starting = true
                                    error = null
                                    retryKey++
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t("Повторить", "Retry", es = "Reintentar", zh = "重试", ja = "再試行", ko = "재시도", pt = "Tentar de novo", uk = "Повторити"),
                                color = Color(0xFF1A1116),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (active) PINK.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.32f)
            )
            .border(
                1.dp,
                if (active) PINK.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
