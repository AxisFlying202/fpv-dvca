package eu.darken.fpv.dvca.main.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import androidx.preference.Preference

// From https://stackoverflow.com/a/20157577/877465
class VersionPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {
    init {
        val versionName: String?
        val packageManager = context.packageManager
        if (packageManager != null) {
            versionName = try {
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            summary = versionName
        }
        isSelectable = false
    }
}