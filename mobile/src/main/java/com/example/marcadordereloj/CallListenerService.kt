package com.example.marcadordereloj

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class CallListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // Log para depuración en Android Studio
        Log.d("CallListenerService", "Mensaje recibido del reloj: ${messageEvent.path}")

        if (messageEvent.path == "/start_call") {
            val phoneNumber = String(messageEvent.data)
            
            // Mostrar un aviso visual inmediato en el teléfono
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Reloj solicitando llamada a: $phoneNumber", Toast.LENGTH_LONG).show()
            }

            // Lanzar la actividad Trampolín con prioridad máxima
            val intent = Intent(this, CallTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("phone_number", phoneNumber)
            }
            
            try {
                startActivity(intent)
                Log.d("CallListenerService", "Trampolín de llamada lanzado")
            } catch (e: Exception) {
                Log.e("CallListenerService", "Error al abrir el lanzador de llamadas: ${e.message}")
            }
        }
    }
}
