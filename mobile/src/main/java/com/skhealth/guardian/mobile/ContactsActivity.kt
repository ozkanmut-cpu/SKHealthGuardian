package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class ContactsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        render()
    }

    private fun render() {
        val root = UiStyle.page(this)
        val contacts = ContactStore.contacts(this)
        root.addView(UiStyle.detailHeader(this, "Acil durum kişileri", "Alarm sırasında SMS gönderilecek ve aranacak kişileri yönet."))

        val form = UiStyle.card(this)
        val formHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(UiStyle.icon(this@ContactsActivity, SkIcon.PLUS, 24, UiStyle.BLUE, "Yeni kişi"), LinearLayout.LayoutParams(UiStyle.dp(this@ContactsActivity, 34), UiStyle.dp(this@ContactsActivity, 34)))
            addView(UiStyle.text(this@ContactsActivity, "Yeni kişi", 19f, UiStyle.TEXT, true))
        }
        form.addView(formHeader)

        val name = UiStyle.field(this, "İsim")
        val phone = UiStyle.field(this, "Telefon (+90…)").apply { inputType = 3 }
        val sms = UiStyle.check(this, "Alarmda SMS gönder", true)
        val call = UiStyle.check(this, "Alarmda ara", false)
        form.addView(name)
        form.addView(phone)
        form.addView(sms)
        form.addView(call)

        val addButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = UiStyle.dp(this@ContactsActivity, 54)
            background = UiStyle.rounded(0xFF1677FF.toInt(), 18, context = this@ContactsActivity)
            isClickable = true
            isFocusable = true
            addView(UiStyle.icon(this@ContactsActivity, SkIcon.PLUS, 21, UiStyle.TEXT))
            addView(UiStyle.text(this@ContactsActivity, "Kişiyi ekle", 16f, UiStyle.TEXT, true).apply { setPadding(UiStyle.dp(this@ContactsActivity, 8), 0, 0, 0) })
            setOnClickListener {
                if (phone.text.isBlank()) {
                    Toast.makeText(this@ContactsActivity, "Telefon numarası gerekli", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val list = ContactStore.contacts(this@ContactsActivity).toMutableList()
                list += EmergencyContact(
                    name.text.toString().ifBlank { "Kişi" },
                    phone.text.toString().trim(),
                    sms.isChecked,
                    call.isChecked
                )
                ContactStore.save(this@ContactsActivity, list)
                render()
            }
        }
        form.addView(addButton, UiStyle.sectionParams(this, 10))
        root.addView(form)

        root.addView(UiStyle.sectionTitle(this, "Tanımlı kişiler"))
        if (contacts.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                val row = LinearLayout(this@ContactsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(UiStyle.icon(this@ContactsActivity, SkIcon.CONTACTS, 25, UiStyle.MUTED), LinearLayout.LayoutParams(UiStyle.dp(this@ContactsActivity, 36), UiStyle.dp(this@ContactsActivity, 36)))
                row.addView(UiStyle.text(this@ContactsActivity, "Henüz kişi tanımlı değil", 16f, UiStyle.MUTED))
                addView(row)
            })
        }

        contacts.forEachIndexed { index, contact ->
            val card = UiStyle.card(this)
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(UiStyle.icon(this, SkIcon.CONTACTS, 28, UiStyle.PURPLE, contact.name), LinearLayout.LayoutParams(UiStyle.dp(this, 40), UiStyle.dp(this, 40)))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(UiStyle.text(this@ContactsActivity, contact.name, 18f, UiStyle.TEXT, true))
            labels.addView(UiStyle.text(this@ContactsActivity, contact.phoneNumber, 14.5f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@ContactsActivity, 4), 0, 0) })
            header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(header)

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiStyle.dp(this@ContactsActivity, 10), 0, 0)
            }
            if (contact.smsEnabled) {
                actionRow.addView(UiStyle.iconLabel(this, SkIcon.BELL, "SMS", UiStyle.GREEN, UiStyle.GREEN, 17, 13f, true, 5), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            if (contact.callEnabled) {
                actionRow.addView(UiStyle.iconLabel(this, SkIcon.CONTACTS, "Arama", UiStyle.GREEN, UiStyle.GREEN, 17, 13f, true, 5), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            if (!contact.smsEnabled && !contact.callEnabled) {
                actionRow.addView(UiStyle.text(this, "Bildirim kapalı", 13f, UiStyle.MUTED))
            }
            card.addView(actionRow)

            val remove = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = UiStyle.dp(this@ContactsActivity, 50)
                background = UiStyle.rounded(UiStyle.SURFACE_2, 16, 0xFF405064.toInt(), 1, this@ContactsActivity)
                isClickable = true
                isFocusable = true
                addView(UiStyle.icon(this@ContactsActivity, SkIcon.DELETE, 20, UiStyle.RED))
                addView(UiStyle.text(this@ContactsActivity, "Kişiyi kaldır", 15f, UiStyle.RED, true).apply { setPadding(UiStyle.dp(this@ContactsActivity, 7), 0, 0, 0) })
                setOnClickListener {
                    AlertDialog.Builder(this@ContactsActivity)
                        .setTitle("Kişiyi kaldır")
                        .setMessage("${contact.name} acil durum listesinden kaldırılsın mı?")
                        .setNegativeButton("İptal", null)
                        .setPositiveButton("Kaldır") { _, _ ->
                            val list = ContactStore.contacts(this@ContactsActivity).toMutableList()
                            if (index < list.size) list.removeAt(index)
                            ContactStore.save(this@ContactsActivity, list)
                            render()
                        }.show()
                }
            }
            card.addView(remove, UiStyle.sectionParams(this, 12))
            root.addView(card, UiStyle.sectionParams(this, 10))
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(UiStyle.BG)
            addView(root)
        })
    }
}
