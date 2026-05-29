package com.agendadevocional

import com.agendadevocional.model.MensagemDia
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnotacoesPreviewCard(
    mensagemDia: MensagemDia,
    onClick: () -> Unit,
    fontSizeMultiplier: Float,
    selectedLanguage: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TextSnippet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = getLocalizedString(selectedLanguage, "anotacoes").uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val hasNotes = !mensagemDia.anotacao.isNullOrBlank()
                val hasAudio = !mensagemDia.audioPath.isNullOrBlank()
                
                Column {
                    if (hasNotes) {
                        Text(
                            text = mensagemDia.anotacao ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (hasAudio) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getLocalizedString(selectedLanguage, "tab_mensagem_voz"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                              )
                        }
                    } else {
                        Text(
                            text = getLocalizedString(selectedLanguage, "hint_escreva"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * fontSizeMultiplier).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
