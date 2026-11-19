package com.drbaysinger.audiobc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as M3Text

val CardShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(28.dp)

fun Modifier.card(selected: Boolean = false, shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier = this
    .clip(shape)
    .background(if (selected) AccentTint else Card)
    .border(1.dp, if (selected) Accent else Line, shape)

fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
    )
}

@Composable
fun T(
    text: String,
    color: Color = Text,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) = M3Text(
    text,
    color = color,
    fontSize = size,
    fontWeight = weight,
    maxLines = maxLines,
    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    modifier = modifier,
)

/** Pill button ≥ 56 dp tall: filled (amber / custom) or outlined. */
@Composable
fun Pill(
    modifier: Modifier = Modifier,
    filled: Boolean,
    color: Color = Accent,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(PillShape)
            .then(if (filled) Modifier.background(if (enabled) color else color.copy(alpha = 0.4f)) else Modifier.background(Card).border(1.dp, Line, PillShape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Two-way segmented tab: the selected one gets a thin amber outline (its label is coloured by the caller). */
@Composable
fun TabCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(Card)
            .border(1.dp, if (selected) Accent else Line, CardShape)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun NumberInput(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.SemiBold,
    placeholder: String = "",
    placeholderColor: Color = Grey,
    placeholderContent: (@Composable () -> Unit)? = null,
    /** Shown right after the digits once a value is typed (e.g. the unit). */
    suffix: (@Composable () -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(color = Text, fontSize = size, fontWeight = weight)
    val textWidth = with(density) { measurer.measure(value, style).size.width.toDp() } + 3.dp
    BasicTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }) },
        singleLine = true,
        textStyle = TextStyle(color = Text, fontSize = size, fontWeight = weight),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    if (placeholderContent != null) placeholderContent()
                    else M3Text(placeholder, color = placeholderColor, fontSize = size, fontWeight = weight, maxLines = 1)
                }
                if (suffix == null) {
                    inner()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(textWidth)) { inner() }
                        if (value.isNotEmpty()) suffix()
                    }
                }
            }
        },
    )
}

/** Labeled numeric field used in the expanded Conditions card. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    dot: Color? = null,
    compact: Boolean = false,
) {
    Column(
        modifier
            .card(shape = RoundedCornerShape(10.dp))
            .heightIn(min = if (compact) 0.dp else 56.dp)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, Muted, 12.sp, modifier = Modifier.weight(1f))
            if (dot != null) Dot(dot)
        }
        NumberInput(value, onValue, Modifier.fillMaxWidth().padding(top = 2.dp), 16.sp, placeholder = placeholder)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Gap(w: Dp) = Spacer(Modifier.width(w))
