package com.vxplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.vxplayer.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VXSpace {
    val xs = 4.dp; val small = 8.dp; val medium = 12.dp; val large = 16.dp; val xl = 24.dp
}
val VXShapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))
object VXMotion {
    const val Quick = 120
    const val Standard = 200
    const val Emphasis = 320
    val Enter = LinearOutSlowInEasing
    val Move = FastOutSlowInEasing
}
object VXSize {
    val touch = 48.dp
    val icon = 24.dp
    val card = 20.dp
    val sheet = 28.dp
    val border = 1.dp
}
object VXBrand {
    val Blue = androidx.compose.ui.graphics.Color(0xFF79B8FF)
    val Violet = androidx.compose.ui.graphics.Color(0xFFBFA3FF)
    val gradient = Brush.linearGradient(listOf(Blue, Violet))
}
private val VXFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)
private val BaseTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val VXTypography = BaseTypography.let { t ->
    fun TextStyle.brand() = copy(fontFamily = VXFont)
    Typography(
        displayLarge=t.displayLarge.brand(), displayMedium=t.displayMedium.brand(), displaySmall=t.displaySmall.brand(),
        headlineLarge=t.headlineLarge.brand().copy(fontWeight=FontWeight.Bold), headlineMedium=t.headlineMedium.brand().copy(fontWeight=FontWeight.Bold), headlineSmall=t.headlineSmall.brand(),
        titleLarge=t.titleLarge.brand(), titleMedium=t.titleMedium.brand(), titleSmall=t.titleSmall.brand(),
        bodyLarge=t.bodyLarge.brand(), bodyMedium=t.bodyMedium.brand(), bodySmall=t.bodySmall.brand(),
        labelLarge=t.labelLarge.brand(), labelMedium=t.labelMedium.brand().copy(fontFeatureSettings="tnum"), labelSmall=t.labelSmall.brand().copy(fontFeatureSettings="tnum")
    )
}

val LocalVXHaptics = staticCompositionLocalOf { true }

@Composable
fun VXIconButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val feedback = LocalVXHaptics.current
    IconButton({ if(feedback) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick() }, enabled = enabled, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
fun VXCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, animationSpec = tween(VXMotion.Quick, easing = VXMotion.Move), label = "card press")
    Card(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, shape = VXShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(VXSize.border, MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), interactionSource = interactions, content = content)
}
