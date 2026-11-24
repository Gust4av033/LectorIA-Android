package com.lectoria.app.presentation.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lectoria.app.R
import com.lectoria.app.core.designsystem.Midnight

@Composable
internal fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(15.dp), color = Midnight.Container.copy(alpha = 0.45f)) {
            Icon(
                painterResource(R.drawable.ic_library),
                contentDescription = null,
                tint = Midnight.Highlight,
                modifier = Modifier.padding(12.dp).size(23.dp),
            )
        }
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = Midnight.MutedText,
            )
        }
    }
}

@Composable
fun PendingScreen(
    @StringRes title: Int,
    @StringRes headline: Int,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    tag: String,
) {
    LazyColumn(
        modifier = Modifier.testTag(tag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { BrandHeader() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow(title)
                Text(stringResource(headline), style = MaterialTheme.typography.headlineLarge)
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    IconBadge(icon)
                    StatusLabel(R.string.coming_soon)
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Midnight.MutedText,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlassCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Midnight.Surface,
        border = BorderStroke(1.dp, Midnight.Highlight.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier.padding(22.dp),
        ) { content() }
    }
}

@Composable
internal fun Eyebrow(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelSmall, color = Midnight.Highlight)
}

@Composable
internal fun StatusLabel(@StringRes text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(Midnight.Highlight, CircleShape))
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = Midnight.Highlight)
    }
}

@Composable
internal fun IconBadge(@DrawableRes icon: Int) {
    Surface(shape = RoundedCornerShape(20.dp), color = Midnight.Container.copy(alpha = 0.25f)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(19.dp).size(34.dp),
            tint = Midnight.Highlight,
        )
    }
}
