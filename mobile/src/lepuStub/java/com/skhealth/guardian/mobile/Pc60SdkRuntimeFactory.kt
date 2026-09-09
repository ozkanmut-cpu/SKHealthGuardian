package com.skhealth.guardian.mobile

object Pc60SdkRuntimeFactory {
    fun create(): Pc60SdkRuntime = object : Pc60SdkRuntime {
        override val available: Boolean = false
        override fun start(
            context: android.content.Context,
            onState: (String) -> Unit,
            onSample: (com.skhealth.guardian.shared.Pc60Sample) -> Unit
        ) {
            onState("Lepu SDK yok; ham BLE modu")
        }
        override fun stop() = Unit
        override fun restart() = Unit
    }
}
