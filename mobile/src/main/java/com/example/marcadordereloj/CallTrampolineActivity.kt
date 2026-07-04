package com.example.marcadordereloj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class CallTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prioridad máxima: Despertar teléfono y saltar bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val rawNumber = intent.getStringExtra("phone_number")
        if (!rawNumber.isNullOrEmpty()) {
            // Limpieza absoluta: SOLO números y el signo + literal
            val phoneNumber = rawNumber.filter { it.isDigit() || it == '+' }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    // ACTION_CALL: La orden mágica para llamar SIN intervención
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(callIntent)
                    // Toast de confirmación de que se ha enviado la orden de marca
                    Toast.makeText(this, "Llamando a $phoneNumber...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // Si falla, mostramos el error exacto en un Toast largo
                    Toast.makeText(this, "Error de Sistema: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "⚠️ ERROR: No has concedido el permiso 'Hacer llamadas' en DannyPhone", Toast.LENGTH_LONG).show()
            }
        }
        
        // Cerramos esta pantalla invisible tras 1 segundo para no estorbar
        window.decorView.postDelayed({ finish() }, 1000)
    }
}
