package com.agendadevocional

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import br.com.bibliafalada.agendadevocional.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    val selectedLanguage = remember {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.getString("selected_language", "pt") ?: "pt"
    }

    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1500
        ),
        label = "Splash Alpha"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2500) // Tempo de exibição da splash
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val imageRes = if (selectedLanguage == "en") R.drawable.agenda_icon_en else R.drawable.agenda_icon
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "Logo de Abertura",
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim.value),
            contentScale = ContentScale.Fit
        )
    }
}
