package com.mohdshayan.kickset.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mohdshayan.kickset.ui.theme.LocalReducedMotion
import com.mohdshayan.kickset.ui.theme.RadiusMd
import com.mohdshayan.kickset.ui.theme.RadiusSm
import com.mohdshayan.kickset.ui.theme.WorkingStyle

private val fractionRe = Regex("""(\d+)/(\d+)""")

/** Fractions drawn at 70 percent with the numerator lifted, never as Unicode vulgar fractions. */
fun fractionString(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in fractionRe.findAll(text)) {
        append(text.substring(last, m.range.first))
        pushStyle(SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.35f)))
        append(m.groupValues[1])
        pop()
        pushStyle(SpanStyle(fontSize = 0.7.em))
        append("/")
        append(m.groupValues[2])
        pop()
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun FractionText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(fractionString(text), style = style, color = color, modifier = modifier.semantics { contentDescription = text.replace("/", " over ") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KicksetTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, RoundedCornerShape(RadiusSm)),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o), style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(RadiusSm),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(true, o == selected, borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.5.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}

/** A length input with its error below, or the parsed value echoed in the other unit. */
@Composable
fun LengthField(
    label: String, value: String, onValueChange: (String) -> Unit,
    error: String?, echo: String?, modifier: Modifier = Modifier, decimalOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(24)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                echo != null -> FractionText(echo, MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(" ")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimalOnly) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(RadiusSm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    )
}

/** The one card on a calculator screen. */
@Composable
fun SketchPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RadiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

/** Headline answer: crossfades in 150 ms when it changes, snaps under reduced motion. */
@Composable
fun Readout(label: String, primary: String, secondary: String?, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(
            targetState = primary,
            transitionSpec = { if (reduced) fadeIn(snap()) togetherWith fadeOut(snap()) else fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "readout",
        ) { v -> FractionText(v, MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary) }
        if (secondary != null) FractionText(secondary, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SmallReadout(label: String, primary: String, secondary: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FractionText(primary, MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (secondary != null) FractionText(secondary, MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The arithmetic under every answer. */
@Composable
fun WorkingLines(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { FractionText(it, WorkingStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(RadiusMd), modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(RadiusMd), modifier = modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

/** A message in the result area, for an input the maths cannot use. */
@Composable
fun ResultMessage(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Centred empty state with one action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, art: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (art != null) { art(); Spacer(Modifier.height(16.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        if (actionLabel != null && onAction != null) { Spacer(Modifier.height(20.dp)); PrimaryButton(actionLabel, onAction) }
    }
}

/**
 * The first frame of a calculator, in the shape of one: the mode switch, a field, a chip row and the
 * sketch panel. It shows only while settings are being read from disk, and never shimmers.
 */
@Composable
fun CalculatorSkeleton(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = "Loading" }) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(42.dp).background(c, RoundedCornerShape(RadiusSm)))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(56.dp).background(c, RoundedCornerShape(RadiusSm)))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Box(Modifier.size(64.dp, 40.dp).background(c, RoundedCornerShape(RadiusSm))) }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(260.dp).background(c, RoundedCornerShape(RadiusMd)))
    }
}

/** Static loading rows in the shape of a list; no shimmer. */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = "Loading" }) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.width(160.dp).height(16.dp).background(c, RoundedCornerShape(RadiusSm)))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(96.dp).height(12.dp).background(c, RoundedCornerShape(RadiusSm)))
                }
                Box(Modifier.size(48.dp, 20.dp).background(c, RoundedCornerShape(RadiusSm)))
            }
        }
    }
}
