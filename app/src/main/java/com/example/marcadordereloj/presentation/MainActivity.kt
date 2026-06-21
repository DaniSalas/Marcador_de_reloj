package com.example.marcadordereloj.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.example.marcadordereloj.presentation.theme.MarcadorDeRelojTheme
import com.google.android.gms.wearable.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private val _contacts = mutableStateListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            MarcadorDeRelojTheme {
                MainPager(_contacts)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        
        // Carga inicial de datos guardados
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { dataItems ->
            for (item in dataItems) {
                if (item.uri.path == "/speed_dial") {
                    updateContactsFromDataItem(DataMapItem.fromDataItem(item))
                }
            }
            dataItems.release()
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Usamos un bucle for manual para evitar la ambigüedad del iterador en DataEventBuffer
        for (i in 0 until dataEvents.count) {
            val event = dataEvents[i]
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/speed_dial") {
                updateContactsFromDataItem(DataMapItem.fromDataItem(event.dataItem))
            }
        }
    }

    private fun updateContactsFromDataItem(dataMapItem: DataMapItem) {
        val dataMap = dataMapItem.dataMap
        val names = dataMap.getStringArray("names") ?: emptyArray()
        val numbers = dataMap.getStringArray("numbers") ?: emptyArray()
        
        _contacts.clear()
        for (i in names.indices) {
            if (i < numbers.size) {
                if (names[i].isNotEmpty() || numbers[i].isNotEmpty()) {
                    _contacts.add(Contact(names[i], numbers[i]))
                }
            }
        }
    }
}

data class Contact(val name: String, val number: String)

@Composable
fun MainPager(contacts: List<Contact>) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        HorizontalPager(state = pagerState) { page ->
            if (page == 0) {
                DialerApp()
            } else {
                ContactsScreen(contacts)
            }
        }
        
        // Adaptador para el indicador de página en Wear OS 1.4
        val pageIndicatorState = remember {
            object : PageIndicatorState {
                override val pageCount: Int get() = 2
                override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
                override val selectedPage: Int get() = pagerState.currentPage
            }
        }

        HorizontalPageIndicator(
            pageIndicatorState = pageIndicatorState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        )
    }
}

@Composable
fun ContactsScreen(contacts: List<Contact>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Agenda Rápida",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        
        if (contacts.isEmpty()) {
            item {
                Text(
                    "Sin contactos.\nConfigura en DannyPhone.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(contacts) { contact ->
                Chip(
                    onClick = {
                        if (contact.number.isNotEmpty()) {
                            coroutineScope.launch {
                                sendCallRequestToPhone(context, contact.number)
                            }
                        }
                    },
                    label = { 
                        Text(
                            text = contact.name.ifEmpty { "Sin nombre" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis 
                        ) 
                    },
                    secondaryLabel = { Text(contact.number) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DialerApp() {
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (phoneNumber.isEmpty()) "Marcar..." else phoneNumber,
            style = MaterialTheme.typography.title3,
            color = if (phoneNumber.isEmpty()) Color.Gray else Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        Button(
                            onClick = {
                                when (key) {
                                    "⌫" -> if (phoneNumber.isNotEmpty()) phoneNumber = phoneNumber.dropLast(1)
                                    "C" -> phoneNumber = ""
                                    else -> if (phoneNumber.length < 15) phoneNumber += key
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (key == "⌫" || key == "C") Color.DarkGray else MaterialTheme.colors.surface
                            )
                        ) {
                            Text(key, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                if (phoneNumber.isNotEmpty()) {
                    coroutineScope.launch {
                        sendCallRequestToPhone(context, phoneNumber)
                    }
                }
            },
            modifier = Modifier.size(width = 80.dp, height = 36.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
        ) {
            Text("Llamar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

suspend fun sendCallRequestToPhone(context: Context, number: String) {
    try {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)
        val nodes = nodeClient.connectedNodes.await()

        if (nodes.isEmpty()) {
            Toast.makeText(context, "Sin conexión", Toast.LENGTH_SHORT).show()
            return
        }

        for (node in nodes) {
            messageClient.sendMessage(node.id, "/start_call", number.toByteArray()).await()
        }
        Toast.makeText(context, "Llamada enviada", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
    }
}
