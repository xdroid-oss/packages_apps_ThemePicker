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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val overridingIconPack = MutableStateFlow<String?>(null)
    private var savedIconPack: String? =
        runBlocking { interactor.selectedIconPack.first() ?: "" }

    val previewingIconPack: StateFlow<String?> =
        combine(interactor.selectedIconPack, overridingIconPack) { current, override ->
            override ?: current
        }
        .stateIn(viewModelScope, started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), initialValue = null)

    val packOptions: Flow<List<OptionItemViewModel2<IconPackInfo>>> =
        combine(previewingIconPack, flowOf(interactor.installedIconPacks)) { previewingPkg, packs ->
            packs.map { pack ->
                val isPreviewed = pack.packageName == previewingPkg
                OptionItemViewModel2(
                    key = MutableStateFlow(pack.packageName.ifEmpty { "system" }),
                    payload = pack,
                    text = Text.Loaded(pack.name),
                    isSelected = MutableStateFlow(isPreviewed),
                    skipForegroundColorBinding = true,
                    onClicked = if (isPreviewed) {
                        MutableStateFlow(null)
                    } else {
                        MutableStateFlow({
                            overridingIconPack.value = pack.packageName
                            viewModelScope.launch {
                                interactor.setIconPack(pack.packageName)
                            }
                        } as (() -> Unit)?)
                    },
                )
            }
        }

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(interactor.selectedIconPack, overridingIconPack) { current, override ->
            if (override != null && override != current) {
                suspend {
                    savedIconPack = override
                    overridingIconPack.value = null
                }
            } else null
        }

    val summary: Flow<Text> =
        previewingIconPack.map { pkg ->
            val name = if (pkg.isNullOrEmpty()) {
                appContext.getString(R.string.icon_pack_system_default)
            } else {
                interactor.installedIconPacks
                    .firstOrNull { it.packageName == pkg }?.name ?: pkg
            }
            Text.Loaded(name)
        }

    val entryIcon: Flow<android.graphics.drawable.Drawable?> =
        previewingIconPack.map { pkg ->
            if (pkg.isNullOrEmpty()) {
                appContext.packageManager.getApplicationIcon(appContext.packageName)
            } else {
                interactor.installedIconPacks
                    .firstOrNull { it.packageName == pkg }?.icon
            }
        }

    fun resetPreview() {
        val override = overridingIconPack.value
        overridingIconPack.value = null
        if (override != null && override != savedIconPack) {
            savedIconPack?.let { saved ->
                viewModelScope.launch {
                    interactor.setIconPack(saved.ifEmpty { "" })
                }
            }
        }
    }
}
