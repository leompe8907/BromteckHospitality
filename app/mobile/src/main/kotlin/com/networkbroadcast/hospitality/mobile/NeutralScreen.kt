package com.networkbroadcast.hospitality.mobile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.networkbroadcast.hospitality.brand.BrandConfig
import com.networkbroadcast.hospitality.brand.imageModel
import com.networkbroadcast.hospitality.companion.LinkState
import com.networkbroadcast.hospitality.companion.isValidPairingCode
import com.networkbroadcast.hospitality.designsystem.HospitalityColors
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme
import kotlinx.coroutines.launch

/** Textos de la pantalla de antes del login y de la de escribir el código, en los idiomas de la app (los del mockup). */
internal data class NeutralText(
    val title: String,
    val subtitle: String,
    val scan: String,
    val typeCode: String,
    val foot: String,
    val codeTitle: String,
    val codeHint: String,
    val connect: String,
    val invalidCode: String,
    val linkFailed: String,
    val scanFailed: String,
    val checking: String,
    val failed: String,
) {
    companion object {
        fun of(language: String): NeutralText = when (language) {
            "en" -> NeutralText(
                "Scan the QR on your hotel TV to see the content", "You'll find it on the screen in your room. It only takes a few seconds.",
                "Scan QR code", "Enter the 6-digit code", "No username or password needed.",
                "TV code", "It's below the QR on your room's screen. It expires after 10 minutes.", "Connect",
                "The code isn't valid or has expired", "Couldn't connect to the TV. Try again.", "Couldn't read the QR. Enter the 6-digit code.",
                "Connecting…", "Could not connect",
            )
            "pt" -> NeutralText(
                "Escaneie o QR na televisão do seu hotel para ver o conteúdo", "Ele aparece na tela do seu quarto. Leva só alguns segundos.",
                "Escanear código QR", "Digitar o código de 6 dígitos", "Sem usuário nem senha.",
                "Código da TV", "Fica abaixo do QR, na tela do seu quarto. Vence em 10 minutos.", "Conectar",
                "O código não é válido ou já venceu", "Não foi possível conectar à TV. Tente de novo.", "Não foi possível ler o QR. Digite o código de 6 dígitos.",
                "Conectando…", "Não foi possível conectar",
            )
            "fr" -> NeutralText(
                "Scannez le QR sur la télévision de votre hôtel pour voir le contenu", "Il s'affiche sur l'écran de votre chambre. Cela ne prend que quelques secondes.",
                "Scanner le code QR", "Saisir le code à 6 chiffres", "Ni identifiant ni mot de passe.",
                "Code de la TV", "Il est sous le QR, sur l'écran de votre chambre. Il expire après 10 minutes.", "Connecter",
                "Le code n'est pas valide ou a expiré", "Connexion à la TV impossible. Réessayez.", "Impossible de lire le QR. Saisissez le code à 6 chiffres.",
                "Connexion…", "Connexion impossible",
            )
            "de" -> NeutralText(
                "Scannen Sie den QR-Code auf dem Fernseher Ihres Hotels, um die Inhalte zu sehen", "Er erscheint auf dem Bildschirm in Ihrem Zimmer. Es dauert nur wenige Sekunden.",
                "QR-Code scannen", "6-stelligen Code eingeben", "Kein Benutzername, kein Passwort.",
                "TV-Code", "Er steht unter dem QR auf dem Bildschirm Ihres Zimmers. Er läuft nach 10 Minuten ab.", "Verbinden",
                "Der Code ist ungültig oder abgelaufen", "Keine Verbindung zum Fernseher. Bitte erneut versuchen.", "Der QR-Code konnte nicht gelesen werden. Geben Sie den 6-stelligen Code ein.",
                "Verbinde…", "Verbindung nicht möglich",
            )
            else -> NeutralText(
                "Escanea el QR en la televisión de tu hotel para ver el contenido", "Lo verás en la pantalla de tu habitación. Solo toma unos segundos.",
                "Escanear código QR", "Escribir el código de 6 dígitos", "No necesitas usuario ni contraseña.",
                "Código de la TV", "Aparece debajo del QR, en la pantalla de tu habitación. Vence a los 10 minutos.", "Conectar",
                "El código no es válido o ya venció", "No se pudo conectar con la TV. Prueba de nuevo.", "No se pudo leer el QR. Escribe el código de 6 dígitos.",
                "Conectando…", "No se pudo conectar",
            )
        }
    }
}

