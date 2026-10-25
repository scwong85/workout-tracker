package com.workouttracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Rowing
import androidx.compose.material.icons.outlined.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workouttracker.domain.Category

val Category.icon: ImageVector
    get() = when (this) {
        Category.CHEST -> Icons.Outlined.FitnessCenter
        Category.BACK -> Icons.Outlined.Rowing
        Category.LEGS -> Icons.AutoMirrored.Outlined.DirectionsRun
        Category.CORE -> Icons.Outlined.CenterFocusStrong
        Category.ARMS -> Icons.Outlined.SportsMma
        Category.SHOULDERS -> Icons.Outlined.Accessibility
        Category.FULL_BODY -> Icons.Outlined.Bolt
    }

/** Distinct, mid-saturation hues that read on both dark and light surfaces. */
val Category.color: Color
    get() = when (this) {
        Category.CHEST -> Color(0xFF4F8DFD)
        Category.BACK -> Color(0xFF2BB6A3)
        Category.LEGS -> Color(0xFFF08C3A)
        Category.CORE -> Color(0xFFE25C8A)
        Category.ARMS -> Color(0xFF9B7BF0)
        Category.SHOULDERS -> Color(0xFF43A9E0)
        Category.FULL_BODY -> Color(0xFFE0B43A)
    }

@Composable
fun CategoryAvatar(category: Category, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .background(category.color.copy(alpha = 0.18f), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.label, tint = category.color, modifier = Modifier.size(size * 0.55f))
    }
}
