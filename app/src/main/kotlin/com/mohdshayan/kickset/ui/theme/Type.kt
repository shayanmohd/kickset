package com.mohdshayan.kickset.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.kickset.R

/* Archivo for body and labels, Archivo Narrow SemiBold for readouts. Both bundled, SIL OFL 1.1. */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
)
val ArchivoNarrow = FontFamily(Font(R.font.archivonarrow_semibold, FontWeight.SemiBold))

private const val TNUM = "tnum"

/** The answer on a calculator: 40sp narrow readout with tabular digits. */
val ReadoutStyle = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = TNUM)

/** Labels on dimension lines inside the sketch. */
val DimensionStyle = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM)

/** Working lines under a result. */
val WorkingStyle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM)

val AppTypography = Typography(
    headlineLarge = ReadoutStyle,
    headlineMedium = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, fontFeatureSettings = TNUM),
    headlineSmall = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, fontFeatureSettings = TNUM),
    titleLarge = TextStyle(fontFamily = ArchivoNarrow, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    labelLarge = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    labelSmall = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
)