/** Archivo del logo de la marca (el mismo que usa la TV): `brands/<marca>/assets/logo.png`, solo si `showLogo` es true. */
private const val BRAND_LOGO_ASSET = "asset:logo.png"

/**
 * Colores de la pantalla de antes del emparejamiento (`NEUTRAL` del mockup): claros y con un azul pizarra de acento,
 * los mismos para todas las marcas. Todavía no se sabe de qué hotel es: los colores del hotel llegan con la sesión.
 */
internal val NeutralColors = HospitalityColors(
    background = Color(0xFFF2F1EC),
    surface = Color(0xFFFFFFFF),
    surfaceBorder = Color(0xFFDEDCD4),
    rail = Color(0xFFF2F1EC),
    textPrimary = Color(0xFF17191C),
    textSecondary = Color(0xFF4F545B),
    textMuted = Color(0xFF80858B),
    accent = Color(0xFF3D5A6C),
    focus = Color(0xFF3D5A6C),
    isDark = false,
)

/**
 * Logo de la pantalla de antes del login, o null si no hay: el del panel ([logoUrl], que solo se conoce antes del login
 * si la app tiene panel y es de un solo hotel) o el que cargó la marca (`showLogo`). El de Panaccess no sirve acá: solo
 * llega con la sesión abierta.
 */
internal fun neutralLogo(brand: BrandConfig, logoUrl: String?): String? = logoUrl ?: BRAND_LOGO_ASSET.takeIf { brand.showLogo }

/**
 * El código de 6 dígitos que trae un QR leído con el escáner: el enlace `hospitality://pair?code=123456` (o cualquier
 * enlace con `code=`), o los 6 dígitos solos (se aceptan espacios, "482 915"). null si no hay un código válido.
 */
internal fun pairingCodeFrom(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    Regex("[?&]code=(\\d{6})(?:&|$)").find(text)?.let { return it.groupValues[1] }
    return text.filterNot { it == ' ' }.takeIf(::isValidPairingCode)
}

/**
 * Primera pantalla, antes de tener sesión (mockup: `s_neutral` y `s_code`). Sin la línea de marca del mockup ("Hospitality
 * TV"): el huésped no tiene por qué ver el nombre de la app genérica. De arriba abajo: el logo si hay ([neutralLogo]),
 * la ilustración de la TV con el QR y el celular, el título, el texto, "Escanear código QR" (escáner de Google Play
 * services), "Escribir el código de 6 dígitos" (abre el teclado numérico) y la nota "No necesitas usuario ni contraseña".
 * El fondo es la primera foto de `backgrounds` de la marca, si trae; si no, el liso del tema.
 *
 * Un código, venga del escáner, del teclado o del enlace del QR abierto con la cámara ([incomingCode]), se canjea con
 * [onLink]; si el emparejamiento trae el login del hotel, [MobileApplication.bindCompanionSession] abre la sesión de
 * Panaccess y esta pantalla deja de verse.
 *
 * En builds de depuración se suma un formulario para entrar con una cuenta de prueba.
 */
@Composable
internal fun NeutralScreen(
    text: NeutralText,
    brand: BrandConfig,
    logoUrl: String?,
    session: MobileSession,
    incomingCode: String?,
    onCodeConsumed: () -> Unit,
    onLink: suspend (String) -> LinkState,
    debugLogin: ((user: String, password: String, room: String?) -> Unit)?,
) {
    var typing by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    // Un enlace del QR (cámara del celular) con la app sin sesión: directo a canjear el código.
    LaunchedEffect(incomingCode) {
        if (incomingCode != null) {
            prefill = incomingCode
            typing = true
            onCodeConsumed()
        }
    }
    Box(Modifier.fillMaxSize()) {
        MobileBackdrop(brand.backgrounds.firstOrNull()?.let { MobileBackground.Photo(it) })
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            if (typing) {
                val close = { typing = false; prefill = "" }
                BackHandler(onBack = close)
                CodeEntry(text, initialCode = prefill, onBack = close, onLink = onLink, onLinked = close)
            } else {
                NeutralMain(
                    text, neutralLogo(brand, logoUrl), brand.displayName, session, debugLogin,
                    onScanned = { prefill = it; typing = true },
                    onType = { typing = true },
                )
            }
        }
    }
}

