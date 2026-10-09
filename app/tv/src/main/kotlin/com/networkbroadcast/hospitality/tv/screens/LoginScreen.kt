package com.networkbroadcast.hospitality.tv.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.networkbroadcast.hospitality.brand.UiText
import com.networkbroadcast.hospitality.designsystem.FocusCard
import com.networkbroadcast.hospitality.designsystem.HospitalityTheme

/**
 * Login de la TV (diseño A "Recepción" del canvas de login): a la izquierda la foto de la marca con
 * su nombre, a la derecha el formulario. Lo ve el instalador o el staff, no el huésped: después del
 * primer login la TV entra sola (PanaccessSession.restore) y desde la TV no se puede cerrar sesión.
 * El texto no nombra al proveedor: para el hotel es "la cuenta de TV".
 */
@Composable
fun LoginScreen(
    text: UiText,
    brandName: String,
    backgrounds: List<String>,
    initialUser: String?,
    busy: Boolean,
    error: String?,
    onSubmit: (user: String, password: String) -> Unit,
) {
    val colors = HospitalityTheme.colors
    Row(Modifier.fillMaxSize().background(colors.background)) {
        // Foto de la marca (sin fotos queda el fondo liso con el nombre).
        Box(Modifier.weight(0.55f).fillMaxHeight()) {
            HotelBackdrop(backgrounds)
            Column(
                Modifier.align(Alignment.BottomStart).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text.loginTagline.uppercase(),
                    style = HospitalityTheme.typography.label.copy(fontSize = 13.sp, letterSpacing = 3.sp),
                    color = colors.textPrimary,
                )
                Text(
                    brandName,
                    style = HospitalityTheme.typography.display.copy(fontSize = 56.sp, lineHeight = 58.sp),
                    color = colors.textPrimary,
                )
                Box(Modifier.size(width = 60.dp, height = 2.dp).background(colors.accent))
            }
        }
        LoginForm(text, initialUser, busy, error, onSubmit, Modifier.weight(0.45f).fillMaxHeight())
    }
}

@Composable
private fun LoginForm(
    text: UiText,
    initialUser: String?,
    busy: Boolean,
    error: String?,
    onSubmit: (user: String, password: String) -> Unit,
    modifier: Modifier,
) {
    val colors = HospitalityTheme.colors
    var user by rememberSaveable { mutableStateOf(initialUser.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val first = rememberInitialFocus()
    val focus = LocalFocusManager.current
    // Foco explícito entre el campo y el ojo: están superpuestos visualmente (el ojo "adentro" del
    // borde derecho del campo), y con elementos que se solapan el buscador de foco automático de
    // Compose no siempre adivina bien hacia dónde ir con las flechas. Se lo decimos a mano.
    val passwordFocusRequester = remember { FocusRequester() }
    val eyeFocusRequester = remember { FocusRequester() }
    fun submit() { if (!busy && user.isNotBlank() && password.isNotEmpty()) onSubmit(user, password) }

    Column(modifier.background(colors.surface).padding(horizontal = 60.dp)) {
        // El teclado en pantalla de la TV ocupa la mitad de abajo: con imePadding + scroll el campo con
        // foco y el botón quedan visibles encima del teclado (en el HAKO Pro tapaba contraseña y Entrar).
        Column(
            Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(top = 56.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text.loginEyebrow.uppercase(),
                    style = HospitalityTheme.typography.label.copy(fontSize = 13.sp, letterSpacing = 2.5.sp),
                    color = colors.accent,
                )
                Text(text.loginTitle, style = HospitalityTheme.typography.title.copy(fontSize = 38.sp), color = colors.textPrimary)
                Text(text.loginHint, style = HospitalityTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = colors.textSecondary)
            }
            LoginField(
                label = text.user,
                value = user,
                onValueChange = { user = it },
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.focusRequester(first),
            )
            LoginField(
                label = text.password,
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .focusRequester(passwordFocusRequester)
                    .focusProperties { right = eyeFocusRequester },
                overlayEnd = {
                    EyeToggle(
                        visible = passwordVisible,
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !busy,
                        showLabel = text.showPassword,
                        hideLabel = text.hidePassword,
                        modifier = Modifier
                            .focusRequester(eyeFocusRequester)
                            .focusProperties { left = passwordFocusRequester },
                    )
                },
            )
            if (error != null) Text(error, style = HospitalityTheme.typography.body.copy(fontSize = 15.sp), color = colors.accent)
            FocusCard(
                onClick = ::submit,
                enabled = !busy,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Box(Modifier.matchParentSize().background(colors.accent))
                Text(
                    if (busy) text.connecting else text.enter,
                    style = HospitalityTheme.typography.label,
                    color = colors.background,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LockIcon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Text(text.loginNote, style = HospitalityTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 19.sp), color = colors.textSecondary)
            }
        }
        // Pie para el instalador: qué equipo es y qué versión de la app tiene.
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceBorder))
        Text(
            "${Build.MANUFACTURER} ${Build.MODEL} · v${appVersion()}",
            style = HospitalityTheme.typography.body.copy(fontSize = 12.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}

private val FieldShape = RoundedCornerShape(8.dp)

/** Campo con la etiqueta arriba (amarilla con foco, como el borde) y un halo para verlo desde lejos. */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** Se dibuja superpuesto sobre el borde derecho del campo (ej. el ojo de ver contraseña), como
     * hermano del OutlinedTextField dentro del mismo Box, no como su trailingIcon real. */
    overlayEnd: (@Composable () -> Unit)? = null,
) {
    val colors = HospitalityTheme.colors
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = HospitalityTheme.typography.body.copy(fontSize = 13.sp), color = if (focused) colors.focus else colors.textSecondary)
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                shape = FieldShape,
                textStyle = HospitalityTheme.typography.body.copy(fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, disabledTextColor = colors.textSecondary,
                    focusedContainerColor = colors.background, unfocusedContainerColor = colors.background, disabledContainerColor = colors.background,
                    focusedBorderColor = colors.focus, unfocusedBorderColor = colors.surfaceBorder, disabledBorderColor = colors.surfaceBorder,
                    cursorColor = colors.focus,
                ),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .border(4.dp, if (focused) colors.focus.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            )
            if (overlayEnd != null) {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { overlayEnd() }
            }
        }
    }
}

