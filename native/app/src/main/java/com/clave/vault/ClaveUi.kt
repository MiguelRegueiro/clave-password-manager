@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.clave.vault

import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clave.autofill.CredentialRules
import kotlinx.coroutines.delay

private val LightPalette = lightColorScheme(
    primary = Color(0xFF202226), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E5E7), onPrimaryContainer = Color(0xFF202226),
    secondary = Color(0xFF5D6269), secondaryContainer = Color(0xFFE8E9EB),
    background = Color(0xFFF4F5F6), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEDEF), onSurface = Color(0xFF191B1E),
    onSurfaceVariant = Color(0xFF5D6269), outline = Color(0xFF858A91), outlineVariant = Color(0xFFD6D8DB),
    surfaceContainer = Color(0xFFECEDEF), surfaceContainerLow = Color(0xFFF8F8F9),
    surfaceContainerHigh = Color(0xFFE5E7E9), surfaceContainerHighest = Color(0xFFDDE0E3),
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF454A50), onPrimary = Color(0xFFF0F1F2),
    primaryContainer = Color(0xFF33373C), onPrimaryContainer = Color(0xFFF0F1F2),
    secondary = Color(0xFFC3C6CA), secondaryContainer = Color(0xFF34383D),
    background = Color(0xFF0D0F11), surface = Color(0xFF17191C),
    surfaceVariant = Color(0xFF222529), onSurface = Color(0xFFF0F1F2),
    onSurfaceVariant = Color(0xFFB5B9BE), outline = Color(0xFF858A90), outlineVariant = Color(0xFF41464B),
    surfaceContainer = Color(0xFF1E2125), surfaceContainerLow = Color(0xFF17191C),
    surfaceContainerHigh = Color(0xFF25292D), surfaceContainerHighest = Color(0xFF2E3237),
)