@Composable
private fun NeutralMain(
    text: NeutralText,
    logo: String?,
    hotelName: String,
    session: MobileSession,
    debugLogin: ((String, String, String?) -> Unit)?,
    onScanned: (String) -> Unit,
    onType: () -> Unit,
) {
    val colors = HospitalityTheme.colors
    val context = LocalContext.current
    var scanFailed by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // La ilustración ocupa lo que sobra (como `.illus{flex:1}` del mockup), sin pasar de 330 dp de alto.
        val illusHeight = minOf((maxWidth - 52.dp) * (300f / 340f), (maxHeight * 0.42f).coerceIn(160.dp, 330.dp))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                logo?.let { AsyncImage(model = imageModel(it), contentDescription = hotelName, modifier = Modifier.height(40.dp)) }
                NeutralIllustration(Modifier.fillMaxWidth().padding(top = 16.dp).height(illusHeight))
            }
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    text.title, color = colors.textPrimary,
                    style = HospitalityTheme.typography.title.copy(fontSize = 30.sp, lineHeight = 33.6.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Text(
                    text.subtitle, color = colors.textSecondary,
                    style = HospitalityTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
                    modifier = Modifier.padding(bottom = 22.dp),
                )
                val status = when {
                    session == MobileSession.Checking -> text.checking
                    session is MobileSession.Failed -> "${text.failed}: ${session.detail}"
                    scanFailed -> text.scanFailed
                    else -> null
                }
                status?.let { Text(it, style = HospitalityTheme.typography.label, color = colors.accent, modifier = Modifier.padding(bottom = 12.dp)) }
                PrimaryButton(text.scan, icon = R.drawable.ic_qr) {
                    scanFailed = false
                    scanQr(context, onCode = onScanned, onFail = { scanFailed = true })
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(rad(22))).clickable(onClick = onType),
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Text(text.typeCode, color = colors.textSecondary, style = HospitalityTheme.typography.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Text(text.foot, color = colors.textMuted, style = HospitalityTheme.typography.body.copy(fontSize = 12.5.sp))
                }
                if (debugLogin != null) DebugLoginForm(checking = session == MobileSession.Checking, onLogin = debugLogin)
            }
        }
    }
}

/**
 * Abre el escáner de QR de Google Play services (la cámara la maneja Play services: la app no pide permiso). Si el QR
 * trae un código ([pairingCodeFrom]) llama a [onCode]; si no se pudo leer, o el celular no tiene Play services, a
 * [onFail]. Si el huésped cierra el escáner no pasa nada.
 */
private fun scanQr(context: Context, onCode: (String) -> Unit, onFail: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> pairingCodeFrom(barcode.rawValue)?.let(onCode) ?: onFail() }
        .addOnFailureListener { onFail() }
}

