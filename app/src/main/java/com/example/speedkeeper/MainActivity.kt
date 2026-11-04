package com.example.speedkeeper

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var speedText: TextView
    private lateinit var toggle: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val v = intent?.getDoubleExtra(SpeedService.EXTRA_MBPS, -1.0) ?: -1.0
            speedText.text = when {
                v == -2.0 -> "Wi-Fi নেই"
                v < 0 -> "Error"
                else -> String.format("%.2f MB/s", v)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        speedText = TextView(this).apply {
            text = "0.00 MB/s"
            textSize = 40f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        toggle = Button(this).apply {
            setOnClickListener { toggleService() }
        }
        root.addView(speedText)
        root.addView(toggle)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        refreshButton()
    }

    private fun toggleService() {
        val i = Intent(this, SpeedService::class.java)
        if (SpeedService.running) stopService(i) else startForegroundService(i)
        toggle.postDelayed({ refreshButton() }, 300)
    }

    private fun refreshButton() {
        toggle.text = if (SpeedService.running) "বন্ধ করো" else "চালু করো"
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SpeedService.ACTION_SPEED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        refreshButton()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }
}
