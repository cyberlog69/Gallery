package com.gallery.core.io

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File

/**
 * Windows & Desktop native clipboard manager.
 * Supports copying decoded images directly to clipboard for instant pasting (Ctrl+V)
 * into chats (Slack, WhatsApp, Discord), Word documents, email, and photo editors.
 */
object DesktopClipboardHelper : ClipboardOwner {

    override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) {
        // No cleanup needed
    }

    /**
     * Copies a BufferedImage to the system clipboard as DataFlavor.imageFlavor.
     */
    fun copyImageToClipboard(image: BufferedImage): Boolean {
        return try {
            val transferable = ImageTransferable(image)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, this)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Copies a list of File objects to the system clipboard as DataFlavor.javaFileListFlavor.
     */
    fun copyFilesToClipboard(files: List<File>): Boolean {
        return try {
            val transferable = FileListTransferable(files)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, this)
            true
        } catch (_: Exception) {
            false
        }
    }

    private class ImageTransferable(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }

    private class FileListTransferable(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.javaFileListFlavor) throw UnsupportedFlavorException(flavor)
            return files
        }
    }
}
