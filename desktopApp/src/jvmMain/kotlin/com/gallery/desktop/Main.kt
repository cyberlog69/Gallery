package com.gallery.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.gallery.core.scanner.DesktopFileScanner
import com.gallery.desktop.ui.PicasaViewerScreen
import com.gallery.desktop.ui.generatePicasaWindowIcon
import com.gallery.desktop.ui.theme.PicasaTheme
import com.gallery.desktop.viewmodel.DesktopGalleryViewModel

fun main(args: Array<String>) = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    val scope = rememberCoroutineScope()
    val viewModel = remember { DesktopGalleryViewModel(scope) }

    LaunchedEffect(Unit) {
        // If a file or directory path was passed as CLI argument, open it directly!
        if (args.isNotEmpty()) {
            val target = java.io.File(args[0])
            if (target.exists()) {
                val folder = if (target.isDirectory) target else target.parentFile
                if (folder != null) {
                    val targetFile = if (target.isFile) target else null
                    viewModel.loadFolder(folder, targetFile)
                }
            }
        } else {
            // Otherwise, scan default Pictures directory
            val picturesDir = DesktopFileScanner.getDefaultPicturesDirectory()
            viewModel.loadFolder(picturesDir)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Picasa Photo Viewer",
        icon = remember { BitmapPainter(generatePicasaWindowIcon(128)) },
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionLeft -> {
                        viewModel.previous()
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.next()
                        true
                    }
                    Key.Spacebar -> {
                        viewModel.toggleSlideshow()
                        true
                    }
                    Key.Escape -> {
                        exitApplication()
                        true
                    }
                    Key.R -> {
                        if (event.isCtrlPressed) {
                            viewModel.rotateLeft()
                        } else {
                            viewModel.rotateRight()
                        }
                        true
                    }
                    Key.F -> {
                        viewModel.toggleFilmstrip()
                        true
                    }
                    Key.I -> {
                        viewModel.toggleExif()
                        true
                    }
                    Key.T -> {
                        viewModel.toggleAeroTheme()
                        true
                    }
                    Key.E -> {
                        viewModel.toggleEditor()
                        true
                    }
                    Key.S -> {
                        viewModel.toggleFavorite()
                        true
                    }
                    Key.D -> {
                        viewModel.toggleDuplicatePanel()
                        true
                    }
                    Key.C -> {
                        if (event.isCtrlPressed) {
                            viewModel.copyCurrentToClipboard()
                            true
                        } else false
                    }
                    Key.P -> {
                        if (event.isCtrlPressed) {
                            viewModel.openPrintDialog()
                            true
                        } else false
                    }
                    Key.Delete -> {
                        viewModel.deleteCurrent()
                        true
                    }
                    Key.Equals, Key.Plus -> {
                        viewModel.zoomIn()
                        true
                    }
                    Key.Minus -> {
                        viewModel.zoomOut()
                        true
                    }
                    Key.Zero -> {
                        viewModel.resetTransform()
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        // Native OS Drag-and-Drop file and folder loading
        LaunchedEffect(window) {
            window.dropTarget = object : java.awt.dnd.DropTarget() {
                override fun drop(evt: java.awt.dnd.DropTargetDropEvent) {
                    try {
                        evt.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                        val droppedFiles = evt.transferable.getTransferData(
                            java.awt.datatransfer.DataFlavor.javaFileListFlavor
                        ) as? List<*>
                        val firstFile = droppedFiles?.firstOrNull() as? java.io.File
                        if (firstFile != null && firstFile.exists()) {
                            val folder = if (firstFile.isDirectory) firstFile else firstFile.parentFile
                            if (folder != null) {
                                val targetFile = if (firstFile.isFile) firstFile else null
                                viewModel.loadFolder(folder, targetFile)
                            }
                        }
                        evt.dropComplete(true)
                    } catch (e: Exception) {
                        evt.dropComplete(false)
                    }
                }
            }
        }

        PicasaTheme {
            PicasaViewerScreen(viewModel = viewModel)
        }
    }
}
