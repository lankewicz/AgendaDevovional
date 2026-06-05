package com.agendadevocional

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agendadevocional.model.MensagemDia
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgendaComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAnotacoesSectionRendersAndTabsSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioRecorder = AndroidAudioRecorder(context)
        val audioPlayer = AndroidAudioPlayer(context)

        val dummyMensagem = MensagemDia(
            data = "03 de Junho de 2026",
            versiculo = "O Senhor é o meu pastor",
            referencia = "Salmo 23:1",
            contexto = "Contexto de teste",
            significado = "Significado de teste",
            mensagem = "Mensagem de teste"
        )

        composeTestRule.setContent {
            LocaleManager.applicationContext = context.applicationContext
            AnotacoesSection(
                mensagemDia = dummyMensagem,
                onSaveAnotacao = { _, _ -> },
                onSaveAudioPath = { _, _ -> },
                audioRecorder = audioRecorder,
                audioPlayer = audioPlayer,
                fontSizeMultiplier = 1.0f,
                selectedLanguage = "pt"
            )
        }

        // Verify the tabs are rendered
        composeTestRule.onNodeWithText("Texto / Ditado").assertExists()
        composeTestRule.onNodeWithText("Mensagem de Voz").assertExists()

        // Click on the voice message tab
        composeTestRule.onNodeWithText("Mensagem de Voz").performClick()
        
        // After switching, the recording hint should exist
        val expectedHint = "O que Deus falou ao seu coração hoje?\n(Toque no microfone para gravar)"
        composeTestRule.onNodeWithText(expectedHint).assertExists()
    }
}
