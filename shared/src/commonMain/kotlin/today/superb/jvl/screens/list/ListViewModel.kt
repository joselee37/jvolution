package today.superb.jvl.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import today.superb.jvl.data.MuseumObject
import today.superb.jvl.data.MuseumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ListViewModel(museumRepository: MuseumRepository) : ViewModel() {
    val objects: StateFlow<List<MuseumObject>> =
        museumRepository.getObjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