@Composable fun ClaveTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val activity = LocalContext.current as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
    }
    MaterialTheme(colorScheme = if (dark) DarkPalette else LightPalette, shapes = Shapes(
        small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}

@Composable fun ClaveApp(
    document: VaultDocument?, busy: Boolean, error: String?, notice: String?,
    screen: String, selected: String?, draft: Credential?, autofillEnabled: Boolean,
    themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    onUnlock: () -> Unit, onLock: () -> Unit,
    onScreen: (String) -> Unit, onSelect: (String) -> Unit, onNew: (String, String?) -> Unit,
    onEdit: (Credential) -> Unit, onBack: () -> Unit, onSave: (Credential) -> Unit,
    onDelete: (String) -> Unit, onConfirm: (String) -> Unit, onRestore: (String) -> Unit,
    onSaveProfile: (String?, String, AccountAccent) -> Unit,
    onDeleteProfile: (String) -> Unit, onCopy: (String) -> Unit, onEnableAutofill: () -> Unit,
    onDismissError: () -> Unit, onDismissNotice: () -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }
    var creatingProfile by remember(document == null, screen) { mutableStateOf(false) }
    var profileFilter by remember { mutableStateOf<String?>(null) }
    if (document != null && profileFilter != null && profileFilter !in document.profiles) profileFilter = null
    LaunchedEffect(notice) { if (notice != null) { snackbars.showSnackbar(notice); onDismissNotice() } }
    val detail = document?.credentials?.firstOrNull { it.id == selected }
    // Keep the bottom bar out of the IME's space, including its closing animation.
    val keyboardOpen = WindowInsets.isImeVisible || WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = document != null) {
        if (draft != null || selected != null) onBack() else if (screen != "vault") onScreen("vault") else onLock()
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (document != null && screen in listOf("vault", "profiles") && draft == null && detail == null && !keyboardOpen) {
                FloatingActionButton(onClick = { if (!busy) { if (screen == "profiles") creatingProfile = true else onNew("", profileFilter) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(20.dp)) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = if (screen == "profiles") "Nuevo perfil" else "Nueva cuenta", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            if (document != null && draft == null && detail == null && !keyboardOpen) Surface(
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(30.dp), shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) { Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("vault" to "Cuentas", "generator" to "Generar", "profiles" to "Perfiles", "settings" to "Ajustes").forEachIndexed { index, (key, label) ->
                    val active = screen == key
                    Surface(onClick = { if (!busy) onScreen(key) }, shape = RoundedCornerShape(26.dp),
                        color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        contentColor = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).semantics { this.selected = active; role = Role.Tab }) {
                        Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                            Icon(painterResource(listOf(R.drawable.ic_shield, R.drawable.ic_key, R.drawable.ic_profiles, R.drawable.ic_settings)[index]),
                                contentDescription = null, modifier = Modifier.size(28.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            } }
        },
    ) { padding ->
        // Scaffold and the outer IME modifier already account for these insets.
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (busy && document != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (document == null) {
                LockedScreen(busy, onUnlock)
            } else {
                when {
                    draft != null -> key(draft.id) { Editor(draft, document, busy, onBack, onSave) }
                    detail != null -> key(detail.id) { AccountTheme(document.accountColor(detail)) {
                        Detail(detail, document, busy, onBack, onEdit, onDelete, onConfirm, onRestore, onCopy)
                    } }
                    screen == "generator" -> Generator(onCopy) { password -> onNew(password, profileFilter) }
                    screen == "profiles" -> Profiles(document, busy, creatingProfile, { creatingProfile = false }, onSaveProfile, onDeleteProfile)
                    screen == "settings" -> SettingsScreen(autofillEnabled, onEnableAutofill, themeMode, onThemeChange)
                    else -> VaultList(document, busy, onSelect, onLock, autofillEnabled, onEnableAutofill, !keyboardOpen,
                        profileFilter, { profileFilter = it })
                }
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = onDismissError,
        title = { Text("No se pudo completar") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = onDismissError) { Text("Entendido") } })
}

@Composable private fun LockedScreen(busy: Boolean, unlock: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the action near its previous 62%-height position while raising the branding.
        // Short/landscape windows scroll instead of letting the two areas overlap.
        val canvasHeight = maxHeight.coerceAtLeast(480.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(canvasHeight * 0.20f))
        Surface(shape = RoundedCornerShape(24.dp)) {
            Image(painterResource(R.drawable.ic_clave), contentDescription = "Logo de Clave", modifier = Modifier.size(88.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Clave", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height((canvasHeight * 0.42f - 186.dp).coerceAtLeast(24.dp)))
        FilledTonalIconButton(onClick = unlock, enabled = !busy,
            modifier = Modifier.size(84.dp).semantics { contentDescription = if (busy) "Autenticando" else "Desbloquear con biometría" },
            shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
            if (busy) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(canvasHeight * 0.25f))
        }
    }
}

@Composable private fun CompactSearch(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(value = value, onValueChange = onChange, singleLine = true,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "Buscar cuentas" },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
        decorationBox = { innerField ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glyph(R.drawable.ic_search)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text("Buscar cuentas", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerField()
                    }
                }
            }
        })
}

@Composable private fun VaultList(doc: VaultDocument, busy: Boolean, select: (String) -> Unit, lock: () -> Unit, autofill: Boolean, enable: () -> Unit, reserveFabSpace: Boolean,
                                 profile: String?, onProfileChange: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val entries = doc.credentials.filter { (profile == null || it.profile == profile) &&
        listOf(it.title, it.username, it.website, it.appPackage).any { text -> text.contains(query, ignoreCase = true) } }.sortedBy { it.title.lowercase() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactSearch(query, { query = it }, Modifier.weight(1f))
                FilledTonalIconButton(onClick = lock, modifier = Modifier.size(48.dp)) { Glyph(R.drawable.ic_lock, "Bloquear") }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = if (reserveFabSpace) 88.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceChip("Todas", profile == null, { onProfileChange(null) })
                doc.profiles.forEach { name -> ChoiceChip(name, profile == name, { onProfileChange(name) }, accent = doc.profileColor(name)) }
            }
        }
        if (entries.isEmpty()) item { InfoCard(if (doc.credentials.isEmpty()) "Sin cuentas todavía" else "Sin resultados", if (doc.credentials.isEmpty()) "Añade tu primera cuenta." else "Prueba otra búsqueda o perfil.") }
        items(entries, key = { it.id }) { entry ->
            val accent = doc.accountColor(entry)
            ElevatedCard(onClick = { if (!busy) select(entry.id) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccountTile(entry.title, accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (profile == null || entry.status == "pending") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (profile == null) Text(entry.profile, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.status == "pending") Text("· Pendiente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Glyph(R.drawable.ic_chevron)
                }
            }
        }
        if (!autofill) item { SettingRow(R.drawable.ic_key, "Autocompletado", "Activar en Android", enable) }
        }
    }
}

@Composable private fun Detail(entry: Credential, doc: VaultDocument, busy: Boolean, back: () -> Unit, edit: (Credential) -> Unit,
    delete: (String) -> Unit, confirm: (String) -> Unit, restore: (String) -> Unit, copy: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ActionIcon(R.drawable.ic_back, "Volver a cuentas", back)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccountTile(entry.title, doc.accountColor(entry), large = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(entry.title)
                ProfileBadge(entry.profile, doc.profileColor(entry.profile))
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Usuario", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.username)
                }
                ActionIcon(R.drawable.ic_copy, "Copiar usuario", { copy(entry.username) })
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contraseña", style = MaterialTheme.typography.labelLarge)
                Text(if (visible) entry.password else "••••••••••••••••", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                Row {
                    ActionIcon(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye, if (visible) "Ocultar contraseña" else "Mostrar contraseña", { visible = !visible })
                    ActionIcon(R.drawable.ic_copy, "Copiar contraseña", { copy(entry.password) })
                }
            }
        }
        if (entry.website.isNotBlank()) InfoCard("Sitio web", entry.website)
        if (entry.appPackage.isNotBlank()) InfoCard("Aplicación vinculada", entry.appPackage)
        if (entry.status == "pending") InfoCard("Confirma el cambio en la web", "Esta contraseña se guardó antes de rellenarla. Clave no puede saber si la web aceptó el cambio.") {
            TextButton(onClick = { confirm(entry.id) }, enabled = !busy) { Text("La web aceptó la contraseña") }
            if (entry.previousPassword != null) TextButton(onClick = { restoring = true }, enabled = !busy) { Text("Restaurar contraseña anterior") }
        }
        Button(onClick = { edit(entry) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)) { Text("Editar cuenta") }
        TextButton(onClick = { deleting = true }, enabled = !busy, shape = RoundedCornerShape(16.dp)) { Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) ConfirmDialog("¿Eliminar esta cuenta?", "Se borrará de la bóveda. No cambia ni elimina tu cuenta en la web.", { deleting = false }, { deleting = false; delete(entry.id) })
    if (restoring) ConfirmDialog("¿Restaurar la contraseña anterior?", "Solo cambia lo guardado en Clave. Hazlo si la web no aceptó la nueva contraseña.", { restoring = false }, { restoring = false; restore(entry.id) })
}

