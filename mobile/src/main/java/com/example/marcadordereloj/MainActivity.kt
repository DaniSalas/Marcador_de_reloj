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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

data class ContactSlot(val id: Int, val name: String, val number: String)

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

            // Personalization States (Phone)
            var customName by remember { mutableStateOf(sharedPrefs.getString("custom_name", "DannyPhone") ?: "DannyPhone") }
            var titleColor by remember { mutableLongStateOf(sharedPrefs.getLong("title_color", Color(0xFF1976D2).toArgb().toLong())) }
            var bgColor by remember { mutableLongStateOf(sharedPrefs.getLong("bg_color", Color(0xFFE3F2FD).toArgb().toLong())) }
            
            // Personalization States (Watch)
            var watchTitleColor by remember { mutableLongStateOf(sharedPrefs.getLong("watch_title_color", Color.White.toArgb().toLong())) }
            var watchBgColor by remember { mutableLongStateOf(sharedPrefs.getLong("watch_bg_color", Color.Black.toArgb().toLong())) }

            var selectedFont by remember { 
                val saved = sharedPrefs.getString("font_family", FontOption.CURSIVE.name)
                mutableStateOf(FontOption.valueOf(saved ?: FontOption.CURSIVE.name))
            }
            var fontSize by remember { mutableFloatStateOf(sharedPrefs.getFloat("font_size", 72f)) }
            var bgImageUri by remember { mutableStateOf(sharedPrefs.getString("bg_image_uri", null)) }
            var useImageBg by remember { mutableStateOf(sharedPrefs.getBoolean("use_image_bg", false)) }

            val contacts = remember {
                val list = mutableListOf<ContactSlot>()
                for (i in 0 until 12) {
                    list.add(ContactSlot(
                        i,
                        sharedPrefs.getString("name_$i", "") ?: "",
                        sharedPrefs.getString("number_$i", "") ?: ""
                    ))
                }
                androidx.compose.runtime.snapshots.SnapshotStateList<ContactSlot>().apply { addAll(list) }
            }

            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(titleColor.toInt()),
                background = Color(bgColor.toInt())
            )) {
                DannyPhoneApp(
                    currentLanguage = currentLanguage,
                    onLanguageChange = { 
                        currentLanguage = it
                        sharedPrefs.edit().putString("language", it.name).apply()
                        syncSettingsToWatch(context, it, customName, Color(watchTitleColor.toInt()), Color(watchBgColor.toInt()))
                    },
                    customName = customName,
                    onNameChange = {
                        customName = it
                        sharedPrefs.edit().putString("custom_name", it).apply()
                        syncSettingsToWatch(context, currentLanguage, it, Color(watchTitleColor.toInt()), Color(watchBgColor.toInt()))
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
                    watchTitleColor = Color(watchTitleColor.toInt()),
                    onWatchTitleColorChange = {
                        watchTitleColor = it.toArgb().toLong()
                        sharedPrefs.edit().putLong("watch_title_color", it.toArgb().toLong()).apply()
                        syncSettingsToWatch(context, currentLanguage, customName, it, Color(watchBgColor.toInt()))
                    },
                    watchBackgroundColor = Color(watchBgColor.toInt()),
                    onWatchBgColorChange = {
                        watchBgColor = it.toArgb().toLong()
                        sharedPrefs.edit().putLong("watch_bg_color", it.toArgb().toLong()).apply()
                        syncSettingsToWatch(context, currentLanguage, customName, Color(watchTitleColor.toInt()), it)
                    },
                    selectedFont = selectedFont,
                    onFontChange = {
                        selectedFont = it
                        sharedPrefs.edit().putString("font_family", it.name).apply()
                    },
                    fontSize = fontSize,
                    onFontSizeChange = {
                        fontSize = it
                        sharedPrefs.edit().putFloat("font_size", it).apply()
                    },
                    bgImageUri = bgImageUri,
                    onBgImageChange = {
                        bgImageUri = it
                        sharedPrefs.edit().putString("bg_image_uri", it).apply()
                        useImageBg = it != null
                        sharedPrefs.edit().putBoolean("use_image_bg", it != null).apply()
                    },
                    useImageBg = useImageBg,
                    contacts = contacts,
                    onAutoSync = {
                        syncSettingsToWatch(context, currentLanguage, customName, Color(watchTitleColor.toInt()), Color(watchBgColor.toInt()))
                    },
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
    watchTitleColor: Color,
    onWatchTitleColorChange: (Color) -> Unit,
    watchBackgroundColor: Color,
    onWatchBgColorChange: (Color) -> Unit,
    selectedFont: FontOption,
    onFontChange: (FontOption) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    bgImageUri: String?,
    onBgImageChange: (String?) -> Unit,
    useImageBg: Boolean,
    contacts: androidx.compose.runtime.snapshots.SnapshotStateList<ContactSlot>,
    onAutoSync: () -> Unit,
    onPermissionsClick: () -> Unit,
    onOverlayClick: () -> Unit,
    checkCallPerm: () -> Boolean,
    checkOverlayPerm: () -> Boolean
) {
    var currentScreen by remember { mutableStateOf("home") }
    val strings = getTranslations(currentLanguage)
    var showDonationDialog by remember { mutableStateOf(false) }

    var lastScreen by remember { mutableStateOf(currentScreen) }
    LaunchedEffect(currentScreen) {
        if (lastScreen == "settings" || lastScreen == "language" || lastScreen == "watch_settings") {
            onAutoSync()
        }
        lastScreen = currentScreen
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

    Scaffold(
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    strings, customName, titleColor, backgroundColor, selectedFont, fontSize, bgImageUri, useImageBg,
                    onNavigate = { currentScreen = it },
                    onDonationClick = { showDonationDialog = true }
                )
                "contacts" -> ContactsScreen(
                    strings, currentLanguage, customName, watchTitleColor, watchBackgroundColor,
                    titleColor, backgroundColor, useImageBg, bgImageUri, contacts, onBack = { currentScreen = "home" }
                )
                "language" -> LanguageScreen(
                    strings, currentLanguage, onLanguageChange, 
                    titleColor, backgroundColor, useImageBg, bgImageUri, onBack = { currentScreen = "home" }
                )
                "permissions" -> PermissionsScreen(
                    strings, onPermissionsClick, onOverlayClick, checkCallPerm(), checkOverlayPerm(),
                    titleColor, backgroundColor, useImageBg, bgImageUri, onBack = { currentScreen = "home" }
                )
                "settings" -> SettingsScreen(
                    strings, customName, onNameChange, titleColor, onTitleColorChange, 
                    backgroundColor, onBgColorChange, selectedFont, onFontChange,
                    fontSize, onFontSizeChange,
                    useImageBg, bgImageUri, onBgImageChange, onBack = { currentScreen = "home" }
                )
                "watch_settings" -> WatchSettingsScreen(
                    strings, watchTitleColor, onWatchTitleColorChange, 
                    watchBackgroundColor, onWatchBgColorChange,
                    titleColor, backgroundColor, useImageBg, bgImageUri, onBack = { currentScreen = "home" }
                )
            }
        }
    }
}

