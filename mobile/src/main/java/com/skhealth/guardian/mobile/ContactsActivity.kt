package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class ContactsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); UiStyle.applyBars(this); render() }
    private fun render() {
        val root=UiStyle.page(this); val contacts=ContactStore.contacts(this)
        root.addView(UiStyle.title(this,"Acil durum kişileri")); root.addView(UiStyle.subtitle(this,"Alarm sırasında SMS gönderilecek ve aranacak kişileri yönet."))
        val form=UiStyle.card(this); form.addView(UiStyle.text(this,"Yeni kişi",19f,UiStyle.TEXT,true))
        val name=UiStyle.field(this,"İsim"); val phone=UiStyle.field(this,"Telefon (+90…)"); phone.inputType=3
        val sms=UiStyle.check(this,"Alarmda SMS gönder",true); val call=UiStyle.check(this,"Alarmda ara",false)
        form.addView(name); form.addView(phone); form.addView(sms); form.addView(call)
        form.addView(UiStyle.button(this,"Kişiyi ekle").apply{setOnClickListener{
            if(phone.text.isBlank()) return@setOnClickListener Toast.makeText(this@ContactsActivity,"Telefon numarası gerekli",Toast.LENGTH_LONG).show()
            val list=ContactStore.contacts(this@ContactsActivity).toMutableList(); list+=EmergencyContact(name.text.toString().ifBlank{"Kişi"},phone.text.toString().trim(),sms.isChecked,call.isChecked); ContactStore.save(this@ContactsActivity,list); render()
        }}); root.addView(form)
        root.addView(UiStyle.sectionTitle(this,"Tanımlı kişiler"))
        if(contacts.isEmpty()) root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@ContactsActivity,"Henüz kişi tanımlı değil",16f,UiStyle.MUTED))})
        contacts.forEachIndexed{index,c->
            val card=UiStyle.card(this); val actions=buildList{if(c.smsEnabled)add("SMS");if(c.callEnabled)add("Arama")}.joinToString(" • ").ifBlank{"Bildirim kapalı"}
            card.addView(UiStyle.text(this,c.name,18f,UiStyle.TEXT,true)); card.addView(UiStyle.text(this,c.phoneNumber,15f,UiStyle.MUTED).apply{setPadding(0,UiStyle.dp(this@ContactsActivity,5),0,0)}); card.addView(UiStyle.text(this,actions,14f,UiStyle.GREEN).apply{setPadding(0,UiStyle.dp(this@ContactsActivity,8),0,0)})
            card.addView(UiStyle.button(this,"Kişiyi kaldır",false).apply{setOnClickListener{AlertDialog.Builder(this@ContactsActivity).setTitle("Kişiyi kaldır").setMessage("${c.name} acil durum listesinden kaldırılsın mı?").setNegativeButton("İptal",null).setPositiveButton("Kaldır"){_,_->val list=ContactStore.contacts(this@ContactsActivity).toMutableList();if(index<list.size)list.removeAt(index);ContactStore.save(this@ContactsActivity,list);render()}.show()}})
            root.addView(card,UiStyle.sectionParams(this,10))
        }
        setContentView(ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)})
    }
}
