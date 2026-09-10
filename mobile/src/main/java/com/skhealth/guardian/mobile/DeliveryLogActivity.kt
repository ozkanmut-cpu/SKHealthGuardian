package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        val entries = DeliveryLogStore.entries(this, 200).asReversed()
        val ok = entries.count { it.ok }
        root.addView(UiStyle.detailHeader(this,"SMS / Arama kayıtları",if(entries.isEmpty()) "Henüz iletişim kaydı yok." else "$ok/${entries.size} işlem başarılı"))
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))

        if (entries.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                gravity=Gravity.CENTER_HORIZONTAL
                addView(UiStyle.icon(this@DeliveryLogActivity,SkIcon.CONTACTS,34,UiStyle.MUTED,"İletişim kaydı yok"))
                addView(UiStyle.text(this@DeliveryLogActivity,"İletişim olayları burada görünecek.",16f,UiStyle.MUTED,false,Gravity.CENTER).apply{setPadding(0,UiStyle.dp(this@DeliveryLogActivity,10),0,0)})
            })
        }

        entries.forEach { e ->
            val card = UiStyle.card(this)
            val accent = if (e.ok) UiStyle.GREEN else UiStyle.RED
            val channelIcon = if (e.channel.contains("arama",true) || e.channel.contains("call",true)) SkIcon.CONTACTS else SkIcon.BELL
            val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            header.addView(UiStyle.icon(this,channelIcon,24,accent,e.channel),LinearLayout.LayoutParams(UiStyle.dp(this,28),UiStyle.dp(this,28)))
            header.addView(UiStyle.text(this,e.channel,17f,accent,true).apply{setPadding(UiStyle.dp(this@DeliveryLogActivity,9),0,0,0)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            header.addView(UiStyle.icon(this,if(e.ok)SkIcon.STATUS_OK else SkIcon.STATUS_ALERT,22,accent,if(e.ok)"Başarılı" else "Başarısız"))
            card.addView(header)
            card.addView(UiStyle.iconLabel(this,SkIcon.CONTACTS,e.target,UiStyle.MUTED,UiStyle.TEXT,16,14f,false,7).apply{setPadding(0,UiStyle.dp(this@DeliveryLogActivity,8),0,0)})
            card.addView(UiStyle.iconLabel(this,SkIcon.CLOCK,fmt.format(Date(e.timestampMs)),UiStyle.MUTED,UiStyle.MUTED,16,13f,false,7).apply{setPadding(0,UiStyle.dp(this@DeliveryLogActivity,6),0,0)})
            card.addView(UiStyle.text(this,e.detail,15f,UiStyle.TEXT).apply{setPadding(0,UiStyle.dp(this@DeliveryLogActivity,9),0,0)})
            root.addView(card, UiStyle.sectionParams(this, 10))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
    }
}
