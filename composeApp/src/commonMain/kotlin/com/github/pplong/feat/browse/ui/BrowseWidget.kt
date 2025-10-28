package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.FTPFileSelectableUiModel
import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.browse.SearchState
import com.github.pplong.feat.browse.mapToUiIntent
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.CREATE_FOLDER
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.DELETE
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.INFO
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.MOVE
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.REFRESH
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.SEARCH
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.SHARE
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.UPLOAD_FILE
import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction.UPLOAD_MEDIA
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_arrow_downward
import uniftp.composeapp.generated.resources.ic_arrow_upward
import uniftp.composeapp.generated.resources.ic_close
import uniftp.composeapp.generated.resources.ic_create_new_folder
import uniftp.composeapp.generated.resources.ic_delete
import uniftp.composeapp.generated.resources.ic_draft
import uniftp.composeapp.generated.resources.ic_home
import uniftp.composeapp.generated.resources.ic_image_upload
import uniftp.composeapp.generated.resources.ic_refresh
import uniftp.composeapp.generated.resources.ic_search
import uniftp.composeapp.generated.resources.search

@Composable
fun DraggablePathIndicator(
    path: String,
    onPathClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val paths = path.split("/")
    val rowState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(paths) {
        coroutineScope.launch {
            if (paths.isNotEmpty()) {
                rowState.animateScrollToItem(paths.lastIndex)
            }
        }
    }
    LazyRow(
        state = rowState,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        item {
            Icon(
                painter = painterResource(Res.drawable.ic_home),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        onPathClicked("/")
                    },
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        items(paths.size - 1) { index ->
            SinglePathText(paths[index + 1], isCurPath = paths.lastIndex == index + 1) {
                onPathClicked(
                    buildPath(paths, index + 1)
                )
            }
        }
    }
}

/**
 * @param: path: /etc/abc, /
 */
@Composable
fun SinglePathText(
    path: String, isCurPath: Boolean = false, onButtonClick: () -> Unit = {}
) {
    val source = remember { MutableInteractionSource() }
    val ripple = ripple(color = Color.Gray, bounded = true)

    Card(
        shape = RoundedCornerShape(size = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .padding(start = 0.dp)
            .clip(RoundedCornerShape(size = 8.dp))
            .clickable(
                interactionSource = source,
                indication = ripple,
                onClick = onButtonClick
            )
    ) {
        Text(
            text = "/".plus(path),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurPath) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun buildPath(paths: List<String>, index: Int): String {
    val builder = StringBuilder()
    for (i in 1..index) {
        builder.append("/")
        builder.append(paths[i])
    }
    return builder.toString()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseFloatingToolbar(
    barStatus: BrowseToolbarStatus,
    onIntent: (BrowseUiIntent) -> Unit,
) {
    val list =
        if (barStatus == BrowseToolbarStatus.STANDARD) standardBrowseToolbarList else fileBrowseToolBarList

    val uiIntent =
        if (barStatus == BrowseToolbarStatus.STANDARD) UPLOAD_FILE else BrowseToolbarBarAction.DOWNLOAD
    HorizontalFloatingToolbar(
        expanded = true,
        content = {

            list.forEachIndexed { index, action ->
                key(action) {
                    IconButton(
                        onClick = {
                            onIntent(action.mapToUiIntent())
                        }
                    ) {
                        Icon(
                            painter = mapActionToResource(action),
                            contentDescription = null
                        )
                    }
//                    // TODO performance issue here?
//                    var isVisible by remember { mutableStateOf(false) }
//                    LaunchedEffect(Unit) {
//                        delay(index * 100L + 150L)
//                        isVisible = true
//                    }
//                    AnimatedVisibility(
//                        visible = isVisible,
//                        enter = (fadeIn(
//                            animationSpec = tween(
//                                150
//                            )
//                        ) +
//                                slideInVertically(
//                                    animationSpec = tween(
//                                        150
//                                    ),
//                                    initialOffsetY = { it }
//                                )),
//                        exit = fadeOut(animationSpec = tween(0, delayMillis = 0))
//                    ) {
//
//
//                    }
                }
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = {
                    onIntent(uiIntent.mapToUiIntent())
                },
                content = {
                    Icon(
                        painter = painterResource(if (barStatus == BrowseToolbarStatus.STANDARD) Res.drawable.ic_arrow_upward else Res.drawable.ic_arrow_downward),
                        contentDescription = null
                    )
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseFloatingActionMenu(
    modifier: Modifier,
    barStatus: BrowseToolbarStatus,
    onIntent: (BrowseUiIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val actionList =
        if (barStatus == BrowseToolbarStatus.STANDARD) standardFabsMenuList else fileSelectionFabsMenuList
    val focusRequester = remember { FocusRequester() }
    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = !expanded }
            ) {
                val imageVector = when {
                    checkedProgress > 0.5f -> Res.drawable.ic_close
                    barStatus == BrowseToolbarStatus.STANDARD -> Res.drawable.ic_arrow_upward
                    else -> Res.drawable.ic_arrow_downward
                }
                Icon(
                    painter = painterResource(imageVector),
                    contentDescription = null
                )
            }
        }
    ) {
        for (action in actionList) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onIntent(action.mapToUiIntent())
                    expanded = !expanded
                },
                text = { Text(stringResource(action.titleRes)) },
                icon = {
                    Icon(
                        painterResource(action.iconRes),
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun BoxScope.BrowseSearchBar(
    searchState: SearchState,
    cancel: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    SearchBar(
        modifier = Modifier.align(Alignment.TopCenter),
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    onSearch(searchQuery, selectedIndex == 0)
                },
                expanded = true,
                onExpandedChange = { },
                placeholder = { Text(stringResource(Res.string.search)) },
                leadingIcon = {
                    Icon(
                        painterResource(Res.drawable.ic_search),
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(onClick = cancel) {
                        Icon(
                            painterResource(Res.drawable.ic_close),
                            contentDescription = null
                        )
                    }
                }
            )
        },
        expanded = true,
        onExpandedChange = { expanded ->
            if (!expanded) {
                cancel()
            }
        }
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            BrowseSearchBarDirectorSelection.entries.forEachIndexed { index, selection ->
                ToggleButton(
                    checked = selectedIndex == index,
                    onCheckedChange = {
                        selectedIndex = index
                        if (searchQuery.isNotEmpty()) {
                            onSearch(searchQuery, selectedIndex == 0)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            BrowseSearchBarDirectorSelection.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    Text(stringResource(selection.titleRes))
                }
            }
        }


        if (searchState.loadingStatus.isLoading()) {
            Spacer(modifier = Modifier.padding(top = 64.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(64.dp).align(Alignment.Center)
                )
            }
        } else if (searchState.loadingStatus.isSuccess()) {
            SearchResultList(searchState.result, {})
        }
    }
}

@Composable
fun SearchResultList(
    fileList: List<FTPFileUiModel>,
    onIntent: (BrowseUiIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(fileList) { file ->
            FTPFileInfo(
                FTPFileSelectableUiModel(file),
                onIntent,
                BrowseToolbarStatus.STANDARD,
            )
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun mapActionToResource(action: BrowseToolbarBarAction): Painter {
    return when (action) {
        REFRESH -> painterResource(Res.drawable.ic_refresh)
        SEARCH -> painterResource(Res.drawable.ic_refresh)
        CREATE_FOLDER -> painterResource(Res.drawable.ic_create_new_folder)
        DELETE -> painterResource(Res.drawable.ic_delete)
        MOVE -> painterResource(Res.drawable.ic_refresh)
        SHARE -> painterResource(Res.drawable.ic_refresh)
        INFO -> painterResource(Res.drawable.ic_refresh)
        UPLOAD_FILE -> painterResource(Res.drawable.ic_draft)
        UPLOAD_MEDIA -> painterResource(Res.drawable.ic_image_upload)
        BrowseToolbarBarAction.DOWNLOAD -> painterResource(Res.drawable.ic_arrow_downward)
    }
}

@Composable
@Preview
fun PreviewDraggablePathIndicator() {
    DraggablePathIndicator(
        path = "/test/test",
        onPathClicked = {}
    )
}
