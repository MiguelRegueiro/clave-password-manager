@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.clave.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class AccentPaint(val container: Color, val text: Color)

@Composable fun accentPaint(accent: AccountAccent): AccentPaint =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        AccentPaint(Color(accent.darkContainer), Color(accent.darkText))
    else AccentPaint(Color(accent.lightContainer), Color(accent.lightText))

@Composable fun AccountTheme(accent: AccountAccent, content: @Composable () -> Unit) {
    val paint = accentPaint(accent)
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = paint.text, onPrimary = paint.container,
        primaryContainer = paint.container, onPrimaryContainer = paint.text), content = content)
}

@Composable fun ProfileBadge(name: String, accent: AccountAccent) {
    val paint = accentPaint(accent)
    Surface(color = paint.container, contentColor = paint.text, shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, paint.text.copy(alpha = 0.18f))) {
        Text(name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun AccountTile(title: String, accent: AccountAccent, large: Boolean = false) {
    val paint = accentPaint(accent)
    Surface(color = paint.container, contentColor = paint.text, shape = RoundedCornerShape(if (large) 20.dp else 15.dp),
        shadowElevation = 3.dp, border = BorderStroke(1.dp, paint.text.copy(alpha = 0.18f))) {
        Box(Modifier.size(if (large) 64.dp else 48.dp), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun ColorAction(accent: AccountAccent, description: String, enabled: Boolean = true, action: () -> Unit) {
    val paint = accentPaint(accent)
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Surface(shape = CircleShape, color = paint.container, border = BorderStroke(2.dp, paint.text)) {
            Box(Modifier.size(24.dp))
        }
    }
}

@Composable fun AccentPicker(title: String, selected: AccountAccent?, inheritLabel: String? = null,
                            dismiss: () -> Unit, choose: (AccountAccent?) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inheritLabel != null) OutlinedButton(onClick = { choose(null) }, modifier = Modifier.fillMaxWidth().semantics {
                this.selected = selected == null
            }) { Text(inheritLabel, fontWeight = if (selected == null) FontWeight.Bold else FontWeight.Normal) }
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountAccent.entries.forEach { accent ->
                    val paint = accentPaint(accent)
                    Surface(onClick = { choose(accent) }, color = paint.container, contentColor = paint.text,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (accent == selected) 3.dp else 1.dp, paint.text.copy(alpha = if (accent == selected) 1f else 0.25f)),
                        modifier = Modifier.semantics { this.selected = accent == selected; role = Role.RadioButton }) {
                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(accent.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Cerrar") } })
}
