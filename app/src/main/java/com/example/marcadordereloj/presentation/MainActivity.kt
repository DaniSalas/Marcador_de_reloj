package com.example.marcadordereloj.presentation

import android.content.Context
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.graphics.toArgb
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

enum class AppLanguage { ENGLISH, ESPANOL_LATINO, CATALA, GALEGO, EUSKARA, BABLE, DEUTSCH, FRANCAIS, ITALIANO, HINDI, KOREAN, JAPANESE }

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private val _contacts = mutableStateListOf<Contact>()
    private var _currentLanguage by mutableStateOf(AppLanguage.ESPANOL_LATINO)
    private var _customAppName by mutableStateOf("DannyPhone")
    private var _titleColor by mutableStateOf(Color.White)
    private var _backgroundColor by mutableStateOf(Color.Black)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            MarcadorDeRelojTheme {
                MainPager(_contacts, _currentLanguage, _customAppName, _titleColor, _backgroundColor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buffer ->
            for (i in 0 until buffer.count) {
                val item = buffer.get(i)
                if (item.uri.path == "/speed_dial") {
                    updateFromDataItem(DataMapItem.fromDataItem(item))
                }
            }
            buffer.release()
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (i in 0 until dataEvents.count) {
            val event = dataEvents.get(i)
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/speed_dial") {
                updateFromDataItem(DataMapItem.fromDataItem(event.dataItem))
            }
        }
    }

    private fun updateFromDataItem(dataMapItem: DataMapItem) {
        val dataMap = dataMapItem.dataMap
        
        if (dataMap.containsKey("language")) {
            val langName = dataMap.getString("language", AppLanguage.ESPANOL_LATINO.name)
            _currentLanguage = try { AppLanguage.valueOf(langName) } catch (e: Exception) { AppLanguage.ESPANOL_LATINO }
        }

        if (dataMap.containsKey("app_name")) {
            _customAppName = dataMap.getString("app_name", "DannyPhone")
        }

        if (dataMap.containsKey("title_color")) {
            _titleColor = Color(dataMap.getLong("title_color", Color.White.toArgb().toLong()).toInt())
        }

        if (dataMap.containsKey("bg_color")) {
            _backgroundColor = Color(dataMap.getLong("bg_color", Color.Black.toArgb().toLong()).toInt())
        }
        
        if (dataMap.containsKey("names") && dataMap.containsKey("numbers")) {
            val names = dataMap.getStringArray("names") ?: emptyArray()
            val numbers = dataMap.getStringArray("numbers") ?: emptyArray()
            _contacts.clear()
            val count = if (names.size < numbers.size) names.size else numbers.size
            for (i in 0 until count) {
                if (names[i].isNotEmpty() || numbers[i].isNotEmpty()) {
                    _contacts.add(Contact(names[i], numbers[i]))
                }
            }
        }
    }
}

data class Contact(val name: String, val number: String)

@Composable
fun MainPager(contacts: List<Contact>, language: AppLanguage, appName: String, titleColor: Color, backgroundColor: Color) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val strings = getWatchTranslations(language)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        HorizontalPager(state = pagerState) { page ->
            if (page == 0) {
                DialerApp(strings, appName, titleColor)
            } else {
                ContactsScreen(contacts, strings, titleColor)
            }
        }
        
        val pageIndicatorState = remember {
            object : PageIndicatorState {
                override val pageCount: Int get() = 2
                override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
                override val selectedPage: Int get() = pagerState.currentPage
            }
        }

        HorizontalPageIndicator(
            pageIndicatorState = pageIndicatorState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 1.dp)
        )
    }
}

