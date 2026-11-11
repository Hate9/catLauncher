/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import android.content.Intent
import android.os.Looper
import android.os.Handler
import android.app.WallpaperManager
import android.app.WallpaperColors
import android.app.Activity
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.compose.ui.zIndex
import io.github.hate9.catlauncher.data.Category
import io.github.hate9.catlauncher.data.FAVORITES_ID
import io.github.hate9.catlauncher.data.HIDDEN_ID
import io.github.hate9.catlauncher.data.SNAP_DOWN
import io.github.hate9.catlauncher.data.SNAP_OFF
import io.github.hate9.catlauncher.data.ItemRef
import io.github.hate9.catlauncher.data.UNCATEGORIZED_ID
import io.github.hate9.catlauncher.widgets.WidgetController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface MenuState {
    val anchor: Offset

    data class ForItem(val row: Row.Entry, override val anchor: Offset) : MenuState
    data class ForHeader(val category: Category, override val anchor: Offset) : MenuState
}

private sealed interface DialogState {
    data class Rename(val category: Category) : DialogState
    data class Delete(val category: Category) : DialogState
    data class NewCategory(val itemId: String?) : DialogState
    data class MoveTo(val row: Row.Entry) : DialogState
    data class ChangeIcon(val row: Row.Entry) : DialogState
    data class RenameItem(val row: Row.Entry) : DialogState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(vm: LauncherViewModel, widgets: WidgetController?, settingsRequests: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val config by vm.config.collectAsState()
    val rows by vm.rows.collectAsState()
    val query by vm.query.collectAsState()
    val shown = vm.dragRows ?: rows
    val listState = rememberLazyListState()

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var menu by remember { mutableStateOf<MenuState?>(null) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsRequests) { if (settingsRequests > 0) settingsOpen = true }

    // Dialogs leave the IME attached and focus falls into the search field; tidy up when they close.
    LaunchedEffect(dialog == null, settingsOpen) {
        if (dialog == null && !settingsOpen) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // Touching the list ends search-field editing.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // Wallpaper parallax: the list's scroll fraction becomes the wallpaper's vertical offset.
    val view = LocalView.current
    LaunchedEffect(config.scrollWallpaper) {
        val wm = WallpaperManager.getInstance(context)
        if (!config.scrollWallpaper) {
            runCatching { view.windowToken?.let { wm.setWallpaperOffsets(it, 0.5f, 0f) } }
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty() || info.totalItemsCount == 0) 0f else {
                val avg = visible.sumOf { it.size }.toFloat() / visible.size
                val scrolled = listState.firstVisibleItemIndex * avg + listState.firstVisibleItemScrollOffset
                val total = info.totalItemsCount * avg - info.viewportSize.height
                if (total <= 0f) 0f else (scrolled / total).coerceIn(0f, 1f)
            }
        }.collect { fraction ->
            val token = view.windowToken ?: return@collect
            runCatching {
                wm.setWallpaperOffsetSteps(1f, 1f)
                wm.setWallpaperOffsets(token, 0.5f, fraction)
            }
        }
    }

    // Filtering rebuilds the list from the top; without this the widget rows re-inserted above
    // the anchored first header would land out of sight when the query is cleared.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    // When a block appears above a list that is sitting at the top (first favorite, first widget),
    // LazyColumn keeps the old first row anchored and hides the new one; stay at the top instead.
    LaunchedEffect(shown.firstOrNull()?.key) {
        if (listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0) listState.scrollToItem(0)
    }

    // A category created from a menu is inserted above the viewport; bring it into view.
    var scrollToKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollToKey, shown) {
        val key = scrollToKey ?: return@LaunchedEffect
        val index = shown.indexOfFirst { it.key == key }
        if (index >= 0) {
            scrollToKey = null
            listState.animateScrollToItem(index)
        }
    }

