package com.example.marcadordereloj

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onPermissionsClick = {
                        requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    },
                    hasPermission = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }
}

data class Contact(val name: String, val number: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onPermissionsClick: () -> Unit, hasPermission: Boolean) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("SpeedDial", Context.MODE_PRIVATE) }
    
    val contacts = remember {
        mutableStateListOf<Contact>().apply {
            for (i in 0 until 12) {
                add(Contact(
                    sharedPrefs.getString("name_$i", "") ?: "",
                    sharedPrefs.getString("number_$i", "") ?: ""
                ))
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DannyPhone - Agenda") },
                actions = {
                    IconButton(onClick = { 
                        syncWithWear(context, contacts)
                        saveContacts(sharedPrefs, contacts)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar y Sincronizar")
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Permisos de Llamada") },
                            onClick = {
                                showMenu = false
                                onPermissionsClick()
                            },
                            trailingIcon = {
                                if (hasPermission) Text("✅")
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Marcación Rápida (12 contactos)", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(contacts) { index, contact ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.padding(end = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            TextField(
                                value = contact.name,
                                onValueChange = { contacts[index] = contact.copy(name = it) },
                                label = { Text("Nombre") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextField(
                                value = contact.number,
                                onValueChange = { contacts[index] = contact.copy(number = it) },
                                label = { Text("Teléfono") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

fun saveContacts(prefs: android.content.SharedPreferences, contacts: List<Contact>) {
    prefs.edit().apply {
        contacts.forEachIndexed { i, contact ->
            putString("name_$i", contact.name)
            putString("number_$i", contact.number)
        }
        apply()
    }
}

fun syncWithWear(context: Context, contacts: List<Contact>) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create("/speed_dial")
            val dataMap = putDataMapReq.dataMap
            
            val names = contacts.map { it.name }.toTypedArray()
            val numbers = contacts.map { it.number }.toTypedArray()
            
            dataMap.putStringArray("names", names)
            dataMap.putStringArray("numbers", numbers)
            dataMap.putLong("timestamp", System.currentTimeMillis())
            
            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq).await()
            
            launch(Dispatchers.Main) {
                Toast.makeText(context, "Sincronizado con el reloj", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            launch(Dispatchers.Main) {
                Toast.makeText(context, "Error al sincronizar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
