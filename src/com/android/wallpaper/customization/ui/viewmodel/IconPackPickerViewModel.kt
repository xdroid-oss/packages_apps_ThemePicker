package com.android.wallpaper.customization.ui.viewmodel

import android.content.Context
import com.android.customization.picker.iconpack.IconPackInfo
import com.android.customization.picker.iconpack.domain.interactor.IconPackInteractor
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                            viewModelScope.launch {
                                interactor.setIconPack(pack.packageName)
                            }
                        } as (() -> Unit)?)
                    },
                )
            }
        }

    val onApply: Flow<(suspend () -> Unit)?> =
        overridingIconPack.map { null }
}
