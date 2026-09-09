package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

class SettingsHubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })

        root.addView(UiStyle.title(this, "Ayarlar"))
        root.addView(UiStyle.subtitle(this, "Uygulama ve alarm yapılandırması"))

        val card = UiStyle.card(this, 6)
        addItem(card, "Alarm ayarları", "SpO₂, nabız ve diğer alarm kuralları", MeasurementSettingsActivity::class.java)
        addItem(card, "Cihazlar", "Galaxy Watch ve PC-60FW", DevicesActivity::class.java)
        addItem(card, "Acil durum kişileri", "Arama ve SMS ayarları", ContactsActivity::class.java)
        addItem(card, "Veri yönetimi", "Yedekleme, CSV ve geri yükleme", ExportActivity::class.java)
        addItem(card, "Sistem sağlık kontrolü", "İzinlar ve arka plan servisleri".replace("İzinlar", "İzinler"), SystemHealthActivity::class.java)
        addItem(card, "Kurulum / QA", "İzin, bağlantı ve alarm testleri", SystemTestActivity::class.java)
        addItem(card, "SMS / arama kayıtları", "Teslim ve çağrı durumları", DeliveryLogActivity::class.java, divider = false)
        root.addView(card)
    }

    private fun addItem(parent: LinearLayout, title: String, subtitle: String, target: Class<out Activity>, divider: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiStyle.dp(this@SettingsHubActivity, 12), UiStyle.dp(this@SettingsHubActivity, 15), UiStyle.dp(this@SettingsHubActivity, 12), UiStyle.dp(this@SettingsHubActivity, 15))
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@SettingsHubActivity, target)) }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, title, 15.5f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this, subtitle, 12.5f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SettingsHubActivity, 5), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.text(this, "›", 24f, UiStyle.MUTED))
        parent.addView(row)
        if (divider) parent.addView(UiStyle.divider(this))
    }
}
