package com.android.customization.picker.iconpack.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.android.customization.picker.iconpack.IconPackInfo
import com.android.customization.picker.iconpack.IconPackUtil
import com.android.wallpaper.model.Screen
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.util.PreviewUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ThemePickerIconPackRepository
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val contentResolver: ContentResolver,
    @BackgroundDispatcher private val backgroundScope: CoroutineScope,
) : IconPackRepository {

    companion object {
        private const val ICON_PACK_PATH = "icon_pack"
        private const val ICON_PACK_VALUE = "icon_pack_value"
    }

    private val metadataKey = "com.android.launcher3.themedicon.option"

    private var previewUtils: PreviewUtils? = null

    private val previewUtilsFlow = flow {
        if (previewUtils == null) {
            PreviewUtils(appContext, metadataKey, Screen.HOME_SCREEN).let {
                if (it.supportsPreview()) {
                    previewUtils = it
                }
            }
        }
        emit(previewUtils)
    }

    override val installedIconPacks: List<IconPackInfo>
        get() = IconPackUtil.getInstalledIconPacks(appContext)

    override val selectedIconPack: Flow<String?> =
        previewUtilsFlow
            .flatMapLatest { utils ->
                callbackFlow {
                    var disposableHandle: DisposableHandle? = null
                    if (utils != null) {
                        val uri = utils.getUri(ICON_PACK_PATH)
                        val observer = object : ContentObserver(null) {
                            override fun onChange(selfChange: Boolean) {
                                trySend(queryIconPack(uri))
                            }
                        }
                        contentResolver.registerContentObserver(
                            uri, false, observer
                        )
                        trySend(queryIconPack(uri))
                        disposableHandle = DisposableHandle {
                            contentResolver.unregisterContentObserver(observer)
                        }
                    }
                    awaitClose { disposableHandle?.dispose() }
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null,
            )

    override suspend fun setIconPack(packageName: String) {
        val utils = previewUtilsFlow.first()
        if (utils == null) {
            Log.e("IconPackRepository", "Cannot set icon pack: PreviewUtils is null, content provider not available")
            return
        }
        val uri = utils.getUri(ICON_PACK_PATH)
        val values = ContentValues()
        values.put(ICON_PACK_VALUE, packageName)
        val updated = contentResolver.update(uri, values, null, null)
        if (updated > 0) {
            Log.i("IconPackRepository", "Icon pack set to '$packageName' ($updated rows updated)")
        } else {
            Log.e("IconPackRepository", "Failed to set icon pack: update returned $updated for URI $uri")
        }
    }

    private fun queryIconPack(uri: Uri): String? {
        val cursor: Cursor? = contentResolver.query(
            uri, null, null, null, null
        )
        var result: String? = null
        if (cursor != null && cursor.moveToNext()) {
            result = cursor.getString(
                cursor.getColumnIndex(ICON_PACK_VALUE)
            )
        }
        cursor?.close()
        return result
    }
}
