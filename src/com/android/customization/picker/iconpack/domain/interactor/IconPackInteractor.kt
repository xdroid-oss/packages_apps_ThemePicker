package com.android.customization.picker.iconpack.domain.interactor

import com.android.customization.picker.iconpack.IconPackInfo
import com.android.customization.picker.iconpack.data.repository.IconPackRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class IconPackInteractor
@Inject
constructor(
    private val repository: IconPackRepository,
) {

    val installedIconPacks: List<IconPackInfo>
        get() = repository.installedIconPacks

    val selectedIconPack: Flow<String?> = repository.selectedIconPack

    suspend fun setIconPack(packageName: String) {
        repository.setIconPack(packageName)
    }
}