    val drag = remember(listState) {
        DragController(
            listState = listState,
            thresholdPx = with(density) { 10.dp.toPx() },
            edgePx = with(density) { 72.dp.toPx() },
            onBegin = vm::beginDrag,
            onMove = vm::moveRow,
            onEnd = vm::endDrag,
            onDragStarted = { menu = null; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }

    // A home screen never finishes on back; back just peels off whatever is open.
    BackHandler(enabled = true) {
        when {
            menu != null -> { menu = null; drag.disarm() }
            query.isNotEmpty() -> { vm.setQuery(""); keyboard?.hide() }
            else -> keyboard?.hide()
        }
    }

    LaunchedEffect(drag) {
        snapshotFlow { drag.autoScroll != 0f }.collectLatest { scrolling ->
            while (scrolling) {
                listState.scrollBy(drag.autoScroll * 18f)
                drag.settle()
                delay(8)
            }
        }
    }

    val darkText = rememberWallpaperDarkText()
    // System bar icons follow the same wallpaper-derived choice as the launcher text.
    val rootView = LocalView.current
    SideEffect {
        (rootView.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, rootView).apply {
                isAppearanceLightStatusBars = darkText
                isAppearanceLightNavigationBars = darkText
            }
        }
    }
    CompositionLocalProvider(LocalDarkText provides darkText) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchBar(
                query = query,
                onQueryChange = vm::setQuery,
                onGo = {
                    keyboard?.hide()
                    shown.firstOrNull { it is Row.Entry }?.let { vm.launch((it as Row.Entry).item) }
                },
                onLongPress = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    settingsOpen = true
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            LazyColumn(
                state = listState,
                flingBehavior = rememberHeaderSnapFlingBehavior(listState, headerTopGapPx = with(density) { HEADER_TOP_GAP.toPx() }, rows = { shown }) { forward ->
                    !drag.isActive && query.isBlank() && when (config.snapMode) {
                        SNAP_OFF -> false
                        SNAP_DOWN -> forward
                        else -> true
                    }
                },
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(drag) { trackDrag(drag) }
                    .pointerInput(listState) {
                        detectTapGestures(onLongPress = { pos ->
                            val under = listState.layoutInfo.visibleItemsInfo.firstOrNull { pos.y >= it.offset && pos.y < it.offset + it.size }
                            if (under == null || under.key == Row.Footer.key) settingsOpen = true
                        })
                    },
            ) {
                items(shown, key = { it.key }) { row ->
                    when (row) {
                        is Row.Widget -> WidgetRow(row, widgets, vm)
                        is Row.Header -> HeaderRow(row, Modifier.animateItem()) { anchor ->
                            menu = MenuState.ForHeader(row.category, anchor)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        is Row.Entry -> {
                            val dragged = drag.draggingKey == row.key
                            EntryRow(
                                row = row,
                                showIcon = config.showIcons,
                                modifier = if (dragged) Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = drag.translationFor(row.key)
                                        shadowElevation = 12.dp.toPx()
                                        scaleX = 1.03f; scaleY = 1.03f
                                    } else Modifier.animateItem(),
                                onClick = { vm.launch(row.item) },
                                onLongClick = { anchor ->
                                    if (query.isBlank()) drag.arm(row.key)
                                    menu = MenuState.ForItem(row, anchor)
                                },
                            )
                        }
                        Row.Footer -> Column {
                            Spacer(Modifier.height(HEADER_TOP_GAP))
                            HorizontalDivider(color = launcherTextColor().copy(alpha = 0.4f))
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        when (val m = menu) {
            is MenuState.ForItem -> ContextMenuOverlay(anchor = m.anchor, onDismiss = { menu = null; drag.disarm() }) {
                ItemMenu(vm, m.row, onClose = { menu = null; drag.disarm() }, onDialog = { dialog = it })
            }
            is MenuState.ForHeader -> ContextMenuOverlay(anchor = m.anchor, onDismiss = { menu = null }) {
                HeaderMenu(vm, config.categories, m.category, onClose = { menu = null }, onDialog = { dialog = it })
            }
            null -> {}
        }

        when (val d = dialog) {
            is DialogState.Rename -> TextInputDialog("Rename category", d.category.name, "Rename", onDismiss = { dialog = null }) {
                vm.repository.renameCategory(d.category.id, it); dialog = null
            }
            is DialogState.NewCategory -> TextInputDialog("New category", "", "Create", onDismiss = { dialog = null }) {
                scrollToKey = "h:" + vm.repository.createCategory(it, d.itemId); dialog = null
            }
            is DialogState.Delete -> ConfirmDialog(
                "Delete \"${d.category.name}\"?", "Its ${d.category.items.size} items move to $UNCATEGORIZED_LABEL.", "Delete",
                onDismiss = { dialog = null },
            ) { vm.repository.deleteCategory(d.category.id); dialog = null }
            is DialogState.MoveTo -> ChooseCategoryDialog(
                config.categories, d.row.categoryId,
                onDismiss = { dialog = null },
                onNewCategory = { dialog = DialogState.NewCategory(d.row.item.id) },
            ) { vm.repository.moveItemToCategory(d.row.item.id, it); dialog = null }
            is DialogState.ChangeIcon -> IconPickerDialog(vm, d.row.item, d.row.label, config.iconPack, onDismiss = { dialog = null })
            is DialogState.RenameItem -> TextInputDialog(
                "Rename app", d.row.label, "Rename",
                onDismiss = { dialog = null },
                onReset = if (d.row.item.customLabel != null) ({ vm.repository.setItemLabel(d.row.item.id, null); dialog = null }) else null,
            ) { vm.repository.setItemLabel(d.row.item.id, it); dialog = null }
            null -> {}
        }

        if (settingsOpen) {
            SettingsSheet(config, vm.repository, widgets, onDismiss = { settingsOpen = false })
        }
    }
    }
}

private const val UNCATEGORIZED_LABEL = "Uncategorized"

/** Left inset shared by headers and rows. */
private val ROW_INSET = 16.dp

/** Gap between a category's last row and the next header's divider; the snap target skips it. */
private val HEADER_TOP_GAP = 6.dp

/**
 * No scrim over the wallpaper, so text colour follows the wallpaper rather than the theme: the
 * system's WallpaperColors say whether dark text is readable on it (API 27+). A soft shadow in
 * the opposite colour covers busy regions.
 */
@Composable
private fun rememberWallpaperDarkText(): Boolean {
    val context = LocalContext.current
    val fallback = !isSystemInDarkTheme()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return fallback
    var darkText by remember { mutableStateOf(fallback) }
    DisposableEffect(context) {
        val wm = WallpaperManager.getInstance(context)
        fun read() {
            val colors = runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return
            darkText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            } else {
                // Hints are private before 12; judge by the dominant colour's luminance.
                ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > 0.5
            }
        }
        read()
        val listener = WallpaperManager.OnColorsChangedListener { _, which -> if (which and WallpaperManager.FLAG_SYSTEM != 0) read() }
        wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wm.removeOnColorsChangedListener(listener) }
    }
    return darkText
}

private val LocalDarkText = compositionLocalOf { true }

@Composable
private fun launcherTextColor(): Color = if (LocalDarkText.current) Color(0xFF1A1A1A) else Color.White

@Composable
private fun launcherTextStyle(): TextStyle = TextStyle(
    shadow = Shadow(
        color = (if (LocalDarkText.current) Color.White else Color.Black).copy(alpha = 0.7f),
        offset = Offset(0f, 1f),
        blurRadius = 6f,
    )
)

/**
 * Runs in the Initial pass so, once a row has armed the drag controller via its long click,
 * movement is seen (and consumed) here before the list's scroll or the row's click handler.
 */
private suspend fun PointerInputScope.trackDrag(drag: DragController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pointerId = down.id
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.first().also { pointerId = it.id }
            if (drag.isActive && change.pressed) {
                val moved = change.positionChange()
                if (moved != Offset.Zero) drag.onPointerMove(moved)
                change.consume()
            }
            if (!change.pressed) {
                if (drag.isActive) {
                    change.consume()
                    drag.onPointerUp()
                }
                break
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}

/**
 * Long press that beats the text field's own (text selection) long press: it watches the
 * Initial pass, fires a little before the system timeout, and consumes the rest of the gesture.
 */
private suspend fun PointerInputScope.detectLongPressBeforeChildren(onLongPress: () -> Unit) {
    val slop = viewConfiguration.touchSlop
    val timeout = (viewConfiguration.longPressTimeoutMillis - 60).coerceAtLeast(100)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val held = withTimeoutOrNull(timeout) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (held == null) {
            onLongPress()
            // Swallow the remainder so the field neither selects text nor shows the keyboard.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val textColor = launcherTextColor()
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = 16.sp),
        cursorBrush = SolidColor(textColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET, vertical = 6.dp)
            .pointerInput(onLongPress) { detectLongPressBeforeChildren(onLongPress) },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = textColor.copy(alpha = 0.6f), fontSize = 16.sp)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Text(
                        "✕",
                        color = textColor,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { onQueryChange("") }
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderRow(row: Row.Header, modifier: Modifier, onLongClick: (Offset) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot(); height = it.size.height }
            .combinedClickable(onClick = {}, onLongClick = { onLongClick(Offset(origin.x + with(density) { ROW_INSET.toPx() }, origin.y + height)) })
    ) {
        Spacer(Modifier.height(HEADER_TOP_GAP))
        HorizontalDivider(color = launcherTextColor().copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = row.category.name,
            fontSize = 22.sp,
            color = launcherTextColor(),
            style = launcherTextStyle(),
            modifier = Modifier.padding(horizontal = ROW_INSET),
        )
        Spacer(Modifier.height(2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(row: Row.Entry, showIcon: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: (Offset) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot(); height = it.size.height }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick(Offset(origin.x + with(density) { ROW_INSET.toPx() }, origin.y + height)) },
            )
            .padding(horizontal = ROW_INSET, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            if (row.icon != null) Image(bitmap = row.icon, contentDescription = null, modifier = Modifier.size(LauncherViewModel.ICON_DP.dp))
            else Spacer(Modifier.size(LauncherViewModel.ICON_DP.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text = row.label, fontSize = 20.sp, color = launcherTextColor(), style = launcherTextStyle(), maxLines = 1)
    }
}

@Composable
private fun LazyItemScope.WidgetRow(row: Row.Widget, widgets: WidgetController?, vm: LauncherViewModel) {
    val context = LocalContext.current
    val widthDp = remember { (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt() - 32 }
    val view = remember(row.entry.appWidgetId, row.entry.heightDp) { widgets?.viewFor(row.entry, widthDp) }
    if (view == null) {
        // Provider currently unavailable; WidgetController.startListening prunes widgets that are really gone.
        Spacer(Modifier.height(1.dp))
        return
    }
    AndroidView(
        factory = { (view.parent as? android.view.ViewGroup)?.removeView(view); view },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(row.entry.heightDp.dp),
    )
}

@Composable
private fun ItemMenu(vm: LauncherViewModel, row: Row.Entry, onClose: () -> Unit, onDialog: (DialogState) -> Unit) {
    val context = LocalContext.current
    val ref = row.item.ref
    var shortcuts by remember(row.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }
    LaunchedEffect(row.key) { shortcuts = vm.shortcutsFor(ref) }

    MenuTitle(row.label, row.icon)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        shortcuts.forEach { sc ->
            val icon = remember(sc.id) { vm.shortcutIcon(sc) }
            MenuItem(
                text = (sc.shortLabel ?: sc.longLabel ?: sc.id).toString(),
                icon = icon,
                trailing = {
                    IconButton(onClick = { vm.addShortcut(sc, ref.userSerial, row.categoryId); onClose() }) { Text("+") }
                },
            ) { vm.launchShortcut(sc, ref.userSerial); onClose() }
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
    }
    val favorite = row.item.favoritedAt != null
    val hidden = row.item.hiddenAt != null
    MenuItem(if (favorite) "Remove from favorites" else "Add to favorites") { vm.repository.setFavorite(row.item.id, !favorite); onClose() }
    MenuItem(if (hidden) "Unhide" else "Hide") { vm.repository.setHidden(row.item.id, !hidden); onClose() }
    if (row.block == null) MenuItem("Move to category…") { onDialog(DialogState.MoveTo(row)); onClose() }
    MenuItem("Change icon…") { onDialog(DialogState.ChangeIcon(row)); onClose() }
    MenuItem("Rename…") { onDialog(DialogState.RenameItem(row)); onClose() }
    if (ref !is ItemRef.IntentShortcut) MenuItem("App info") { vm.repository.apps.openAppInfo(ref); onClose() }
    when (ref) {
        is ItemRef.App -> MenuItem("Uninstall") {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${ref.packageName}"))
            vm.repository.apps.userFor(ref.userSerial)?.let { intent.putExtra(Intent.EXTRA_USER, it) }
            runCatching { context.startActivity(intent) }
            onClose()
        }
        else -> MenuItem("Remove from list") { vm.repository.removeItem(row.item.id); onClose() }
    }
}

@Composable
private fun HeaderMenu(vm: LauncherViewModel, categories: List<Category>, category: Category, onClose: () -> Unit, onDialog: (DialogState) -> Unit) {
    val movable = categories.filter { !it.isUncategorized }
    val index = movable.indexOfFirst { it.id == category.id }
    MenuTitle(category.name)
    if (category.id == FAVORITES_ID) {
        MenuItem("Starred apps appear here as well as in their category.", enabled = false) {}
        return
    }
    if (category.id == HIDDEN_ID) {
        MenuItem("Hidden apps; turn off \"Show hidden\" in settings to tuck them away.", enabled = false) {}
        return
    }
    if (!category.isUncategorized) {
        MenuItem("Rename…") { onDialog(DialogState.Rename(category)); onClose() }
        MenuItem("Move up", enabled = index > 0) { vm.repository.moveCategory(category.id, -1); onClose() }
        MenuItem("Move down", enabled = index in 0 until movable.lastIndex) { vm.repository.moveCategory(category.id, 1); onClose() }
    }
    MenuItem("New category…") { onDialog(DialogState.NewCategory(null)); onClose() }
    if (category.manual) MenuItem("Reset to automatic order") { vm.repository.resetCategoryOrder(category.id); onClose() }
    if (!category.isUncategorized) MenuItem("Delete…") { onDialog(DialogState.Delete(category)); onClose() }
}
