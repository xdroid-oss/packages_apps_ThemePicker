package com.android.customization.picker.iconpack.data.repository

import com.android.customization.picker.iconpack.IconPackInfo
import kotlinx.coroutines.flow.Flow

interface IconPackRepository {

    val installedIconPacks: List<IconPackInfo>

    val selectedIconPack: Flow<String?>

    suspend fun setIconPack(packageName: String)
}
