package com.mohdshayan.kickset.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale:
 *   RadiusSm 4dp  chips, fields
 *   RadiusMd 8dp  buttons, the sketch panel
 *   RadiusLg 16dp bottom sheet top corners
 */
val RadiusSm = 4.dp
val RadiusMd = 8.dp
val RadiusLg = 16.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusMd),
    extraLarge = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg),
)
