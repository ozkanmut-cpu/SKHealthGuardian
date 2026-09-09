package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.widget.*

class ContactsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        val contacts = ContactStore.contacts(this)
        root.addView(TextView(this).apply { text = "Acil durum kişileri"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = if (contacts.isEmpty()) "Henüz kişi tanımlı değil" else "${contacts.size} kişi tanımlı • arama sırası yukarıdan aşağıya"
            textSize = 15f; setPadding(0, 10, 0, 18)
        })

        val name = EditText(this).apply { hint = "İsim" }
        val phone = EditText(this).apply { hint = "Telefon (+90...)"; inputType = 3 }
        val sms = CheckBox(this).apply { text = "Alarmda SMS gönder"; isChecked = true }
        val call = CheckBox(this).apply { text = "Alarmda ara" }
        root.addView(TextView(this).apply { text = "Yeni kişi"; textSize = 19f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 6, 0, 8) })
        root.addView(name); root.addView(phone); root.addView(sms); root.addView(call)
        root.addView(Button(this).apply {
            text = "Kişiyi ekle"
            setOnClickListener {
                if (phone.text.isBlank()) return@setOnClickListener Toast.makeText(this@ContactsActivity, "Telefon numarası gerekli", Toast.LENGTH_LONG).show()
                val list = ContactStore.contacts(this@ContactsActivity).toMutableList()
                list += EmergencyContact(name.text.toString().ifBlank { "Kişi" }, phone.text.toString().trim(), sms.isChecked, call.isChecked)
                ContactStore.save(this@ContactsActivity, list)
                render()
            }
        })

        root.addView(TextView(this).apply { text = "Tanımlı kişiler"; textSize = 19f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 24, 0, 8) })
        contacts.forEachIndexed { index, c ->
            val actions = buildList { if (c.smsEnabled) add("SMS"); if (c.callEnabled) add("Arama") }.joinToString(" + ").ifBlank { "Bildirim kapalı" }
            root.addView(TextView(this).apply {
                text = "${index + 1}. ${c.name}\n${c.phoneNumber}\n$actions"
                textSize = 17f; setPadding(0, 14, 0, 4)
            })
            root.addView(Button(this).apply {
                text = "Bu kişiyi kaldır"
                setOnClickListener {
                    AlertDialog.Builder(this@ContactsActivity)
                        .setTitle("Kişiyi kaldır")
                        .setMessage("${c.name} acil durum listesinden kaldırılsın mı?")
                        .setNegativeButton("İptal", null)
                        .setPositiveButton("Kaldır") { _, _ ->
                            val list = ContactStore.contacts(this@ContactsActivity).toMutableList()
                            if (index < list.size) list.removeAt(index)
                            ContactStore.save(this@ContactsActivity, list)
                            render()
                        }.show()
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
