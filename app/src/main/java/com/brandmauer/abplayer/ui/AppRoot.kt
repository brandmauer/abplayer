package com.brandmauer.abplayer.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import com.brandmauer.abplayer.data.coverInPlayer
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import com.brandmauer.abplayer.i18n.I18n
import androidx.compose.ui.semantics.onClick as semOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.brandmauer.abplayer.AddKind
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.Popup
import com.brandmauer.abplayer.Sheet
import com.brandmauer.abplayer.ToastMsg
import com.brandmauer.abplayer.VIEW_PLAYLIST
import com.brandmauer.abplayer.data.OpenAudioDocuments
import com.brandmauer.abplayer.data.OpenAudioFolder
import com.brandmauer.abplayer.data.OpenPlaylistDocument
import kotlinx.coroutines.delay

/** Пока библиотека читается с диска — только фон в цвете темы (тема запоминается отдельно), затем настоящий интерфейс. */
@Composable
fun AppRoot(hub: Hub) {
    if (!hub.ready) {
        val dark = remember { hub.bootDark() }
        AbTheme(dark) {
            val c = LocalColors.current
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(c.bg)
            )
        }
        return
    }
    AppRootReady(hub)
}

@Composable
private fun AppRootReady(hub: Hub) {
    val dark = when (hub.data.theme) {
        "dark" -> true
        "light" -> false
        else -> true // по умолчанию — тёмная тема
    }
    AbTheme(dark, hub.data.accentColor) {
      CompositionLocalProvider(
          LocalFx provides (hub.data.fxLevel > 0f),
          LocalFxLevel provides effectiveFx(hub.data.fxLevel),
          // свой резиновый скролл (rubberBand) вместо системного растяжения/свечения на краях списков
          LocalOverscrollConfiguration provides (if (hub.data.fxLevel > 0f) null else OverscrollConfiguration())
      ) {
        SideEffect {
            FxRuntime.level = effectiveFx(hub.data.fxLevel)
            FxRuntime.sound = hub.data.fxSound
            FxRuntime.haptic = hub.data.fxHaptic
        }
        val c = LocalColors.current

        // иконки системных панелей под цвет темы
        val view = LocalView.current
        SideEffect {
            // оболочки вроде MIUI/One UI умеют сами «инвертировать» цвета чужих приложений и
            // перекрашивать тёмный текст в белый — запрещаем это для корневого вью
            if (android.os.Build.VERSION.SDK_INT >= 29) view.isForceDarkAllowed = false
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val ctl = WindowCompat.getInsetsController(window, view)
                ctl.isAppearanceLightStatusBars = !dark
                ctl.isAppearanceLightNavigationBars = !dark
                // фон окна под цвет палитры: за краями и при «резиновом» скролле не мелькает чужой цвет
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(
                    (c.bg.red * 255f).toInt(), (c.bg.green * 255f).toInt(), (c.bg.blue * 255f).toInt()
                )))
            }
        }

        // «Экран» в энергосбережении: уровень 2 («Не гаснет») держит экран включённым, пока
        // что-то играет; на 0/1 — обычное системное поведение (экран гаснет как всегда).
        LaunchedEffect(hub.effectivePower().screen, hub.isPlaying) {
            view.keepScreenOn = hub.effectivePower().screen >= 2 && hub.isPlaying
        }

        // Циклический пейджер: виртуальных страниц много больше трёх, реальный экран —
        // остаток от деления на 3, поэтому свайп с последнего экрана попадает на первый и наоборот.
        val screenCount = 3
        val virtualCount = 30_000
        val startVirtual = (virtualCount / 2) - (virtualCount / 2) % screenCount + 1 // соответствует экрану 1 (главный)
        val pager = rememberPagerState(initialPage = startVirtual) { virtualCount }
        fun logicalPage(virtual: Int): Int = ((virtual % screenCount) + screenCount) % screenCount

        // переходы между экранами по запросу логики (после выбора трека и т.п.)
        val nav = hub.navRequest
        LaunchedEffect(nav) {
            if (nav != null) {
                val cur = pager.currentPage
                var diff = nav.screen - logicalPage(cur)
                if (diff > screenCount / 2) diff -= screenCount
                if (diff < -screenCount / 2) diff += screenCount
                pager.animateScrollToPage(cur + diff)
                hub.navRequest = null
            }
        }

        // выбор файлов / папки / плейлиста m3u для добавления в список
        var pendingView by remember { mutableStateOf(VIEW_PLAYLIST) }
        val filesLauncher = rememberLauncherForActivityResult(OpenAudioDocuments()) { uris ->
            hub.onFilesPicked(pendingView, uris)
        }
        val folderLauncher = rememberLauncherForActivityResult(OpenAudioFolder()) { uri ->
            if (uri != null) hub.onFolderPicked(pendingView, uri)
        }
        val radioFileLauncher = rememberLauncherForActivityResult(OpenPlaylistDocument()) { uri ->
            if (uri != null) hub.onRadioFilePicked(uri)
        }
        val coverLauncher = rememberLauncherForActivityResult(
            com.brandmauer.abplayer.data.OpenSingleImageDocument()
        ) { uri -> if (uri != null) hub.onCoverPicked(pendingView, uri) }
        val req = hub.addRequest
        LaunchedEffect(req) {
            if (req != null) {
                pendingView = req.view
                hub.addRequest = null
                when (req.kind) {
                    AddKind.FILES -> filesLauncher.launch(Unit)
                    AddKind.FOLDER -> folderLauncher.launch(Unit)
                    AddKind.RADIO_FILE -> radioFileLauncher.launch(Unit)
                    AddKind.RADIO_URL -> Unit // ссылку запрашиваем текстовым диалогом, см. askRadioUrl()
                    AddKind.COVER_IMAGE -> coverLauncher.launch(Unit)
                }
            }
        }

        // экспорт/импорт настроек файлом
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> if (uri != null) hub.writeExportTo(uri) }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) hub.readImportFrom(uri) }
        val exportReq = hub.exportRequest
        LaunchedEffect(exportReq) {
            if (exportReq != null) {
                hub.exportRequest = null
                exportLauncher.launch("abplayer-settings.json")
            }
        }
        val importReq = hub.importRequest
        LaunchedEffect(importReq) {
            if (importReq != null) {
                hub.importRequest = null
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }

        // системная кнопка «назад»: диалог → снять выделение файлов → шторка → возврат на главный экран
        val selecting = hub.selectingView(logicalPage(pager.currentPage) == 2)
        BackHandler(enabled = hub.popup != null || selecting != null || hub.sheets.isNotEmpty() || logicalPage(pager.currentPage) != 1) {
            if (hub.popup != null) hub.closePopup()
            else if (selecting != null) hub.exitSelect(selecting)
            else if (hub.sheets.isNotEmpty()) hub.closeSheet()
            else hubGoMain(hub)
        }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            // в. размытая обложка на весь экран — общий фон для настроек, плеера и списка
            if (hub.data.fxLevel > 0f && hub.data.fxBackdropAlpha > 0f) {
                val bt = hub.trackById(hub.currentTrackId)
                CoverBackdrop(
                    bt?.id, bt != null && bt.hasArt && coverInPlayer(hub.data.coverMode),
                    blur = hub.data.fxBackdropBlur, alpha = hub.data.fxBackdropAlpha
                )
            }
            // б. тонкое зерно фона (под всеми экранами)
            if (hub.data.fxLevel > 0f) GrainLayer(dark, effectiveFx(hub.data.fxLevel), Modifier.fillMaxSize())
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                beyondViewportPageCount = 0
            ) { virtualPage ->
                key(dark) {
                when (logicalPage(virtualPage)) {
                    0 -> SettingsScreen(hub, dark) { hub.openSheet(Sheet.Menu(true)) }
                    1 -> MainScreen(hub) { hub.openSheet(Sheet.Menu(false)) }
                    else -> Column(
                        Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 18.dp)
                    ) {
                        ListView(hub, VIEW_PLAYLIST, null)
                    }
                }
                }
            }

            // шторки (стопкой, как в прототипе)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxH = maxHeight
                if (hub.sheets.isNotEmpty()) {
                    hub.sheets.toList().forEach { sheet ->
                        key(sheet) { SheetLayer(hub, sheet, dark, maxH) }
                    }
                }
                val p = hub.popup
                if (p != null) key(p) { PopupLayer(hub, p) }
            }

            BusyBar(hub)
            ToastHost(hub)
        }
      }
    }
}

