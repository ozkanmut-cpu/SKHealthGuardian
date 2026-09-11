package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlucoseSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        val config = GlucoseSettings.load(this)
        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(UiStyle.BG)
            addView(root)
        })

        root.addView(
            UiStyle.detailHeader(
                this,
                "Kan şekeri ayarları",
                "Accu-Chek ölçümleri, sahiplik doğrulaması ve günlük ölçüm hatırlatmaları"
            )
        )

        val bridge = DosefolkBridgeStatusStore.load(this)
        val bridgeCard = UiStyle.card(this)
        bridgeCard.addView(UiStyle.iconLabel(this, SkIcon.LINK, "Dosefolk bağlantısı", UiStyle.PURPLE, UiStyle.TEXT, 22, 18f, true))
        val bridgeState = if (bridge.lastReceivedAtMs > 0L) "Bağlı • ilaç olayları alınıyor" else "Henüz Dosefolk ilaç olayı alınmadı"
        bridgeCard.addView(
            UiStyle.text(
                this,
                bridgeState,
                13f,
                if (bridge.lastReceivedAtMs > 0L) UiStyle.GREEN else UiStyle.MUTED,
                bridge.lastReceivedAtMs > 0L
            ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 10), 0, 0) }
        )
        if (bridge.lastReceivedAtMs > 0L) {
            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
            val anchorLabel = when (bridge.lastAnchor) {
                GlucoseScheduleCoordinator.MedicationAnchor.MORNING_FIRST_GROUP.name -> "Sabah ilk ilaç grubu"
                GlucoseScheduleCoordinator.MedicationAnchor.MORNING_SECOND_POST_MEAL_GROUP.name -> "Sabah ikinci tok ilaç grubu"
                GlucoseScheduleCoordinator.MedicationAnchor.EVENING_COMBINED_POST_MEAL_GROUP.name -> "Akşam tok ilaç grubu"
                GlucoseScheduleCoordinator.MedicationAnchor.BEDTIME_TOUJEO.name -> "Yatmadan önce / Toujeo"
                else -> "İlaç grubu"
            }
            bridgeCard.addView(
                UiStyle.text(
                    this,
                    "Son olay: $anchorLabel • ${fmt.format(Date(bridge.lastTakenAtMs))}",
                    12.5f,
                    UiStyle.MUTED
                ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 7), 0, 0) }
            )
        }
        bridgeCard.addView(
            UiStyle.text(
                this,
                "Dosefolk ilaçların kaynağıdır. Orko Takip yalnızca ölçüm zamanını hesaplamak için ilaç grubunu ve gerçek alınma saatini kullanır; ilaç adı veya dozu bu bağlantıyla aktarılmaz.",
                12.5f,
                UiStyle.MUTED
            ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 8), 0, 0) }
        )
        bridgeCard.addView(
            UiStyle.iconButton(this, SkIcon.CLOCK, "Dosefolk grup eşlemelerini düzenle", true, UiStyle.PURPLE).apply {
                setOnClickListener {
                    val intent = Intent().setClassName(
                        "com.ozkanmut.ilactakip",
                        "com.ozkanmut.ilactakip.OrkoBridgeSettingsActivity"
                    )
                    runCatching { startActivity(intent) }
                        .onFailure {
                            Toast.makeText(
                                this@GlucoseSettingsActivity,
                                "Dosefolk bulunamadı veya güncel değil",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            },
            UiStyle.sectionParams(this, 10)
        )
        root.addView(bridgeCard)

        val rules = UiStyle.card(this)
        rules.addView(UiStyle.iconLabel(this, SkIcon.CLOCK, "Günlük plan", UiStyle.BLUE, UiStyle.TEXT, 22, 18f, true))
        rules.addView(
            UiStyle.text(
                this,
                "Sabah açlık: sabah ilk ilaç grubu • Kahvaltı +2 saat: yemek başlangıcından 2 saat sonra • Öğlen: kahvaltı başlangıcından 5 saat sonra • Akşam +2 saat: yemek başlangıcından 2 saat sonra • Yatmadan önce: Toujeo ile aynı dönem",
                13f,
                UiStyle.MUTED
            ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 10), 0, 0) }
        )
        rules.addView(
            UiStyle.text(
                this,
                "Yemek süresi 30 dakika varsayılır. Tok ilaç alınma saati yemek bitişi kabul edildiği için +2 saat hedefi ilaç saatinden 90 dakika sonradır.",
                12.5f,
                UiStyle.MUTED
            ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 8), 0, 0) }
        )
        root.addView(rules, UiStyle.sectionParams(this))

        val reminders = UiStyle.card(this)
        reminders.addView(UiStyle.iconLabel(this, SkIcon.BELL, "Hatırlatmalar", UiStyle.AMBER, UiStyle.TEXT, 22, 18f, true))
        val reminderEnabled = UiStyle.check(this, "Ölçüm zamanı bildirimi aktif", config.remindersEnabled)
        val overdueEnabled = UiStyle.check(this, "Geciken ölçüm bildirimi aktif", config.overdueEnabled)
        val graceField = UiStyle.labeledField(this, "Gecikme bildirimi bekleme süresi (dk)", config.overdueGraceMinutes.toString(), true)
        reminders.addView(reminderEnabled)
        reminders.addView(overdueEnabled)
        reminders.addView(graceField)
        root.addView(reminders, UiStyle.sectionParams(this))

        val ownership = UiStyle.card(this)
        ownership.addView(UiStyle.iconLabel(this, SkIcon.STATUS_OK, "Ölçüm kime ait?", UiStyle.GREEN, UiStyle.TEXT, 22, 18f, true))
        val ownershipPrompt = UiStyle.check(this, "Yeni ölçümde Orko / Başka kişi bildirimi göster", config.ownershipPromptEnabled)
        ownership.addView(ownershipPrompt)
        ownership.addView(
            UiStyle.text(
                this,
                "Bu bildirim kapatılsa bile yeni ölçümler otomatik olarak Orko'ya yazılmaz. Doğrulanmamış ölçümler Şeker geçmişi ekranında bekler.",
                12.5f,
                UiStyle.MUTED
            ).apply { setPadding(0, UiStyle.dp(this@GlucoseSettingsActivity, 8), 0, 0) }
        )
        root.addView(ownership, UiStyle.sectionParams(this))

        root.addView(
            UiStyle.iconButton(this, SkIcon.STATUS_OK, "Kan şekeri ayarlarını kaydet", true, UiStyle.GREEN).apply {
                setOnClickListener {
                    val grace = input(graceField).text.toString().toIntOrNull()
                    if (grace == null || grace !in 1..120) {
                        Toast.makeText(this@GlucoseSettingsActivity, "Gecikme süresi 1–120 dakika olmalı", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    GlucoseSettings.save(
                        this@GlucoseSettingsActivity,
                        GlucoseSettings.Config(
                            remindersEnabled = reminderEnabled.isChecked,
                            overdueEnabled = overdueEnabled.isChecked,
                            overdueGraceMinutes = grace,
                            ownershipPromptEnabled = ownershipPrompt.isChecked
                        )
                    )
                    Toast.makeText(this@GlucoseSettingsActivity, "Kan şekeri ayarları kaydedildi", Toast.LENGTH_SHORT).show()
                }
            },
            UiStyle.sectionParams(this, 16)
        )
    }

    private fun input(field: LinearLayout): EditText = UiStyle.labeledFieldInput(field)
}
