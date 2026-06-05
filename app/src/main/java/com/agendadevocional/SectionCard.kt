package com.agendadevocional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(
    title: String,
    content: String,
    fontSizeMultiplier: Float = 1f,
    highlighted: Boolean = false
) {
    Column {
        if (title.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (highlighted) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 16.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }

        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (16 * fontSizeMultiplier).sp,
                lineHeight = (26 * fontSizeMultiplier).sp,
                fontFamily = if (highlighted) FontFamily.Serif else FontFamily.Default
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
    }
}
