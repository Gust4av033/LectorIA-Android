package com.lectoria.app.core.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Normal-size action text meets AA using the existing midnight palette. */
@Composable
fun LectorButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick, modifier, enabled, shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Midnight.Container, contentColor = Midnight.Text),
        content = content)
}

@Composable
fun LectorTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick, modifier, enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = Midnight.Highlight), content = content)
}
