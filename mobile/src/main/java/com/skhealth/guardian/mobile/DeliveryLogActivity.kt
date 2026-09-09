package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class DeliveryLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = DeliveryLogStore.formatted(this).ifBlank { "Henüz SMS/arama kaydı yok." }
        setContentView(ScrollView(this).apply {
            addView(TextView(this@DeliveryLogActivity).apply {
                setPadding(32, 32, 32, 32)
                textSize = 16f
                this.text = text
            })
        })
    }
}
