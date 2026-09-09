package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.Pc60Sample

interface Pc60SdkRuntime {
    val available: Boolean
    fun start(
        context: Context,
        onState: (String) -> Unit,
        onSample: (Pc60Sample) -> Unit
    )
    fun stop()
    fun restart()
}