@Composable
fun ContactsScreen(contacts: List<Contact>, strings: WatchTranslations, titleColor: Color) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = strings.agendaTitle,
                style = MaterialTheme.typography.caption1,
                color = titleColor,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        
        if (contacts.isEmpty()) {
            item {
                Text(
                    strings.noContacts,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            items(contacts) { contact ->
                Chip(
                    onClick = {
                        if (contact.number.isNotEmpty()) {
                            coroutineScope.launch {
                                sendCallRequestToPhone(context, contact.number, strings.sending)
                            }
                        }
                    },
                    label = { 
                        Text(
                            text = contact.name.ifEmpty { strings.noName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis 
                        ) 
                    },
                    secondaryLabel = { Text(contact.number) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DialerApp(strings: WatchTranslations, appName: String, titleColor: Color) {
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (phoneNumber.isEmpty()) appName else phoneNumber,
            style = MaterialTheme.typography.title2.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), 
            color = if (phoneNumber.isEmpty()) titleColor else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(1.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(0.96f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    row.forEach { key ->
                        Button(
                            onClick = {
                                when (key) {
                                    "⌫" -> if (phoneNumber.isNotEmpty()) phoneNumber = phoneNumber.dropLast(1)
                                    "C" -> phoneNumber = ""
                                    else -> if (phoneNumber.length < 15) phoneNumber += key
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp), 
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = when (key) {
                                    "⌫" -> Color.DarkGray
                                    "C" -> Color(0xFFB00020)
                                    else -> MaterialTheme.colors.surface
                                }
                            )
                        ) {
                            Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = {
                if (phoneNumber.isNotEmpty()) {
                    coroutineScope.launch {
                        sendCallRequestToPhone(context, phoneNumber, strings.sending)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.80f) 
                .height(36.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
        ) {
            Text(strings.callButton, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

suspend fun sendCallRequestToPhone(context: Context, number: String, sendingMsg: String) {
    try {
        val nodeClient = Wearable.getNodeClient(context)
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) return
        val messageClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            messageClient.sendMessage(node.id, "/start_call", number.toByteArray()).await()
        }
        Toast.makeText(context, sendingMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {}
}

data class WatchTranslations(
    val dialPlaceholder: String,
    val callButton: String,
    val agendaTitle: String,
    val noContacts: String,
    val noName: String,
    val sending: String
)

fun getWatchTranslations(lang: AppLanguage): WatchTranslations {
    return when(lang) {
        AppLanguage.ENGLISH -> WatchTranslations("Dial", "CALL", "Contacts", "No contacts. Check Phone.", "No name", "Calling...")
        AppLanguage.ESPANOL_LATINO -> WatchTranslations("Marcar", "LLAMAR", "Contactos", "Sin contactos. Mira el móvil.", "Sin nombre", "Llamando...")
        AppLanguage.CATALA -> WatchTranslations("Marcar", "TRUCAR", "Contactes", "Sense contactes. Mira el mòbil.", "Sense nom", "Trucant...")
        AppLanguage.GALEGO -> WatchTranslations("Marcar", "CHAMAR", "Contactos", "Sen contactos. Mira o móbil.", "Sen nome", "Chamando...")
        AppLanguage.EUSKARA -> WatchTranslations("Markatu", "DEITU", "Agenda", "Kontakturik ez. Begiratu mugikorra.", "Izenik gabe", "Deitzen...")
        AppLanguage.BABLE -> WatchTranslations("Marcar", "LLAMAR", "Axenda", "Ensin contautos. Mira'l móvil.", "Ensin nome", "Llamando...")
        AppLanguage.DEUTSCH -> WatchTranslations("Wählen", "ANRUFEN", "Kontakte", "Keine Kontakte. Handy prüfen.", "Kein Name", "Anrufen...")
        AppLanguage.FRANCAIS -> WatchTranslations("Composer", "APPELER", "Contacts", "Pas de contacts. Vérifier tel.", "Sans nom", "Appel en cours...")
        AppLanguage.ITALIANO -> WatchTranslations("Componi", "CHIAMA", "Contatti", "Nessun contacto. Controlla tel.", "Senza nome", "Chiamata...")
        AppLanguage.HINDI -> WatchTranslations("डायल", "कॉल करें", "संपर्क", "कोई संपर्क नहीं. फोन चेक करें।", "कोई नाम नहीं", "कॉल हो रहा है...")
        AppLanguage.KOREAN -> WatchTranslations("다이얼", "전화 걸기", "연락처", "연락처가 없습니다. 폰을 확인하세요.", "이름 없음", "전화 거는 중...")
        AppLanguage.JAPANESE -> WatchTranslations("ダイヤル", "電話をかける", "連絡先", "連絡先がありません。スマホを確認してください。", "名前なし", "電話をかけています...")
    }
}
