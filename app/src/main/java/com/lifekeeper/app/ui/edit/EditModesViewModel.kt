package com.lifekeeper.app.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifekeeper.app.LifekeeperApp
import com.lifekeeper.app.data.model.Mode
import com.lifekeeper.app.data.repository.ModeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditModesViewModel(
    private val modeRepo: ModeRepository,
) : ViewModel() {

    val modes: StateFlow<List<Mode>> = modeRepo.modes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun addMode(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch { modeRepo.addMode(name.trim(), colorHex) }
    }

    fun updateMode(mode: Mode) {
        viewModelScope.launch { modeRepo.updateMode(mode) }
    }

    fun deleteMode(mode: Mode) {
        viewModelScope.launch { modeRepo.deleteMode(mode) }
    }

    fun reorderModes(orderedIds: List<Long>) {
        viewModelScope.launch { modeRepo.reorderModes(orderedIds) }
    }

    companion object {
        fun factory(app: LifekeeperApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditModesViewModel(app.modeRepository) }
        }
    }
}
