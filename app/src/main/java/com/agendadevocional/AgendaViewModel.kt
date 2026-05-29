package com.agendadevocional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendadevocional.data.MensagensRepository
import com.agendadevocional.model.MensagemDia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AgendaState {
    object Loading : AgendaState()
    data class Success(val mensagens: List<MensagemDia>, val indexHoje: Int) : AgendaState()
    data class Error(val message: String) : AgendaState()
}

class AgendaViewModel(private val repository: MensagensRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AgendaState>(AgendaState.Loading)
    val uiState: StateFlow<AgendaState> = _uiState.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            try {
                val mensagens = repository.carregarMensagens()
                val indexHoje = repository.indiceHoje(mensagens)
                _uiState.value = AgendaState.Success(mensagens, indexHoje)
            } catch (e: Exception) {
                _uiState.value = AgendaState.Error(e.message ?: "Erro desconhecido ao carregar mensagens")
            }
        }
    }

    fun syncData(url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.syncMensagens(url)
            if (success) {
                carregarDados()
            }
            onResult(success)
        }
    }

    fun changeLanguage(language: String, url: String?, onResult: (Boolean) -> Unit) {
        _uiState.value = AgendaState.Loading
        viewModelScope.launch {
            val success = repository.setLanguageAndLoad(language, url)
            carregarDados()
            onResult(success)
        }
    }

    fun toggleFavorite(mensagem: MensagemDia) {
        viewModelScope.launch {
            repository.toggleFavorite(mensagem)
            carregarDados()
        }
    }

    fun salvarAnotacao(mensagem: MensagemDia, anotacao: String?) {
        viewModelScope.launch {
            repository.salvarAnotacao(mensagem.data, anotacao)
            carregarDados()
        }
    }

    fun salvarAudioPath(mensagem: MensagemDia, audioPath: String?) {
        viewModelScope.launch {
            repository.salvarAudioPath(mensagem.data, audioPath)
            carregarDados()
        }
    }
}
