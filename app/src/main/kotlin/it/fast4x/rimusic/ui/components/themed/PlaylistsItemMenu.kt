package it.fast4x.rimusic.ui.components.themed

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.PlaylistSortBy
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.PlaylistPreview
import it.fast4x.rimusic.transaction
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.ui.screens.home.PINNED_PREFIX
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.MONTHLY_PREFIX
import it.fast4x.rimusic.utils.menuStyleKey
import it.fast4x.rimusic.utils.playlistSortByKey
import it.fast4x.rimusic.utils.playlistSortOrderKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun PlaylistsItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onSelectUnselect: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onUncheck: (() -> Unit)? = null,
    playlist: PlaylistPreview? = null,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onImportOnlinePlaylist: (() -> Unit)? = null,
    onAddToPlaylist: ((PlaylistPreview) -> Unit)? = null,
    showOnSyncronize: Boolean = false,
    onSyncronize: (() -> Unit)? = null,
    onRenumberPositions: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    showonListenToYT: Boolean = false,
    onListenToYT: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onGoToPlaylist: ((Long) -> Unit)? = null
    ) {
    val (colorPalette, typography) = LocalAppearance.current
    val density = LocalDensity.current

    var isViewingPlaylists by remember {
        mutableStateOf(false)
    }

    var height by remember {
        mutableStateOf(0.dp)
    }

    val menuStyle by rememberPreference(
        menuStyleKey,
        MenuStyle.List
    )

    if (menuStyle == MenuStyle.Grid) {
        PlaylistsItemGridMenu(
            onDismiss = onDismiss,
            modifier = modifier,
            playlist = playlist,
            onSelectUnselect = onSelectUnselect,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
            onImportOnlinePlaylist = onImportOnlinePlaylist,
            onAddToPlaylist = onAddToPlaylist,
            showOnSyncronize = showOnSyncronize,
            onSyncronize = onSyncronize,
            onRenumberPositions = onRenumberPositions,
            onDelete = onDelete,
            onRename = onRename,
            showonListenToYT = showonListenToYT,
            onListenToYT = onListenToYT,
            onExport = onExport,
            onImport = onImport,
            onGoToPlaylist = onGoToPlaylist
        )
    } else {

        AnimatedContent(
            targetState = isViewingPlaylists,
            transitionSpec = {
                val animationSpec = tween<IntOffset>(400)
                val slideDirection =
                    if (targetState) AnimatedContentTransitionScope.SlideDirection.Left
                    else AnimatedContentTransitionScope.SlideDirection.Right

                slideIntoContainer(slideDirection, animationSpec) togetherWith
                        slideOutOfContainer(slideDirection, animationSpec)
            }, label = ""
        ) { currentIsViewingPlaylists ->
            if (currentIsViewingPlaylists) {
                val sortBy by rememberPreference(playlistSortByKey, PlaylistSortBy.DateAdded)
                val sortOrder by rememberPreference(playlistSortOrderKey, SortOrder.Descending)
                val playlistPreviews by remember {
                    Database.playlistPreviews(sortBy, sortOrder)
                }.collectAsState(initial = emptyList(), context = Dispatchers.IO)

                val pinnedPlaylists = playlistPreviews.filter {
                    it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                }

                val unpinnedPlaylists = playlistPreviews.filter {
                    !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                    !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true)
                }

                var isCreatingNewPlaylist by rememberSaveable {
                    mutableStateOf(false)
                }

                if (isCreatingNewPlaylist && onAddToPlaylist != null) {
                    InputTextDialog(
                        onDismiss = { isCreatingNewPlaylist = false },
                        title = stringResource(R.string.enter_the_playlist_name),
                        value = "",
                        placeholder = stringResource(R.string.enter_the_playlist_name),
                        setValue = { text ->
                            onDismiss()
                            transaction {
                                val playlistId = Database.insert(Playlist(name = text))
                                onAddToPlaylist(
                                    PlaylistPreview(
                                        Playlist(
                                            id = playlistId,
                                            name = text
                                        ), 0
                                    )
                                )
                            }


                        }
                    )

                }

                BackHandler {
                    isViewingPlaylists = false
                }

                Menu(
                    modifier = modifier
                        .requiredHeight(height)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { isViewingPlaylists = false },
                            icon = R.drawable.chevron_back,
                            color = colorPalette.textSecondary,
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(20.dp)
                        )

                        if (onAddToPlaylist != null) {
                            SecondaryTextButton(
                                text = stringResource(R.string.new_playlist),
                                onClick = { isCreatingNewPlaylist = true },
                                alternative = true
                            )
                        }
                    }

                    if (pinnedPlaylists.isNotEmpty()) {
                        BasicText(
                            text = stringResource(R.string.pinned_playlists),
                            style = typography.m.semiBold,
                            modifier = modifier.padding(start = 20.dp, top = 5.dp)
                        )

                        onAddToPlaylist?.let { onAddToPlaylist ->
                            pinnedPlaylists.forEach { playlistPreview ->
                                MenuEntry(
                                    icon = R.drawable.add_in_playlist,
                                    text = playlistPreview.playlist.name.substringAfter(
                                        PINNED_PREFIX
                                    ),
                                    secondaryText = "${playlistPreview.songCount} " + stringResource(
                                        R.string.songs
                                    ),
                                    onClick = {
                                        onDismiss()
                                        onAddToPlaylist(
                                            PlaylistPreview(
                                                playlistPreview.playlist,
                                                playlistPreview.songCount
                                            )
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(
                                            icon = R.drawable.open,
                                            color = colorPalette.text,
                                            onClick = {
                                              if (onGoToPlaylist != null) {
                                                    onGoToPlaylist(playlistPreview.playlist.id)
                                                    onDismiss()
                                              }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (unpinnedPlaylists.isNotEmpty()) {
                        BasicText(
                            text = stringResource(R.string.playlists),
                            style = typography.m.semiBold,
                            modifier = modifier.padding(start = 20.dp, top = 5.dp)
                        )

                        onAddToPlaylist?.let { onAddToPlaylist ->
                            unpinnedPlaylists.forEach { playlistPreview ->
                                MenuEntry(
                                    icon = R.drawable.add_in_playlist,
                                    text = playlistPreview.playlist.name,
                                    secondaryText = "${playlistPreview.songCount} " + stringResource(
                                        R.string.songs
                                    ),
                                    onClick = {
                                        onDismiss()
                                        onAddToPlaylist(
                                            PlaylistPreview(
                                                playlistPreview.playlist,
                                                playlistPreview.songCount
                                            )
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(
                                            icon = R.drawable.open,
                                            color = colorPalette.text,
                                            onClick = {
                                                if (onGoToPlaylist != null) {
                                                    onGoToPlaylist(playlistPreview.playlist.id)
                                                    onDismiss()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Menu(
                    modifier = modifier
                        .onPlaced { height = with(density) { it.size.height.toDp() } }
                ) {
                    val thumbnailSizeDp = Dimensions.thumbnails.song + 20.dp
                    val thumbnailSizePx = thumbnailSizeDp.px
                    val thumbnailArtistSizeDp = Dimensions.thumbnails.song + 10.dp
                    val thumbnailArtistSizePx = thumbnailArtistSizeDp.px

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                    ) {
                        if (playlist != null) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp
                            )
                        }

                        /*
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            //icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                            icon = R.drawable.heart,
                            //color = colorPalette.favoritesIcon,
                            color = if (likedAt == null) colorPalette.textDisabled else colorPalette.text,
                            onClick = {
                                query {
                                    if (Database.like(
                                            mediaItem.mediaId,
                                            if (likedAt == null) System.currentTimeMillis() else null
                                        ) == 0
                                    ) {
                                        Database.insert(mediaItem, Song::toggleLike)
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(24.dp)
                        )

                        if (!isLocal) IconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette.text,
                            onClick = onShare,
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(24.dp)
                        )

                    }
                    */
                    }

                    Spacer(
                        modifier = Modifier
                            .height(8.dp)
                    )

                    onSelectUnselect?.let { onSelectUnselect ->
                        MenuEntry(
                            icon = R.drawable.checked,
                            text = "${stringResource(R.string.item_select)}/${stringResource(R.string.item_deselect)}",
                            onClick = {
                                onDismiss()
                                onSelectUnselect()
                            }
                        )
                    }
                    onSelect?.let { onSelect ->
                        MenuEntry(
                            icon = R.drawable.checked,
                            text = stringResource(R.string.item_select),
                            onClick = {
                                onDismiss()
                                onSelect()
                            }
                        )
                    }
                    /*
                onUncheck?.let { onUncheck ->
                    MenuEntry(
                        icon = R.drawable.unchecked,
                        text = stringResource(R.string.item_uncheck),
                        onClick = {
                            onDismiss()
                            onUncheck()
                        }
                    )
                }
                 */
                    onPlayNext?.let { onPlayNext ->
                        MenuEntry(
                            icon = R.drawable.play_skip_forward,
                            text = stringResource(R.string.play_next),
                            onClick = {
                                onDismiss()
                                onPlayNext()
                            }
                        )
                    }

                    onEnqueue?.let { onEnqueue ->
                        MenuEntry(
                            icon = R.drawable.enqueue,
                            text = stringResource(R.string.enqueue),
                            onClick = {
                                onDismiss()
                                onEnqueue()
                            }
                        )
                    }

                    if (showOnSyncronize) onSyncronize?.let { onSyncronize ->
                        MenuEntry(
                            icon = R.drawable.sync,
                            text = stringResource(R.string.sync),
                            onClick = {
                                onDismiss()
                                onSyncronize()
                            }
                        )
                    }

                    onImportOnlinePlaylist?.let { onImportOnlinePlaylist ->
                        MenuEntry(
                            icon = R.drawable.add_in_playlist,
                            text = stringResource(R.string.import_playlist),
                            onClick = {
                                onDismiss()
                                onImportOnlinePlaylist()
                            }
                        )
                    }

                    if (onAddToPlaylist != null) {
                        MenuEntry(
                            icon = R.drawable.add_in_playlist,
                            text = stringResource(R.string.add_to_playlist),
                            onClick = { isViewingPlaylists = true },
                            trailingContent = {
                                Image(
                                    painter = painterResource(R.drawable.chevron_forward),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        colorPalette.textSecondary
                                    ),
                                    modifier = Modifier
                                        .size(16.dp)
                                )
                            }
                        )
                    }

                    onRename?.let { onRename ->
                        MenuEntry(
                            icon = R.drawable.title_edit,
                            text = stringResource(R.string.rename),
                            onClick = {
                                onDismiss()
                                onRename()
                            }
                        )
                    }

                    onDelete?.let { onDelete ->
                        MenuEntry(
                            icon = R.drawable.trash,
                            text = stringResource(R.string.delete),
                            onClick = {
                                onDismiss()
                                onDelete()
                            }
                        )
                    }

                    onRenumberPositions?.let { onRenumberPositions ->
                        MenuEntry(
                            icon = R.drawable.position,
                            text = stringResource(R.string.renumber_songs_positions),
                            onClick = {
                                onDismiss()
                                onRenumberPositions()
                            }
                        )
                    }

                    if (showonListenToYT) onListenToYT?.let { onListenToYT ->
                        MenuEntry(
                            icon = R.drawable.play,
                            text = stringResource(R.string.listen_on_youtube),
                            onClick = {
                                onDismiss()
                                onListenToYT()
                            }
                        )
                    }

                    onExport?.let { onExport ->
                        MenuEntry(
                            icon = R.drawable.export,
                            text = stringResource(R.string.export_playlist),
                            onClick = {
                                onDismiss()
                                onExport()
                            }
                        )
                    }

                    onImport?.let { onImport ->
                        MenuEntry(
                            icon = R.drawable.resource_import,
                            text = stringResource(R.string.import_playlist),
                            onClick = {
                                onDismiss()
                                onImport()
                            }
                        )
                    }
                }
            }
        }
    }
}