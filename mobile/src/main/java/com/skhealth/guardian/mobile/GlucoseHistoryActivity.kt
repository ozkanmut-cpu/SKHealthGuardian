package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.skhealth.guardian.shared.BloodGlucoseReading
import com.skhealth.guardian.shared.GlucoseScheduleEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlucoseHistoryActivity : Activity() {
    private val fmt = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale("tr", "TR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(UiStyle.BG)
            addView(root)
        })

        val pending = BloodGlucoseStore.pendingOwnership(this, 500).asReversed()
        val all = BloodGlucoseStore.recent(this, 500).asReversed()
        val orko = all.filter { it.belongsToOrko }

        root.addView(
            UiStyle.detailHeader(
                this,
                "Şeker geçmişi",
                when {
                    all.isEmpty() -> "Henüz Accu-Chek ölçümü yok"
                    pending.isNotEmpty() -> "${pending.size} ölçüm kime ait olduğu doğrulanmayı bekliyor"
                    else -> "${orko.size} Orko ölçümü • son ${all.size} cihaz kaydı"
                }
            )
        )

        addDailyPlan(root)

        if (pending.isNotEmpty()) {
            root.addView(UiStyle.text(this, "Doğrulama bekleyenler", 18f, UiStyle.TEXT, true), UiStyle.sectionParams(this, 14))
            pending.forEach { reading -> root.addView(readingCard(reading, showOwnershipButtons = true), UiStyle.sectionParams(this, 8)) }
        }

        root.addView(UiStyle.text(this, "Tüm şeker ölçümleri", 18f, UiStyle.TEXT, true), UiStyle.sectionParams(this, 16))
        if (all.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                addView(UiStyle.text(this@GlucoseHistoryActivity, "Accu-Chek'ten veri geldiğinde burada görünecek.", 14f, UiStyle.MUTED))
            }, UiStyle.sectionParams(this, 8))
        } else {
            all.forEach { reading -> root.addView(readingCard(reading, showOwnershipButtons = false), UiStyle.sectionParams(this, 8)) }
        }
    }

    private fun addDailyPlan(root: LinearLayout) {
        val statuses = GlucoseDailyPlan.statuses(this)
        val card = UiStyle.card(this, 17)
        card.addView(UiStyle.text(this, "Bugünün glikoz planı", 17f, UiStyle.TEXT, true))
        if (statuses.isEmpty()) {
            card.addView(UiStyle.text(this, "İlaç zamanları geldiğinde günlük ölçüm hedefleri burada oluşacak.", 13f, UiStyle.MUTED).apply {
                setPadding(0, UiStyle.dp(this@GlucoseHistoryActivity, 8), 0, 0)
            })
        } else {
            statuses.forEachIndexed { index, s ->
                if (index == 0) card.addView(UiStyle.divider(this))
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, UiStyle.dp(this@GlucoseHistoryActivity, 8), 0, UiStyle.dp(this@GlucoseHistoryActivity, 8))
                }
                val stateColor = when (s.state) {
                    GlucoseDailyPlan.State.COMPLETED -> UiStyle.GREEN
                    GlucoseDailyPlan.State.DUE -> UiStyle.BLUE
                    GlucoseDailyPlan.State.OVERDUE -> UiStyle.AMBER
                    GlucoseDailyPlan.State.UPCOMING -> UiStyle.MUTED
                }
                row.addView(UiStyle.icon(this, if (s.state == GlucoseDailyPlan.State.COMPLETED) SkIcon.STATUS_OK else SkIcon.CLOCK, 20, stateColor), LinearLayout.LayoutParams(UiStyle.dp(this, 28), UiStyle.dp(this, 28)))
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(UiStyle.text(this, checkpointLabel(s.checkpoint), 14f, UiStyle.TEXT, true))
                val detail = buildString {
                    append(SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(s.targetAtMs)))
                    append(" • ")
                    append(stateLabel(s.state))
                    s.completedValueMgDl?.let { append(" • $it mg/dL") }
                }
                labels.addView(UiStyle.text(this, detail, 12.5f, stateColor).apply { setPadding(0, UiStyle.dp(this@GlucoseHistoryActivity, 4), 0, 0) })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(row)
            }
        }
        root.addView(card, UiStyle.sectionParams(this, 10))
    }

    private fun readingCard(reading: BloodGlucoseReading, showOwnershipButtons: Boolean): LinearLayout = UiStyle.card(this, 16).apply {
        val top = LinearLayout(this@GlucoseHistoryActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(UiStyle.text(this@GlucoseHistoryActivity, "${reading.valueMgDl} mg/dL", 24f, UiStyle.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val ownerText = when (reading.ownership) {
            BloodGlucoseReading.Ownership.ORKO -> "ORKO"
            BloodGlucoseReading.Ownership.OTHER_PERSON -> "BAŞKA KİŞİ"
            BloodGlucoseReading.Ownership.UNCONFIRMED -> "BEKLİYOR"
        }
        val ownerColor = when (reading.ownership) {
            BloodGlucoseReading.Ownership.ORKO -> UiStyle.GREEN
            BloodGlucoseReading.Ownership.OTHER_PERSON -> UiStyle.MUTED
            BloodGlucoseReading.Ownership.UNCONFIRMED -> UiStyle.AMBER
        }
        top.addView(UiStyle.text(this@GlucoseHistoryActivity, ownerText, 12f, ownerColor, true))
        addView(top)

        val detail = listOfNotNull(
            fmt.format(Date(reading.measuredAtMs)),
            reading.checkpointHint?.let(::checkpointLabel),
            contextLabel(reading.context)
        ).filter { it.isNotBlank() }.joinToString(" • ")
        addView(UiStyle.text(this@GlucoseHistoryActivity, detail, 12.5f, UiStyle.MUTED).apply {
            setPadding(0, UiStyle.dp(this@GlucoseHistoryActivity, 7), 0, 0)
        })

        if (showOwnershipButtons && reading.ownership == BloodGlucoseReading.Ownership.UNCONFIRMED) {
            val buttons = LinearLayout(this@GlucoseHistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                setPadding(0, UiStyle.dp(this@GlucoseHistoryActivity, 12), 0, 0)
            }
            buttons.addView(choiceButton("Orko", UiStyle.GREEN) {
                BloodGlucoseStore.confirmOwnership(this@GlucoseHistoryActivity, reading.id, BloodGlucoseReading.Ownership.ORKO)
                GlucoseOwnershipNotification.dismiss(this@GlucoseHistoryActivity, reading.id)
                render()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = UiStyle.dp(this@GlucoseHistoryActivity, 5) })
            buttons.addView(choiceButton("Başka kişi", UiStyle.MUTED) {
                BloodGlucoseStore.confirmOwnership(this@GlucoseHistoryActivity, reading.id, BloodGlucoseReading.Ownership.OTHER_PERSON)
                GlucoseOwnershipNotification.dismiss(this@GlucoseHistoryActivity, reading.id)
                render()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@GlucoseHistoryActivity, 5) })
            addView(buttons)
        }
    }

    private fun choiceButton(label: String, color: Int, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        minimumHeight = UiStyle.dp(this@GlucoseHistoryActivity, 46)
        background = UiStyle.rounded(UiStyle.SURFACE_2, 15, color, 1, this@GlucoseHistoryActivity)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(UiStyle.text(this@GlucoseHistoryActivity, label, 14f, color, true))
    }

    private fun checkpointLabel(checkpoint: GlucoseScheduleEngine.Checkpoint): String = when (checkpoint) {
        GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> "Sabah açlık"
        GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL -> "Kahvaltı +2 saat"
        GlucoseScheduleEngine.Checkpoint.MIDDAY -> "Öğlen"
        GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> "Akşam +2 saat"
        GlucoseScheduleEngine.Checkpoint.BEDTIME -> "Yatmadan önce"
    }

    private fun contextLabel(context: BloodGlucoseReading.MeasurementContext): String = when (context) {
        BloodGlucoseReading.MeasurementContext.FASTING -> "Açlık"
        BloodGlucoseReading.MeasurementContext.PRE_MEAL -> "Yemek öncesi"
        BloodGlucoseReading.MeasurementContext.POST_MEAL -> "Yemek sonrası"
        BloodGlucoseReading.MeasurementContext.BEDTIME -> "Yatmadan önce"
        BloodGlucoseReading.MeasurementContext.OTHER -> "Diğer"
        BloodGlucoseReading.MeasurementContext.UNKNOWN -> ""
    }

    private fun stateLabel(state: GlucoseDailyPlan.State): String = when (state) {
        GlucoseDailyPlan.State.UPCOMING -> "Yaklaşan"
        GlucoseDailyPlan.State.DUE -> "Şimdi ölçülebilir"
        GlucoseDailyPlan.State.OVERDUE -> "Gecikti"
        GlucoseDailyPlan.State.COMPLETED -> "Tamamlandı"
    }
}