/** Botón principal del mockup (`.btn.pri`): 54 dp de alto, color de acento, ícono y texto. */
@Composable
private fun PrimaryButton(label: String, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = HospitalityTheme.colors
    val ink = HospitalityColors.readableTextColor(colors.accent)
    Row(
        Modifier.fillMaxWidth().height(54.dp).alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(rad(27, 2))).background(colors.accent)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = ink, modifier = Modifier.size(22.dp)) }
        Text(label, color = ink, style = HospitalityTheme.typography.label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Escribir el código de 6 dígitos que muestra la TV debajo del QR (mockup `s_code`): seis casillas, "Conectar" y un
 * teclado numérico propio. Si llega con un código completo (del escáner o del enlace) se conecta solo.
 */
@Composable
private fun CodeEntry(text: NeutralText, initialCode: String, onBack: () -> Unit, onLink: suspend (String) -> LinkState, onLinked: () -> Unit) {
    val colors = HospitalityTheme.colors
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initialCode.filter(Char::isDigit).take(6)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun connect() {
        if (busy || !isValidPairingCode(code)) return
        busy = true
        error = null
        scope.launch {
            val result = onLink(code)
            busy = false
            when (result) {
                is LinkState.Linked -> onLinked()
                is LinkState.Failed -> error = when (result.reason) {
                    LinkState.Reason.InvalidCode, LinkState.Reason.Expired -> text.invalidCode
                    else -> text.linkFailed
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) { if (isValidPairingCode(code)) connect() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp)) { BackButton(onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 6.dp)) {
            Text(
                text.codeTitle, color = colors.textPrimary,
                style = HospitalityTheme.typography.title.copy(fontSize = 28.sp, lineHeight = 30.8.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            Text(
                text.codeHint, color = colors.textSecondary,
                style = HospitalityTheme.typography.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(6) { i ->
                    DigitBox(code.getOrNull(i), current = code.length < 6 && i == code.length, bad = error != null, modifier = Modifier.weight(1f))
                }
            }
            error?.let { Text(it, color = ERROR_RED, style = HospitalityTheme.typography.body.copy(fontSize = 13.5.sp), modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (busy) text.checking else text.connect, enabled = isValidPairingCode(code) && !busy, onClick = ::connect)
        }
        Keypad(
            enabled = !busy,
            onDigit = { if (code.length < 6) { code += it; error = null } },
            onDelete = { code = code.dropLast(1); error = null },
        )
    }
}

/** Rojo de error del mockup (`.digs.bad .dg`). Fijo: un error tiene que verse igual con cualquier tema. */
private val ERROR_RED = Color(0xFFE5484D)

/** Una casilla del código: el dígito, o vacía; la que toca escribir con borde de acento, todas en rojo si hubo error. */
@Composable
private fun DigitBox(digit: Char?, current: Boolean, bad: Boolean, modifier: Modifier) {
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(rad(16, 2))
    val border = when {
        bad -> ERROR_RED
        current -> colors.accent
        else -> colors.surfaceBorder
    }
    Box(
        modifier.height(62.dp).clip(shape).background(colors.surface).border(if (current && !bad) 2.dp else 1.5.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (digit != null) {
            Text(digit.toString(), color = colors.textPrimary, style = HospitalityTheme.typography.title.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
        } else if (current) {
            Box(Modifier.size(width = 2.dp, height = 28.dp).background(colors.accent))
        }
    }
}

/** Teclado numérico del mockup (`.pad`): 1 a 9, 0 y borrar, en tres columnas. */
@Composable
private fun Keypad(enabled: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val rows = listOf("123", "456", "789", " 0<")
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(key, enabled, Modifier.weight(1f)) { if (key == '<') onDelete() else onDigit(key) }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: Char, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (key == ' ') {
        Spacer(modifier.height(58.dp))
        return
    }
    val colors = HospitalityTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(58.dp).clip(shape).background(colors.surface).border(1.dp, colors.surfaceBorder, shape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == '<') {
            Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
        } else {
            Text(key.toString(), color = colors.textPrimary, style = HospitalityTheme.typography.title.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/**
 * Módulos de un QR de adorno (no se puede leer): ruido fijo con las tres esquinas y las líneas de sincronismo de un QR
 * de verdad. Mismo armado que `qrPath` del mockup.
 */
internal fun qrModules(n: Int = 25, seed: Long = 7): List<BooleanArray> {
    var state = seed
    fun next(): Double {
        state = state * 6364136223846793005L + 1442695040888963407L
        return (state ushr 11).toDouble() / (1L shl 53).toDouble()
    }
    val m = List(n) { BooleanArray(n) { next() > 0.52 } }
    fun finder(ox: Int, oy: Int) {
        for (y in -1..7) for (x in -1..7) {
            val col = ox + x
            val row = oy + y
            if (col !in 0 until n || row !in 0 until n) continue
            m[row][col] = x in 0..6 && y in 0..6 && (x == 0 || x == 6 || y == 0 || y == 6 || (x in 2..4 && y in 2..4))
        }
    }
    finder(0, 0)
    finder(n - 7, 0)
    finder(0, n - 7)
    for (i in 8 until n - 8) {
        m[6][i] = i % 2 == 0
        m[i][6] = i % 2 == 0
    }
    return m
}

private val QR_INK = Color(0xFF0D1114)
private val TV_BODY = Color(0xFF1C2327)

private fun DrawScope.drawQr(modules: List<BooleanArray>, origin: Offset, side: Float) {
    val cell = side / modules.size
    modules.forEachIndexed { y, row ->
        row.forEachIndexed { x, on -> if (on) drawRect(QR_INK, Offset(origin.x + x * cell, origin.y + y * cell), Size(cell, cell)) }
    }
}

/**
 * La ilustración del mockup (`s_neutral`, viewBox 340×300): la TV de la habitación con el QR y el código debajo, y un
 * celular inclinado leyéndolo. Los grises de la TV son fijos (es un aparato); los círculos de fondo, el celular y los
 * acentos toman los colores del tema.
 */
@Composable
private fun NeutralIllustration(modifier: Modifier) {
    val colors = HospitalityTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Tamaños en unidades del viewBox: el Canvas se escala entero, así que 11 unidades = 11 px antes de escalar.
    val codeStyle = remember(density) {
        with(density) {
            TextStyle(fontFamily = MobileFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11f.toSp(), letterSpacing = 3f.toSp(), color = Color(0xFF9DB0B9))
        }
    }
    val modules = remember { qrModules() }
    Canvas(modifier) {
        val s = minOf(size.width / 340f, size.height / 300f)
        withTransform({
            translate((size.width - 340f * s) / 2f, (size.height - 300f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawCircle(colors.surfaceBorder.copy(alpha = colors.surfaceBorder.alpha * 0.55f), 128f, Offset(170f, 150f))
            drawCircle(colors.surfaceBorder.copy(alpha = colors.surfaceBorder.alpha * 0.5f), 92f, Offset(170f, 150f))
            // TV, pie y base.
            drawRoundRect(TV_BODY, Offset(38f, 46f), Size(264f, 170f), CornerRadius(16f))
            drawRoundRect(Color(0xFF2D3B43), Offset(50f, 58f), Size(240f, 146f), CornerRadius(8f))
            drawPath(Path().apply { moveTo(126f, 216f); lineTo(214f, 216f); lineTo(224f, 236f); lineTo(116f, 236f); close() }, TV_BODY)
            drawRoundRect(TV_BODY, Offset(104f, 234f), Size(132f, 6f), CornerRadius(3f))
            // QR en la pantalla de la TV y el código de 6 dígitos debajo.
            drawRoundRect(Color.White, Offset(118f, 72f), Size(104f, 104f), CornerRadius(7f))
            drawQr(modules, Offset(125f, 79f), 90f)
            val code = measurer.measure("482 915", codeStyle)
            drawText(code, topLeft = Offset(170f - code.size.width / 2f, 196f - code.firstBaseline))
            // Celular inclinado leyendo el QR.
            rotate(8f, pivot = Offset(250f, 220f)) {
                drawRoundRect(colors.background, Offset(206f, 148f), Size(86f, 132f), CornerRadius(17f))
                drawRoundRect(colors.textPrimary, Offset(206f, 148f), Size(86f, 132f), CornerRadius(17f), style = Stroke(3.5f))
                drawRoundRect(Color(0xFF10171B), Offset(214f, 160f), Size(70f, 104f), CornerRadius(8f))
                val corners = Path().apply {
                    moveTo(224f, 178f); lineTo(224f, 170f); lineTo(232f, 170f)
                    moveTo(274f, 178f); lineTo(274f, 170f); lineTo(266f, 170f)
                    moveTo(224f, 250f); lineTo(224f, 258f); lineTo(232f, 258f)
                    moveTo(274f, 250f); lineTo(274f, 258f); lineTo(266f, 258f)
                }
                drawPath(corners, colors.accent, style = Stroke(3f, cap = StrokeCap.Round))
                drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(234f, 190f), Size(30f, 30f), CornerRadius(3f))
                drawQr(modules, Offset(236f, 192f), 26f)
                drawRoundRect(colors.accent, Offset(233f, 204f), Size(32f, 2.5f), CornerRadius(1.2f))
                drawRoundRect(colors.textPrimary.copy(alpha = 0.8f), Offset(240f, 154f), Size(18f, 3.6f), CornerRadius(1.8f))
            }
            // Puntos del celular a la TV.
            val arc = Path().apply { moveTo(200f, 150f); relativeCubicTo(-6f, -18f, -2f, -34f, 12f, -44f) }
            drawPath(arc, colors.accent, style = Stroke(2.4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f))))
        }
    }
}

@Composable
private fun DebugLoginForm(checking: Boolean, onLogin: (String, String, String?) -> Unit) {
    val colors = HospitalityTheme.colors
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DEBUG · cuenta de prueba", style = HospitalityTheme.typography.label, color = colors.textPrimary.copy(alpha = 0.6f))
        OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(room, { room = it }, label = { Text("Licencia / habitación (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent)
                .let { if (checking || user.isBlank() || password.isBlank()) it else it.clickable { onLogin(user, password, room) } },
            contentAlignment = Alignment.Center,
        ) {
            Text("Entrar", style = HospitalityTheme.typography.label, color = colors.background, textAlign = TextAlign.Center)
        }
    }
}
