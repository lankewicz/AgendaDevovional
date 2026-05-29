package com.agendadevocional

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Função auxiliar para carregar imagens da pasta assets
fun loadBitmapFromAssets(context: Context, fileName: String): ImageBitmap? {
    return try {
        context.assets.open(fileName).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun LanguageSelectionScreen(
    isDownloading: Boolean,
    onLanguageSelected: (String) -> Unit
) {
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LocaleManager.applicationContext = context.applicationContext
    var activeBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Carregar bitmap correspondente de forma assíncrona para evitar congelamento e economizar memória
    LaunchedEffect(selectedLanguage) {
        val fileName = when (selectedLanguage) {
            "pt" -> "portugues.webp"
            "en" -> "ingles.webp"
            "es" -> "espanhol.webp"
            else -> "blank.webp"
        }
        withContext(Dispatchers.IO) {
            val bitmap = loadBitmapFromAssets(context, fileName)
            if (bitmap != null) {
                activeBitmap = bitmap
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Brilho sutil de fundo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Cabeçalho
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Selecione o Idioma",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Choose Language / Seleccione el Idioma",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Animar a cor da borda do mapa conforme o idioma selecionado
            val selectedBorderColor by animateColorAsState(
                targetValue = when (selectedLanguage) {
                    "pt" -> Color(0xFF00C853)
                    "en" -> Color(0xFF0288D1)
                    "es" -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
                animationSpec = tween(durationMillis = 400),
                label = "MapBorderColor"
            )

            // 2. Mapa Múndi Devocional (Principal destaque da tela, fundo branco para mesclar)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.8f) // Aumentamos o peso para o mapa ocupar mais espaço
                    .padding(vertical = 12.dp)
                    .border(
                        width = if (selectedLanguage != null) 2.dp else 1.dp,
                        color = selectedBorderColor,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeBitmap != null) {
                        Image(
                            bitmap = activeBitmap!!,
                            contentDescription = "Mapa Múndi Devocional",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // Mantém a proporção exata sem esticar
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // 3. Opções de Idioma e Botão na Parte Inferior
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageOptionCardMini(
                        flag = "🇧🇷",
                        name = "Português",
                        isSelected = selectedLanguage == "pt",
                        selectedColor = Color(0xFF00E676),
                        onClick = { selectedLanguage = "pt" },
                        modifier = Modifier.weight(1f)
                    )
                    LanguageOptionCardMini(
                        flag = "🇺🇸",
                        name = "English",
                        isSelected = selectedLanguage == "en",
                        selectedColor = Color(0xFF29B6F6),
                        onClick = { selectedLanguage = "en" },
                        modifier = Modifier.weight(1f)
                    )
                    LanguageOptionCardMini(
                        flag = "🇪🇸",
                        name = "Español",
                        isSelected = selectedLanguage == "es",
                        selectedColor = Color(0xFFFF9100),
                        onClick = { selectedLanguage = "es" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Botão Confirmar animado ou Spinner
                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = when (selectedLanguage) {
                                "pt" -> Color(0xFF00E676)
                                "en" -> Color(0xFF29B6F6)
                                "es" -> Color(0xFFFF9100)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = LocaleManager.getLocalizedString(selectedLanguage ?: "pt", "baixando_conteudo"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = selectedLanguage != null,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            if (selectedLanguage == "en" || selectedLanguage == "es") {
                                Text(
                                    text = LocaleManager.getLocalizedString(selectedLanguage!!, "internet_necessaria"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = { selectedLanguage?.let { onLanguageSelected(it) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (selectedLanguage) {
                                        "pt" -> Color(0xFF00C853)
                                        "en" -> Color(0xFF0288D1)
                                        "es" -> Color(0xFFE65100)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            ) {
                                Text(
                                    text = LocaleManager.getLocalizedString(selectedLanguage!!, "confirmar").uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageOptionCardMini(
    flag: String,
    name: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                selectedColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = flag,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
