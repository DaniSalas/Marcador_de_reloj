package com.example.marcadordereloj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class CallListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("CallListenerService", "Message received: ${messageEvent.path}")
        if (messageEvent.path == "/start_call") {
            val phoneNumber = String(messageEvent.data)
            makeCall(phoneNumber)
        }
    }

    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            Log.e("CallListenerService", "Permission CALL_PHONE not granted")
            // Ideally, send a message back to the watch or show a notification on phone
        }
    }
}
