package com.eddigits.eddido.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.eddigits.eddido.model.Kind

/** Each tab has its own icon and accent colour; the tab pill fades between them. */
val Kind.icon: ImageVector
    get() = when (this) {
        Kind.TASK -> Icons.Outlined.CheckCircle
        Kind.TIMER -> Icons.Outlined.HourglassTop
        Kind.STOPWATCH -> Icons.Outlined.Timer
        Kind.FOCUS -> Icons.Outlined.SelfImprovement
        Kind.HABIT -> Icons.Outlined.LocalFireDepartment
        Kind.LIST -> Icons.Outlined.ShoppingCart
        Kind.COUNTDOWN -> Icons.Outlined.EventNote
        Kind.NOTE -> Icons.Outlined.StickyNote2
    }

val Kind.accent: Color
    get() = when (this) {
        Kind.TASK -> Color(0xFFDC4C3E)
        Kind.TIMER -> Color(0xFFF59E0B)
        Kind.STOPWATCH -> Color(0xFF06B6D4)
        Kind.FOCUS -> Color(0xFF8B5CF6)
        Kind.HABIT -> Color(0xFFF97316)
        Kind.LIST -> Color(0xFF10B981)
        Kind.COUNTDOWN -> Color(0xFF3B82F6)
        Kind.NOTE -> Color(0xFFEAB308)
    }
