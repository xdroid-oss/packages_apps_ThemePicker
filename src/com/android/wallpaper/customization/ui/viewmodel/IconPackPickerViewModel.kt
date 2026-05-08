package com.android.wallpaper.customization.ui.viewmodel

import android.content.Context
import com.android.customization.picker.iconpack.IconPackInfo
import com.android.customization.picker.iconpack.domain.interactor.IconPackInteractor
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class IconPackPickerViewModel
@AssistedInject
constructor(
    @ApplicationContext private val appContext: Context,
    private val interactor: IconPackInteractor,
    @Assisted private val viewModelScope: CoroutineScope,
) {

    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): IconPackPickerViewModel
    }

    private var savedIconPack: String? =
        runBlocking { interactor.selectedIconPack.first() ?: "" }

    val packOptions: Flow<List<OptionItemViewModel2<IconPackInfo>>> =
        combine(interactor.selectedIconPack, flowOf(interactor.installedIconPacks)) { current, packs ->
            packs.map { pack ->
                val isSelected = pack.packageName == current
                OptionItemViewModel2(
                    key = MutableStateFlow(pack.packageName.ifEmpty { "system" }),
                    payload = pack,
                    text = Text.Loaded(pack.name),
                    isSelected = MutableStateFlow(isSelected),
                    skipForegroundColorBinding = true,
                    onClicked = if (isSelected) {
                        MutableStateFlow(null)
                    } else {
                        MutableStateFlow({
                            viewModelScope.launch {
                                interactor.setIconPack(pack.packageName)
                            }
                        } as (() -> Unit)?)
                    },
                )
            }
        }

    val onApply: Flow<(suspend () -> Unit)?> =
        MutableStateFlow(null)

    val summary: Flow<Text> =
        interactor.selectedIconPack.map { pkg ->
            val name = if (pkg.isNullOrEmpty()) {
                appContext.getString(R.string.icon_pack_system_default)
            } else {
                interactor.installedIconPacks
                    .firstOrNull { it.packageName == pkg }?.name ?: pkg
            }
            Text.Loaded(name)
        }

    val entryIcon: Flow<android.graphics.drawable.Drawable?> =
        interactor.selectedIconPack.map { pkg ->
            if (pkg.isNullOrEmpty()) {
                appContext.packageManager.getApplicationIcon(appContext.packageName)
            } else {
                interactor.installedIconPacks
                    .firstOrNull { it.packageName == pkg }?.icon
            }
        }

    fun resetPreview() {
        savedIconPack?.let { saved ->
            viewModelScope.launch {
                interactor.setIconPack(saved.ifEmpty { "" })
            }
        }
    }
}
