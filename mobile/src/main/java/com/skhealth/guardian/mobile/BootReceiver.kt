package com.skhealth.guardian.mobile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
class BootReceiver: BroadcastReceiver(){ override fun onReceive(context: Context, intent: Intent){ ContextCompat.startForegroundService(context, Intent(context, WatchdogService::class.java)) } }
