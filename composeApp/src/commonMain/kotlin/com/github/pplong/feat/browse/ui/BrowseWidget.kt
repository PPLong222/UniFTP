package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_arrow_downward
import uniftp.composeapp.generated.resources.ic_arrow_upward
import uniftp.composeapp.generated.resources.ic_create_new_folder
import uniftp.composeapp.generated.resources.ic_path
import uniftp.composeapp.generated.resources.ic_refresh

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
                painter = painterResource(Res.drawable.ic_path),
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
    actionClicked: (BrowseToolbarBarAction) -> Unit,
    fabClicked: (BrowseToolbarStatus) -> Unit
) {
    val list =
        if (barStatus == BrowseToolbarStatus.STANDARD) standardBrowseToolbarList else fileBrowseToolBarList


    HorizontalFloatingToolbar(
        expanded = true,
        content = {

            list.forEachIndexed { index, action ->
                key(action) {
                    IconButton(
                        onClick = {
                            actionClicked(action)
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
                    fabClicked(barStatus)
                },
                content = {
                    if (barStatus == BrowseToolbarStatus.STANDARD) {
                        Icon(
                            painter =
                                painterResource(Res.drawable.ic_arrow_upward),
                            contentDescription = null
                        )
                    } else {
                        Icon(
                            painter =
                                painterResource(Res.drawable.ic_arrow_downward),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    )
    
}

@Composable
private fun mapActionToResource(action: BrowseToolbarBarAction): Painter {
    return when (action) {
        BrowseToolbarBarAction.REFRESH -> painterResource(Res.drawable.ic_refresh)
        BrowseToolbarBarAction.SEARCH -> painterResource(Res.drawable.ic_refresh)
        BrowseToolbarBarAction.CREATE_FOLDER -> painterResource(Res.drawable.ic_create_new_folder)
        BrowseToolbarBarAction.DELETE -> painterResource(Res.drawable.ic_refresh)
        BrowseToolbarBarAction.MOVE -> painterResource(Res.drawable.ic_refresh)
        BrowseToolbarBarAction.SHARE -> painterResource(Res.drawable.ic_refresh)
        BrowseToolbarBarAction.INFO -> painterResource(Res.drawable.ic_refresh)
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
