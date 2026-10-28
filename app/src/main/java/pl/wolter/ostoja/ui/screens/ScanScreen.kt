package pl.wolter.ostoja.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.qr.QrScan
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.press
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onManual: () -> Unit,
    onResult: (List<OtpAccount>) -> Unit,
    onPair: ((String) -> Unit)? = null,
    /** Tryb serii (np. kilka kodów eksportu z Google Authenticator): każdy nowy kod trafia tu, a skaner zostaje otwarty. */
    onEach: ((String) -> Unit)? = null,
    title: String = "Skanuj kod QR",
    hint: String = "Kod zostanie odczytany automatycznie",
) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<OtpAccount>?>(null) }
    var pairCode by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf(0L) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) { AppLock.allowExternal(); perm.launch(Manifest.permission.CAMERA) } }

    val seen = remember { mutableSetOf<String>() }
    var flash by remember { mutableStateOf(0) }

    fun handle(text: String) {
        if (done) return
        if (onEach != null) {
            if (seen.add(text)) { onEach(text); flash++ }
            return
        }
        // Kod parowania z aplikacji Ostoja na Windows.
        if (onPair != null && pl.wolter.ostoja.sync.PairInfo.isPair(text)) { done = true; pairCode = text; return }
        try {
            val list = OtpUri.parse(text)
            done = true
            result = list
        } catch (e: Exception) {
            val t = System.currentTimeMillis()
            if (t - lastError > 2500) {
                lastError = t
                toaster.show(e.message ?: "To nie jest kod 2FA")
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val text = decodeQrFromImage(ctx, uri)
            if (text == null) toaster.show("Nie znaleziono kodu QR na zdjęciu") else handle(text)
        }
    }

    var found by remember { mutableStateOf(false) }
    val corner by androidx.compose.animation.animateColorAsState(if (found) C.accent else Color.White, label = "corner")
    val frameScale by androidx.compose.animation.core.animateFloatAsState(
        if (found) .94f else 1f, androidx.compose.animation.core.tween(260, easing = pl.wolter.ostoja.ui.theme.Motion.Bouncy), label = "frame",
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10))) {
        if (granted) {
            CameraPreview(torch) { handle(it) }
        }
        ScanOverlay(corner, frameScale)

        if (!granted) Column(
            Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(330.dp))
            Text("Potrzebny dostęp do aparatu", style = T.head.copy(fontSize = 18.sp), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Aparat służy tylko do odczytania kodu. Obraz nie jest nigdzie zapisywany.",
                style = T.sub.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Color.White.copy(.75f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GlassPill(if (asked) "Otwórz ustawienia" else "Zezwól") {
                AppLock.allowExternal()
                if (asked) ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                else perm.launch(Manifest.permission.CAMERA)
            }
        }

        // górny pasek: wstecz · tytuł · latarka
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 12.dp),
        ) {
            GlassCircle(Ic.Back, "Wstecz", Modifier.align(Alignment.CenterStart), onClick = onClose)
            Text(title, style = T.head.copy(fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, modifier = Modifier.align(Alignment.Center))
            GlassCircle(Ic.Flash, "Latarka", Modifier.align(Alignment.CenterEnd), active = torch, enabled = granted) { torch = !torch }
        }

        if (granted) BoxWithConstraints(Modifier.fillMaxSize()) {
            val top = maxHeight * .47f + 130.dp + 32.dp
            Column(Modifier.fillMaxWidth().padding(top = top, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (found) "Odczytano kod" else "Umieść kod QR w ramce", style = T.head.copy(fontSize = 18.sp), color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(hint, style = T.sub.copy(fontSize = 14.sp), color = Color.White.copy(.75f), textAlign = TextAlign.Center)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onEach == null) GlassPill("Wpisz klucz ręcznie") { onManual() } else GlassPill("Gotowe") { onClose() }
            GlassPill("Z galerii") {
                AppLock.allowExternal()
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
    // Krótkie potwierdzenie (narożniki w kolorze akcentu, ramka „łapie” kod) przed przejściem dalej.
    LaunchedEffect(pairCode) {
        val c = pairCode ?: return@LaunchedEffect
        found = true
        kotlinx.coroutines.delay(420)
        onPair?.invoke(c)
    }
    // W trybie serii: krótki błysk narożników przy każdym nowym kodzie.
    LaunchedEffect(flash) {
        if (flash == 0) return@LaunchedEffect
        found = true
        kotlinx.coroutines.delay(500)
        found = false
    }
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        found = true
        kotlinx.coroutines.delay(420)
        onResult(r)
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, desc: String, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else .4f }
            .press(enabled = enabled, onClick = onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = .14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .press(onClick = onClick)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = .16f))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.bodyM, color = Color.White) }
}

/** Ramka 260 dp (radius 36), przyciemnienie 55% i białe narożniki 52 dp / 4 dp jak w prototypie. */
@Composable
private fun ScanOverlay(corner: Color, scale: Float) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = 260.dp.toPx() * scale
        val left = (size.width - side) / 2
        val top = size.height * .47f - side / 2
        val r = 36.dp.toPx() * scale
        drawRect(Color.Black.copy(alpha = .55f))
        drawRoundRect(Color.Transparent, Offset(left, top), GSize(side, side), CornerRadius(r), blendMode = BlendMode.Clear)
        val len = 52.dp.toPx()
        val w = 4.dp.toPx()
        val h = w / 2
        val l = left + h; val t = top + h; val rr = left + side - h; val b = top + side - h
        val rc = r - h
        val p = Path().apply {
            moveTo(l, t + len); lineTo(l, t + rc); quadraticTo(l, t, l + rc, t); lineTo(l + len, t)
            moveTo(rr - len, t); lineTo(rr - rc, t); quadraticTo(rr, t, rr, t + rc); lineTo(rr, t + len)
            moveTo(rr, b - len); lineTo(rr, b - rc); quadraticTo(rr, b, rr - rc, b); lineTo(rr - len, b)
            moveTo(l + len, b); lineTo(l + rc, b); quadraticTo(l, b, l, b - rc); lineTo(l, b - len)
        }
        drawPath(p, corner, style = Stroke(w))
    }
}

@Composable
private fun CameraPreview(torch: Boolean, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onText)
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        val main = ContextCompat.getMainExecutor(ctx)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()
                )
                .build()
            analysis.setAnalyzer(executor, QrAnalyzer { text -> main.execute { callback(text) } })
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private var last = ""
    private var lastAt = 0L
    private var frame = 0

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val data = ByteArray(buf.remaining()).also { buf.get(it) }
            // Wspólny odczyt z komputerem: zwykły, odwrócony (ciemny motyw) i bez linii siatki (kod z GitHuba na monitorze).
            val text = QrScan.decodeFrame(data, image.width, image.height, plane.rowStride, frame++)
            if (text != null) {
                val t = System.currentTimeMillis()
                if (text != last || t - lastAt > 1500) {
                    last = text; lastAt = t
                    onText(text)
                }
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}

/** Odczyt kodu QR ze zrzutu ekranu / zdjęcia (np. strona GitHuba na telefonie albo eksport z Google Authenticator). */
fun decodeQrFromImage(ctx: Context, uri: Uri): String? = runCatching {
    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val max = maxOf(info.size.width, info.size.height)
        if (max > 2000) {
            val s = 2000f / max
            d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
        }
    }
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    QrScan.decode(QrScan.luminance(px), bmp.width, bmp.height)
}.getOrNull()
