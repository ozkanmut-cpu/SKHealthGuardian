package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.AlarmConfig
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.WatchSpO2AlarmPolicy
import com.skhealth.guardian.shared.WatchSpO2AlarmSnapshot
import com.skhealth.guardian.shared.WatchSpO2Decision
import com.skhealth.guardian.wear.sensor.SamsungSensorGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val measurementMutex = Mutex()
    private lateinit var sensor: SensorGateway
    private lateinit var bridge: PhoneBridge
    private var activeConfig = AlarmConfig()
    private var engine = AlarmEngine(activeConfig)
    private var engineSignature = ""
    private val watchSpO2Policy = WatchSpO2AlarmPolicy()
    private var lastValidReadingAt = 0L
    private var serviceStartedAt = 0L
    private var lastSensorFailureAlertAt = 0L
    @Volatile private var inMemoryTechnicalAlertRetry: AlertEvent? = null
    @Volatile private var engineStateDirty = false

    override fun onCreate() {
        super.onCreate()
        serviceStartedAt = System.currentTimeMillis()
        activeConfig = WearSettings.load(this)
        engineSignature = WearAlarmEngineStateStore.signature(activeConfig)
        engine = AlarmEngine(activeConfig).also { restoredEngine -> WearAlarmEngineStateStore.load(this, engineSignature)?.let(restoredEngine::restore) }
        refreshWatchSpO2Policy()
        restoreWatchSpO2Policy()
        sensor = SamsungSensorGateway(this)
        bridge = PhoneBridge(this)
        createChannel()
        val intervalMin = WearSettings.measurementIntervalMs(this) / 60_000L
        startForeground(11, NotificationCompat.Builder(this, "monitor").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Orko Takip").setContentText("Sağlık izleme aktif • SpO₂ ve nabız $intervalMin dakikada bir ölçülüyor").setOngoing(true).build())
        startHeartbeat();startMonitoring();startSensorWatchdog()
    }

    private fun startHeartbeat() { scope.launch { while(isActive){ retryEngineStatePersistence();retryInMemoryTechnicalAlert();runCatching{bridge.sendHeartbeat(batteryPct())};delay(60_000L) } } }
    private fun persistEngineState(){ engineStateDirty=!WearAlarmEngineStateStore.save(this,engineSignature,engine.snapshot()) }
    private fun retryEngineStatePersistence(){ if(engineStateDirty) persistEngineState() }
    private suspend fun retryInMemoryTechnicalAlert(){ val pending=inMemoryTechnicalAlertRetry?:return;val delivered=runCatching{bridge.sendAlert(pending)}.getOrDefault(false);if(delivered&&inMemoryTechnicalAlertRetry?.eventId==pending.eventId)inMemoryTechnicalAlertRetry=null }
    private fun batteryPct():Int=getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)

    private fun startMonitoring(){ scope.launch { safeMeasureCycle();while(isActive){val intervalMs=WearSettings.measurementIntervalMs(this@MonitorService);val now=System.currentTimeMillis();val next=((now/intervalMs)+1L)*intervalMs;delay((next-now).coerceAtLeast(0L));safeMeasureCycle()} } }

    private fun startSensorWatchdog(){ scope.launch { while(isActive){ delay(60_000L);val now=System.currentTimeMillis();val reference=if(lastValidReadingAt>0)lastValidReadingAt else serviceStartedAt;val timeout=maxOf(activeConfig.staleDataMs,MIN_SENSOR_STALE_MS);if(now-reference>timeout){runCatching{sensor.reconnect()};safeMeasureCycle();val recovered=if(lastValidReadingAt>0)lastValidReadingAt else serviceStartedAt;if(System.currentTimeMillis()-recovered>timeout&&now-lastSensorFailureAlertAt>timeout){lastSensorFailureAlertAt=now;val alert=AlertEvent(AlertType.SENSOR_FAILURE,now,null,"Saat sensörü uzun süredir geçerli ölçüm üretemiyor");LocalAlarm.raise(this@MonitorService,alert);val delivered=runCatching{bridge.sendAlert(alert)}.getOrDefault(false);if(!delivered)inMemoryTechnicalAlertRetry=alert}}} } }

    private suspend fun safeMeasureCycle()=measurementMutex.withLock{measureCycle()}

    private suspend fun measureCycle(){
        val latest=WearSettings.load(this)
        if(latest!=activeConfig){activeConfig=latest;engineSignature=WearAlarmEngineStateStore.signature(activeConfig);engine=AlarmEngine(activeConfig);persistEngineState()}
        refreshWatchSpO2Policy()
        val wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"skhealth:measurement");wake.acquire(12*60_000L)
        try{
            val hrTriggerAt=System.currentTimeMillis();val hr=measureHeartRateWithRetry();if(hr!=null)processGeneric(HealthReading(timestampMs=System.currentTimeMillis(),heartRate=hr))else recordAndSend(HealthReading(timestampMs=System.currentTimeMillis(),valid=false))
            val spo2=measureSpO2WithRetry();var lowStarted:Long?=null
            val follow=if(spo2!=null){val reading=HealthReading(timestampMs=System.currentTimeMillis(),spo2=spo2);val f=processWatchSpO2(reading);if(f)lowStarted=watchSpO2Policy.snapshot().observationSince?:reading.timestampMs;f}else{recordAndSend(HealthReading(timestampMs=System.currentTimeMillis(),valid=false));false}
            if(follow&&lowStarted!=null)followLowSpO2(lowStarted)
            if(hr!=null&&hr>activeConfig.heartRateHighThreshold)confirmHeartRate(hrTriggerAt)
        }finally{if(wake.isHeld)wake.release()}
    }

    private fun refreshWatchSpO2Policy(){ watchSpO2Policy.update(WearSettings.watchSpO2ImmediateThreshold(this),WearSettings.watchSpO2RecoveryThreshold(this),WearSettings.watchSpO2ConfirmationMs(this)) }

    private suspend fun confirmHeartRate(triggerAt:Long){val delayMs=WearSettings.confirmDelayMs(this);delayUntil(triggerAt+delayMs);val second=runCatching{sensor.measureHeartRate()}.getOrNull();if(second!=null){processGeneric(HealthReading(timestampMs=System.currentTimeMillis(),heartRate=second));return};runCatching{sensor.reconnect()};delayUntil(triggerAt+delayMs+FINAL_RETRY_AFTER_CONFIRM_MS);val final=runCatching{sensor.measureHeartRate()}.getOrNull();if(final!=null)processGeneric(HealthReading(timestampMs=System.currentTimeMillis(),heartRate=final))else recordAndSend(HealthReading(timestampMs=System.currentTimeMillis(),valid=false))}

    private suspend fun followLowSpO2(observationStartedAt:Long){
        val interval=WearSettings.watchSpO2FollowupMs(this);val window=WearSettings.watchSpO2ConfirmationMs(this);var nextAt=maxOf(System.currentTimeMillis(),observationStartedAt+interval);val stopAt=observationStartedAt+window
        while(scope.isActive&&nextAt<=stopAt){delayUntil(nextAt);val value=runCatching{sensor.measureSpO2(WATCH_SPO2_MEASUREMENT_TIMEOUT_MS)}.getOrNull();if(value==null)recordAndSend(HealthReading(timestampMs=System.currentTimeMillis(),valid=false))else if(!processWatchSpO2(HealthReading(timestampMs=System.currentTimeMillis(),spo2=value)))return;nextAt+=interval}
    }

    private suspend fun processWatchSpO2(reading:HealthReading):Boolean{
        recordAndSend(reading);val spo2=reading.spo2;val immediate=WearSettings.watchSpO2ImmediateThreshold(this);val recovery=WearSettings.watchSpO2RecoveryThreshold(this);val confirmMin=WearSettings.watchSpO2ConfirmationMs(this)/60_000L;val decision=watchSpO2Policy.evaluate(reading.timestampMs,spo2,reading.valid);persistWatchSpO2Policy()
        when(decision){
            WatchSpO2Decision.ALARM_IMMEDIATE->{LocalAlarm.raise(this,AlertEvent(AlertType.SPO2_CRITICAL,reading.timestampMs,reading,"Saat SpO₂ kritik: %$spo2 (<%$immediate)"));return false}
            WatchSpO2Decision.ALARM_EARLY->{LocalAlarm.raise(this,AlertEvent(AlertType.SPO2_LOW_CONFIRMED,reading.timestampMs,reading,"Saat SpO₂ $confirmMin dakika boyunca %$recovery altından toparlanmadı: %$spo2"));return false}
            WatchSpO2Decision.RECOVERED->return false
            WatchSpO2Decision.NONE->Unit
        }
        val snapshot=watchSpO2Policy.snapshot();return spo2!=null&&spo2>=immediate&&spo2<recovery&&!snapshot.alarmLatched
    }

    private fun restoreWatchSpO2Policy(){val p=getSharedPreferences(WATCH_SPO2_POLICY_PREFS,MODE_PRIVATE);val since=if(p.contains(KEY_OBSERVATION_SINCE))p.getLong(KEY_OBSERVATION_SINCE,0L)else null;watchSpO2Policy.restore(WatchSpO2AlarmSnapshot(since,p.getBoolean(KEY_ALARM_LATCHED,false)))}
    private fun persistWatchSpO2Policy(){val s=watchSpO2Policy.snapshot();val since=s.observationSince;val e=getSharedPreferences(WATCH_SPO2_POLICY_PREFS,MODE_PRIVATE).edit().putBoolean(KEY_ALARM_LATCHED,s.alarmLatched);if(since==null)e.remove(KEY_OBSERVATION_SINCE)else e.putLong(KEY_OBSERVATION_SINCE,since);e.apply()}
    private suspend fun delayUntil(targetMs:Long){val remaining=targetMs-System.currentTimeMillis();if(remaining>0)delay(remaining)}
    private suspend fun measureHeartRateWithRetry():Int?{for(wait in WearSettings.retryWaitsMs(this)){if(wait>0)delay(wait);val v=runCatching{sensor.measureHeartRate()}.getOrNull();if(v!=null)return v;runCatching{sensor.reconnect()}};return null}
    private suspend fun measureSpO2WithRetry():Int?{for(wait in WearSettings.retryWaitsMs(this)){if(wait>0)delay(wait);val v=runCatching{sensor.measureSpO2()}.getOrNull();if(v!=null)return v;runCatching{sensor.reconnect()}};return null}
    private suspend fun processGeneric(reading:HealthReading){recordAndSend(reading);val alerts=engine.evaluate(reading.copy(spo2=null));persistEngineState();if(alerts.isNotEmpty())LocalAlarm.raise(this,alerts.first())}
    private suspend fun recordAndSend(reading:HealthReading){if(reading.valid&&(reading.spo2!=null||reading.heartRate!=null)){lastValidReadingAt=reading.timestampMs;WearStatusStore.update(this,reading)};runCatching{bridge.send(reading)}}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(intent?.action==ACTION_MEASURE_NOW)scope.launch{safeMeasureCycle()};return START_STICKY}
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("monitor","Orko Takip • Sağlık izleme",NotificationManager.IMPORTANCE_LOW))}

    companion object{
        const val ACTION_MEASURE_NOW="com.skhealth.guardian.wear.MEASURE_NOW"
        private const val MIN_SENSOR_STALE_MS=10*60_000L
        private const val FINAL_RETRY_AFTER_CONFIRM_MS=60_000L
        private const val WATCH_SPO2_MEASUREMENT_TIMEOUT_MS=20_000L
        private const val WATCH_SPO2_POLICY_PREFS="watch_spo2_policy"
        private const val KEY_OBSERVATION_SINCE="observation_since"
        private const val KEY_ALARM_LATCHED="alarm_latched"
    }
}