/** Плашка поверх всех экранов, пока идёт сканирование папки или добавление файлов; есть кнопка «Остановить». */
@Composable
private fun BusyBar(hub: Hub) {
    val kind = hub.busyKind ?: return
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .layeredShadow(LocalFx.current, c.dark, shape, 10.dp)
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.border, shape)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val prog = hub.importProgress
            Txt(
                if (kind == "scan") "Сканирование… найдено файлов: ${hub.scanFound}"
                else if (prog != null) "Добавление файлов… ${prog.first} / ${prog.second}" else "Добавление файлов…",
                13f, color = c.text, modifier = Modifier.weight(1f).padding(end = 10.dp), maxLines = 1
            )
            // кнопка в стиле ряда таймера: пилюля с мягкой обводкой, достаточно широкая, текст не прижат к краям
            val stopShape = RoundedCornerShape(50)
            Pressable(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 124.dp)
                    .border(1.dp, idleButtonBorder(c), stopShape),
                normal = timerButtonBg(c),
                pressed = c.surface3,
                shape = stopShape,
                contentAlignment = Alignment.Center,
                onClick = { hub.stopScan() }
            ) {
                Txt("Остановить", 13f, FontWeight.SemiBold, c.accent2, modifier = Modifier.padding(horizontal = 18.dp), maxLines = 1)
            }
        }
    }
}

