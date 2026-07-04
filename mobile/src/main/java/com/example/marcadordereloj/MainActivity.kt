package com.example.marcadordereloj

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AppLanguage(val label: String) {
    ENGLISH("English"), ESPANOL_LATINO("Español latino"), CATALA("Català"),
    GALEGO("Galego"), EUSKARA("Euskara"), BABLE("Bable"),
    DEUTSCH("Deutsch"), FRANCAIS("Français"), ITALIANO("Italiano"),
    HINDI("हिन्दी"), KOREAN("한국어"), JAPANESE("日本語")
}

enum class FontOption(val family: FontFamily, val label: String) {
    DEFAULT(FontFamily.Default, "Standard"),
    SERIF(FontFamily.Serif, "Serif"),
    MONOSPACE(FontFamily.Monospace, "Modern"),
    CURSIVE(FontFamily.Cursive, "Script")
}

data class ContactSlot(val name: String, val number: String)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("DannyPhonePrefs", Context.MODE_PRIVATE) }
            
            var currentLanguage by remember {
                val saved = sharedPrefs.getString("language", AppLanguage.ESPANOL_LATINO.name)
                mutableStateOf(AppLanguage.valueOf(saved ?: AppLanguage.ESPANOL_LATINO.name))
            }

            // Personalization States
            var customName by remember { mutableStateOf(sharedPrefs.getString("custom_name", "DannyPhone") ?: "DannyPhone") }
            var titleColor by remember { mutableLongStateOf(sharedPrefs.getLong("title_color", Color(0xFF1976D2).toArgb().toLong())) }
            var bgColor by remember { mutableLongStateOf(sharedPrefs.getLong("bg_color", Color(0xFFE3F2FD).toArgb().toLong())) }
            var selectedFont by remember { 
                val saved = sharedPrefs.getString("font_family", FontOption.CURSIVE.name)
                mutableStateOf(FontOption.valueOf(saved ?: FontOption.CURSIVE.name))
            }
            var bgImageUri by remember { mutableStateOf(sharedPrefs.getString("bg_image_uri", null)) }
            var useImageBg by remember { mutableStateOf(sharedPrefs.getBoolean("use_image_bg", false)) }

            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(titleColor.toInt()),
                background = Color(bgColor.toInt())
            )) {
                DannyPhoneApp(
                    currentLanguage = currentLanguage,
                    onLanguageChange = { 
                        currentLanguage = it
                        sharedPrefs.edit().putString("language", it.name).apply()
                    },
                    customName = customName,
                    onNameChange = {
                        customName = it
                        sharedPrefs.edit().putString("custom_name", it).apply()
                    },
                    titleColor = Color(titleColor.toInt()),
                    onTitleColorChange = {
                        titleColor = it.toArgb().toLong()
                        sharedPrefs.edit().putLong("title_color", it.toArgb().toLong()).apply()
                    },
                    backgroundColor = Color(bgColor.toInt()),
                    onBgColorChange = {
                        bgColor = it.toArgb().toLong()
                        sharedPrefs.edit().putLong("bg_color", it.toArgb().toLong()).apply()
                        useImageBg = false
                        sharedPrefs.edit().putBoolean("use_image_bg", false).apply()
                    },
                    selectedFont = selectedFont,
                    onFontChange = {
                        selectedFont = it
                        sharedPrefs.edit().putString("font_family", it.name).apply()
                    },
                    bgImageUri = bgImageUri,
                    onBgImageChange = {
                        bgImageUri = it
                        sharedPrefs.edit().putString("bg_image_uri", it).apply()
                        useImageBg = it != null
                        sharedPrefs.edit().putBoolean("use_image_bg", it != null).apply()
                    },
                    useImageBg = useImageBg,
                    onPermissionsClick = {
                        val perms = mutableListOf(Manifest.permission.CALL_PHONE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        requestPermissionLauncher.launch(perms.toTypedArray())
                    },
                    onOverlayClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    },
                    checkCallPerm = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    },
                    checkOverlayPerm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DannyPhoneApp(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    customName: String,
    onNameChange: (String) -> Unit,
    titleColor: Color,
    onTitleColorChange: (Color) -> Unit,
    backgroundColor: Color,
    onBgColorChange: (Color) -> Unit,
    selectedFont: FontOption,
    onFontChange: (FontOption) -> Unit,
    bgImageUri: String?,
    onBgImageChange: (String?) -> Unit,
    useImageBg: Boolean,
    onPermissionsClick: () -> Unit,
    onOverlayClick: () -> Unit,
    checkCallPerm: () -> Boolean,
    checkOverlayPerm: () -> Boolean
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("home") }
    val strings = getTranslations(currentLanguage)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(280.dp),
                    color = Color(0xFFE3F2FD)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = strings.drawerTitle, 
                            style = MaterialTheme.typography.titleLarge, 
                            color = Color(0xFF1976D2), 
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        NavigationDrawerItem(label = { Text(strings.home) }, selected = currentScreen == "home", onClick = { currentScreen = "home"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Home, null) })
                        NavigationDrawerItem(label = { Text(strings.contacts) }, selected = currentScreen == "contacts", onClick = { currentScreen = "contacts"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Contacts, null) })
                        NavigationDrawerItem(label = { Text(strings.language) }, selected = currentScreen == "language", onClick = { currentScreen = "language"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Language, null) })
                        NavigationDrawerItem(label = { Text(strings.personalizationTitle) }, selected = currentScreen == "settings", onClick = { currentScreen = "settings"; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Palette, null) })
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        if (currentScreen != "home") {
                            val title = when(currentScreen) {
                                "contacts" -> strings.contacts
                                "settings" -> strings.personalizationTitle
                                "language" -> strings.language
                                else -> strings.home
                            }
                            Text(title)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentScreen) {
                    "home" -> HomeScreen(strings, customName, titleColor, backgroundColor, selectedFont, bgImageUri, useImageBg)
                    "contacts" -> ContactsScreen(strings, currentLanguage, customName, titleColor, backgroundColor)
                    "language" -> LanguageScreen(strings, currentLanguage, onLanguageChange)
                    "settings" -> SettingsScreen(
                        strings, customName, onNameChange, titleColor, onTitleColorChange, 
                        backgroundColor, onBgColorChange, selectedFont, onFontChange,
                        onBgImageChange, onPermissionsClick, onOverlayClick, checkCallPerm(), checkOverlayPerm()
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    strings: TranslationStrings, 
    appName: String, 
    titleColor: Color, 
    backgroundColor: Color, 
    font: FontOption,
    bgImageUri: String?,
    useImageBg: Boolean
) {
    var showDonationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (useImageBg && bgImageUri != null) {
            val bitmap = remember(bgImageUri) {
                try {
                    val input = context.contentResolver.openInputStream(Uri.parse(bgImageUri))
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                } catch (e: Exception) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
        }

        Text(
            text = appName,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = font.family,
                fontSize = 72.sp,
                color = titleColor
            )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Button(
                onClick = { showDonationDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = titleColor),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text(strings.donationButton, color = if (titleColor.luminance() > 0.5f) Color.Black else Color.White)
            }
        }

        if (showDonationDialog) {
            AlertDialog(
                onDismissRequest = { showDonationDialog = false },
                title = { Text(strings.donationTitle) },
                text = { Text(strings.donationText) },
                confirmButton = {
                    TextButton(onClick = { showDonationDialog = false }) {
                        Text(strings.ok)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    strings: TranslationStrings, 
    currentLang: AppLanguage,
    customName: String,
    titleColor: Color,
    backgroundColor: Color
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("DannyPhonePrefs", Context.MODE_PRIVATE) }
    val contacts = remember {
        val list = mutableListOf<ContactSlot>()
        for (i in 0 until 12) {
            list.add(ContactSlot(
                sharedPrefs.getString("name_$i", "") ?: "",
                sharedPrefs.getString("number_$i", "") ?: ""
            ))
        }
        mutableStateListOf<ContactSlot>().apply { addAll(list) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.organizeAgenda, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { saveAndSyncAll(context, sharedPrefs, contacts, currentLang, strings, customName, titleColor, backgroundColor) }) {
                Icon(Icons.Default.Save, contentDescription = "Sync", tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(contacts) { index, contact ->
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                if (dragAmount.y < -30f && index > 0) {
                                    val item = contacts.removeAt(index)
                                    contacts.add(index - 1, item)
                                } else if (dragAmount.y > 30f && index < contacts.size - 1) {
                                    val item = contacts.removeAt(index)
                                    contacts.add(index + 1, item)
                                }
                            }
                        )
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val item = contacts.removeAt(index)
                                        contacts.add(index - 1, item)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardArrowUp, null) }
                            
                            Text("${index + 1}", fontWeight = FontWeight.Bold)

                            IconButton(
                                onClick = {
                                    if (index < contacts.size - 1) {
                                        val item = contacts.removeAt(index)
                                        contacts.add(index + 1, item)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardArrowDown, null) }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            TextField(
                                value = contact.name,
                                onValueChange = { contacts[index] = contact.copy(name = it) },
                                label = { Text(strings.name) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                            )
                            TextField(
                                value = contact.number,
                                onValueChange = { contacts[index] = contact.copy(number = it) },
                                label = { Text(strings.phone) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                            )
                        }
                        
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            modifier = Modifier.padding(start = 8.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    strings: TranslationStrings,
    customName: String,
    onNameChange: (String) -> Unit,
    titleColor: Color,
    onTitleColorChange: (Color) -> Unit,
    backgroundColor: Color,
    onBgColorChange: (Color) -> Unit,
    selectedFont: FontOption,
    onFontChange: (FontOption) -> Unit,
    onBgImageChange: (String?) -> Unit,
    onPermissionsClick: () -> Unit,
    onOverlayClick: () -> Unit,
    hasCall: Boolean,
    hasOverlay: Boolean
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        if (uri != null) {
            onBgImageChange(uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).background(Color.White)) {
        Text(strings.personalizationTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = customName,
            onValueChange = onNameChange,
            label = { Text(strings.customNameLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(strings.fontStyleLabel, style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FontOption.values().forEach { option ->
                FilterChip(
                    selected = selectedFont == option,
                    onClick = { onFontChange(option) },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(strings.titleColorLabel, style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Color(0xFF1976D2), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800), Color.Black, Color.DarkGray).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onTitleColorChange(color) }
                        .padding(if (titleColor == color) 4.dp else 0.dp)
                ) {
                    if (titleColor == color) Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(0.5f)))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(strings.backgroundStyleLabel, style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(strings.selectImageBtn) }
            Button(onClick = { onBgColorChange(Color(0xFFE3F2FD)) }, modifier = Modifier.weight(1f)) { Text(strings.selectColorBtn) }
        }

        Spacer(modifier = Modifier.height(32.dp))
        PermissionCard(strings.callPermission, hasCall, onPermissionsClick, strings.activate)
        PermissionCard(strings.overlayPermission, hasOverlay, onOverlayClick, strings.activate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCard(title: String, isGranted: Boolean, onClick: () -> Unit, btnText: String) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error, 
                null, 
                tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            if (!isGranted) Text(btnText, color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LanguageScreen(strings: TranslationStrings, currentLanguage: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.White)) {
        Text(strings.language, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        AppLanguage.values().forEach { lang ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (lang == currentLanguage),
                        onClick = { onLanguageChange(lang) }
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (lang == currentLanguage),
                    onClick = { onLanguageChange(lang) }
                )
                Text(
                    text = lang.label,
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

fun saveAndSyncAll(
    context: Context, 
    prefs: android.content.SharedPreferences, 
    contacts: List<ContactSlot>, 
    lang: AppLanguage, 
    strings: TranslationStrings,
    customName: String,
    titleColor: Color,
    backgroundColor: Color
) {
    prefs.edit().apply {
        contacts.forEachIndexed { i, contact ->
            putString("name_$i", contact.name)
            putString("number_$i", contact.number)
        }
        apply()
    }
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create("/speed_dial")
            val dataMap = putDataMapReq.dataMap
            dataMap.putStringArray("names", contacts.map { it.name }.toTypedArray())
            dataMap.putStringArray("numbers", contacts.map { it.number }.toTypedArray())
            dataMap.putString("language", lang.name)
            dataMap.putString("app_name", customName)
            dataMap.putLong("title_color", titleColor.toArgb().toLong())
            dataMap.putLong("bg_color", backgroundColor.toArgb().toLong())
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()).await()
            launch(Dispatchers.Main) { Toast.makeText(context, strings.syncSuccess, Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            launch(Dispatchers.Main) { Toast.makeText(context, strings.syncError, Toast.LENGTH_SHORT).show() }
        }
    }
}

data class TranslationStrings(
    val drawerTitle: String, val home: String, val contacts: String, val settings: String, val language: String,
    val donationButton: String, val donationTitle: String, val donationText: String, val ok: String,
    val organizeAgenda: String, val name: String, val phone: String, val syncSuccess: String, val syncError: String,
    val callPermission: String, val overlayPermission: String, val activate: String, val note: String,
    val personalizationTitle: String, val customNameLabel: String, val fontStyleLabel: String, val titleColorLabel: String, val backgroundStyleLabel: String, val selectImageBtn: String, val selectColorBtn: String
)

fun getTranslations(lang: AppLanguage): TranslationStrings {
    return when(lang) {
        AppLanguage.ENGLISH -> TranslationStrings("Menu", "Home", "Contacts", "Style", "Language", "please, make a donation", "Support DannyPhone", "If you liked my application you can donate the amount you consider by doing a bizzum to +34655533304", "OK", "Organize Agenda", "Name", "Phone", "Synced!", "Error", "Calls", "Overlay", "ACTIVATE", "Note: Activate permissions.", "Style Settings", "App Name", "Font", "Text Color", "Background", "Gallery", "Color")
        AppLanguage.ESPANOL_LATINO -> TranslationStrings("Menú", "Inicio", "Contactos", "Estilo", "Idioma", "por favor, haz una donación", "Apoya a DannyPhone", "Si te gustó mi aplicación puedes donar la cantidad que consideres haciendo un bizzum al +34655533304", "Aceptar", "Organizar Agenda", "Nombre", "Celular", "¡Sincronizado!", "Error", "Llamadas", "Superposición", "ACTIVAR", "Nota: Activa los permisos.", "Ajustes de Estilo", "Nombre App", "Fuente", "Color", "Fondo", "Galería", "Color")
        AppLanguage.CATALA -> TranslationStrings("Menú", "Inici", "Contactes", "Estil", "Idioma", "si us plau, fes una donació", "Ajuda a DannyPhone", "Si t'ha agradat la meva aplicació pots donar per Bizzum al +34655533304", "Acceptar", "Organitzar Agenda", "Nom", "Telèfon", "Sincronitzat!", "Error", "Trucades", "Superposició", "ACTIVAR", "Nota: Activa els permisos.", "Ajustos d'Estil", "Nom App", "Font", "Color Text", "Fons", "Galeria", "Color")
        AppLanguage.GALEGO -> TranslationStrings("Menú", "Inicio", "Contactos", "Estilo", "Lingua", "por favor, fai unha doazón", "Apoia a DannyPhone", "Se che gustou a miña aplicación podes doar por Bizzum ao +34655533304", "Aceptar", "Organizar Axenda", "Nome", "Teléfono", "Sincronizado!", "Erro", "Chamadas", "Superposición", "ACTIVAR", "Nota: Activa os permisos.", "Axustes de Estilo", "Nome App", "Fonte", "Color Texto", "Fondo", "Galería", "Color")
        AppLanguage.EUSKARA -> TranslationStrings("Menua", "Hasiera", "Agenda", "Pertsonalizatu", "Hizkuntza", "mesedez, egin dohaintza bat", "DannyPhone Lagundu", "Nire aplicación gustatu bazaizu, bizzum bat egin dezakezu +34655533304 zenbakira", "Onartu", "Agenda Antolatu", "Izena", "Telefonoa", "Sinkronizatuta!", "Errorea", "Deiak", "Gainjartzea", "AKTIBATU", "Nota: Baimenak aktibatu.", "Estilo Ezarpenak", "App Izena", "Letra", "Testu Kolorea", "Atzealdea", "Galeria", "Kolorea")
        AppLanguage.BABLE -> TranslationStrings("Menú DannyPhone", "Entamu", "Axenda", "Estilu", "Llingua", "por favor, fai una donación", "Sofita a DannyPhone", "Si te prestó la mio aplicación pues donar por Bizzum al +34655533304", "Aceptar", "Organizar Axenda", "Nome", "Teléfanu", "¡Sincronizáu!", "Error", "Llamaes", "Superposición", "ACTIVAR", "Nota: Activa los permisos.", "Axustes d'Estilu", "Nome App", "Fonte", "Color Testu", "Fondu", "Galería", "Color")
        AppLanguage.DEUTSCH -> TranslationStrings("Menü", "Startseite", "Kontakte", "Stil", "Sprache", "bitte spenden Sie", "Unterstützen Sie DannyPhone", "Wenn Ihnen meine Anwendung gefallen hat, können Sie den Betrag spenden, den Sie für angemessen halten, indem Sie ein Bizzum an +34655533304 senden", "OK", "Agenda organisieren", "Name", "Telefon", "Synchronisiert!", "Fehler", "Anrufe", "Überlagerung", "AKTIVIEREN", "Nota: Baimenak aktibatu.", "Stil-Einstellungen", "App-Name", "Schriftart", "Farbe", "Hintergrund", "Galerie", "Farbe")
        AppLanguage.FRANCAIS -> TranslationStrings("Menu", "Accueil", "Contacts", "Style", "Langue", "s'il vous plaît, faites un don", "Soutenir DannyPhone", "Si vous avez aimé mon application, vous pouvez donner le montant que vous considérez en haciendo un bizzum au +34655533304", "OK", "Organiser l'agenda", "Nom", "Téléphone", "Synchronisé !", "Erreur", "Appels", "Superposition", "ACTIVER", "Note: Activa les permisos.", "Paramètres de style", "Nom de l'app", "Police", "Couleur", "Fond", "Galerie", "Couleur")
        AppLanguage.ITALIANO -> TranslationStrings("Menu", "Home", "Contatti", "Stile", "Lingua", "per favore, fai una donazione", "Sostieni DannyPhone", "Se ti è piaciuta la mia aplicación puoi donare l'importo que ritieni opportuno haciendo un bizzum al +34655533304", "OK", "Organizza agenda", "Nom", "Telefono", "Sincronizzato!", "Errore", "Chiamate", "Sovrapposición", "ATTIVA", "Note: Activa i permessi.", "Impostaciones stile", "Nome App", "Font", "Colore", "Sfondo", "Galleria", "Colore")
        AppLanguage.HINDI -> TranslationStrings("मेनू", "होम", "संपर्क", "शैली", "भाषा", "कृपया दान करें", "DannyPhone का समर्थन करें", "यदि आपको मेरा एप्लिकेशन पसंद आया है, तो आप +34655533304 पर बिज़म करके अपनी इच्छानुसार राशि दान कर सकते हैं", "ठीक है", "एजेंडा व्यवस्थित करें", "नाम", "फ़ोन", "सिंक हो गया!", "त्रुटि", "कॉल", "ओवरले", "सक्रिय करें", "ध्यान दें: अनुमति सक्रिय करें।", "शैली सेटिंग्स", "ऐप का नाम", "फ़ॉन्ट", "रंग", "पृष्ठभूमि", "गैलरी", "रंग")
        AppLanguage.KOREAN -> TranslationStrings("메뉴", "홈", "연락처", "스타일", "언어", "기부해 주세요", "DannyPhone 지원", "제 애플리케이션이 마음에 드셨다면 +34655533304로 bizzum을 보내 원하는 금액을 기부하실 수 있습니다", "확인", "일정 정리", "이름", "전화번호", "동기화됨!", "오류", "전화", "오버레이", "활성화", "참고: 권한을 활성화하십시오.", "스타일 설정", "앱 이름", "글꼴", "색상", "배경", "갤러리", "색상")
        AppLanguage.JAPANESE -> TranslationStrings("メニュー", "ホーム", "連絡先", "スタイル", "言語", "寄付をお願いします", "DannyPhone をサポート", "私のアプリを気に入っていただけたなら, +34655533304 に bizzum を送って, お好きな金額 को寄付していただけます", "OK", "アジェンダの整理", "名前", "電話番号", "同期しました！", "エラー", "通話", "オーバーレイ", "有効にする", "注意：権限を有効にしてください。", "スタイル設定", "アプリ名", "フォント", "色", "背景", "ギャラリー", "色")
    }
}
