/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wallpaper.picker.common.preview.ui.binder

import android.os.Bundle
import android.os.Message
import android.os.RemoteException
import android.util.Log
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.model.grid.DefaultShapeGridManager.Companion.COL_GRID_NAME
import com.android.customization.model.grid.DefaultShapeGridManager.Companion.COL_SHAPE_KEY
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.color.data.util.MaterialColorsGenerator
import com.android.systemui.shared.keyguard.shared.model.KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END
import com.android.systemui.shared.keyguard.shared.model.KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_HIDE_SMART_SPACE
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_INITIALLY_SELECTED_SLOT_ID
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_QUICK_AFFORDANCE_ID
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_SLOT_ID
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.MESSAGE_ID_DEFAULT_PREVIEW
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.MESSAGE_ID_HIDE_SMART_SPACE
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.MESSAGE_ID_PREVIEW_QUICK_AFFORDANCE_SELECTED
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.MESSAGE_ID_SLOT_SELECTED
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.MESSAGE_ID_START_CUSTOMIZING_QUICK_AFFORDANCES
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.model.Screen
import com.android.wallpaper.picker.common.preview.ui.binder.WorkspaceCallbackBinder.Companion.sendMessage
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class ThemePickerWorkspaceCallbackBinder
@Inject
constructor(
    private val defaultWorkspaceCallbackBinder: DefaultWorkspaceCallbackBinder,
    private val materialColorsGenerator: MaterialColorsGenerator,
) : WorkspaceCallbackBinder {

    private var lockScreenJob: Job? = null
    private var homeScreenJob: Job? = null

    override fun bind(
        workspaceCallback: Message,
        viewModel: CustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        screen: Screen,
        clockViewFactory: ClockViewFactory,
        lifecycleOwner: LifecycleOwner,
    ) {
        defaultWorkspaceCallbackBinder.bind(
            workspaceCallback = workspaceCallback,
            viewModel = viewModel,
            colorUpdateViewModel = colorUpdateViewModel,
            screen = screen,
            clockViewFactory = clockViewFactory,
            lifecycleOwner = lifecycleOwner,
        )

        if (viewModel !is ThemePickerCustomizationOptionsViewModel) {
            throw IllegalArgumentException(
                "viewModel $viewModel is not a ThemePickerCustomizationOptionsViewModel."
            )
        }

        when (screen) {
            Screen.LOCK_SCREEN -> {
                lockScreenJob?.cancel()
                lockScreenJob =
                    lifecycleOwner.lifecycleScope.launch {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                            launch {
                                viewModel.selectedOption.collect {
                                    when (it) {
                                        ThemePickerLockCustomizationOption.SHORTCUTS ->
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_START_CUSTOMIZING_QUICK_AFFORDANCES,
                                                Bundle().apply {
                                                    putString(
                                                        KEY_INITIALLY_SELECTED_SLOT_ID,
                                                        SLOT_ID_BOTTOM_START,
                                                    )
                                                },
                                            )
                                        else ->
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_DEFAULT_PREVIEW,
                                                Bundle.EMPTY,
                                            )
                                    }
                                }
                            }

                            launch {
                                viewModel.keyguardQuickAffordancePickerViewModel2.selectedSlotId
                                    .collect {
                                        safeSendMessage(
                                            workspaceCallback,
                                            MESSAGE_ID_SLOT_SELECTED,
                                            Bundle().apply { putString(KEY_SLOT_ID, it) },
                                        )
                                    }
                            }

                            launch {
                                viewModel.keyguardQuickAffordancePickerViewModel2
                                    .previewingQuickAffordances
                                    .collect {
                                        it[SLOT_ID_BOTTOM_START]?.let {
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_PREVIEW_QUICK_AFFORDANCE_SELECTED,
                                                Bundle().apply {
                                                    putString(KEY_SLOT_ID, SLOT_ID_BOTTOM_START)
                                                    putString(KEY_QUICK_AFFORDANCE_ID, it)
                                                },
                                            )
                                        }
                                        it[SLOT_ID_BOTTOM_END]?.let {
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_PREVIEW_QUICK_AFFORDANCE_SELECTED,
                                                Bundle().apply {
                                                    putString(KEY_SLOT_ID, SLOT_ID_BOTTOM_END)
                                                    putString(KEY_QUICK_AFFORDANCE_ID, it)
                                                },
                                            )
                                        }
                                    }
                            }

                            launch {
                                viewModel.clockPickerViewModel.showKeyguardPreviewRendererSmartspace
                                    .collect {
                                        safeSendMessage(
                                            workspaceCallback,
                                            MESSAGE_ID_HIDE_SMART_SPACE,
                                            Bundle().apply { putBoolean(KEY_HIDE_SMART_SPACE, !it) },
                                        )
                                    }
                            }
                        }
                    }
            }
            Screen.HOME_SCREEN -> {
                homeScreenJob?.cancel()
                homeScreenJob =
                    lifecycleOwner.lifecycleScope.launch {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                            launch {
                                viewModel.appIconPickerViewModel.previewingShapeKey.collect {
                                    safeSendMessage(
                                        workspaceCallback,
                                        MESSAGE_ID_UPDATE_SHAPE,
                                        bundleOf(COL_SHAPE_KEY to it),
                                    )
                                }
                            }

                            launch {
                                viewModel.gridPickerViewModel.previewingGridKey.collect {
                                    safeSendMessage(
                                        workspaceCallback,
                                        MESSAGE_ID_UPDATE_GRID,
                                        bundleOf(COL_GRID_NAME to it),
                                    )
                                }
                            }

                            launch {
                                combine(
                                        viewModel.colorPickerViewModel2.previewingColorOption,
                                        viewModel.darkModeViewModel.overridingIsDarkMode,
                                        ::Pair,
                                    )
                                    .collect { (colorOption, darkMode) ->
                                        val bundle =
                                            Bundle().apply {
                                                if (colorOption != null) {
                                                    val (ids, colors) =
                                                        materialColorsGenerator.generate(
                                                            colorOption.seedColor,
                                                            colorOption.style,
                                                            darkMode,
                                                        )
                                                    putIntArray(KEY_COLOR_RESOURCE_IDS, ids)
                                                    putIntArray(KEY_COLOR_VALUES, colors)
                                                }

                                                if (darkMode != null) {
                                                    putBoolean(KEY_DARK_MODE, darkMode)
                                                }
                                            }
                                        safeSendMessage(
                                            workspaceCallback,
                                            MESSAGE_ID_UPDATE_COLOR,
                                            bundle,
                                        )
                                    }
                            }

                            launch {
                                var hasEmitted = false
                                viewModel.iconPackPickerViewModel.iconPackOverride
                                    .collect { override ->
                                        if (override != null) {
                                            hasEmitted = true
                                            // Translate "" (System Icons) to sentinel so ""
                                            // can mean "revert to saved" unambiguously
                                            val value = override.ifEmpty { "system_icons" }
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_UPDATE_ICON_PACK,
                                                bundleOf(KEY_ICON_PACK_VALUE to value),
                                            )
                                        } else if (hasEmitted) {
                                            // Override was cleared (e.g. resetPreview),
                                            // tell preview to revert to saved icon pack
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_UPDATE_ICON_PACK,
                                                bundleOf(KEY_ICON_PACK_VALUE to ""),
                                            )
                                        }
                                    }
                            }

                            if (BaseFlags.get().isExtendibleThemeManager()) {
                                launch {
                                    viewModel.appIconPickerViewModel.previewingIconStyle.collect {
                                        safeSendMessage(
                                            workspaceCallback,
                                            MESSAGE_ID_UPDATE_ICON_THEMED,
                                            Bundle().apply {
                                                putBoolean(KEY_BOOLEAN_VALUE, it.getIsThemedIcon())
                                            },
                                        )
                                    }
                                }
                            } else {
                                launch {
                                    viewModel.appIconPickerViewModel.previewingIsThemeIconEnabled
                                        .collect {
                                            safeSendMessage(
                                                workspaceCallback,
                                                MESSAGE_ID_UPDATE_ICON_THEMED,
                                                Bundle().apply { putBoolean(KEY_BOOLEAN_VALUE, it) },
                                            )
                                        }
                                }
                            }
                        }
                    }
            }
        }
    }

    override fun unbind() {
        lockScreenJob?.cancel()
        lockScreenJob = null
        homeScreenJob?.cancel()
        homeScreenJob = null
    }

    companion object {
        const val TAG = "ThemePickerWorkspaceCallbackBinder"

        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        const val MESSAGE_ID_UPDATE_SHAPE = 2586
        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        const val MESSAGE_ID_UPDATE_GRID = 7414
        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        const val MESSAGE_ID_UPDATE_COLOR = 856
        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        const val MESSAGE_ID_UPDATE_ICON_THEMED = 311
        const val MESSAGE_ID_UPDATE_ICON_PACK = 1738
        const val KEY_ICON_PACK_VALUE: String = "icon_pack_value"
        const val KEY_COLOR_RESOURCE_IDS: String = "color_resource_ids"
        const val KEY_COLOR_VALUES: String = "color_values"
        const val KEY_DARK_MODE: String = "use_dark_mode"
        const val KEY_BOOLEAN_VALUE: String = "boolean_value"

        const val MESSAGE_ID_UPDATE_COMMAND = 512
        const val KEY_UPDATE_METHOD = "update_method"

        fun safeSendMessage(workspaceCallback: Message, what: Int, data: Bundle) {
            try {
                workspaceCallback.sendMessage(what, data)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to send message to workspace callback", e)
            }
        }
    }
}
