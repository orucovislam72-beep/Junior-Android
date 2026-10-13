package com.riglineaimstriker.junior.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единственный способ показать иконку Junior. Цвет задаётся [tint] (по умолчанию LocalContentColor — наследуется от кнопок, onPrimary, error); ресурс сам по себе
 * нейтрален (@color/junior_icon_stroke) и перекрашивается здесь.
 * contentDescription = null допустим, только если родитель уже даёт полное доступное имя.
 */
@Composable
fun JuniorIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
