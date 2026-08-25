package com.softdread.widgets.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.softdread.widgets.data.device.ScreenTimeDataSource

/** Live permission state for the three widgets that need something granted. */
data class PermissionStatus(
    val hasUsageAccess: Boolean,
    val hasCalendar: Boolean,
    val hasCoarseLocation: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            hasUsageAccess = ScreenTimeDataSource(context).hasUsageAccess(),
            hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
            hasCoarseLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
}