@Composable private fun Editor(original: Credential, doc: VaultDocument, busy: Boolean, back: () -> Unit, save: (Credential) -> Unit) {
    val profiles = doc.profiles
    // Deliberately not rememberSaveable: no secrets in saved instance state or disk bundles.
    var title by remember { mutableStateOf(original.title) }
    var user by remember { mutableStateOf(original.username) }
    var secret by remember { mutableStateOf(original.password) }
    var website by remember { mutableStateOf(original.website) }
    var profile by remember { mutableStateOf(original.profile) }
    var visible by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val changed = title != original.title || user != original.username || secret != original.password || website != original.website || profile != original.profile
    BackHandler { if (changed) discard = true else back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(R.drawable.ic_back, "Cancelar edición", { if (changed) discard = true else back() })
        Heading(if (original.title.isBlank()) "Nueva cuenta" else "Editar cuenta")
        OutlinedTextField(user, { user = it.take(250) }, label = { Text("Usuario o correo") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(secret, { secret = it.take(1024) }, label = { Text("Contraseña") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(20.dp), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        Row {
            ActionIcon(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye, if (visible) "Ocultar contraseña" else "Mostrar contraseña", { visible = !visible })
            TextButton(onClick = { secret = CredentialRules.password() }, enabled = !busy) { Glyph(R.drawable.ic_refresh); Spacer(Modifier.width(8.dp)); Text("Generar") }
        }
        OutlinedTextField(title, { title = it.take(100) }, label = { Text("Nombre (opcional)") }, shape = RoundedCornerShape(20.dp), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(website, { website = it }, label = { Text("Web HTTPS (opcional)") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        Text("Perfil", style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            profiles.forEach { name -> ChoiceChip(name, profile == name, { profile = name }, accent = doc.profileColor(name)) }
        }
        Button(onClick = { save(original.copy(title = title.trim(), username = user.trim(), password = secret, website = website.trim(), profile = profile)) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)) { Text("Guardar cuenta") }
    }
    if (discard) ConfirmDialog("¿Descartar cambios?", "Los cambios sin guardar se perderán.", { discard = false }, back)
}

@Composable private fun Generator(copy: (String) -> Unit, add: (String) -> Unit) {
    var length by remember { mutableFloatStateOf(20f) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf(CredentialRules.password()) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) { delay(30_000); visible = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Generador")
        val scheme = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh,
            shadowElevation = 4.dp, border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.32f))) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (visible) password else "•".repeat(length.toInt()), Modifier.fillMaxWidth(),
                    fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 23.sp,
                    softWrap = true, overflow = TextOverflow.Clip, letterSpacing = 0.sp)
                Row(Modifier.align(Alignment.End)) {
                    ActionIcon(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye,
                        if (visible) "Ocultar contraseña" else "Mostrar contraseña", { visible = !visible })
                    ActionIcon(R.drawable.ic_copy, "Copiar contraseña", { copy(password) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Longitud", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHigh) {
                Text("${length.toInt()}", Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        val sliderColors = SliderDefaults.colors(
            thumbColor = Color(0xFF969BA1),
            activeTrackColor = Color(0xFF747A81),
            inactiveTrackColor = Color(0xFF303439),
        )
        Slider(value = length, onValueChange = { length = it }, valueRange = 12f..64f, steps = 0,
            colors = sliderColors,
            onValueChangeFinished = { password = CredentialRules.password(length.toInt(), symbols) },
            track = { state -> SliderDefaults.Track(sliderState = state, colors = sliderColors, drawStopIndicator = null) })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Incluir símbolos", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = symbols, onCheckedChange = { symbols = it; password = CredentialRules.password(length.toInt(), it) })
        }
        Button(onClick = { password = CredentialRules.password(length.toInt(), symbols) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)) {
            Glyph(R.drawable.ic_refresh); Spacer(Modifier.width(10.dp)); Text("Generar otra", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = { add(password) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.7f))) {
            Glyph(R.drawable.ic_plus); Spacer(Modifier.width(10.dp)); Text("Guardar cuenta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun Profiles(doc: VaultDocument, busy: Boolean, creating: Boolean, closeCreate: () -> Unit,
                                save: (String?, String, AccountAccent) -> Unit, delete: (String) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("Perfiles") }
        items(doc.profiles, key = { it }) { profile ->
            ElevatedCard(onClick = { editing = profile }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountTile(profile, doc.profileColor(profile))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile, style = MaterialTheme.typography.titleMedium)
                        val count = doc.credentials.count { it.profile == profile }
                        Text("$count ${if (count == 1) "cuenta" else "cuentas"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Glyph(R.drawable.ic_chevron)
                }
            }
        }
    }
    if (creating || editing != null) key(creating, editing) {
        ProfileEditor(doc, if (creating) null else editing, busy,
            { closeCreate(); editing = null }, save, delete)
    }
}

@Composable private fun ProfileEditor(doc: VaultDocument, original: String?, busy: Boolean, dismiss: () -> Unit,
                                     save: (String?, String, AccountAccent) -> Unit, delete: (String) -> Unit) {
    var name by remember { mutableStateOf(original.orEmpty()) }
    var color by remember { mutableStateOf(original?.let { doc.profileColor(it) } ?: AccountAccent.SLATE) }
    var deleting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val initialDocument = remember { doc }
    LaunchedEffect(doc) { if (submitted && doc != initialDocument) dismiss() }
    val duplicate = doc.profiles.any { it != original && it.equals(name.trim(), true) }
    val valid = name.isNotBlank() && !duplicate && (original != null || doc.profiles.size < 100)
    val changed = original == null || name.trim() != original || color != doc.profileColor(original)
    val canDelete = original != null && doc.profiles.size > 1 && doc.credentials.none { it.profile == original }
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 18.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (original == null) "Nuevo perfil" else "Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (original == null) "Organiza tus cuentas" else "${doc.credentials.count { it.profile == original }} cuentas", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = dismiss, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Cerrar" }) {
                        Text("×", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, enabled = !busy, singleLine = true,
                    label = { Text("Nombre") }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                    isError = duplicate, supportingText = if (duplicate) ({ Text("Ese perfil ya existe") }) else null)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Color del perfil", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccountAccent.entries.chunked(4).forEach { rowOptions ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOptions.forEach { option ->
                                    val swatch = when (option) {
                                        AccountAccent.BLUE -> Color(0xFF4D8FFF)
                                        AccountAccent.TEAL -> Color(0xFF36B69B)
                                        AccountAccent.VIOLET -> Color(0xFFA77AE8)
                                        AccountAccent.ROSE -> Color(0xFFE477A5)
                                        AccountAccent.AMBER -> Color(0xFFE0B24F)
                                        AccountAccent.CORAL -> Color(0xFFEE8066)
                                        AccountAccent.INDIGO -> Color(0xFF7882E0)
                                        AccountAccent.SLATE -> Color(0xFF9299A1)
                                    }
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Surface(onClick = { color = option }, enabled = !busy,
                                            shape = CircleShape, color = swatch,
                                            border = BorderStroke(if (option == color) 2.dp else 1.dp,
                                                if (option == color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.size(48.dp).semantics {
                                                contentDescription = option.label
                                                selected = option == color
                                                role = Role.RadioButton
                                            }) { }
                                    }
                                }
                                repeat(4 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (original != null && !canDelete) Text(
                    if (doc.profiles.size <= 1) "Conserva al menos un perfil." else "Mueve las cuentas a otro perfil para eliminarlo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (original != null) OutlinedButton(onClick = { deleting = true }, enabled = !busy && canDelete,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.8f))) {
                        Glyph(R.drawable.ic_trash); Spacer(Modifier.width(6.dp)); Text("Eliminar")
                    }
                    Button(onClick = { submitted = true; save(original, name.trim(), color) }, enabled = !busy && valid && changed,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)) { Text(if (original == null) "Crear perfil" else "Guardar") }
                }
            }
        }
    }
    if (deleting && original != null) ConfirmDialog("¿Eliminar $original?", "Se eliminará este perfil vacío.", { deleting = false }, {
        deleting = false; submitted = true; delete(original)
    })
}

@Composable private fun SettingsScreen(enabled: Boolean, enable: () -> Unit, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    var help by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading("Ajustes")
        Row(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Apariencia" },
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ChoiceChip(mode.label, mode == themeMode, { onThemeChange(mode) },
                    Modifier.weight(1f).height(44.dp),
                    selectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
        SettingRow(R.drawable.ic_key, "Autocompletado", if (enabled) "Activo" else "Desactivado", enable)
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Glyph(R.drawable.ic_lock)
                Column {
                    Text("Bloqueo automático", style = MaterialTheme.typography.titleSmall)
                    Text("Al salir · 2 min sin actividad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingRow(R.drawable.ic_help, "Ayuda y seguridad", "Autocompletado, datos y recuperación", { help = true })
        Text("Solo en este dispositivo · Sin copia de seguridad", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Ayuda y seguridad") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HelpSection("Autocompletado", "Activa Clave en Android. En Chrome, selecciona «Autocompletar con otro servicio» y reinicia el navegador. Toca un campo de contraseña, elige Clave, confirma la biometría y revisa el sitio. Puedes elegir una cuenta o generar una contraseña y guardarla en un perfil.")
                HelpSection("Cambios de contraseña", "Clave guarda antes de rellenar. Termina el cambio en la web y confírmalo en la cuenta de Clave. Si la web lo rechaza, puedes restaurar la contraseña anterior cuando exista.")
                HelpSection("Compatibilidad", "Algunas webs y navegadores no exponen campos compatibles. Se rechazan HTTP y formularios ambiguos. Clave no verifica automáticamente la relación entre la app solicitante y el dominio: comprueba ambos antes de confirmar.")
                HelpSection("Privacidad", "La bóveda se bloquea al salir y tras 2 minutos sin actividad. Los detalles se ocultan en 30 segundos. Las capturas están permitidas: evita compartir contraseñas visibles. El portapapeles se limpia cuando Android lo permite.")
                HelpSection("Sin recuperación", "No hay copia, exportación ni sincronización. Desinstalar, borrar datos, perder el teléfono o cambiar la biometría puede impedir recuperar tus contraseñas. Usa cuentas de prueba hasta validar el funcionamiento en tu móvil.")
            }
        }, confirmButton = { TextButton(onClick = { help = false }) { Text("Cerrar") } })
}

@Composable private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One visual language for every single-selection control, in both color schemes. */
@Composable private fun ChoiceChip(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: AccountAccent? = null,
    selectedColor: Color? = null,
    selectedContentColor: Color? = null,
) {
    val paint = accent?.let { accentPaint(it) }
    val container by animateColorAsState(if (chosen) paint?.container ?: selectedColor ?: MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "choice background")
    val foreground by animateColorAsState(if (chosen) paint?.text ?: selectedContentColor ?: MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "choice foreground")
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = container, contentColor = foreground,
        shadowElevation = if (chosen) 3.dp else 0.dp,
        border = if (chosen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.heightIn(min = 36.dp).semantics { selected = chosen; role = Role.RadioButton }) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable private fun Glyph(resource: Int, description: String? = null) {
    Icon(painterResource(resource), contentDescription = description, modifier = Modifier.size(22.dp))
}

@Composable private fun ActionIcon(resource: Int, description: String, action: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = action, enabled = enabled) { Glyph(resource, description) }
}

@Composable private fun SettingRow(icon: Int, title: String, subtitle: String, action: () -> Unit) {
    ElevatedCard(onClick = action, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Glyph(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Glyph(R.drawable.ic_chevron)
        }
    }
}

@Composable private fun Heading(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    if (subtitle.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun InfoCard(title: String, description: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

@Composable private fun ConfirmDialog(title: String, body: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
