package com.android.customization.picker.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class IconPackInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
)

object IconPackUtil {

    private val ICON_PACK_INTENTS = arrayOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.dlto.atom.launcher.THEME",
    )

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = mutableListOf<IconPackInfo>()

        packs.add(
            IconPackInfo(
                packageName = "",
                name = "System Icons",
                icon = context.packageManager.getApplicationIcon(context.packageName),
            )
        )

        val seenPackages = mutableSetOf<String>()

        for (action in ICON_PACK_INTENTS) {
            val intent = Intent(action)
            val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            for (activity in activities) {
                val pkg = activity.activityInfo.packageName
                if (seenPackages.add(pkg)) {
                    val appInfo = activity.activityInfo.applicationInfo
                    packs.add(
                        IconPackInfo(
                            packageName = pkg,
                            name = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                        ),
                    )
                }
            }
        }

        val apexIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("com.anddoes.launcher.THEME")
        }
        val apexActivities: List<ResolveInfo> = pm.queryIntentActivities(apexIntent, 0)
        for (activity in apexActivities) {
            val pkg = activity.activityInfo.packageName
            if (seenPackages.add(pkg)) {
                val appInfo = activity.activityInfo.applicationInfo
                packs.add(
                    IconPackInfo(
                        packageName = pkg,
                        name = appInfo.loadLabel(pm).toString(),
                        icon = appInfo.loadIcon(pm),
                    ),
                )
            }
        }

        return packs.sortedBy { it.name.lowercase() }
    }
}
