package com.riglineaimstriker.junior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Аватар Junior: буква J, строго чёрно-белая. Тёмная тема — белый квадрат, светлая — чёрный. */
@Composable
fun JuniorAvatar(size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(if (dark) Color.White else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "J",
            color = if (dark) Color.Black else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.6f).sp,
        )
    }
}
