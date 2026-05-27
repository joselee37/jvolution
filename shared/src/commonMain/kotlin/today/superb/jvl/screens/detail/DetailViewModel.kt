package today.superb.jvl.screens.detail

import androidx.lifecycle.ViewModel
import today.superb.jvl.data.MuseumObject
import today.superb.jvl.data.MuseumRepository
import kotlinx.coroutines.flow.Flow

class DetailViewModel(private val museumRepository: MuseumRepository) : ViewModel() {
    fun getObject(objectId: Int): Flow<MuseumObject?> =
        museumRepository.getObjectById(objectId)
}
