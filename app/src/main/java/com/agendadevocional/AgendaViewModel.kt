package com.agendadevocional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendadevocional.data.MensagensRepository
import com.agendadevocional.model.MensagemDia
import com.agendadevocional.model.TimelineNota
import com.agendadevocional.model.DataLeitura
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AgendaState {
    object Loading : AgendaState()
    data class Success(val mensagens: List<MensagemDia>, val indexHoje: Int) : AgendaState()
    data class Error(val message: String) : AgendaState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(private val repository: MensagensRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()

    val uiState: StateFlow<AgendaState> = combine(_searchQuery, _showOnlyFavorites) { query, onlyFavs ->
        Pair(query, onlyFavs)
    }.flatMapLatest { (query, onlyFavs) ->
        flow {
            // Garante o carregamento inicial/população do banco de dados na primeira execução
            val list = repository.carregarMensagens()
            emit(list)
        }.flatMapLatest {
            repository.getFilteredMensagens(query, onlyFavs)
        }.map { list ->
            val indexHoje = repository.indiceHoje(list)
            AgendaState.Success(list, indexHoje) as AgendaState
        }.catch { e ->
            emit(AgendaState.Error(e.message ?: "Erro ao carregar mensagens"))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AgendaState.Loading
    )

    init {
        viewModelScope.launch {
            repository.limparAudiosOrfaos()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowOnlyFavorites(onlyFavorites: Boolean) {
        _showOnlyFavorites.value = onlyFavorites
    }

    fun syncData(url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.syncMensagens(url)
            onResult(success)
        }
    }

    fun changeLanguage(language: String, url: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.setLanguageAndLoad(language, url)
            onResult(success)
        }
    }

    fun toggleFavorite(mensagem: MensagemDia) {
        viewModelScope.launch {
            repository.toggleFavorite(mensagem)
        }
    }

    fun salvarAnotacao(mensagem: MensagemDia, anotacao: String?) {
        viewModelScope.launch {
            repository.salvarAnotacao(mensagem.data, anotacao)
        }
    }

    fun salvarAudioPath(mensagem: MensagemDia, audioPath: String?) {
        viewModelScope.launch {
            repository.salvarAudioPath(mensagem.data, audioPath)
        }
    }

    val allTimelineNotas: StateFlow<List<TimelineNota>> = repository.getAllTimelineNotasFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getNotasDoDia(data: String): Flow<List<TimelineNota>> {
        return repository.getNotasDoDia(data)
    }

    fun salvarTimelineNota(data: String, hora: Int, texto: String) {
        viewModelScope.launch {
            repository.salvarTimelineNota(data, hora, texto)
        }
    }

    fun excluirTimelineNota(data: String, hora: Int) {
        viewModelScope.launch {
            repository.excluirTimelineNota(data, hora)
        }
    }

    val allLeituras: StateFlow<List<DataLeitura>> = repository.getAllLeiturasFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun marcarComoLido(dataIso: String) {
        viewModelScope.launch {
            repository.marcarComoLido(dataIso)
        }
    }

    fun resetAllData(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                repository.resetAllData()
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}