private fun hubGoMain(hub: Hub) {
    hub.navigate(1)
}

// ============================================================ шторка

@Composable
private fun SheetLayer(hub: Hub, sheet: Sheet, dark: Boolean, maxH: Dp) {
    val c = LocalColors.current
    val fixed = sheet is Sheet.ListSheet
    val isSound = sheet is Sheet.Sound || sheet is Sheet.Effects
    // Единая предельная высота для всех шторок: снизу вверх до строки с названием программы (она остаётся видимой)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bigH = (maxH - topInset - 72.dp).coerceAtLeast(240.dp)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        Modifier
            .fillMaxSize()
            // у шторки «Эффекты» затемнение слабое: иначе фон обложки не видно, пока двигаешь его ползунки
            .background(Color.Black.copy(alpha = if (sheet is Sheet.Effects) 0.12f else 0.55f))
            .pointerInput(Unit) { detectTapGestures { hub.closeSheet() } }
            .semantics { semOnClick(label = I18n.t("Закрыть")) { hub.closeSheet(); true } }
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(300)) { it }
        ) {
            val sizeMod = if (fixed) Modifier.height(bigH) else Modifier.heightIn(max = bigH)
            var dragTotal by remember { mutableStateOf(0f) }
            val swipeMod = if (isSound) {
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onVerticalDrag = { change, amount ->
                            if (amount > 0) dragTotal += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragTotal > 70.dp.toPx()) hub.closeSheet()
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    )
                }
            } else Modifier
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sizeMod)
                    .layeredShadow(LocalFx.current, c.dark, shape, 16.dp)
                    .clip(shape)
                    .background(c.surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(swipeMod)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)
                    .then(if (fixed) Modifier else Modifier.rubberBand().verticalScroll(rememberScrollState()))
            ) {
                when (sheet) {
                    is Sheet.Sleep -> SleepSheet(hub)
                    is Sheet.Sound -> SoundSheet(hub)
                    is Sheet.Effects -> EffectsSheet(hub)
                    is Sheet.ListSheet -> ListView(hub, sheet.view, onBack = { hub.closeSheet() })
                    is Sheet.Ab -> AbSheet(hub)
                    is Sheet.Sort -> SortSheet(hub, sheet.plid)
                    is Sheet.TrackMenu -> TrackMenuSheet(hub, sheet.id, sheet.view)
                    is Sheet.AddToPlaylist -> AddToPlaylistSheet(hub, sheet.ids)
                    is Sheet.PlaylistMenu -> PlaylistMenuSheet(hub, sheet.plid, sheet.view)
                    is Sheet.SeekStep -> SeekStepSheet(hub)
                    is Sheet.About -> AboutSheet(hub)
                    is Sheet.Privacy -> PrivacySheet(hub)
                    is Sheet.Licenses -> LicensesSheet(hub)
                    is Sheet.AccentColor -> AccentColorSheet(hub)
                    is Sheet.Covers -> CoversSheet(hub)
                    is Sheet.Menu -> MenuSheet(hub, sheet.onSettingsScreen, dark)
                    is Sheet.RadioEdit -> RadioEditSheet(hub, sheet.id)
                    is Sheet.TrackInfo -> TrackInfoSheet(hub, sheet.id)
                    is Sheet.Power -> PowerSheet(hub)
                    is Sheet.Help -> HelpSheet(hub)
                }
            }
        }
    }
}

// ============================================================ диалоги поверх шторок

