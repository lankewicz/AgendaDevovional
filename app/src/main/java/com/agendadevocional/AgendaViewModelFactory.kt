package com.agendadevocional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agendadevocional.data.MensagensRepository

class AgendaViewModelFactory(private val repository: MensagensRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgendaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AgendaViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