/**
 * Toggle de ver/ocultar contraseña: sin fondo ni borde (sería un recuadro de más superpuesto al
 * campo). El foco se nota con el ícono agrandándose un poco y cambiando de color.
 */
@Composable
private fun EyeToggle(
    visible: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    showLabel: String,
    hideLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = HospitalityTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.2f else 1f, label = "eyeToggleScale")
    Box(
        modifier = modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) EyeIcon else EyeOffIcon,
            contentDescription = if (visible) hideLabel else showLabel,
            tint = if (focused) colors.focus else colors.textSecondary,
            modifier = Modifier.scale(scale).size(20.dp),
        )
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
}

/** Candado de trazo, el mismo del diseño. */
private val LockIcon: ImageVector = ImageVector.Builder("lock", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 11f); lineTo(17f, 11f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(19f, 19f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(7f, 21f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(5f, 13f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
        moveTo(8f, 11f); lineTo(8f, 7f)
        arcToRelative(4f, 4f, 0f, false, true, 8f, 0f); lineTo(16f, 11f)
    }
}.build()

/** Ojo abierto: "ver contraseña". Mismo estilo de trazo que [LockIcon]. */
private val EyeIcon: ImageVector = ImageVector.Builder("eye", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(2f, 12f)
        quadTo(7f, 4.5f, 12f, 4.5f); quadTo(17f, 4.5f, 22f, 12f)
        quadTo(17f, 19.5f, 12f, 19.5f); quadTo(7f, 19.5f, 2f, 12f)
        close()
        moveTo(15f, 12f)
        arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
        arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        close()
    }
}.build()

/** Ojo tachado: "contraseña oculta". */
private val EyeOffIcon: ImageVector = ImageVector.Builder("eye_off", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(2f, 12f)
        quadTo(7f, 4.5f, 12f, 4.5f); quadTo(17f, 4.5f, 22f, 12f)
        quadTo(17f, 19.5f, 12f, 19.5f); quadTo(7f, 19.5f, 2f, 12f)
        close()
        moveTo(15f, 12f)
        arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
        arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        close()
        moveTo(3f, 20f); lineTo(21f, 4f)
    }
}.build()
