package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.*

class ContactsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        val name = EditText(this).apply { hint="İsim" }; val phone = EditText(this).apply { hint="Telefon (+90...)"; inputType=3 }
        val sms = CheckBox(this).apply { text="SMS gönder"; isChecked=true }; val call = CheckBox(this).apply { text="Ara" }
        root.addView(TextView(this).apply { text="Acil durum kişileri"; textSize=22f }); root.addView(name); root.addView(phone); root.addView(sms); root.addView(call)
        root.addView(Button(this).apply { text="Kişiyi ekle"; setOnClickListener {
            if (phone.text.isBlank()) return@setOnClickListener
            val list = ContactStore.contacts(this@ContactsActivity).toMutableList()
            list += EmergencyContact(name.text.toString().ifBlank { "Kişi" }, phone.text.toString(), sms.isChecked, call.isChecked)
            ContactStore.save(this@ContactsActivity, list); render()
        }})
        ContactStore.contacts(this).forEachIndexed { i,c ->
            root.addView(Button(this).apply { text="${c.name}  ${c.phoneNumber}   SMS:${c.smsEnabled} Ara:${c.callEnabled}  [Sil]"; setOnClickListener {
                val list=ContactStore.contacts(this@ContactsActivity).toMutableList(); if(i<list.size) list.removeAt(i); ContactStore.save(this@ContactsActivity,list); render()
            }})
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