@Composable
private fun PopupLayer(hub: Hub, popup: Popup) {
    val c = LocalColors.current
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { hub.closePopup() } }
            .semantics { semOnClick(label = I18n.t("Закрыть")) { hub.closePopup(); true } }
    )
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visibleState = visible, enter = slideInVertically(tween(280)) { it }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .layeredShadow(LocalFx.current, c.dark, shape, 16.dp)
                    .clip(shape)
                    .background(c.surface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp)
            ) {
                SheetHandle()
                when (popup) {
                    is Popup.TextInput -> TextInputContent(hub, popup)
                    is Popup.Confirm -> {
                        Txt(popup.title, 16f, FontWeight.ExtraBold, c.text)
                        if (popup.text.isNotEmpty()) {
                            Txt(popup.text, 12f, color = c.textDim, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton("Отмена", { hub.closePopup() }, Modifier.weight(1f))
                            ChipButton(popup.confirm, {
                                hub.closePopup()
                                popup.onOk()
                            }, Modifier.weight(1f), primary = true)
                        }
                    }
                    is Popup.EqMenu -> {
                        val p = hub.data.sound.presets.firstOrNull { it.id == popup.presetId }
                        if (p == null) {
                            hub.closePopup()
                        } else {
                            Txt(p.name, 16f, FontWeight.ExtraBold, c.text, maxLines = 1)
                            Txt("Эквалайзер", 12f, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
                            Column(Modifier.padding(top = 8.dp)) {
                                OptionRow("Применить", { hub.closePopup(); hub.applyEqPreset(p.id) }, icon = Ic.apply)
                                OptionRow("Переименовать", {
                                    hub.closePopup()
                                    hub.askText("Переименовать эквалайзер", p.name, "Название", "Сохранить") { v ->
                                        val name = v.trim()
                                        if (name.isEmpty()) {
                                            hub.toast("Введите название")
                                            false
                                        } else {
                                            hub.renameEq(p.id, name)
                                            true
                                        }
                                    }
                                }, icon = Ic.edit, divider = p.id != "manual")
                                if (p.id != "manual") {
                                    OptionRow("Удалить", { hub.closePopup(); hub.deleteEq(p.id) }, icon = Ic.trash, divider = false)
                                }
                            }
                        }
                    }
                    is Popup.AddSource -> {
                        Txt("Добавить", 16f, FontWeight.ExtraBold, c.text)
                        Column(Modifier.padding(top = 8.dp)) {
                            OptionRow("Добавить файлы", { hub.chooseAdd(AddKind.FILES, popup.view) }, icon = Ic.music)
                            OptionRow("Добавить папку", { hub.chooseAdd(AddKind.FOLDER, popup.view) }, icon = Ic.folder)
                            OptionRow(
                                "Добавить радиопоток",
                                { hub.closePopup(); hub.addRadioToPlaylistPrompt(hub.viewPlid(popup.view)) },
                                icon = Ic.volume, divider = false
                            )
                        }
                    }
                    is Popup.RadioSource -> {
                        Txt("Добавить радио", 16f, FontWeight.ExtraBold, c.text)
                        Column(Modifier.padding(top = 8.dp)) {
                            OptionRow("Добавить по ссылке", { hub.askRadioUrl() }, icon = Ic.plus)
                            OptionRow(
                                "Добавить файл плейлиста (m3u/m3u8)",
                                { hub.chooseAdd(AddKind.RADIO_FILE, com.brandmauer.abplayer.data.RADIO) },
                                icon = Ic.folder, divider = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputContent(hub: Hub, p: Popup.TextInput) {
    val c = LocalColors.current
    var value by remember { mutableStateOf(TextFieldValue(p.initial, TextRange(0, p.initial.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        focus.requestFocus()
        keyboard?.show()
    }
    val confirm = { if (p.onOk(value.text)) hub.closePopup() }

    Txt(p.title, 16f, FontWeight.ExtraBold, c.text)
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(44.dp)
            .border(1.dp, c.border, shape)
            .clip(shape)
            .background(c.surface2)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (p.number) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        if (value.text.isEmpty()) Txt(p.placeholder, 14f, color = c.textFaint)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipButton("Отмена", { hub.closePopup() }, Modifier.weight(1f))
        ChipButton(p.confirm, { confirm() }, Modifier.weight(1f), primary = true)
    }
}

// ============================================================ toast

@Composable
private fun ToastHost(hub: Hub) {
    val c = LocalColors.current
    val msg = hub.toastMsg
    var shown by remember { mutableStateOf<ToastMsg?>(null) }
    LaunchedEffect(msg) {
        if (msg != null) {
            shown = msg
            delay(msg.durationMs)
            shown = null
        }
    }
    val s = shown
    if (s != null) {
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 26.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val shape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .layeredShadow(LocalFx.current, c.dark, shape, 10.dp)
                    .clip(shape)
                    .background(c.surface3)
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Txt(s.text, 13f, FontWeight.SemiBold, c.text, textAlign = TextAlign.Center)
            }
        }
    }
}
