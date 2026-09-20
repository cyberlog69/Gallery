package com.gallery.core.util

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.gallery.core.model.ExifData
import java.io.File
import java.io.InputStream

object ExifExtractor {

    fun extract(file: File): ExifData? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val metadata = ImageMetadataReader.readMetadata(file)
            parseMetadata(metadata)
        } catch (e: Throwable) {
            null
        }
    }

    fun extract(stream: InputStream): ExifData? {
        return try {
            val metadata = ImageMetadataReader.readMetadata(stream)
            parseMetadata(metadata)
        } catch (e: Throwable) {
            null
        }
    }

    private fun parseMetadata(metadata: com.drew.metadata.Metadata): ExifData {
        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)

        val make = ifd0?.getString(ExifIFD0Directory.TAG_MAKE)
        val model = ifd0?.getString(ExifIFD0Directory.TAG_MODEL)
        val orientation = ifd0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION) ?: 0

        val aperture = subIfd?.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)
            ?: subIfd?.getDescription(ExifSubIFDDirectory.TAG_APERTURE)
        val shutterSpeed = subIfd?.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
        val iso = subIfd?.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
        val focalLength = subIfd?.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)
        val flash = subIfd?.getDescription(ExifSubIFDDirectory.TAG_FLASH)
        val dateTimeOriginal = subIfd?.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
            ?: ifd0?.getString(ExifIFD0Directory.TAG_DATETIME)

        val width = subIfd?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
            ?: ifd0?.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH) ?: 0
        val height = subIfd?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
            ?: ifd0?.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT) ?: 0

        val lat = gps?.geoLocation?.latitude
        val lon = gps?.geoLocation?.longitude

        return ExifData(
            make = make,
            model = model,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            iso = iso,
            focalLength = focalLength,
            flash = flash,
            width = width,
            height = height,
            orientation = orientation,
            dateTimeOriginal = dateTimeOriginal,
            latitude = lat,
            longitude = lon
        )
    }
}
