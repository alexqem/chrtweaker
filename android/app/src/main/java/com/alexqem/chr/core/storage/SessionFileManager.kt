package com.alexqem.chr.core.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.alexqem.chr.core.model.SensorSample
import com.alexqem.chr.core.model.SessionMeta
import com.google.gson.GsonBuilder
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream

class SessionFileManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun getLogsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createSessionFiles(mode: String): Pair<File, File> {
        val logsDir = getLogsDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = "${stamp}_${mode}_ngx10"
        val csvFile = File(logsDir, "$baseName.csv")
        val metaFile = File(logsDir, "$baseName.meta.json")
        return Pair(csvFile, metaFile)
    }

    fun openCsvWriter(csvFile: File): BufferedWriter {
        val writer = BufferedWriter(FileWriter(csvFile, false))
        writer.write(SensorSample.CSV_HEADER)
        writer.newLine()
        writer.flush()
        return writer
    }

    fun writeCsvRow(writer: BufferedWriter, sample: SensorSample) {
        writer.write(sample.toCsvRow())
        writer.newLine()
        writer.flush()
    }

    fun writeMeta(metaFile: File, meta: SessionMeta) {
        metaFile.writeText(gson.toJson(meta))
    }

    fun listSessions(): List<File> {
        val dir = getLogsDir()
        return dir.listFiles { _, name -> name.endsWith(".csv") }?.sortedByDescending { it.name } ?: emptyList()
    }

    fun exportLogsTarGzToDownloads(): Result<String> = runCatching {
        val files = getLogsDir().listFiles { _, name ->
            name.endsWith(".csv") || name.endsWith(".meta.json")
        }?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty()) error("No log files found")

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val archiveName = "chr_logs_$stamp.tar.gz"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, archiveName)
                put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create Downloads entry")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    writeTarGz(files, out)
                } ?: error("Failed to open Downloads stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outFile = File(downloadsDir, archiveName)
            FileOutputStream(outFile).use { out -> writeTarGz(files, out) }
        }
        archiveName
    }

    private fun writeTarGz(files: List<File>, rawOut: OutputStream) {
        GZIPOutputStream(BufferedOutputStream(rawOut)).use { gzip ->
            for (file in files) {
                val bytes = file.readBytes()
                val header = ByteArray(512)
                val nameBytes = file.name.toByteArray(Charsets.UTF_8)
                nameBytes.copyInto(header, 0, 0, minOf(nameBytes.size, 100))
                "0000644\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 100)
                "0000000\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 108)
                "0000000\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 116)
                String.format(Locale.US, "%011o\u0000", bytes.size).toByteArray(Charsets.US_ASCII).copyInto(header, 124)
                String.format(Locale.US, "%011o\u0000", file.lastModified() / 1000L).toByteArray(Charsets.US_ASCII).copyInto(header, 136)
                "        ".toByteArray(Charsets.US_ASCII).copyInto(header, 148)
                header[156] = '0'.code.toByte()
                "ustar\u0000".toByteArray(Charsets.US_ASCII).copyInto(header, 257)
                "00".toByteArray(Charsets.US_ASCII).copyInto(header, 263)

                var checksum = 0
                for (b in header) checksum += (b.toInt() and 0xFF)
                String.format(Locale.US, "%06o\u0000 ", checksum).toByteArray(Charsets.US_ASCII).copyInto(header, 148)

                gzip.write(header)
                gzip.write(bytes)
                val rem = bytes.size % 512
                if (rem != 0) gzip.write(ByteArray(512 - rem))
            }
            gzip.write(ByteArray(1024))
        }
    }
}
