package com.solanasignal.app.domain.export

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ABExportWriter(private val directory: File) {
    fun writeJsonl(name: String, rows: Iterable<Map<String, Any?>>): File {
        val file = File(directory, name)
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { out -> rows.forEach { row -> out.appendLine(toJson(row)) } }
        return file
    }

    fun writeCsv(name: String, columns: List<String>, rows: Iterable<Map<String, Any?>>): File {
        val file = File(directory, name)
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { out ->
            out.appendLine(columns.joinToString(","))
            rows.forEach { row -> out.appendLine(columns.joinToString(",") { csv(row[it]) }) }
        }
        return file
    }

    fun zip(name: String, files: List<File>): File {
        val zip = File(directory, name)
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            files.filter { it.exists() }.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    private fun toJson(row: Map<String, Any?>): String = row.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${escape(key)}\":${json(value)}"
    }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        else -> "\"${escape(value.toString())}\""
    }

    private fun csv(value: Any?): String = "\"${escape(value?.toString() ?: "")}" + "\""
    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