@Composable
fun ScreenWrapper(
    title: String,
    titleColor: Color,
    backgroundColor: Color,
    useImageBg: Boolean,
    bgImageUri: String?,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo (Imagen o Color)
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

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sección Superior: Título
            Box(
                modifier = Modifier.weight(0.4f).fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = titleColor)
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = titleColor,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // Sección Inferior: Contenido (Zona blanca curva)
            Column(
                modifier = Modifier
                    .weight(2.2f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color.White.copy(alpha = 0.85f)),
                content = content
            )
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
    fontSize: Float,
    bgImageUri: String?,
    useImageBg: Boolean,
    onNavigate: (String) -> Unit,
    onDonationClick: () -> Unit
) {
    ScreenWrapper(
        title = "", // El nombre de la app se dibuja aparte para usar el estilo personalizado
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri
    ) {
        // Sobrescribimos la zona del título para el Home para mantener el estilo de fuente personalizado
        Box(
            modifier = Modifier.fillMaxSize().weight(0.4f).padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = font.family,
                    fontSize = (fontSize * 0.8f).sp,
                    color = titleColor
                ),
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2.2f)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.drawerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            HomeMenuButton(strings.contacts, Icons.Default.Contacts) { onNavigate("contacts") }
            HomeMenuButton(strings.language, Icons.Default.Language) { onNavigate("language") }
            HomeMenuButton(strings.phoneStyle, Icons.Default.Palette) { onNavigate("settings") }
            HomeMenuButton(strings.watchStyle, Icons.Default.Watch) { onNavigate("watch_settings") }
            HomeMenuButton(strings.permissions, Icons.Default.Security) { onNavigate("permissions") }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onDonationClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.donationButton, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun HomeMenuButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    strings: TranslationStrings, 
    currentLang: AppLanguage,
    customName: String,
    watchTitleColor: Color,
    watchBackgroundColor: Color,
    titleColor: Color,
    backgroundColor: Color,
    useImageBg: Boolean,
    bgImageUri: String?,
    contacts: androidx.compose.runtime.snapshots.SnapshotStateList<ContactSlot>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("DannyPhonePrefs", Context.MODE_PRIVATE) }
    
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialIndex by remember { mutableStateOf<Int?>(null) }
    var totalDragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemHeightPx = 110f * density 

    ScreenWrapper(
        title = strings.contacts,
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.organizeAgenda, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { saveAndSyncAll(context, sharedPrefs, contacts, currentLang, strings, customName, watchTitleColor, watchBackgroundColor) }) {
                    Icon(Icons.Default.Save, contentDescription = "Sync", tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(contacts, key = { _, item -> item.id }) { index, contact ->
                    val isDragging = draggedItemIndex == index
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                alpha = if (isDragging) 0.95f else 1f
                                shadowElevation = if (isDragging) 20f else 0f
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) Color(0xFFE8F5E9) else Color(0xFFF1F8E9).copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 12.dp else 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}", 
                                fontWeight = FontWeight.ExtraBold, 
                                fontSize = 20.sp,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.width(30.dp)
                            )

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
                            
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(48.dp)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                initialIndex = index
                                                draggedItemIndex = index
                                                totalDragOffsetY = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDragOffsetY += dragAmount.y
                                                
                                                val targetIndex = (initialIndex!! + (totalDragOffsetY / itemHeightPx).toInt())
                                                    .coerceIn(0, contacts.size - 1)
                                                
                                                if (targetIndex != draggedItemIndex) {
                                                    val item = contacts.removeAt(draggedItemIndex!!)
                                                    contacts.add(targetIndex, item)
                                                    draggedItemIndex = targetIndex
                                                }
                                                dragOffsetY = totalDragOffsetY - (draggedItemIndex!! - initialIndex!!) * itemHeightPx
                                            },
                                            onDragEnd = { 
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = { 
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
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
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    useImageBg: Boolean,
    bgImageUri: String?,
    onBgImageChange: (String?) -> Unit,
    onBack: () -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        if (uri != null) {
            onBgImageChange(uri.toString())
        }
    }

    ScreenWrapper(
        title = strings.phoneStyle,
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
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
            
            val fontOptions = FontOption.values()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    fontOptions.take(2).forEach { option ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedFont == option,
                            onClick = { onFontChange(option) },
                            label = { Text(option.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    fontOptions.drop(2).forEach { option ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = selectedFont == option,
                            onClick = { onFontChange(option) },
                            label = { Text(option.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Tamaño de Fuente: ${fontSize.toInt()}sp", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 30f..120f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(strings.titleColorLabel, style = MaterialTheme.typography.titleMedium)
            val colorPresets = listOf(Color(0xFF1976D2), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800), Color.Black, Color.DarkGray)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colorPresets.forEach { color ->
                    ColorCircle(color, isSelected = titleColor == color, onClick = { onTitleColorChange(color) })
                }
            }
            
            Text("Espectro de color (Texto):", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            ColorSpectrumSlider(titleColor, onTitleColorChange)

            Spacer(modifier = Modifier.height(24.dp))
            Text(strings.backgroundStyleLabel, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(strings.selectImageBtn) }
                Button(onClick = { onBgColorChange(Color(0xFFE3F2FD)) }, modifier = Modifier.weight(1f)) { Text(strings.selectColorBtn) }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Color de fondo plano:", style = MaterialTheme.typography.bodyMedium)
            val bgPresets = listOf(Color(0xFFE3F2FD), Color(0xFFF1F8E9), Color(0xFFFFF3E0), Color(0xFFF3E5F5), Color.White, Color.Black)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bgPresets.forEach { color ->
                    ColorCircle(color, isSelected = backgroundColor == color, onClick = { onBgColorChange(color) })
                }
            }
            Text("Espectro de color (Fondo):", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            ColorSpectrumSlider(backgroundColor, onBgColorChange)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ColorSpectrumSlider(currentColor: Color, onColorChange: (Color) -> Unit) {
    var hue by remember { mutableFloatStateOf(0f) }
    
    Slider(
        value = hue,
        onValueChange = { 
            hue = it
            onColorChange(Color.hsv(it, 0.7f, 0.9f))
        },
        valueRange = 0f..360f,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = Color.hsv(hue, 0.7f, 0.9f),
            activeTrackColor = Color.Gray
        )
    )
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() }
            .padding(if (isSelected) 4.dp else 0.dp)
    ) {
        if (isSelected) Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(0.5f)))
    }
}

@Composable
fun WatchSettingsScreen(
    strings: TranslationStrings,
    watchTitleColor: Color,
    onWatchTitleColorChange: (Color) -> Unit,
    watchBackgroundColor: Color,
    onWatchBgColorChange: (Color) -> Unit,
    titleColor: Color, // Para el ScreenWrapper
    backgroundColor: Color,
    useImageBg: Boolean,
    bgImageUri: String?,
    onBack: () -> Unit
) {
    ScreenWrapper(
        title = strings.watchStyle,
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(strings.watchStyle, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))

            Text(strings.titleColorLabel, style = MaterialTheme.typography.titleMedium)
            val colorPresets = listOf(Color.White, Color.Black, Color(0xFF1976D2), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colorPresets.forEach { color ->
                    ColorCircle(color, isSelected = watchTitleColor == color, onClick = { onWatchTitleColorChange(color) })
                }
            }
            Text("Espectro de color (Texto Reloj):", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            ColorSpectrumSlider(watchTitleColor, onWatchTitleColorChange)

            Spacer(modifier = Modifier.height(32.dp))
            Text(strings.backgroundStyleLabel, style = MaterialTheme.typography.titleMedium)
            val bgPresets = listOf(Color.Black, Color.White, Color(0xFF1976D2), Color(0xFFE3F2FD), Color(0xFFF1F8E9), Color(0xFF333333))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bgPresets.forEach { color ->
                    ColorCircle(color, isSelected = watchBackgroundColor == color, onClick = { onWatchBgColorChange(color) })
                }
            }
            Text("Espectro de color (Fondo Reloj):", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            ColorSpectrumSlider(watchBackgroundColor, onWatchBgColorChange)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionsScreen(
    strings: TranslationStrings,
    onPermissionsClick: () -> Unit,
    onOverlayClick: () -> Unit,
    hasCall: Boolean,
    hasOverlay: Boolean,
    titleColor: Color,
    backgroundColor: Color,
    useImageBg: Boolean,
    bgImageUri: String?,
    onBack: () -> Unit
) {
    ScreenWrapper(
        title = strings.permissions,
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(strings.permissions, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(strings.note, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            
            PermissionCard(strings.callPermission, hasCall, onPermissionsClick, strings.activate)
            PermissionCard(strings.overlayPermission, hasOverlay, onOverlayClick, strings.activate)
        }
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
fun LanguageScreen(
    strings: TranslationStrings, 
    currentLanguage: AppLanguage, 
    onLanguageChange: (AppLanguage) -> Unit,
    titleColor: Color,
    backgroundColor: Color,
    useImageBg: Boolean,
    bgImageUri: String?,
    onBack: () -> Unit
) {
    ScreenWrapper(
        title = strings.language,
        titleColor = titleColor,
        backgroundColor = backgroundColor,
        useImageBg = useImageBg,
        bgImageUri = bgImageUri,
        onBack = onBack
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
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

fun syncSettingsToWatch(
    context: Context, 
    lang: AppLanguage, 
    appName: String, 
    titleColor: Color, 
    backgroundColor: Color
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create("/speed_dial")
            val dataMap = putDataMapReq.dataMap
            dataMap.putString("language", lang.name)
            dataMap.putString("app_name", appName)
            dataMap.putLong("title_color", titleColor.toArgb().toLong())
            dataMap.putLong("bg_color", backgroundColor.toArgb().toLong())
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()).await()
        } catch (e: Exception) {}
    }
}

data class TranslationStrings(
    val drawerTitle: String, val home: String, val contacts: String, val settings: String, val language: String, val permissions: String,
    val donationButton: String, val donationTitle: String, val donationText: String, val ok: String,
    val organizeAgenda: String, val name: String, val phone: String, val syncSuccess: String, val syncError: String,
    val callPermission: String, val overlayPermission: String, val activate: String, val note: String,
    val personalizationTitle: String, val customNameLabel: String, val fontStyleLabel: String, val titleColorLabel: String, val backgroundStyleLabel: String, val selectImageBtn: String, val selectColorBtn: String,
    val phoneStyle: String, val watchStyle: String
)

fun getTranslations(lang: AppLanguage): TranslationStrings {
    return when(lang) {
        AppLanguage.ENGLISH -> TranslationStrings("Menu", "Home", "Contacts", "Style", "Language", "Permissions", "please, make a donation", "Support DannyPhone", "If you liked my application you can donate the amount you consider by doing a bizzum to +34655533304", "OK", "Organize Agenda", "Name", "Phone", "Synced!", "Error", "Calls", "Overlay", "ACTIVATE", "Note: Activate permissions.", "Style Settings", "App Name", "Font", "Text Color", "Background", "Gallery", "Color", "Phone Style", "Watch Style")
        AppLanguage.ESPANOL_LATINO -> TranslationStrings("Menú", "Inicio", "Contactos", "Estilo", "Idioma", "Permisos", "por favor, haz una donación", "Apoya a DannyPhone", "Si te gustó mi aplicación puedes donar la cantidad que consideres haciendo un bizzum al +34655533304", "Aceptar", "Organizar Agenda", "Nombre", "Celular", "¡Sincronizado!", "Error", "Llamadas", "Superposición", "ACTIVAR", "Nota: Activa los permisos.", "Ajustes de Estilo", "Nombre App", "Fuente", "Color", "Fondo", "Galería", "Color", "Estilo Teléfono", "Estilo Reloj")
        AppLanguage.CATALA -> TranslationStrings("Menú", "Inici", "Contactes", "Estil", "Idioma", "Permisos", "si us plau, fes una donación", "Ajuda a DannyPhone", "Si t'ha agradat la meva aplicació pots donar per Bizzum al +34655533304", "Acceptar", "Organitzar Agenda", "Nom", "Telèfon", "Sincronitzat!", "Error", "Trucades", "Superposició", "ACTIVAR", "Nota: Activa els permisos.", "Ajustos d'Estil", "Nom App", "Font", "Color Text", "Fons", "Galeria", "Color", "Estil Telèfon", "Estil Rellotge")
        AppLanguage.GALEGO -> TranslationStrings("Menú", "Inicio", "Contactos", "Estilo", "Lingua", "Permisos", "por favor, fai unha doazón", "Apoia a DannyPhone", "Se che gustou a miña aplicación podes doar por Bizzum ao +34655533304", "Aceptar", "Organizar Axenda", "Nome", "Teléfono", "Sincronizado!", "Erro", "Chamadas", "Superposición", "ACTIVAR", "Nota: Activa os permisos.", "Axustes de Estilo", "Nome App", "Fonte", "Color Texto", "Fondo", "Galería", "Color", "Estilo Teléfono", "Estilo Reloxo")
        AppLanguage.EUSKARA -> TranslationStrings("Menua", "Hasiera", "Agenda", "Pertsonalizatu", "Hizkuntza", "Baimenak", "mesedez, egin dohaintza bat", "DannyPhone Lagundu", "Nire aplicación gustatu bazaizu, bizzum bat egin dezakezu +34655533304 zenbakira", "Onartu", "Agenda Antolatu", "Izena", "Telefonoa", "Sinkronizatuta!", "Errorea", "Deiak", "Gainjartzea", "AKTIBATU", "Nota: Baimenak aktibatu.", "Estilo Ezarpenak", "App Izena", "Letra", "Testu Kolorea", "Atzealdea", "Galeria", "Color", "Telefono Estiloa", "Erloju Estiloa")
        AppLanguage.BABLE -> TranslationStrings("Menú DannyPhone", "Entamu", "Axenda", "Estilu", "Llingua", "Permisos", "por favor, fai una donación", "Sofita a DannyPhone", "Si te prestó la mio aplicación pues donar por Bizzum al +34655533304", "Aceptar", "Organizar Axenda", "Nome", "Teléfanu", "¡Sincronizáu!", "Error", "Llamaes", "Superposición", "ACTIVAR", "Nota: Activa los permisos.", "Axustes d'Estilu", "Nome App", "Fonte", "Color Testu", "Fondu", "Galería", "Color", "Estilu Teléfanu", "Estilu Reló")
        AppLanguage.DEUTSCH -> TranslationStrings("Menü", "Startseite", "Kontakte", "Stil", "Sprache", "Berechtigungen", "bitte spenden Sie", "Unterstützen Sie DannyPhone", "Wenn Ihnen meine Anwendung gefallen hat, können Sie den Betrag spenden, den Sie für angemessen halten, indem Sie ein Bizzum an +34655533304 senden", "OK", "Agenda organisieren", "Name", "Telefon", "Synchronisiert!", "Fehler", "Anrufe", "Überlagerung", "AKTIVIEREN", "Nota: Baimenak aktibatu.", "Stil-Einstellungen", "App-Name", "Schriftart", "Farbe", "Hintergrund", "Galerie", "Farbe", "Telefon-Stil", "Uhren-Stil")
        AppLanguage.FRANCAIS -> TranslationStrings("Menu", "Accueil", "Contacts", "Style", "Langue", "Autorisations", "s'il vous plaît, faites un don", "Soutenir DannyPhone", "Si vous avez aimé mon application, vous pouvez donner le montant que vous considérez en haciendo un bizzum au +34655533304", "OK", "Organiser l'agenda", "Nom", "Téléphone", "Synchronisé !", "Erreur", "Appels", "Superposition", "ACTIVER", "Note: Activa les permisos.", "Paramètres de style", "Nom de l'app", "Police", "Couleur", "Fond", "Galerie", "Couleur", "Style Téléphone", "Style Montre")
        AppLanguage.ITALIANO -> TranslationStrings("Menu", "Home", "Contatti", "Stile", "Lingua", "Permessi", "per favore, fai una donazione", "Sostieni DannyPhone", "Se ti è piaciuta la mia aplicación puoi donare l'importo que ritieni opportuno haciendo un bizzum al +34655533304", "OK", "Organizza agenda", "Nom", "Telefono", "Sincronizzato!", "Errore", "Chiamate", "Sovrapposición", "ATTIVA", "Note: Activa i permessi.", "Impostaciones stile", "Nome App", "Font", "Colore", "Sfondo", "Galleria", "Colore", "Stile Telefono", "Stile Orologio")
        AppLanguage.HINDI -> TranslationStrings("मेनू", "होम", "संपर्क", "शैली", "भाषा", "अनुमतियां", "कृपया दान करें", "DannyPhone का समर्थन करें", "यदि आपको मेरा एप्लिकेशन पसंद आया है, तो आप +34655533304 पर बिज़म करके अपनी इच्छानुसार राशि दान कर सकते हैं", "ठीक es", "एजenda व्यवस्थित करें", "नाम", "फ़ोन", "सिंक हो गया!", "त्रुट유", "कॉल", "ओवरले", "सक्रिय करें", "ध्यान दें: अनुमति सक्रिय करें।", "शैली setिंग्स", "ऐप का नाम", "फ़ॉन्ट", "रंग", "पृष्ठभूमि", "गैलरी", "रंग", "फ़ोन शैली", "घड़ी की शैली")
        AppLanguage.KOREAN -> TranslationStrings("메뉴", "홈", "연락처", "스타일", "언어", "권한", "기부해 주세요", "DannyPhone 지원", "제 애플리케이션이 마음에 드셨다면 +34655533304로 bizzum을 보내 원하는 금액을 기부하실 su isseumnida", "확인", "일정 정리", "이름", "전화번호", "동기화됨!", "오류", "전화", "오버레이", "활성화", "참고: 권한을 활성화하십시오.", "스타일 설정", "앱 이름", "글꼴", "색상", "배경", "갤러리", "색상", "폰 estilo", "시계 스타일")
        AppLanguage.JAPANESE -> TranslationStrings("メニュー", "ホーム", "連絡先", "スタイル", "言語", "権限", "寄付をお願いします", "DannyPhone をサポート", "私のアプリを気に入っていただけたなら, +34655533304 に bizzum を送ante, お好きな金額 को寄付していただけます", "OK", "アジェンダの整理", "名前", "電話番号", "同期しました！", "エラー", "通話", "オーバーレイ", "有効にする", "注意：権限を有効 in してください。", "スタイル設定", "アプリ名", "フォント", "色", "背景", "갤러리", "色", "電話スタイル", "時計スタイル")
    }
}
