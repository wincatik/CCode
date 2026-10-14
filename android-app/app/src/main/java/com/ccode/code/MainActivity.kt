package com.ccode.code

import android.content.Context
import android.content.ContentValues
import android.content.ComponentName
import android.content.ClipData
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import android.util.Base64
import android.webkit.MimeTypeMap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

private val Black = Color.Black
private var White = Color(0xFFF5F5F7)
private var Panel = Color(0xFF202125)
private var Muted = Color(0xFF8B8C91)
private var Blue = Color(0xFF3478F6)
private val DefaultBackground = Color.Black

private enum class AppSection { CHATS, FILES, SETTINGS }
private enum class BrandTheme { CCODE, CLAUDE, GEMINI, CHATGPT }

private data class RgbColor(val r: Int, val g: Int, val b: Int)

private fun rgbFromHex(value: String, fallback: RgbColor = RgbColor(0, 0, 0)): RgbColor {
    val color = colorFromHex(value, Color(fallback.r / 255f, fallback.g / 255f, fallback.b / 255f))
    return RgbColor((color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())
}

private fun hexFromRgb(rgb: RgbColor): String = "%02X%02X%02X".format(rgb.r.coerceIn(0, 255), rgb.g.coerceIn(0, 255), rgb.b.coerceIn(0, 255))

private fun panelFor(background: Color): Color = Color(
    (background.red * 0.92f + 0.07f).coerceIn(0f, 1f),
    (background.green * 0.92f + 0.08f).coerceIn(0f, 1f),
    (background.blue * 0.92f + 0.10f).coerceIn(0f, 1f)
)

private fun isLightColor(color: Color): Boolean = color.red * .299f + color.green * .587f + color.blue * .114f > .52f

private fun colorFromHex(value: String, fallback: Color): Color = runCatching {
    val clean = value.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    Color(android.graphics.Color.parseColor("#$clean"))
}.getOrDefault(fallback)

private fun applyLauncherTheme(context: Context, theme: BrandTheme) {
    val packageManager = context.packageManager
    val aliases = mapOf(
        BrandTheme.CCODE to "com.ccode.code.CCodeLauncher",
        BrandTheme.CLAUDE to "com.ccode.code.ClaudeLauncher",
        BrandTheme.GEMINI to "com.ccode.code.GeminiLauncher",
        BrandTheme.CHATGPT to "com.ccode.code.ChatGptLauncher"
    )
    aliases.forEach { (item, className) ->
        packageManager.setComponentEnabledSetting(ComponentName(context, className), if (item == theme) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q3_k_m.gguf?download=true"
private const val LITE_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q3_k_m.gguf?download=true"
private const val FAST_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf?download=true"
private const val BALANCED_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true"
private const val JAVA_TOOLCHAIN_URL = "https://files.manuscdn.com/user_upload_by_module/session_file/310519664018242987/qpijGrfJgSnvjWjb.zip"
private const val CCODE_NOTIFICATION_CHANNEL = "ccode_status"

private data class StreakStats(val current: Int, val best: Int, val activeDays: Int)

private fun recordDailyActivity(settings: android.content.SharedPreferences): StreakStats {
    val today = java.time.LocalDate.now().toString()
    val previous = settings.getString("last_activity_day", "") ?: ""
    var current = settings.getInt("current_streak", 0)
    var best = settings.getInt("best_streak", 0)
    var activeDays = settings.getInt("active_days", 0)
    if (previous != today) {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        current = if (previous == yesterday) current + 1 else 1
        activeDays += 1
        best = maxOf(best, current)
        settings.edit().putString("last_activity_day", today).putInt("current_streak", current).putInt("best_streak", best).putInt("active_days", activeDays).apply()
    }
    return StreakStats(current, best, activeDays)
}

private data class GeneratedFile(val name: String, val content: String, val binaryBase64: String? = null, val mime: String? = null, val diskPath: String? = null)
private data class ActivityStep(val title: String, val detail: String, val done: Boolean, val active: Boolean, val code: String = "")
data class ProjectFile(val path: String, val content: String, val ready: Boolean = true, val binaryBase64: String? = null, val mime: String? = null, val diskPath: String? = null)
private data class GeneratedProject(val name: String, val files: List<ProjectFile>)
data class WorkspaceProject(val id: String, val name: String, val sourceName: String, val files: List<ProjectFile>, val createdAt: Long, val chatName: String = "")
private data class WebSource(val title: String, val url: String)
private data class WebContext(val text: String, val sources: List<WebSource>)

private fun safeWorkspacePath(path: String): String = path.replace("\\", "/").split("/").filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("/").ifBlank { "file.bin" }
private fun isTextWorkspaceFile(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in setOf("gd", "gds", "tscn", "godot", "py", "js", "ts", "kt", "java", "cpp", "h", "c", "cs", "txt", "json", "xml", "html", "htm", "css", "md", "ini", "cfg", "toml", "yaml", "yml", "gradle", "properties", "shader", "svg")

private fun uniqueWorkspaceName(projects: List<WorkspaceProject>, requested: String, ignoreId: String? = null): String {
    val base = requested.trim().ifBlank { "Проект" }
    if (projects.none { it.id != ignoreId && it.name.equals(base, true) }) return base
    var index = 1
    while (projects.any { it.id != ignoreId && it.name.equals("$base ($index)", true) }) index++
    return "$base ($index)"
}

private fun isAndroidJavaProject(project: WorkspaceProject): Boolean {
    val hasJava = project.files.any { it.path.substringAfterLast('.', "").equals("java", true) }
    val hasManifest = project.files.any { it.path.substringAfterLast('/').equals("AndroidManifest.xml", true) }
    return hasJava && hasManifest
}

private fun extractJavaToolchainComponent(apk: File, root: File): Long {
    val staging = File(root, "extracted.tmp").apply { deleteRecursively(); mkdirs() }
    var extracted = 0L
    ZipFile(apk).use { archive ->
        val bootstrap = archive.entries().asSequence().firstOrNull { it.name == "bootstrap.zip" || it.name == "toolchain.zip" || it.name.endsWith("/bootstrap.zip") || it.name.endsWith("/toolchain.zip") }
        if (bootstrap != null) {
            val nested = File(staging, "bootstrap.zip")
            archive.getInputStream(bootstrap).use { input -> nested.outputStream().use { input.copyTo(it) } }
            ZipFile(nested).use { bundle -> bundle.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && !entry.name.contains("..")) {
                    val target = File(staging, entry.name)
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) return@forEach
                    target.parentFile?.mkdirs()
                    bundle.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    extracted += target.length()
                }
            } }
            nested.delete()
        }
        }
    if (extracted <= 0L) error("В APK-компоненте не найден bootstrap toolchain")
    val finalDir = File(root, "extracted")
    finalDir.deleteRecursively()
    if (!staging.renameTo(finalDir)) error("Не удалось сохранить распакованный toolchain")
    File(root, "toolchain.ready").writeText("extractedBytes=$extracted\nsource=${apk.name}\n")
    return extracted
}

private fun extractArchiveToWorkspace(context: Context, uri: Uri, sourceName: String, chatName: String = ""): WorkspaceProject? = runCatching {
    val id = "workspace-${System.currentTimeMillis()}"
    val root = File(context.filesDir, "workspace/$id").apply { mkdirs() }
    val files = mutableListOf<ProjectFile>()
    var total = 0L
    ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть архив")).use { zip ->
        var entry: ZipEntry? = null; var count = 0
        while (count < 600 && zip.nextEntry.also { entry = it } != null) {
            val current = entry ?: break
            if (current.isDirectory) continue
            val relative = safeWorkspacePath(current.name)
            val target = File(root, relative)
            if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) continue
            target.parentFile?.mkdirs()
            val bytes = zip.readBytes().also { total += it.size }
            if (total > 120L * 1024 * 1024) break
            target.writeBytes(bytes)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(relative.substringAfterLast('.', "").lowercase()).orEmpty()
            val text = if (isTextWorkspaceFile(relative) && bytes.size <= 240_000) bytes.toString(Charsets.UTF_8) else ""
            files += ProjectFile(relative, text, binaryBase64 = if (text.isBlank() && bytes.size <= 8 * 1024 * 1024) Base64.encodeToString(bytes, Base64.NO_WRAP) else null, mime = mime, diskPath = target.absolutePath)
            count++
        }
    }
    if (files.isEmpty()) error("Архив пустой")
    WorkspaceProject(id, sourceName.substringBeforeLast('.').ifBlank { "Распакованный проект" }, sourceName, files, System.currentTimeMillis(), chatName)
}.getOrNull()

private fun materializeGeneratedProject(context: Context, project: GeneratedProject, chatName: String = ""): WorkspaceProject = run {
    val id = "workspace-ai-${System.currentTimeMillis()}"
    val root = File(context.filesDir, "workspace/$id").apply { mkdirs() }
    val files = project.files.map { source ->
        val relative = safeWorkspacePath(source.path)
        val target = File(root, relative)
        target.parentFile?.mkdirs()
        val bytes = when {
            source.diskPath != null -> File(source.diskPath).readBytes()
            source.binaryBase64 != null -> Base64.decode(source.binaryBase64, Base64.DEFAULT)
            else -> source.content.toByteArray()
        }
        target.writeBytes(bytes)
        source.copy(path = relative, diskPath = target.absolutePath)
    }
    WorkspaceProject(id, project.name, "${project.name}.zip", files, System.currentTimeMillis(), chatName)
}

private fun saveWorkspace(context: Context, projects: List<WorkspaceProject>) {
    runCatching {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(JSONObject().put("id", project.id).put("name", project.name).put("sourceName", project.sourceName).put("chatName", project.chatName).put("createdAt", project.createdAt).put("files", JSONArray().also { fs -> project.files.forEach { file -> fs.put(JSONObject().put("path", file.path).put("content", file.content).put("binaryBase64", file.binaryBase64).put("mime", file.mime).put("diskPath", file.diskPath)) } })
            )
        }
        File(context.filesDir, "workspace.json").writeText(array.toString())
    }
}

private fun workspaceExportJson(projects: List<WorkspaceProject>): JSONArray = JSONArray().also { array ->
    projects.forEach { project ->
        array.put(JSONObject().put("id", project.id).put("name", project.name).put("sourceName", project.sourceName).put("chatName", project.chatName).put("createdAt", project.createdAt).put("files", JSONArray().also { fs -> project.files.forEach { file ->
            val bytes = file.diskPath?.let { runCatching { File(it).readBytes() }.getOrNull() }
            fs.put(JSONObject().put("path", file.path).put("content", file.content).put("binaryBase64", file.binaryBase64 ?: bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).put("mime", file.mime).put("size", bytes?.size ?: file.content.toByteArray().size))
        } }))
    }
}

private fun importWorkspaceJson(context: Context, value: JSONArray?): List<WorkspaceProject> = value?.let { array -> List(array.length()) { i ->
    val item = array.getJSONObject(i); val id = "workspace-import-${System.currentTimeMillis()}-$i"; val root = File(context.filesDir, "workspace/$id").apply { mkdirs() }; val fs = item.optJSONArray("files") ?: JSONArray()
    val files = List(fs.length()) { j ->
        val f = fs.getJSONObject(j); val relative = safeWorkspacePath(f.optString("path")); val target = File(root, relative); target.parentFile?.mkdirs(); val encoded = f.optString("binaryBase64").takeIf { it.isNotBlank() }; val bytes = encoded?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() } ?: f.optString("content").toByteArray(); target.writeBytes(bytes); ProjectFile(relative, if (isTextWorkspaceFile(relative)) f.optString("content").ifBlank { bytes.take(240_000).toByteArray().toString(Charsets.UTF_8) } else "", binaryBase64 = if (!isTextWorkspaceFile(relative)) encoded else null, mime = f.optString("mime").takeIf { it.isNotBlank() }, diskPath = target.absolutePath)
    }
    WorkspaceProject(id, item.optString("name", "Импортированный проект"), item.optString("sourceName", "archive.zip"), files, item.optLong("createdAt", System.currentTimeMillis()), item.optString("chatName", ""))
} } ?: emptyList()

private fun loadWorkspace(context: Context): List<WorkspaceProject> = runCatching {
    val file = File(context.filesDir, "workspace.json"); if (!file.exists()) return emptyList()
    val array = JSONArray(file.readText())
    List(array.length()) { i ->
        val item = array.getJSONObject(i); val fs = item.optJSONArray("files") ?: JSONArray()
        WorkspaceProject(item.optString("id"), item.optString("name", "Проект"), item.optString("sourceName", "archive.zip"), List(fs.length()) { j ->
            val f = fs.getJSONObject(j); val disk = f.optString("diskPath").takeIf { it.isNotBlank() }; val diskFile = disk?.let(::File)
            val content = f.optString("content").ifBlank { if (diskFile != null && diskFile.exists() && isTextWorkspaceFile(f.optString("path"))) runCatching { diskFile.readBytes().take(240_000).toByteArray().toString(Charsets.UTF_8) }.getOrDefault("") else "" }
            ProjectFile(f.optString("path"), content, binaryBase64 = f.optString("binaryBase64").takeIf { it.isNotBlank() }, mime = f.optString("mime").takeIf { it.isNotBlank() }, diskPath = disk)
        }, item.optLong("createdAt", System.currentTimeMillis()), item.optString("chatName", ""))
    }
}.getOrDefault(emptyList())

private fun workspaceMemory(projects: List<WorkspaceProject>, query: String): String {
    val lower = query.lowercase()
    val hits = projects.flatMap { project -> project.files.filter { lower.contains(it.path.substringAfterLast('/').lowercase()) || lower.contains(project.name.lowercase()) }.map { project to it } }.take(12)
    if (hits.isEmpty()) return ""
    return hits.joinToString("\n") { (project, file) -> "Проект ${project.name}, файл ${file.path}: ${if (file.content.isNotBlank()) file.content.take(1200) else "бинарный/ресурсный файл, размер и структура сохранены локально."}" }
}

private fun projectContentMemory(project: GeneratedProject?): String {
    if (project == null) return ""
    return project.files.take(12).joinToString("\n\n") { file -> "ФАЙЛ ${file.path}\n${file.content.take(3000)}" }.take(12000)
}

private fun workspaceProjectForQuery(projects: List<WorkspaceProject>, query: String): WorkspaceProject? {
    val lower = query.lowercase()
    return projects.asSequence().map { project ->
        val projectHit = lower.contains(project.name.lowercase()) || lower.contains(project.sourceName.lowercase())
        val fileHit = project.files.any { file -> lower.contains(file.path.lowercase()) || lower.contains(file.path.substringAfterLast('/').lowercase()) }
        Triple(project, projectHit, fileHit)
    }.filter { it.second || it.third }.sortedByDescending { if (it.second) 2 else 1 }.map { it.first }.firstOrNull()
}

private fun isProjectRecallRequest(prompt: String): Boolean = Regex("(?iu)(помнишь|вспомни|напомни|покажи.*проект|открой.*проект|продолжи.*проект|continue.*project|remember)").containsMatchIn(prompt)

private fun workspaceAsGeneratedProject(project: WorkspaceProject): GeneratedProject = GeneratedProject(project.name, project.files)

private fun isFixRequest(prompt: String): Boolean = Regex("(?iu)(исправь|почини|фикс|баг|ошибк|не запуска|не работает|crash|error|fix|debug|broken|doesn't work)").containsMatchIn(prompt)

private fun isProjectModificationRequest(prompt: String): Boolean = isFixRequest(prompt) || Regex("(?iu)(добавь|добавить|измени|изменить|улучши|улучшить|реализуй|реализовать|замени|заменить|обнови|обновить|переделай|переделать|add|change|modify|improve|implement|update)").containsMatchIn(prompt)

private fun extractFixProject(text: String, prior: GeneratedProject): GeneratedProject? {
    val blocks = Regex("(?s)```(?:[\\w+#.-]+)?\\s*(.*?)```").findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
    if (blocks.isEmpty()) return null
    val candidates = prior.files.filter { it.content.isNotBlank() }
    val files = blocks.mapIndexed { index, code ->
        val target = candidates.firstOrNull { old ->
            (code.contains("extends ") && old.path.endsWith(".gd", true)) ||
            (code.contains("<html", true) && old.path.endsWith(".html", true)) ||
            (code.contains("document.") && old.path.endsWith(".js", true)) ||
            (code.contains("package ") && old.path.endsWith(".java", true)) ||
            (code.contains("import android.") && old.path.endsWith(".java", true))
        } ?: candidates.getOrNull(index) ?: return@mapIndexed null
        ProjectFile(target.path, code)
    }.filterNotNull()
    return files.takeIf { it.isNotEmpty() }?.let { GeneratedProject(prior.name, it) }
}

private fun inspectAttachment(context: Context, uri: Uri): String = runCatching {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "прикреплённый файл"
    } ?: "прикреплённый файл"
    val mime = context.contentResolver.getType(uri).orEmpty()
    val isApk = name.endsWith(".apk", true) || mime.contains("android.package", true)
    val archive = name.endsWith(".zip", true) || mime.contains("zip", true)
    if (isApk) {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        "Файл: $name\nТип: Android APK\nРазмер: ${if (size >= 0) "%.2f МБ".format(size / 1_048_576.0) else "неизвестен"}\nAPK выбран безопасно. Для анализа содержимого можно попросить Code 1.0 изучить этот APK отдельно."
    } else if (archive) {
        val entries = ZipInputStream(context.contentResolver.openInputStream(uri)).use { zip ->
            buildString { var entry: ZipEntry? = null; var count = 0; while (count < 120 && zip.nextEntry.also { entry = it } != null) { append(entry!!.name).append(if (entry!!.isDirectory) "/" else "").append("\n"); count++ } }
        }
        "Файл: $name\nТип: архив/APK\nСодержимое (первые элементы):\n$entries"
    } else if (mime.startsWith("image/")) {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        "Файл: $name\nТип: изображение ($mime)\nРазмер: ${if (size >= 0) "%.2f МБ".format(size / 1_048_576.0) else "неизвестен"}\nКартинку нельзя просмотреть локальной моделью, но её можно использовать как иконку, спрайт или другой ресурс проекта."
    } else if (mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in setOf("gd", "gdscript", "py", "po", "js", "ts", "kt", "java", "cpp", "h", "c", "cs", "txt", "json", "xml", "html", "css", "md", "ini", "cfg", "toml", "yaml", "yml")) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(120_000).toByteArray().toString(Charsets.UTF_8) } ?: ""
        "Файл: $name\nСодержимое:\n${text.take(12000)}"
    } else {
        "Файл: $name\nТип: бинарный или неизвестный файл ($mime). Передано только имя и тип; содержимое не читается, чтобы избежать вылета."
    }
}.getOrElse { "Не удалось прочитать вложение: ${it.message ?: "неизвестная ошибка"}" }

private fun plannedFiles(prompt: String): List<String> {
    val godot = prompt.contains("godot", true) || prompt.contains("гдскрипт", true)
    val web = prompt.contains("html", true) || prompt.contains("хтмл", true) || prompt.contains("веб", true) || prompt.contains("сайт", true)
    val apkGame = isApkGameRequest(prompt)
    val creation = prompt.contains("создай", true) || prompt.contains("напиши", true) || prompt.contains("сделай", true) || prompt.contains("файл", true) || prompt.contains("скрипт", true) || prompt.contains("проект", true) || web
    val multiple = prompt.contains("нескольк", true) || prompt.contains("проект", true) || (prompt.contains("игр", true) && creation)
    val explicit = Regex("(?i)(?:[\\wА-Яа-я ._\\-/]+\\.[A-Za-zА-Яа-я0-9_+-]{1,12})").findAll(prompt).map { it.value.trim().trim(',', '.', ':', ';', '"', '\'') }.filter { it.contains('/') || it.substringAfterLast(' ').contains('.') }.toList()
    if (isProjectModificationRequest(prompt)) return explicit.distinct()
    if (!creation && !godot && !apkGame && explicit.isEmpty()) return emptyList()
    val base = when {
        apkGame -> listOf("AndroidManifest.xml", "src/main/java/com/ccode/game/MainActivity.java", "src/main/res/values/strings.xml", "README.md")
        godot -> listOf("project.godot", "scenes/main.tscn", "scripts/main.gd", "scripts/player.gd")
        web -> listOf("index.html", "styles.css", "script.js", "README.md")
        prompt.contains("python", true) || prompt.contains("пайтон", true) -> listOf("main.py")
        prompt.contains("javascript", true) || prompt.contains("js", true) -> listOf("main.js")
        else -> if (multiple) listOf("README.md") else emptyList()
    }
    return (explicit + base).distinct()
}

private fun isApkGameRequest(prompt: String): Boolean {
    val text = prompt.lowercase().replace('ё', 'е')
    val apkOrAndroid = text.contains("apk") || text.contains("андроид") || text.contains("android")
    val game = text.contains("игр") || text.contains("game")
    val action = text.contains("сдел") || text.contains("созда") || text.contains("напиш") || text.contains("build") || text.contains("make") || text.contains("create")
    return apkOrAndroid && game && action
}

private fun extractGeneratedFile(text: String, prompt: String): GeneratedFile? {
    val block = Regex("(?s)(?:FILE\\s*:\\s*|Файл\\s*:\\s*)?([\\wА-Яа-я./\\\\_\\- +()]+\\.[A-Za-zА-Яа-я0-9_+-]{1,16})?\\s*```(?:[\\w+#.-]+)?\\s*(.*?)```").find(text)
    val fallback = Regex("(?s)```(?:python|py|gd|gdscript|javascript|js|kotlin|java|cpp|c\\+\\+)?\\s*(.*?)```").find(text)
    val match = block ?: fallback ?: return null
    val content = match.groupValues.lastOrNull()?.trim().orEmpty()
    if (content.isBlank()) return null
    val explicit = if (block != null) match.groupValues.getOrNull(1).orEmpty().trim() else ""
    val extension = when {
        prompt.contains("godot", true) || prompt.contains("гдскрипт", true) -> "gd"
        prompt.contains("python", true) || prompt.contains("пайтон", true) -> "py"
        else -> "txt"
    }
    return GeneratedFile(explicit.ifBlank { "ccode_file.$extension" }, content)
}

private fun extractGeneratedProject(text: String, prompt: String): GeneratedProject? {
    val pattern = Regex("(?s)(?:FILE\\s*:\\s*|Файл\\s*:\\s*)([^\\n`]+?)\\s*\\n?```(?:[\\w+#.-]+)?\\s*(.*?)```")
    var files = pattern.findAll(text).map { match -> ProjectFile(match.groupValues[1].trim(), match.groupValues[2].trim()) }.filter { it.path.isNotBlank() && it.content.isNotBlank() }.toList()
    if (files.isEmpty()) {
        val planned = plannedFiles(prompt)
        val codeBlocks = Regex("(?s)```(?:[\\w+#.-]+)?\\s*(.*?)```").findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
        if (planned.isNotEmpty() && codeBlocks.isNotEmpty()) {
            files = codeBlocks.take(planned.size).mapIndexed { index, code -> ProjectFile(planned[index], code) }
        }
    }
    if (files.isEmpty()) {
        val namedBlocks = Regex("(?s)([\\wА-Яа-я./\\\\_\\- +()]+\\.[A-Za-zА-Яа-я0-9_+-]{1,16})\\s*:?\\s*```(?:[\\w+#.-]+)?\\s*(.*?)```").findAll(text).map { ProjectFile(it.groupValues[1].trim(), it.groupValues[2].trim()) }.filter { it.content.isNotBlank() }.toList()
        files = namedBlocks
    }
    if (files.isEmpty()) return null
    val name = when { prompt.contains("godot", true) || prompt.contains("гдскрипт", true) -> "GodotProject"; prompt.contains("html", true) || prompt.contains("хтмл", true) || prompt.contains("веб", true) -> "HTMLProject"; else -> "CCodeProject" }
    return GeneratedProject(name, files)
}

private fun isGameRequest(prompt: String): Boolean = Regex("(?iu)\\b(игр(а|у|ы|е)?|game|gameplay)\\b").containsMatchIn(prompt) || prompt.contains("прыгающ", true) || prompt.contains("ball", true)

private fun isTrivialGeneratedCode(content: String): Boolean = content.trim().let { it.length < 220 || Regex("(?i)hello\\s*world|print\\s*\\(?['\"]hello|ничего не делает").containsMatchIn(it) }

private fun ensureGeneratedProject(prompt: String, parsed: GeneratedProject?, defaultGameEngine: String = "HTML"): GeneratedProject? {
    val isGodot = prompt.contains("godot", true) || prompt.contains("гдскрипт", true)
    val isHtml = prompt.contains("html", true) || prompt.contains("хтмл", true) || prompt.contains("веб", true) || prompt.contains("сайт", true) || (isGameRequest(prompt) && defaultGameEngine.equals("HTML", true))
    val inferredGodot = isGameRequest(prompt) && defaultGameEngine.equals("Godot", true)
    val isApkGame = isApkGameRequest(prompt)
    if (!isGodot && !inferredGodot && !isHtml && !isApkGame) return parsed
    val existing = parsed?.files.orEmpty().associateBy { it.path }.toMutableMap()
    if (isApkGame) {
        existing.putIfAbsent("AndroidManifest.xml", ProjectFile("AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:theme=\"@style/AppTheme\" android:label=\"@string/app_name\"><activity android:name=\".MainActivity\" android:exported=\"true\"><intent-filter><action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/></intent-filter></activity></application></manifest>"))
        existing.putIfAbsent("src/main/java/com/ccode/game/MainActivity.java", ProjectFile("src/main/java/com/ccode/game/MainActivity.java", "package com.ccode.game;\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.graphics.Color;\nimport android.view.View;\npublic class MainActivity extends Activity {\n  @Override public void onCreate(Bundle state) { super.onCreate(state); setContentView(new GameView(this)); }\n  static class GameView extends View { GameView(android.content.Context c) { super(c); setBackgroundColor(Color.BLACK); } }\n}\n"))
        existing.putIfAbsent("src/main/res/values/strings.xml", ProjectFile("src/main/res/values/strings.xml", "<resources><string name=\"app_name\">Android Game</string></resources>"))
        existing.putIfAbsent("src/main/res/values/styles.xml", ProjectFile("src/main/res/values/styles.xml", "<resources><style name=\"AppTheme\" parent=\"android:style/Theme.Material.Light.NoActionBar\"/></resources>"))
        return GeneratedProject("AndroidGameProject", existing.values.toList())
    }
    if (isGodot || inferredGodot) {
        existing.putIfAbsent("project.godot", ProjectFile("project.godot", """[application]
config/name="CCode Godot Project"
run/main_scene="res://scenes/main.tscn"
[display]
window/size/viewport_width=960
window/size/viewport_height=540
[rendering]
renderer/rendering_method="gl_compatibility"
"""))
        existing.putIfAbsent("scenes/main.tscn", ProjectFile("scenes/main.tscn", """[gd_scene load_steps=2 format=3]

[ext_resource path="res://scripts/main.gd" type="Script" id="1_main"]

[node name="Main" type="Node2D"]
script = ExtResource("1_main")
"""))
        if (existing["scripts/main.gd"] == null || isTrivialGeneratedCode(existing["scripts/main.gd"]!!.content)) existing["scripts/main.gd"] = ProjectFile("scripts/main.gd", """extends Node3D

var elapsed := 0.0
func _ready():
    RenderingServer.set_default_clear_color(Color("10131c"))

func _process(delta):
    elapsed += delta
    var lamp = get_node_or_null("Flashlight")
    if lamp: lamp.rotation.y = sin(elapsed * 0.35) * 0.12
""")
        existing.putIfAbsent("scripts/player.gd", ProjectFile("scripts/player.gd", """extends CharacterBody3D

@export var speed := 3.0
func _physics_process(_delta):
    var input_dir = Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
    velocity.x = input_dir.x * speed
    velocity.z = input_dir.y * speed
    move_and_slide()
"""))
        if (prompt.contains("3d", true) || prompt.contains("хоррор", true) || prompt.contains("horror", true)) existing["scenes/main.tscn"] = ProjectFile("scenes/main.tscn", """[gd_scene load_steps=9 format=3]

[ext_resource path="res://scripts/main.gd" type="Script" id="1"]

[sub_resource type="StandardMaterial3D" id="FloorMat"]
albedo_color = Color(0.06, 0.08, 0.12, 1)
roughness = 0.9

[sub_resource type="PlaneMesh" id="FloorMesh"]
size = Vector2(40, 40)
material = SubResource("FloorMat")

[sub_resource type="StandardMaterial3D" id="PlayerMat"]
albedo_color = Color(0.15, 0.52, 0.95, 1)
metallic = 0.1
roughness = 0.4

[sub_resource type="SphereMesh" id="PlayerMesh"]
radius = 0.6
height = 1.2
material = SubResource("PlayerMat")

[sub_resource type="StandardMaterial3D" id="ObstacleMat"]
albedo_color = Color(0.65, 0.12, 0.16, 1)
roughness = 0.7

[sub_resource type="BoxMesh" id="ObstacleMesh"]
size = Vector3(2.2, 2.2, 2.2)
material = SubResource("ObstacleMat")

[sub_resource type="Environment" id="WorldEnv"]
background_mode = 1
background_color = Color(0.01, 0.015, 0.04, 1)
ambient_light_source = 3
ambient_light_color = Color(0.25, 0.32, 0.55, 1)
ambient_light_energy = 0.7

[node name="GameWorld3D" type="Node3D"]
script = ExtResource("1")

[node name="WorldEnvironment" type="WorldEnvironment" parent="."]
environment = SubResource("WorldEnv")

[node name="Floor" type="MeshInstance3D" parent="."]
mesh = SubResource("FloorMesh")

[node name="Player" type="CharacterBody3D" parent="."]
position = Vector3(0, 0.8, 7)

[node name="Body" type="MeshInstance3D" parent="Player"]
mesh = SubResource("PlayerMesh")

[node name="Camera3D" type="Camera3D" parent="Player"]
position = Vector3(0, 4.2, 8.5)
rotation_degrees = Vector3(-14, 0, 0)
current = true

[node name="Flashlight" type="SpotLight3D" parent="Player"]
light_energy = 5.0
spot_range = 18.0
spot_angle = 42.0
shadow_enabled = true

[node name="ObstacleA" type="MeshInstance3D" parent="."]
position = Vector3(-3, 1.1, 1)
mesh = SubResource("ObstacleMesh")

[node name="ObstacleB" type="MeshInstance3D" parent="."]
position = Vector3(3, 1.1, -3)
scale = Vector3(0.7, 1.6, 0.7)
mesh = SubResource("ObstacleMesh")
""")
        return GeneratedProject("GodotProject", existing.values.toList())
    }
    if (isHtml && (prompt.contains("прыгающ", true) || prompt.contains("шарик", true) || prompt.contains("bouncing", true) || prompt.contains("ball", true))) {
        existing["index.html"] = ProjectFile("index.html", """<!doctype html><html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Прыгающий шарик</title><link rel="stylesheet" href="styles.css"></head><body><canvas id="game"></canvas><div class="hint">Прыгающий шарик · нажми или коснись экрана</div><script src="script.js"></script></body></html>""")
        existing["styles.css"] = ProjectFile("styles.css", """html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#08111f}#game{display:block;width:100%;height:100%;background:radial-gradient(circle at 50% 40%,#16365a,#050914)}.hint{position:fixed;top:16px;left:0;right:0;text-align:center;color:#dbeafe;font:16px sans-serif;text-shadow:0 2px 6px #000}""")
        existing["script.js"] = ProjectFile("script.js", """const c=document.querySelector('#game'),x=c.getContext('2d');let dpr=innerWidth>0?devicePixelRatio:1,w=0,h=0,b={x:0,y:0,r:28,vx:180,vy:0};function resize(){w=innerWidth;h=innerHeight;c.width=w*dpr;c.height=h*dpr;x.setTransform(dpr,0,0,dpr,0,0);b.x=w/2;b.y=h/2}addEventListener('resize',resize);addEventListener('pointerdown',()=>b.vy=-520);resize();let last=performance.now();function loop(t){let dt=Math.min((t-last)/1000,.03);last=t;b.vy+=1100*dt;b.x+=b.vx*dt;b.y+=b.vy*dt;if(b.x<b.r||b.x>w-b.r)b.vx*=-1;if(b.y>b.r&&b.y>h-b.r){b.y=h-b.r;b.vy*=-.82}x.clearRect(0,0,w,h);x.beginPath();x.arc(b.x,b.y,b.r,0,Math.PI*2);x.fillStyle='#38bdf8';x.shadowColor='#38bdf8';x.shadowBlur=22;x.fill();x.shadowBlur=0;requestAnimationFrame(loop)}loop(last);""")
    }
    existing.putIfAbsent("index.html", ProjectFile("index.html", """<!doctype html>
<html lang="ru">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>CCode HTML Project</title><link rel="stylesheet" href="styles.css"></head>
<body><main><h1>CCode HTML Project</h1><p>Проект создан локально.</p><button id="action">Нажать</button></main><script src="script.js"></script></body>
</html>
"""))
    existing.putIfAbsent("styles.css", ProjectFile("styles.css", "body { font-family: sans-serif; margin: 0; padding: 2rem; background: #111827; color: white; } button { padding: .7rem 1rem; border-radius: .6rem; }"))
    existing.putIfAbsent("script.js", ProjectFile("script.js", "document.getElementById('action')?.addEventListener('click', () => alert('CCode project works'));"))
    return GeneratedProject("HTMLProject", existing.values.toList())
}

private data class CCodeModel(val id: String, val title: String, val details: String, val fileName: String, val url: String, val requirements: String)

private val MODEL_CATALOG = listOf(
    CCodeModel("code1", "Code 1.0", "Qwen2.5 Coder · 3B · около 1,7 ГБ", "model.gguf", MODEL_URL, "Минимум: 6 ГБ ОЗУ · 2,2 ГБ свободной памяти"),
    CCodeModel("balanced", "Code 1.0 Balanced", "Qwen2.5 Coder · 1.5B · Q4_K_M · около 1,12 ГБ", "model-balanced.gguf", BALANCED_MODEL_URL, "Минимум: 4 ГБ ОЗУ · 1,5 ГБ свободной памяти"),
    CCodeModel("lite", "Code 1.0 Lite", "Qwen2.5 Coder · 1.5B · Q3_K_M · около 824 МБ", "model-lite.gguf", LITE_MODEL_URL, "Минимум: 3 ГБ ОЗУ · 1,1 ГБ свободной памяти"),
    CCodeModel("fast", "Code 1.0 Fast", "Qwen2.5 Coder · 0.5B · Q4_K_M · около 491 МБ", "model-fast.gguf", FAST_MODEL_URL, "Минимум: 2 ГБ ОЗУ · 700 МБ свободной памяти")
)

private fun webSearchQuery(prompt: String): String? {
    val lower = prompt.lowercase()
    val markers = listOf("найди в интернете", "поищи в интернете", "узнай в интернете", "узнать в интернете", "посмотри в интернете", "что такое в интернете", "посмотри гайд", "найди гайд", "посмотри документацию", "найди документацию", "через интернет", "проверь в интернете", "если не знаешь", "если не понимаешь", "возьми ассет", "возьми ассет с интернета", "найди ассет", "найди модель", "3d-модель", "3d модель", "скачай ассет", "скачай модель", "текстуру с интернета", "спрайт с интернета", "скрипт с интернета")
    val hasUrl = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE).containsMatchIn(prompt)
    val naturalInternetQuestion = lower.contains("в интернете") && (lower.contains("что такое") || lower.contains("кто такой") || lower.contains("кто такая") || lower.contains("узнай") || lower.contains("проверь") || lower.contains("найди") || lower.contains("посмотри"))
    if (!hasUrl && markers.none { lower.contains(it) } && !naturalInternetQuestion) return null
    val cleaned = prompt.replace(Regex("(?i)найди в интернете|поищи в интернете|узнай в интернете|узнать в интернете|посмотри в интернете|что такое в интернете|посмотри гайд|найди гайд|посмотри документацию|найди документацию|через интернет|проверь в интернете"), "").trim(' ', '.', ',', ':', '?', '!')
    return cleaned.ifBlank { prompt }
}

private fun isRemoteAssetRequest(prompt: String): Boolean = Regex("(?iu)(ассет|asset|3d[- ]?модел|модель.*интернет|текстур.*интернет|спрайт.*интернет|скач.*(модел|ассет|текстур|спрайт)|возьми.*(модел|ассет|текстур|спрайт))").containsMatchIn(prompt)

private fun remoteAssetUrls(prompt: String): List<String> = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE).findAll(prompt).map { it.value.trimEnd('.', ',', '!', '?') }.filter {
    it.substringBefore('?').substringAfterLast('/').contains('.')
}.toList().distinct().take(4)

private fun downloadRemoteAssets(context: Context, prompt: String): List<ProjectFile> = runCatching {
    remoteAssetUrls(prompt).mapNotNull { rawUrl ->
        val url = URL(rawUrl)
        val name = safeWorkspacePath(url.path.substringAfterLast('/').ifBlank { "remote_asset.bin" })
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("glb", "gltf", "obj", "fbx", "dae", "png", "jpg", "jpeg", "webp", "svg", "wav", "ogg", "mp3", "zip")) return@mapNotNull null
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 30_000; instanceFollowRedirects = true; requestMethod = "GET"; setRequestProperty("User-Agent", "CCode/0.2.6") }
        try {
            if (connection.responseCode !in 200..299 || connection.contentLengthLong > 32L * 1024 * 1024) return@mapNotNull null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > 32 * 1024 * 1024) return@mapNotNull null
            val mime = connection.contentType?.substringBefore(';').orEmpty().ifBlank { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty() }
            ProjectFile("assets/$name", "", binaryBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP), mime = mime)
        } finally { connection.disconnect() }
    }
}.getOrDefault(emptyList())

private fun fetchWebContext(query: String, sourceLimit: Int): WebContext? {
    return runCatching {
        val safeLimit = sourceLimit.coerceIn(1, 3)
        val directUrl = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE).find(query)?.value?.trimEnd('.', ',', '!', '?')
        if (directUrl != null) {
            val page = fetchPageText(directUrl) ?: return null
            return WebContext("Страница $directUrl:\n${page.second.take(2500)}".take(3000), listOf(WebSource(page.first.take(120), directUrl)))
        }
        val url = URL("https://lite.duckduckgo.com/lite/?q=${URLEncoder.encode(query, "UTF-8")}")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000; requestMethod = "GET"; instanceFollowRedirects = true; setRequestProperty("User-Agent", "CCode/0.2.2") }
        val html = try {
            if (connection.responseCode !in 200..399) return null
            val contentType = connection.contentType?.lowercase().orEmpty()
            if (contentType.isNotBlank() && !contentType.contains("text/html") && !contentType.contains("application/xhtml")) return null
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                var total = 0
                while (total < 96_000) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 96_000 - total))
                    if (read <= 0) break
                    output.write(buffer, 0, read); total += read
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
        val resultAnchors = Regex("<a\\b[^>]*class=['\"][^'\"]*result-link[^'\"]*['\"][^>]*>.*?</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).toList().take(safeLimit)
        if (resultAnchors.isEmpty()) return null
        val snippets = Regex("class=['\"]result-snippet['\"][^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).map { stripHtml(it.groupValues[1]) }.toList()
        val sources = resultAnchors.mapNotNull { anchor ->
            val raw = Regex("href=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(anchor.value)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return@mapNotNull null
            val decoded = runCatching { java.net.URLDecoder.decode(raw.substringAfter("uddg=", raw).substringBefore("&rut="), "UTF-8") }.getOrDefault(raw)
            val title = Regex(">\\s*(.*?)\\s*</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(anchor.value)?.groupValues?.get(1).orEmpty()
            WebSource(stripHtml(title).take(160), decoded)
        }
        if (sources.isEmpty()) return null
        val firstPage = fetchPageText(sources.first().url)?.second.orEmpty()
        val text = sources.mapIndexed { index, source ->
            val pageText = if (index == 0) firstPage else ""
            "${index + 1}. ${source.title}\n${pageText.ifBlank { snippets.getOrNull(index).orEmpty() }}\nИсточник: ${source.url}"
        }.joinToString("\n\n").take(3000)
        WebContext(text, sources)
    }.getOrNull()
}

private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace(Regex("\\s+"), " ").trim()

private fun fetchPageText(pageUrl: String): Pair<String, String>? = runCatching {
    val connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 20_000; requestMethod = "GET"; instanceFollowRedirects = true; setRequestProperty("User-Agent", "Mozilla/5.0 CCode/0.2.2") }
    val html = try {
        if (connection.responseCode !in 200..399) return null
        val contentType = connection.contentType?.lowercase().orEmpty()
        if (contentType.isNotBlank() && !contentType.contains("text/html") && !contentType.contains("application/xhtml")) return null
        connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (total < 180_000) {
                val read = input.read(buffer, 0, minOf(buffer.size, 180_000 - total))
                if (read <= 0) break
                output.write(buffer, 0, read); total += read
            }
            output.toString(Charsets.UTF_8.name())
        }
    } finally { connection.disconnect() }
    val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::stripHtml).orEmpty().ifBlank { pageUrl }
    val text = stripHtml(html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), "")).take(5000)
    title to text
}.getOrNull()

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CCODE_NOTIFICATION_CHANNEL, "CCode", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Статус генерации и загрузки моделей" })
    }
}

private fun sendCCodeNotification(context: Context, title: String, text: String, id: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = Notification.Builder(context, CCODE_NOTIFICATION_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(id, notification)
}

private data class ChatMessage(val text: String, val fromUser: Boolean, val activity: List<ActivityStep> = emptyList(), val project: GeneratedProject? = null, val sources: List<WebSource> = emptyList())

private fun conversationMemory(messages: List<ChatMessage>): String {
    return messages.filter { it.text.isNotBlank() }.takeLast(6).joinToString("\n") { message ->
        val role = if (message.fromUser) "Пользователь" else "Code 1.0"
        "$role: ${message.text.replace(Regex("```(?s:.*?)```"), "[код скрыт; содержимое проекта передано отдельно]").take(900)}"
    }.take(4200)
}
private data class StoredChat(val id: String, val title: String, val messages: List<ChatMessage>, val updatedAt: Long, val pinned: Boolean = false)

private fun chatTitle(messages: List<ChatMessage>): String = messages.firstOrNull { it.fromUser && it.text.isNotBlank() }?.text?.replace(Regex("\\s+"), " ")?.take(42) ?: "Новый чат"

private fun chatMarkdown(messages: List<ChatMessage>): String = buildString {
    appendLine("# CCode — чат")
    appendLine()
    messages.filter { it.text.isNotBlank() }.forEach { message ->
        appendLine(if (message.fromUser) "## Пользователь" else "## CCode")
        appendLine()
        appendLine(message.text)
        appendLine()
    }
}

private fun compactFileAnswer(text: String, file: GeneratedFile?): String {
    if (file == null) return text
    val explanation = text.replace(Regex("(?s)```.*?```"), "").replace(Regex("(?im)^\\s*(?:FILE|Файл)\\s*:.+$"), "").replace(Regex("\\s+"), " ").trim()
    return if (explanation.isBlank()) "Файл ${file.name} подготовлен. Открой активность сообщения, чтобы посмотреть этапы работы." else explanation.take(500)
}

private fun parseStoredChat(item: JSONObject): StoredChat {
    val messages = item.optJSONArray("messages") ?: JSONArray()
    return StoredChat(item.optString("id", "chat-${System.currentTimeMillis()}"), item.optString("title", "Новый чат"), List(messages.length()) { i ->
        val message = messages.getJSONObject(i)
        ChatMessage(message.optString("text"), message.optBoolean("fromUser"), project = message.optJSONArray("project")?.let { files ->
            GeneratedProject(message.optString("projectName", "CCodeProject"), List(files.length()) { j ->
                val file = files.getJSONObject(j)
                ProjectFile(file.optString("path"), file.optString("content"), binaryBase64 = file.optString("binaryBase64").takeIf { it.isNotBlank() }, mime = file.optString("mime").takeIf { it.isNotBlank() })
            })
        }, sources = message.optJSONArray("sources")?.let { sources -> List(sources.length()) { j -> val source = sources.getJSONObject(j); WebSource(source.optString("title"), source.optString("url")) } }.orEmpty())
    }, item.optLong("updatedAt", System.currentTimeMillis()), item.optBoolean("pinned", false))
}

private fun parseLegacyChat(root: JSONObject, fallbackId: String): StoredChat {
    val item = JSONObject().put("id", root.optString("chatId", fallbackId)).put("title", root.optString("title", "Импортированный чат")).put("updatedAt", System.currentTimeMillis()).put("messages", root.optJSONArray("messages") ?: JSONArray())
    return parseStoredChat(item)
}

private fun loadChats(context: Context): List<StoredChat> = runCatching {
    val file = File(context.filesDir, "chats.json")
    if (!file.exists()) return emptyList()
    val array = JSONArray(file.readText())
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val messages = item.optJSONArray("messages") ?: JSONArray()
        StoredChat(item.getString("id"), item.optString("title", "Новый чат"), List(messages.length()) { i ->
            val message = messages.getJSONObject(i)
            ChatMessage(message.optString("text"), message.optBoolean("fromUser"), project = message.optJSONArray("project")?.let { files ->
                GeneratedProject(message.optString("projectName", "CCodeProject"), List(files.length()) { j ->
                    val file = files.getJSONObject(j)
                    ProjectFile(file.optString("path"), file.optString("content"), binaryBase64 = file.optString("binaryBase64").takeIf { it.isNotBlank() }, mime = file.optString("mime").takeIf { it.isNotBlank() })
                })
            }, sources = message.optJSONArray("sources")?.let { sources ->
                List(sources.length()) { j ->
                    val source = sources.getJSONObject(j)
                    WebSource(source.optString("title"), source.optString("url"))
                }
            }.orEmpty())
        }, item.optLong("updatedAt", 0L), item.optBoolean("pinned", false))
    }.sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
}.getOrDefault(emptyList())

private fun saveChats(context: Context, chats: List<StoredChat>) {
    runCatching {
        val array = JSONArray()
        chats.take(50).forEach { chat ->
            array.put(JSONObject().put("id", chat.id).put("title", chat.title).put("updatedAt", chat.updatedAt).put("pinned", chat.pinned).put("messages", JSONArray().also { messages -> chat.messages.forEach { message ->
                messages.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser).put("projectName", message.project?.name).put("project", message.project?.files?.let { files -> JSONArray().also { json -> files.forEach { file -> json.put(JSONObject().put("path", file.path).put("content", file.content).put("binaryBase64", file.binaryBase64).put("mime", file.mime)) } }}).put("sources", JSONArray().also { json -> message.sources.forEach { source -> json.put(JSONObject().put("title", source.title).put("url", source.url)) } }))
            } }))
        }
        File(context.filesDir, "chats.json").writeText(array.toString())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        val savedTheme = runCatching { BrandTheme.valueOf(getSharedPreferences("ccode-settings", MODE_PRIVATE).getString("brand_theme", BrandTheme.CCODE.name) ?: BrandTheme.CCODE.name) }.getOrDefault(BrandTheme.CCODE)
        runCatching { applyLauncherTheme(this, savedTheme) }
        setContent { CCodeApp(this) }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CCodeApp(context: Context) {
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val initialChats = remember { loadChats(context) }
    var chatHistory by remember { mutableStateOf(initialChats) }
    var workspaceProjects by remember { mutableStateOf(loadWorkspace(context)) }
    var currentChatId by remember { mutableStateOf(initialChats.firstOrNull()?.id ?: "chat-${System.currentTimeMillis()}") }
    var profileOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableStateOf(0) }
    var greeting by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableStateOf(0) }
    var metricsText by remember { mutableStateOf("") }
    var modelLoading by remember { mutableStateOf(false) }
    var restoringModel by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(initialChats.firstOrNull()?.messages ?: emptyList()) }
    var workStage by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf<ActivityStep?>(null) }
    val settings = remember { context.getSharedPreferences("ccode-settings", Context.MODE_PRIVATE) }
    var sentCount by remember { mutableStateOf(settings.getInt("sent_count", 0)) }
    var answerCount by remember { mutableStateOf(settings.getInt("answer_count", 0)) }
    var firstMessageAt by remember { mutableStateOf(settings.getLong("first_message_at", 0L)) }
    var sourceLimit by remember { mutableStateOf(settings.getInt("web_source_limit", 4).coerceIn(1, 8)) }
    var streak by remember { mutableStateOf(StreakStats(settings.getInt("current_streak", 0), settings.getInt("best_streak", 0), settings.getInt("active_days", 0))) }
    var lastGeneratedFile by remember { mutableStateOf<GeneratedFile?>(null) }
    var lastGeneratedProject by remember { mutableStateOf<GeneratedProject?>(null) }
    var htmlBuildProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var htmlBuildName by remember { mutableStateOf("") }
    var htmlBuildPackage by remember { mutableStateOf("com.ccode.htmlbuilder") }
    var htmlBuildVersion by remember { mutableStateOf("1.0") }
    var htmlBuildVersionCode by remember { mutableStateOf("1") }
    var htmlBuildFullscreen by remember { mutableStateOf(false) }
    var exportFileTarget by remember { mutableStateOf<GeneratedFile?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf(settings.getString("selected_model", "code1") ?: "code1") }
    var showWorkStatus by remember { mutableStateOf(settings.getBoolean("show_work_status", true)) }
    var defaultGameEngine by remember { mutableStateOf(settings.getString("default_game_engine", "HTML") ?: "HTML") }
    var defaultAppEngine by remember { mutableStateOf(settings.getString("default_app_engine", "HTML") ?: "HTML") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var showImportWarning by remember { mutableStateOf(false) }
    var selectedProjectFile by remember { mutableStateOf<ProjectFile?>(null) }
    var editingProjectFile by remember { mutableStateOf<ProjectFile?>(null) }
    var attachedName by remember { mutableStateOf("") }
    var attachedContext by remember { mutableStateOf("") }
    var attachedAsset by remember { mutableStateOf<ProjectFile?>(null) }
    var section by remember { mutableStateOf(AppSection.CHATS) }
    var chatListOpen by remember { mutableStateOf(true) }
    var chatSearch by remember { mutableStateOf("") }
    var fileSearch by remember { mutableStateOf("") }
    var backgroundHex by remember {
        mutableStateOf((settings.getString("background_color", "000000") ?: "000000").let { if (it.equals("090B10", true)) "000000" else it })
    }
    var accentHex by remember { mutableStateOf(settings.getString("accent_color", "3478F6") ?: "3478F6") }
    val initialBackgroundForText = colorFromHex(backgroundHex, DefaultBackground)
    val defaultTextHex = if (isLightColor(initialBackgroundForText)) "1B1917" else "F5F5F7"
    val defaultMutedHex = if (isLightColor(initialBackgroundForText)) "6B625C" else "8B8C91"
    var textHex by remember { mutableStateOf(settings.getString("text_color", defaultTextHex) ?: defaultTextHex) }
    var mutedTextHex by remember { mutableStateOf(settings.getString("muted_text_color", defaultMutedHex) ?: defaultMutedHex) }
    var brandThemeId by remember { mutableStateOf(settings.getString("brand_theme", BrandTheme.CCODE.name) ?: BrandTheme.CCODE.name) }
    val selectedModel = MODEL_CATALOG.firstOrNull { it.id == selectedModelId } ?: MODEL_CATALOG.first()
    val savedModel = remember(selectedModelId) { File(context.filesDir, selectedModel.fileName) }
    var modelReady by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val timeGreeting = when (hour) {
        in 5..11 -> "Доброе утро…"
        in 12..17 -> "Добрый день…"
        in 18..23 -> "Добрый вечер…"
        else -> "Доброй ночи…"
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; status = "Слушаю…" }
            override fun onBeginningOfSpeech() { status = "Говори, я слушаю…" }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { isListening = false; status = "" }
            override fun onError(error: Int) { isListening = false; status = if (error == SpeechRecognizer.ERROR_NETWORK) "Нет доступа к распознаванию речи" else "Не удалось распознать речь" }
            override fun onResults(results: Bundle?) { input = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); isListening = false; status = "" }
            override fun onPartialResults(partialResults: Bundle?) { input = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { speechRecognizer?.destroy() }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { isListening = true; speechRecognizer?.startListening(speechIntent) } else status = "Нужен доступ к микрофону для голосового ввода"
    }

    fun toggleMicrophone() {
        if (speechRecognizer == null) { status = "Распознавание речи недоступно на этом устройстве"; return }
        if (isListening) { speechRecognizer.stopListening(); isListening = false; status = ""; return }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        else { isListening = true; speechRecognizer.startListening(speechIntent) }
    }

    LaunchedEffect(currentChatId, messages) {
        if (messages.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        val existing = chatHistory.firstOrNull { it.id == currentChatId }
        val updated = StoredChat(currentChatId, existing?.title?.takeIf { it.isNotBlank() && it != "Новый чат" } ?: chatTitle(messages), messages, System.currentTimeMillis(), existing?.pinned ?: false)
        chatHistory = (listOf(updated) + chatHistory.filterNot { it.id == currentChatId }).sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
        settings.edit().putInt("chat_count", chatHistory.size).putString("selected_model_title", selectedModel.title).apply()
        withContext(Dispatchers.IO) { saveChats(context, chatHistory) }
        updateCCodeWidgets(context)
    }

    fun newChat() {
        currentChatId = "chat-${System.currentTimeMillis()}"
        messages = emptyList()
        menuOpen = false
        section = AppSection.CHATS
        chatListOpen = false
    }

    fun openChat(chat: StoredChat) {
        currentChatId = chat.id
        messages = chat.messages
        menuOpen = false
        section = AppSection.CHATS
        chatListOpen = false
    }

    fun deleteChat(chat: StoredChat) {
        val remaining = chatHistory.filterNot { it.id == chat.id }
        chatHistory = remaining
        if (chat.id == currentChatId) {
            currentChatId = remaining.firstOrNull()?.id ?: "chat-${System.currentTimeMillis()}"
            messages = remaining.firstOrNull()?.messages ?: emptyList()
        }
        scope.launch(Dispatchers.IO) { saveChats(context, chatHistory) }
    }

    fun renameChat(chat: StoredChat, title: String) {
        val clean = title.trim().take(80)
        if (clean.isBlank()) return
        chatHistory = chatHistory.map { if (it.id == chat.id) it.copy(title = clean, updatedAt = System.currentTimeMillis()) else it }
            .sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
        scope.launch(Dispatchers.IO) { saveChats(context, chatHistory) }
    }

    fun togglePinned(chat: StoredChat) {
        chatHistory = chatHistory.map { if (it.id == chat.id) it.copy(pinned = !it.pinned) else it }
            .sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
        scope.launch(Dispatchers.IO) { saveChats(context, chatHistory) }
    }

    fun saveEditedProjectFile(file: ProjectFile, content: String) {
        messages = messages.map { message -> message.copy(project = message.project?.copy(files = message.project.files.map { item -> if (item.path == file.path) item.copy(content = content) else item })) }
        editingProjectFile = null
        selectedProjectFile = null
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val chatsForExport = (chatHistory + StoredChat(currentChatId, chatTitle(messages), messages, System.currentTimeMillis())).distinctBy { it.id }
            val data = JSONObject().put("format", "ccode").put("version", 2).put("chatId", currentChatId).put("chats", JSONArray().also { chats -> chatsForExport.forEach { chat -> chats.put(JSONObject().put("id", chat.id).put("title", chat.title).put("updatedAt", chat.updatedAt).put("pinned", chat.pinned).put("messages", JSONArray().also { array -> chat.messages.forEach { message -> array.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser).put("projectName", message.project?.name).put("project", message.project?.files?.let { files -> JSONArray().also { json -> files.forEach { file -> json.put(JSONObject().put("path", file.path).put("content", file.content).put("binaryBase64", file.binaryBase64).put("mime", file.mime)) } }}).put("sources", JSONArray().also { json -> message.sources.forEach { source -> json.put(JSONObject().put("title", source.title).put("url", source.url)) } })) } })) } }).put("workspace", workspaceExportJson(workspaceProjects)).put("settings", JSONObject().also { saved -> settings.all.forEach { (key, value) -> if (value != null) saved.put(key, value) } }).put("stats", JSONObject().put("sentCount", sentCount).put("answerCount", answerCount).put("firstMessageAt", firstMessageAt).put("currentStreak", streak.current).put("bestStreak", streak.best).put("activeDays", streak.activeDays)).toString(2).toByteArray(Charsets.UTF_8)
            val result = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data); it.flush() } ?: error("Не удалось открыть файл") }
            withContext(Dispatchers.Main) { Toast.makeText(context, if (result.isSuccess) "Чат экспортирован в .ccode" else "Не удалось экспортировать .ccode", Toast.LENGTH_SHORT).show() }
        }
    }
    val markdownExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(chatMarkdown(messages).toByteArray()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = uri; showImportWarning = true }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val description = inspectAttachment(context, uri)
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "asset.bin" } ?: "asset.bin"
            val mime = context.contentResolver.getType(uri).orEmpty()
            val originChatName = chatHistory.firstOrNull { it.id == currentChatId }?.title.orEmpty().ifBlank { "Без названия" }
            val archiveProject = if (name.endsWith(".zip", true) || name.endsWith(".apk", true) || mime.contains("zip", true) || mime.contains("android.package", true)) extractArchiveToWorkspace(context, uri, name, originChatName) else null
            val asset = runCatching {
                if (mime.startsWith("image/")) { val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0); if (bytes.size > 12 * 1024 * 1024) error("Изображение больше 12 МБ") else ProjectFile("assets/${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}", "", binaryBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP), mime = mime) } else null
            }.getOrNull()
            withContext(Dispatchers.Main) {
                attachedName = description.substringAfter("Файл: ").substringBefore('\n')
                val archiveSelected = name.endsWith(".zip", true) || name.endsWith(".apk", true) || mime.contains("zip", true) || mime.contains("android.package", true)
                attachedContext = when {
                    archiveProject != null -> description + "\nАрхив распакован в отдельную папку ${archiveProject.name}. Файлы сохранены в разделе Файлы и доступны для памяти новых чатов."
                    archiveSelected -> description + "\nНе удалось распаковать архив: проверь, что это корректный ZIP/APK."
                    mime.startsWith("image/") -> description + "\nФото сохранено как реальный ресурс проекта. Я не вижу его визуальное содержимое, но могу передать файл в проект как иконку приложения, спрайт игры, фон, текстуру, аватар, обложку, картинку интерфейса или другой ресурс по твоему описанию."
                    else -> description
                }
                attachedAsset = asset
                if (archiveProject != null) { val unique = uniqueWorkspaceName(workspaceProjects, archiveProject.name); workspaceProjects = workspaceProjects + archiveProject.copy(name = unique); saveWorkspace(context, workspaceProjects) }
                status = when { archiveProject != null -> "Архив распакован в папку: ${archiveProject.name}"; archiveSelected -> "Не удалось распаковать $attachedName"; else -> "Файл прикреплён: $attachedName" }
            }
        }
    }
    val fileExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = exportFileTarget ?: lastGeneratedFile
        if (uri != null && file != null) scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> if (file.diskPath != null) output.write(File(file.diskPath).readBytes()) else if (file.binaryBase64 != null) output.write(Base64.decode(file.binaryBase64, Base64.DEFAULT)) else output.write(file.content.toByteArray()) } }
    }
    val zipExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = lastGeneratedProject
        if (uri != null && project != null) scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip -> project.files.forEach { file -> zip.putNextEntry(ZipEntry(file.path)); if (file.diskPath != null) zip.write(File(file.diskPath).readBytes()) else if (file.binaryBase64 != null) zip.write(Base64.decode(file.binaryBase64, Base64.DEFAULT)) else zip.write(file.content.toByteArray()); zip.closeEntry() } } } }
    }
    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = lastGeneratedFile
        if (uri != null && file != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val pdf = PdfDocument(); val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); val paint = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK }
                var y = 30f
                ("Файл: ${file.name}\n\n${file.content}".replace("\t", "    ").split("\n")).take(55).forEach { line -> page.canvas.drawText(line.take(95), 24f, y, paint); y += 14f }
                pdf.finishPage(page); pdf.writeTo(output); pdf.close()
            }
        }
    }

    fun exportProjectFile(file: ProjectFile) {
        exportFileTarget = GeneratedFile(file.path.substringAfterLast('/'), file.content, file.binaryBase64, file.mime, file.diskPath)
        fileExportLauncher.launch(file.path.substringAfterLast('/'))
    }
    fun exportProjectZip(project: GeneratedProject) {
        lastGeneratedProject = project
            attachedAsset = null
        zipExportLauncher.launch("${project.name}.zip")
    }

    suspend fun copyApkToDownloads(apk: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, apk.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CCode")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось открыть Downloads")
            try {
                resolver.openOutputStream(uri)?.use { output -> apk.inputStream().use { it.copyTo(output) } } ?: error("Не удалось записать APK")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
            true
        }.getOrDefault(false)
    }

    fun deleteWorkspaceFile(projectId: String, path: String) {
        workspaceProjects = workspaceProjects.mapNotNull { project ->
            if (project.id != projectId) project else {
                project.files.firstOrNull { it.path == path }?.diskPath?.let { File(it).delete() }
                val remaining = project.files.filterNot { it.path == path }
                if (remaining.isEmpty()) { File(context.filesDir, "workspace/$projectId").deleteRecursively(); null } else project.copy(files = remaining)
            }
        }
        saveWorkspace(context, workspaceProjects)
    }

    fun deleteGeneratedFile(item: Triple<StoredChat, ChatMessage, ProjectFile>) {
        val (chat, message, file) = item
        chatHistory = chatHistory.map { stored ->
            if (stored.id != chat.id) stored else stored.copy(messages = stored.messages.map { current ->
                if (current !== message) current else current.copy(project = if (file.mime == "application/zip") null else current.project?.copy(files = current.project.files.filterNot { it.path == file.path }))
            })
        }
        if (chat.id == currentChatId) messages = chatHistory.firstOrNull { it.id == currentChatId }?.messages ?: messages
        scope.launch(Dispatchers.IO) { saveChats(context, chatHistory) }
    }

    LaunchedEffect(currentChatId) {
        greeting = ""
        ready = false
        kotlinx.coroutines.delay(350)
        greeting = timeGreeting
        kotlinx.coroutines.delay(260)
        ready = true
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(savedModel.absolutePath) {
        if (LocalCodeEngine.isLoaded()) {
            modelReady = true
            status = "${selectedModel.title} готов к работе"
            return@LaunchedEffect
        }
        if (savedModel.exists() && savedModel.length() > 100_000_000) {
            restoringModel = true
            modelLoading = true
            status = "Загружаем ${selectedModel.title}…"; workStage = "Восстанавливает модель"
            modelReady = withContext(Dispatchers.Default) { runCatching { LocalCodeEngine.loadModel(savedModel.absolutePath) }.getOrDefault(false) }
            modelLoading = false
            restoringModel = false
            status = if (modelReady) "${selectedModel.title} готов к работе" else "Модель не загрузилась. Выбери её заново."; workStage = ""
        }
    }

    fun chooseModel(model: CCodeModel) {
        selectedModelId = model.id
        settings.edit().putString("selected_model", model.id).putString("selected_model_title", model.title).apply()
        updateCCodeWidgets(context)
        modelOpen = false
        modelReady = false
    }

    fun downloadModel(model: CCodeModel = selectedModel) {
        scope.launch {
            val target = File(context.filesDir, model.fileName)
            chooseModel(model)
            modelLoading = true; downloadProgress = 0; status = "Скачиваем ${model.title}…"; workStage = "Скачивает модель"
            val downloaded = withContext(Dispatchers.IO) {
                runCatching {
                    val existing = if (target.exists()) target.length() else 0L
                    val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000; readTimeout = 60_000
                        if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                    }
                    connection.connect()
                    val append = existing > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                    val start = if (append) existing else 0L
                    val total = (connection.contentLengthLong.takeIf { it > 0 } ?: 1L) + start
                    totalBytes = total
                    downloadedBytes = start
                    if (!append && target.exists()) target.delete()
                    connection.inputStream.use { input -> RandomAccessFile(target, "rw").use { output ->
                        output.seek(start); val buffer = ByteArray(1024 * 1024); var received = start
                        while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); received += count; downloadedBytes = received; downloadProgress = ((received * 100L) / total).toInt().coerceIn(0, 100) }
                    } }
                    target.length() > 100_000_000
                }.getOrDefault(false)
            }
            if (downloaded) {
                status = "Запускаем ${model.title}…"
                val loaded = withContext(Dispatchers.Default) { runCatching { LocalCodeEngine.loadModel(target.absolutePath) }.getOrDefault(false) }
                modelReady = loaded; status = if (loaded) "${model.title} готов к работе" else "Модель скачана, но не загрузилась."
                sendCCodeNotification(context, "CCode", if (loaded) "${model.title} готова к работе" else "Модель скачана, но не загрузилась", 1001)
            } else {
                onboardingStep = 2
                status = "Скачивание прервано. Нажми «Скачать» ещё раз — загрузка продолжится с сохранённого места."
                sendCCodeNotification(context, "CCode", "Загрузка модели прервана", 1002)
            }
            modelLoading = false
        }
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || generating) return
        val promptWithAttachment = if (attachedContext.isNotBlank()) "$prompt\n\n[Прикреплённый файл для анализа]\n$attachedContext" else prompt
        input = ""
        attachedName = ""
        attachedContext = ""
        messages = messages + ChatMessage(prompt, true)
        sentCount += 1
        if (firstMessageAt == 0L) firstMessageAt = System.currentTimeMillis()
        streak = recordDailyActivity(settings)
        settings.edit().putInt("sent_count", sentCount).putLong("first_message_at", firstMessageAt).putString("widget_status", "Активен сегодня").apply()
        updateCCodeWidgets(context)
        if (!modelReady) {
            status = "Сначала выбери модель Code 1.0 Lite"
            modelOpen = true
            return
        }
        val normalized = prompt.lowercase().replace("ё", "е").trim()
        if (normalized in setOf("привет", "здравствуй", "здравствуйте", "хай", "hello", "hi")) {
            messages = messages + ChatMessage("Привет! Чем помочь с кодом?", false)
            answerCount += 1
            settings.edit().putInt("answer_count", answerCount).apply()
            status = ""
            return
        }
        val recalledWorkspace = workspaceProjectForQuery(workspaceProjects, prompt)
        if (isProjectRecallRequest(prompt) && recalledWorkspace != null) {
            val recalled = workspaceAsGeneratedProject(recalledWorkspace)
            val attachedMessage = ChatMessage("Да, помню проект «${recalledWorkspace.name}». Прикрепляю его текущие файлы: ${recalled.files.size}. Теперь можно попросить исправить или изменить конкретный файл.", false, project = recalled)
            messages = messages + attachedMessage
            lastGeneratedProject = recalled
            chatHistory = (chatHistory.filterNot { it.id == currentChatId } + StoredChat(currentChatId, chatTitle(messages), messages, System.currentTimeMillis())).sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
            scope.launch(Dispatchers.IO) { saveChats(context, chatHistory) }
            status = "Проект прикреплён из памяти: ${recalledWorkspace.name}"
            return
        }
        generating = true
        cancelRequested = false
        workStage = if (prompt.length > 120) "Пишет код" else "Анализирует запрос"
        status = if (prompt.length > 120) "Code 1.0 готовит длинный ответ. Это может занять некоторое время…" else "Code 1.0 готовит ответ…"
        generationProgress = 0
        val planned = plannedFiles(prompt)
        val webRequested = webSearchQuery(promptWithAttachment) != null
        val apkGameRequested = isApkGameRequest(prompt)
        val assetRequested = isRemoteAssetRequest(prompt)
        val modificationRequested = isProjectModificationRequest(prompt)
        val referencedWorkspace = workspaceProjectForQuery(workspaceProjects, prompt)
        val priorProject = messages.asReversed().firstOrNull { it.project != null }?.project
            ?: if (modificationRequested) referencedWorkspace?.let(::workspaceAsGeneratedProject) else null
            val gameRequested = Regex("(?iu)\\b(игр(а|у|ы|е)?|game|gameplay)\\b").containsMatchIn(prompt)
            val appRequested = Regex("(?iu)\\b(приложени(е|я|ю|ем)?|app|application)\\b").containsMatchIn(prompt)
            val explicitEngine = Regex("(?iu)\\b(html|хтмл|python|питон|godot|гдскрипт|java|kotlin|андроид|android)\\b").containsMatchIn(prompt)
        val explicitFileNames = Regex("(?i)(?:[\\wА-Яа-я./\\\\_-]+\\.[A-Za-zА-Яа-я0-9_+-]{1,16})").findAll(prompt).map { it.value }.distinct().toList()
        val fileRequested = attachedAsset != null || assetRequested || planned.isNotEmpty() || explicitFileNames.isNotEmpty() || apkGameRequested || modificationRequested || Regex("(?i)создай|напиши|сделай|файл|скрипт|проект|спрайт|иконк|ресурс").containsMatchIn(prompt)
        val requestActivity = listOf(
            ActivityStep("Анализирует запрос", "Изучает формулировку задачи и определяет нужные действия.", false, true)
        ) + (if (webRequested) listOf(ActivityStep("Ищет в интернете", "Подбирает страницы и документацию по запросу.", false, false)) else emptyList()) + listOf(
            ActivityStep("Планирует файлы", if (planned.isEmpty()) "Определяет формат ответа." else "Предварительно определяет структуру проекта.", false, false)
        ) + planned.map { path -> ActivityStep("Ожидает: $path", "Файл запланирован, но Code 1.0 ещё не начал его писать.", false, false) } + listOf(
            ActivityStep("Пишет ответ", "Локальная модель генерирует ответ и код в реальном времени.", false, false),
            ActivityStep("Проверяет файлы", "После генерации приложение проверит кодовые блоки и имена файлов.", false, false),
            ActivityStep("Готовит результат", "После проверки файлы можно будет открыть и экспортировать.", false, false)
        )
        messages = messages + ChatMessage("", false, requestActivity)
        val requestedSourceLimit = sourceLimit
        val webExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            scope.launch(Dispatchers.Main) {
                val last = messages.lastIndex
                if (last >= 0) messages = messages.toMutableList().also { list ->
                    val old = list[last]
                    list[last] = ChatMessage("Веб-поиск остановлен из-за ошибки сети. Попробуй ещё раз.", false, old.activity.map { step -> step.copy(done = true, active = false) }, old.project, old.sources)
                }
                generating = false; generationProgress = 100; workStage = ""; status = ""
            }
        }
        scope.launch(Dispatchers.Default + webExceptionHandler) {
            val webQuery = webSearchQuery(promptWithAttachment)
            val webContext = if (webQuery != null) {
                withContext(Dispatchers.Main) { status = "Ищу информацию в интернете…"; workStage = "Ищет гайд" }
                withContext(Dispatchers.IO) { fetchWebContext(webQuery, requestedSourceLimit) }
            } else null
            val remoteAssets = if (assetRequested) withContext(Dispatchers.IO) { downloadRemoteAssets(context, promptWithAttachment) } else emptyList()
            if (webQuery != null && webContext == null) {
                withContext(Dispatchers.Main) {
                    val last = messages.lastIndex
                    if (last >= 0) messages = messages.toMutableList().also { it[last] = ChatMessage("Не удалось подключиться к интернету. Проверь соединение и попробуй ещё раз.", false, it[last].activity.map { step -> step.copy(done = true, active = false) }) }
                    generating = false; generationProgress = 100; workStage = ""; status = ""
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                val last = messages.lastIndex
                if (last >= 0) messages = messages.toMutableList().also { list ->
                    val old = list[last]
                    list[last] = ChatMessage(old.text, false, old.activity.map { step ->
                        when {
                            step.title == "Анализирует запрос" -> step.copy(done = true, active = false)
                            step.title == "Ищет в интернете" -> step.copy(done = webContext != null, active = false, detail = if (webContext != null) "Найдено источников: ${webContext.sources.size}." else "Поиск не выполнен.")
                            step.title == "Планирует файлы" -> step.copy(active = true, detail = "Структура проекта определена, ожидается запись файлов.")
                            else -> step
                        }
                    })
                }
            }
            val listener = object : LocalCodeEngine.StreamListener {
                val pending = StringBuilder()
                var lastUpdate = 0L
                override fun onToken(token: String) {
                    pending.append(token)
                    val now = System.nanoTime()
                    if (now - lastUpdate > 50_000_000L) {
                        val chunk = pending.toString(); pending.clear(); lastUpdate = now
                        scope.launch { val last = messages.lastIndex; if (last >= 0) messages = messages.toMutableList().also { val old = it[last]; it[last] = ChatMessage("Code 1.0 пишет файлы…", false, old.activity, old.project, old.sources) } }
                    }
                }
                override fun onProgress(progress: Int) {
                    if (progress % 5 == 0) scope.launch {
                        generationProgress = progress
                        if (planned.isNotEmpty()) {
                            val current = (progress * planned.size / 100).coerceIn(0, planned.size - 1)
                            messages = messages.toMutableList().also { list ->
                                val last = list.lastIndex
                                if (last >= 0) {
                                    val old = list[last]
                                    val updatedActivity = old.activity.map { step ->
                                        if (step.title.startsWith("Ожидает:")) {
                                            val index = planned.indexOf(step.title.removePrefix("Ожидает: "))
                                            step.copy(title = if (index < current) "Готовит: ${planned[index]}" else if (index == current) "Пишет: ${planned[index]}" else step.title, detail = if (index < current) "Файл уже прошёл этап генерации." else if (index == current) "Code 1.0 сейчас пишет этот файл." else step.detail, done = index < current, active = index == current)
                                        } else if (step.title == "Анализирует запрос") step.copy(done = true, active = false) else if (step.title == "Планирует файлы") step.copy(done = true, active = false) else if (step.title == "Пишет ответ") step.copy(active = true) else step
                                    }
                                    list[last] = ChatMessage(old.text, false, updatedActivity, old.project, old.sources)
                                }
                            }
                        }
                    }
                }
                override fun onMetrics(promptMs: Long, generationMs: Long, tokens: Int, tokensPerSecond: Float) {
                    scope.launch { metricsText = "Промпт: ${promptMs} мс · Генерация: ${generationMs} мс · $tokens токенов · %.2f ток/с".format(tokensPerSecond) }
                }
            }
            val onlineInstruction = webContext?.let { "\n\nПользователь попросил использовать интернет. Не угадывай содержание страницы и не подменяй его общими знаниями. Отвечай только по блоку ПРОЧИТАННЫЙ ВЕБ-КОНТЕНТ ниже. Если нужной информации в блоке нет, прямо скажи, что страница не содержит ответа. Не создавай файлы и не выводи кодовые блоки, если пользователь отдельно не просил создать файл." }.orEmpty()
            val fileInstruction = if (fileRequested) " Если просят игру, приложение, скрипт или отдельный файл, создавай реальные файлы, а не только README. ОБЯЗАТЕЛЬНЫЙ ФОРМАТ: для каждого файла напиши отдельной строкой FILE: путь/имя.ext, следующей строкой один блок ```язык с полным содержимым```. Сохраняй каждое явно указанное имя и расширение файла без замены и добавляй все дополнительные файлы, необходимые проекту. Разрешены любые расширения, включая специальные файлы движков и плагинов. Для нескольких файлов повтори эту пару. Не добавляй код вне блоков. В конце дай только короткое пояснение." else " Отвечай кратко и по существу обычным текстом. Не создавай файлы."
            val fixInstruction = if (modificationRequested && priorProject != null) " Это запрос на ИЗМЕНЕНИЕ уже существующего проекта. Не начинай новый проект, не создавай README.md и не удаляй рабочие файлы. Сохрани исходную структуру каталогов и имена файлов. Найди нужное исправление или добавь запрошенную функцию и верни только изменённые или новые файлы, но каждый такой файл полностью и без сокращений, в тех же путях FILE-блоками. После ответа приложение автоматически объединит их с остальными файлами и снова прикрепит полный обновлённый проект." else ""
            val defaultEngineInstruction = if (!explicitEngine && gameRequested) " Пользователь попросил игру без указания технологии. Используй движок по умолчанию из настроек CCode: $defaultGameEngine. Создавай полноценные файлы именно для этой технологии." else if (!explicitEngine && appRequested) " Пользователь попросил приложение без указания технологии. Используй технологию по умолчанию из настроек CCode: $defaultAppEngine. Создавай полноценные файлы именно для этой технологии." else ""
            val gameInstruction = if (gameRequested) " Это именно запрос на игру, а не демонстрацию синтаксиса. Не создавай Hello World, print(hello) или пустой квадрат. Создай рабочую игровую механику, игровой цикл, управление, визуальный результат и все необходимые сцены/ресурсы. Для 3D добавь камеру, игрока, освещение и управление; для прыгающего шарика добавь canvas, физику гравитации, отскок и управление касанием." else ""
            val godotInstruction = if (promptWithAttachment.contains("godot", true) || promptWithAttachment.contains("гдскрипт", true)) " Для Godot создай полноценный проект: обязательно FILE-блоки project.godot, сцены и скрипты, а также все дополнительные файлы, перечисленные пользователем или необходимые проекту. Учитывай любую явно указанную версию Godot; если версия не указана, не выдумывай ограничение версии." else ""
            val htmlInstruction = if (promptWithAttachment.contains("html", true) || promptWithAttachment.contains("хтмл", true) || promptWithAttachment.contains("веб", true) || promptWithAttachment.contains("сайт", true)) " Для HTML создай проект с FILE-блоками index.html, styles.css и script.js (и другими нужными файлами)." else ""
            val apkGameInstruction = if (apkGameRequested) " Это запрос на APK/Android-игру. Создай полноценный Android-проект на Java, а не описание и не только README. Обязательно выведи отдельными FILE-блоками AndroidManifest.xml, src/main/java/com/ccode/game/MainActivity.java, src/main/res/values/strings.xml и все нужные ресурсы. Проект должен быть пригоден для последующей локальной сборки APK. Не используй слова ПК вместо APK и не ограничивайся README." else ""
            val assetInstruction = when {
                attachedAsset != null -> " Прикреплённое изображение добавлено в проект как ${attachedAsset!!.path}; используй его как иконку, спрайт или другой ресурс по смыслу задачи."
                assetRequested -> " Пользователь запросил ассеты. Прямые файлы из ссылок будут добавлены приложением в assets/. Для найденных страниц без прямого файла создай рабочие процедурные MeshInstance3D/PrimitiveMesh или 2D-замену, а источники укажи в ASSET_SOURCES.md. Не оставляй игру пустой и не ограничивайся скриптом."
                else -> ""
            }
            val rememberedFiles = workspaceMemory(workspaceProjects, prompt)
            val priorContent = if (modificationRequested) projectContentMemory(priorProject) else ""
            val conversation = conversationMemory(messages.dropLast(2))
            val memoryBlock = if (rememberedFiles.isNotBlank() || priorContent.isNotBlank() || conversation.isNotBlank()) "\n\nПАМЯТЬ О ФАЙЛАХ И ПРОЕКТАХ\n${if (conversation.isNotBlank()) "ИСТОРИЯ ДИАЛОГА\n$conversation\n\n" else ""}$rememberedFiles${if (priorContent.isNotBlank()) "\n\nТЕКУЩЕЕ СОДЕРЖИМОЕ ПРОЕКТА ДЛЯ ИСПРАВЛЕНИЯ\n$priorContent" else ""}\nКОНЕЦ ПАМЯТИ" else ""
            val webBlock = webContext?.let { "\n\nПРОЧИТАННЫЙ ВЕБ-КОНТЕНТ\nИсточник: ${it.sources.joinToString { source -> source.url }}\n${it.text}\nКОНЕЦ ВЕБ-КОНТЕНТА" }.orEmpty()
            val formattedPrompt = "<|im_start|>system\nТы — ${selectedModel.title}, локальный помощник по программированию. Отвечай по-русски.$defaultEngineInstruction$gameInstruction$godotInstruction$htmlInstruction$apkGameInstruction$assetInstruction$fileInstruction$fixInstruction$onlineInstruction$memoryBlock<|im_end|>\n<|im_start|>user\n$promptWithAttachment$webBlock<|im_end|>\n<|im_start|>assistant\n"
            val answer = runCatching { LocalCodeEngine.generateStreaming(formattedPrompt, listener) }
                .getOrElse { error -> "Ошибка локального движка: ${error.message ?: "неизвестная ошибка"}" }
            if (cancelRequested) {
                withContext(Dispatchers.Main) {
                    val last = messages.lastIndex
                    if (last >= 0) messages = messages.toMutableList().also { list ->
                        val old = list[last]
                        list[last] = ChatMessage("Генерация остановлена.", false, old.activity.map { step -> step.copy(done = true, active = false) }, old.project, old.sources)
                    }
                    generating = false; generationProgress = 0; workStage = ""; status = ""
                }
                return@launch
            }
            val cleanAnswer = answer.replaceFirst(Regex("(?is)^\\s*ответ\\s+code\\s*1\\.0\\s*:\\s*"), "").trimStart()
            lastGeneratedFile = if (fileRequested) extractGeneratedFile(cleanAnswer, prompt) else null
            val file = lastGeneratedFile
            val parsedProject = if (fileRequested) {
                if (modificationRequested && priorProject != null) extractGeneratedProject(cleanAnswer, prompt) ?: extractFixProject(cleanAnswer, priorProject)
                else extractGeneratedProject(cleanAnswer, prompt)
            } else null
            val parsedSource = parsedProject?.let { candidate ->
                if (modificationRequested && priorProject != null) candidate.copy(files = candidate.files.filterNot { it.path.equals("README.md", true) && priorProject.files.none { old -> old.path.equals("README.md", true) } }) else candidate
            } ?: file?.let { GeneratedProject("CCodeProject", listOf(ProjectFile(it.name, it.content))) }
            val projectBase = if (modificationRequested && priorProject != null && parsedSource == null) priorProject else ensureGeneratedProject(prompt, parsedSource, defaultGameEngine)
            val mergedProject = if (modificationRequested && priorProject != null && projectBase != null) {
                val replacements = projectBase.files.associateBy { it.path }
                priorProject.copy(files = priorProject.files.map { replacements[it.path] ?: it } + projectBase.files.filter { candidate -> priorProject.files.none { it.path == candidate.path } })
            } else projectBase
            val project = if (mergedProject != null) {
                val additions = buildList {
                    if (attachedAsset != null) add(attachedAsset!!)
                    addAll(remoteAssets)
                    if (assetRequested && webContext != null && webContext.sources.isNotEmpty()) {
                        add(ProjectFile("ASSET_SOURCES.md", buildString {
                            appendLine("# Источники ассетов")
                            appendLine()
                            appendLine("Ссылки найдены CCode по запросу пользователя. Проверь лицензию перед публикацией проекта.")
                            webContext.sources.forEach { source -> appendLine("- [${source.title}](${source.url})") }
                        }))
                    }
                }
                mergedProject.copy(files = mergedProject.files.filterNot { old -> additions.any { it.path == old.path } } + additions)
            } else mergedProject
            lastGeneratedProject = project
            val originChatName = chatHistory.firstOrNull { it.id == currentChatId }?.title.orEmpty().ifBlank { chatTitle(messages).ifBlank { "Без названия" } }
            val generatedWorkspace = project?.let { materializeGeneratedProject(context, it, originChatName) }
            if (generatedWorkspace != null) withContext(Dispatchers.Main) {
                val existing = if (modificationRequested) workspaceProjects.firstOrNull { it.name.equals(generatedWorkspace.name, true) && (it.chatName.isBlank() || it.chatName == originChatName) } else null
                val saved = if (existing != null) generatedWorkspace.copy(id = existing.id, name = existing.name, sourceName = existing.sourceName, chatName = existing.chatName.ifBlank { originChatName }) else {
                    val unique = uniqueWorkspaceName(workspaceProjects, generatedWorkspace.name)
                    generatedWorkspace.copy(name = unique, sourceName = if (unique == generatedWorkspace.name) generatedWorkspace.sourceName else "$unique.zip")
                }
                workspaceProjects = if (existing != null) workspaceProjects.map { if (it.id == existing.id) saved else it } else workspaceProjects + saved
                saveWorkspace(context, workspaceProjects)
            }
            val fileActivity = (project?.files?.map { file -> ActivityStep("Создан: ${file.path}", "Файл готов и доступен для просмотра.", true, false, file.content) } ?: planned.map { path -> ActivityStep("Файл: $path", "Файл был запланирован, но содержимое не найдено в ответе.", false, false) })
            val completedActivity = listOf(
                    ActivityStep("Анализирует запрос", "Запрос принят и классифицирован как задача для Code 1.0.", true, false),
                    if (webContext != null) ActivityStep("Ищет в интернете", "Просмотрено источников: ${webContext.sources.size}.", true, false) else null,
                    ActivityStep("Планирует файлы", if (project != null) "Определено файлов: ${project.files.size}." else "План завершён.", true, false),
                    ActivityStep("Пишет ответ", "Генерация ответа завершена.", true, false)
                ).filterNotNull() + fileActivity + listOf(
                    ActivityStep("Проверяет файлы", if (project != null) "Проверено файлов: ${project.files.size}." else "Кодовые файлы в ответе не найдены.", true, false),
                    ActivityStep("Готовит результат", if (project != null) "Проект готов. Можно открыть любой файл и экспортировать ZIP." else "Готов обычный текстовый ответ.", true, false)
                )
            val explanation = cleanAnswer.replace(Regex("(?s)```.*?```"), "").replace(Regex("(?i)FILE\\s*:\\s*[^\\n]+"), "").trim().take(900)
            val shortAnswer = if (project != null) "Готово. Файлов в проекте: ${project.files.size}. Размер: %.2f МБ.${if (explanation.isNotBlank()) "\n\n$explanation" else ""}".format(project.files.sumOf { it.content.toByteArray().size } / 1_048_576.0) else compactFileAnswer(cleanAnswer, file)
            scope.launch { val last = messages.lastIndex; if (last >= 0) messages = messages.toMutableList().also { it[last] = ChatMessage(shortAnswer, false, completedActivity, project, webContext?.sources.orEmpty()) } }
            answerCount += 1
            settings.edit().putInt("answer_count", answerCount).apply()
            sendCCodeNotification(context, "CCode", "Code 1.0 закончил подготовку ответа", 2001)
            scope.launch { generating = false; generationProgress = 100; workStage = ""; status = "" }
        }
    }

    val backgroundColor = colorFromHex(backgroundHex, DefaultBackground)
    val accentColor = colorFromHex(accentHex, Blue)
    Blue = accentColor
    Panel = panelFor(backgroundColor)
    White = colorFromHex(textHex, Color(0xFFF5F5F7))
    Muted = colorFromHex(mutedTextHex, Color(0xFF8B8C91))
    val brandTheme = runCatching { BrandTheme.valueOf(brandThemeId) }.getOrDefault(BrandTheme.CCODE)
    val visibleChats = chatHistory.filter { chatSearch.isBlank() || it.title.contains(chatSearch, true) }
    val generatedEntries = chatHistory.flatMap { chat -> chat.messages.flatMap { message ->
        val projectFiles = message.project?.files?.takeIf { it.size == 1 }.orEmpty()
        projectFiles.map { file -> Triple(chat, message, file) }
    } }
    val workspaceEntries = workspaceProjects.flatMap { project -> val chat = StoredChat("workspace:${project.id}", project.name, emptyList(), project.createdAt); val message = ChatMessage("Распакованный проект ${project.name}", false, project = GeneratedProject(project.name, project.files)); project.files.map { file -> Triple(chat, message, file) } }
    val allFiles = (generatedEntries + workspaceEntries).distinctBy { "${it.first.id}:${it.third.path}" }

    MaterialTheme(colorScheme = darkColorScheme(background = backgroundColor, surface = Panel, primary = accentColor)) {
        Surface(Modifier.fillMaxSize(), color = backgroundColor) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                CompactTopBar(section, accentColor, brandTheme, onInfo = { profileOpen = true }, onHistory = { menuOpen = true }, onNewChat = ::newChat)
                AnimatedContent(targetState = section, label = "section-transition", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        AppSection.CHATS -> if (chatListOpen) {
                            ChatsListSection(chatHistory, chatSearch, { chatSearch = it }, ::openChat, ::deleteChat, ::renameChat, ::togglePinned, ::newChat, accentColor)
                        } else ChatSection(
                            messages = messages, input = input, onInput = { input = it }, generating = generating,
                            progress = generationProgress, model = selectedModel, modelReady = modelReady,
                            greeting = greeting, ready = ready, status = status, workStage = workStage,
                            metrics = metricsText, showWorkStatus = showWorkStatus, isListening = isListening,
                            attachedName = attachedName, onModel = { modelOpen = true }, onAttach = { attachmentLauncher.launch(arrayOf("text/*", "image/*", "application/zip", "application/vnd.android.package-archive", "application/octet-stream")) },
                            onMic = ::toggleMicrophone, onSend = ::send, onStop = { cancelRequested = true; LocalCodeEngine.cancel(); status = "Останавливаю…" },
                            onActivity = { selectedActivity = it }, onFile = { selectedProjectFile = it },
                            onSaveFile = ::exportProjectFile, onSaveZip = ::exportProjectZip, onCopy = { text -> context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("CCode", text)); Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show() }, accent = accentColor
                        )
                        AppSection.FILES -> FilesSection(workspaceProjects, allFiles, fileSearch, { fileSearch = it }, { selectedProjectFile = it.third }, ::exportProjectFile, ::exportProjectZip, { project ->
                            htmlBuildProject = project
                            htmlBuildName = project.name
                            htmlBuildFullscreen = false
                        }, { id -> workspaceProjects.firstOrNull { it.id == id }?.let { project -> File(context.filesDir, "workspace/${project.id}").deleteRecursively(); workspaceProjects = workspaceProjects.filterNot { it.id == id }; saveWorkspace(context, workspaceProjects) } }, ::deleteWorkspaceFile, ::deleteGeneratedFile, accentColor)
                        AppSection.SETTINGS -> SettingsSection(
                            selectedModel = selectedModel, modelReady = modelReady, modelLoading = modelLoading,
                            sourceLimit = sourceLimit, showWorkStatus = showWorkStatus, backgroundHex = backgroundHex, accentHex = accentHex, textHex = textHex, mutedTextHex = mutedTextHex,
                            onModel = { modelOpen = true }, onSources = { sourceLimit = it; settings.edit().putInt("web_source_limit", it).apply() },
                            onActivity = { showWorkStatus = it; settings.edit().putBoolean("show_work_status", it).apply() },
                            onBackground = { backgroundHex = it; settings.edit().putString("background_color", it).apply() },
                            onAccent = { accentHex = it; settings.edit().putString("accent_color", it).apply() },
                            onText = { textHex = it; settings.edit().putString("text_color", it).apply() }, onMutedText = { mutedTextHex = it; settings.edit().putString("muted_text_color", it).apply() },
                            defaultGameEngine = defaultGameEngine, defaultAppEngine = defaultAppEngine,
                            onGameEngine = { defaultGameEngine = it; settings.edit().putString("default_game_engine", it).apply() }, onAppEngine = { defaultAppEngine = it; settings.edit().putString("default_app_engine", it).apply() },
                            brandTheme = brandTheme, onBrandTheme = { theme ->
                                brandThemeId = theme.name
                                val preset = when (theme) {
                                    BrandTheme.CCODE -> listOf("000000", "3478F6", "F5F5F7", "8B8C91")
                                    BrandTheme.CLAUDE -> listOf("F4EDE5", "D97757", "1B1917", "6B625C")
                                    BrandTheme.GEMINI -> listOf("FFFFFF", "4285F4", "202124", "5F6368")
                                    BrandTheme.CHATGPT -> listOf("FFFFFF", "111111", "111111", "6B7280")
                                }
                                backgroundHex = preset[0]; accentHex = preset[1]; textHex = preset[2]; mutedTextHex = preset[3]
                                settings.edit().putString("brand_theme", theme.name).putString("background_color", preset[0]).putString("accent_color", preset[1]).putString("text_color", preset[2]).putString("muted_text_color", preset[3]).apply()
                                runCatching { applyLauncherTheme(context, theme) }
                            }, accent = accentColor
                        )
                    }
                }
                AppBottomNavigation(section, { selected ->
                    section = selected
                    if (selected == AppSection.CHATS) chatListOpen = true
                }, accentColor)
            }
        }

        if (!modelReady) {
            if (restoringModel) RestoreModelScreen()
            else OnboardingScreen(step = if (modelLoading) 3 else onboardingStep, downloadTitle = selectedModel.title, progress = downloadProgress, downloadedBytes = downloadedBytes, totalBytes = totalBytes,
                onStart = { onboardingStep = 1; scope.launch { kotlinx.coroutines.delay(2000); onboardingStep = 2 } }, onDownload = { model -> onboardingStep = 3; downloadModel(model) })
        }
        if (modelOpen) ModelPickerSheet(selectedModel, modelReady, modelLoading, downloadProgress, MODEL_CATALOG, context, onSelect = ::chooseModel, onDownload = ::downloadModel, onDelete = { model ->
            val file = File(context.filesDir, model.fileName)
            if (model.id == selectedModelId) { LocalCodeEngine.release(); modelReady = false }
            file.delete(); modelOpen = true
        }, onClose = { modelOpen = false })
        if (showImportWarning) AlertDialog(onDismissRequest = { showImportWarning = false }, title = { Text("Импорт .ccode", color = White) }, text = { Text("Чаты, файлы и закрепления будут добавлены из резервной копии.", color = Muted) }, confirmButton = { Button(onClick = {
            showImportWarning = false
            val uri = pendingImport
            scope.launch {
                val imported = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri!!)?.bufferedReader()?.use { it.readText() } ?: "" }.getOrDefault("") }
                val result = runCatching {
                    val root = JSONObject(imported)
                    val importedChats = root.optJSONArray("chats")?.let { chats -> List(chats.length()) { i -> parseStoredChat(chats.getJSONObject(i)) } } ?: listOf(parseLegacyChat(root, currentChatId))
                    val valid = importedChats.filter { it.messages.isNotEmpty() }
                    if (valid.isEmpty()) error("В файле нет сообщений")
                    chatHistory = (valid + chatHistory.filterNot { old -> valid.any { it.id == old.id } }).sortedWith(compareByDescending<StoredChat> { it.pinned }.thenByDescending { it.updatedAt })
                    val selected = chatHistory.firstOrNull { it.id == root.optString("chatId") } ?: valid.first()
                    currentChatId = selected.id
                    messages = selected.messages
                    workspaceProjects = importWorkspaceJson(context, root.optJSONArray("workspace")) + workspaceProjects
                    saveWorkspace(context, workspaceProjects)
                    root.optJSONObject("settings")?.let { saved ->
                        val editor = settings.edit()
                        saved.keys().forEach { key -> when (val value = saved.opt(key)) { is Boolean -> editor.putBoolean(key, value); is Int -> editor.putInt(key, value); is Long -> editor.putLong(key, value); is Number -> editor.putLong(key, value.toLong()); is String -> editor.putString(key, value) } }
                        editor.apply()
                    }
                    root.optJSONObject("stats")?.let { stats -> sentCount = stats.optInt("sentCount", sentCount); answerCount = stats.optInt("answerCount", answerCount); firstMessageAt = stats.optLong("firstMessageAt", firstMessageAt) }
                    saveChats(context, chatHistory)
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, if (result.isSuccess) "Импорт завершён" else "Ошибка импорта: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show() }
                pendingImport = null
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("Импортировать") } }, dismissButton = { TextButton(onClick = { showImportWarning = false; pendingImport = null }) { Text("Отмена", color = White) } }, containerColor = Panel)
        if (htmlBuildProject != null) AlertDialog(onDismissRequest = { htmlBuildProject = null }, containerColor = Panel, title = { Text("Параметры HTML APK", color = White) }, text = { Column { OutlinedTextField(htmlBuildName, { htmlBuildName = it }, label = { Text("Название приложения") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(htmlBuildPackage, { htmlBuildPackage = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(htmlBuildVersion, { htmlBuildVersion = it }, label = { Text("Версия") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(htmlBuildVersionCode, { htmlBuildVersionCode = it }, label = { Text("Version code") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(htmlBuildFullscreen, { htmlBuildFullscreen = it }, colors = CheckboxDefaults.colors(checkedColor = accentColor)); Text("Полноэкранный режим", color = White, fontSize = 13.sp) }; Spacer(Modifier.height(8.dp)); Text("Иконка берётся из первого изображения проекта.", color = Muted, fontSize = 11.sp) } }, confirmButton = { Button(onClick = { val p = htmlBuildProject!!.copy(name = htmlBuildName.trim().ifBlank { htmlBuildProject!!.name }); val opts = HtmlApkBuilder.BuildOptions(p.name, htmlBuildPackage.trim(), htmlBuildVersion.trim(), htmlBuildVersionCode.toIntOrNull() ?: 0, htmlBuildFullscreen); htmlBuildProject = null; scope.launch(Dispatchers.IO) { val result = runCatching { HtmlApkBuilder.build(context, p, opts) }; withContext(Dispatchers.Main) { result.getOrNull()?.let { apk -> copyApkToDownloads(apk); Toast.makeText(context, "APK создан: ${apk.name}", Toast.LENGTH_LONG).show() } ?: Toast.makeText(context, "Ошибка сборки: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show() } } }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("Скачать APK") } }, dismissButton = { TextButton(onClick = { htmlBuildProject = null }) { Text("Отмена", color = White) } })
        if (profileOpen) ProfileSheet(onClose = { profileOpen = false }, onExport = { exportLauncher.launch("CCode.ccode") }, onMarkdown = { markdownExportLauncher.launch("CCode-chat.md") }, onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/json")) }, showWorkStatus = showWorkStatus, onWorkStatusChange = { showWorkStatus = it; settings.edit().putBoolean("show_work_status", it).apply() }, modelName = selectedModel.title, chatCount = chatHistory.size, sentCount = sentCount, answerCount = answerCount, firstMessageAt = firstMessageAt, streak = streak, generatedFile = lastGeneratedFile, onSaveFile = { lastGeneratedFile?.let { exportFileTarget = it; fileExportLauncher.launch(it.name) } }, onSaveZip = { lastGeneratedProject?.let(::exportProjectZip) }, onSavePdf = { lastGeneratedFile?.let { pdfExportLauncher.launch(it.name + ".pdf") } }, sourceLimit = sourceLimit, onSourceLimitChange = { sourceLimit = it; settings.edit().putInt("web_source_limit", it).apply() })
        if (menuOpen) HistorySheet(visibleChats, currentChatId, onNewChat = ::newChat, onOpenChat = ::openChat, onDeleteChat = ::deleteChat, onClose = { menuOpen = false })
        selectedActivity?.let { step -> AlertDialog(onDismissRequest = { selectedActivity = null }, containerColor = Panel, title = { Text(step.title, color = White) }, text = { Text(if (step.code.isNotBlank()) step.code else step.detail, color = if (step.code.isNotBlank()) Color(0xFFD8E5FF) else Muted, fontSize = if (step.code.isNotBlank()) 12.sp else 14.sp) }, confirmButton = { TextButton(onClick = { selectedActivity = null }) { Text("Закрыть", color = accentColor) } }) }
        selectedProjectFile?.let { file -> AlertDialog(onDismissRequest = { selectedProjectFile = null }, containerColor = Panel, title = { Text(file.path, color = White) }, text = { Text(file.content, color = White, fontSize = 12.sp) }, confirmButton = { TextButton(onClick = { selectedProjectFile = null }) { Text("Закрыть", color = accentColor) } }, dismissButton = { TextButton(onClick = { editingProjectFile = file }) { Text("Изменить", color = accentColor) } }) }
        editingProjectFile?.let { file ->
            var draft by remember(file.path) { mutableStateOf(file.content) }
            AlertDialog(onDismissRequest = { editingProjectFile = null }, containerColor = Panel, title = { Text("Изменить ${file.path}", color = White) }, text = { OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp), textStyle = LocalTextStyle.current.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = Muted, focusedTextColor = White, unfocusedTextColor = White)) }, confirmButton = { TextButton(onClick = { saveEditedProjectFile(file, draft) }) { Text("Сохранить", color = accentColor) } }, dismissButton = { TextButton(onClick = { editingProjectFile = null }) { Text("Отмена", color = Muted) } })
        }
    }
}

@Composable
private fun CompactTopBar(section: AppSection, accent: Color, brandTheme: BrandTheme, onInfo: () -> Unit, onHistory: () -> Unit, onNewChat: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        BrandMark(brandTheme, Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("CCode", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(when (section) { AppSection.CHATS -> "Чаты"; AppSection.FILES -> "Файлы"; AppSection.SETTINGS -> "Настройки" }, color = Muted, fontSize = 12.sp)
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Info, "Информация", tint = Muted, modifier = Modifier.size(21.dp)) }
        if (section == AppSection.CHATS) {
            IconButton(onClick = onHistory, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.History, "История чатов", tint = Muted, modifier = Modifier.size(21.dp)) }
            IconButton(onClick = onNewChat, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Add, "Новый чат", tint = accent, modifier = Modifier.size(23.dp)) }
        }
    }
}

@Composable
private fun BrandMark(theme: BrandTheme, modifier: Modifier = Modifier) {
    val background = when (theme) { BrandTheme.CCODE -> Color.Black; BrandTheme.CLAUDE -> Color(0xFFF4EDE5); BrandTheme.GEMINI -> Color.White; BrandTheme.CHATGPT -> Color.White }
    Surface(modifier = modifier, shape = RoundedCornerShape(11.dp), color = background) {
        Box(contentAlignment = Alignment.Center) {
            when (theme) {
                BrandTheme.GEMINI -> Row { Text("<", color = Color(0xFFE94235), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("/", color = Color(0xFF4285F4), fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(">", color = Color(0xFF34A853), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                BrandTheme.CLAUDE -> Text("</>", color = Color(0xFFD97757), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                BrandTheme.CHATGPT -> Text("</>", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                BrandTheme.CCODE -> Text("</>", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(selected: AppSection, onSelect: (AppSection) -> Unit, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(Panel).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(AppSection.CHATS to (Icons.Default.ChatBubbleOutline to "Чаты"), AppSection.FILES to (Icons.Default.FolderOpen to "Файлы"), AppSection.SETTINGS to (Icons.Default.Settings to "Настройки")).forEach { (item, pair) ->
            val active = item == selected
            Surface(onClick = { onSelect(item) }, shape = RoundedCornerShape(14.dp), color = if (active) accent.copy(alpha = .18f) else Color.Transparent) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(pair.first, pair.second, tint = if (active) accent else Muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp)); Text(pair.second, color = if (active) White else Muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ChatsListSection(chats: List<StoredChat>, query: String, onQuery: (String) -> Unit, onOpen: (StoredChat) -> Unit, onDelete: (StoredChat) -> Unit, onRename: (StoredChat, String) -> Unit, onTogglePin: (StoredChat) -> Unit, onNewChat: () -> Unit, accent: Color) {
    val filtered = chats.filter { query.isBlank() || it.title.contains(query, true) }
    var renameTarget by remember { mutableStateOf<StoredChat?>(null) }
    var renameText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Поиск чатов", tint = Muted) }, placeholder = { Text("Поиск чатов", color = Muted, fontSize = 16.sp) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Panel, focusedTextColor = White, unfocusedTextColor = White, cursorColor = accent))
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(bottom = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ChatBubbleOutline, null, tint = accent, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(14.dp))
                Text("Пусто. Пожалуйста, создайте новый чат", color = White, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNewChat, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Новый чат") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.id }) { chat ->
                    val preview = chat.messages.lastOrNull { it.text.isNotBlank() }?.text.orEmpty()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(chat) }.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChatBubbleOutline, null, tint = accent, modifier = Modifier.size(23.dp)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text((if (chat.pinned) "📌 " else "") + chat.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(preview.ifBlank { "Новый чат" }, color = Muted, fontSize = 12.sp, maxLines = 1) }
                        Column(horizontalAlignment = Alignment.End) {
                            Row { TextButton(onClick = { onTogglePin(chat) }, contentPadding = PaddingValues(horizontal = 3.dp)) { Text(if (chat.pinned) "Открепить" else "Закрепить", color = accent, fontSize = 10.sp) }; TextButton(onClick = { renameTarget = chat; renameText = chat.title }, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("Имя", color = accent, fontSize = 10.sp) } }
                            TextButton(onClick = { onDelete(chat) }, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("Удалить", color = Color(0xFFFF6B6B), fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        renameTarget?.let { chat ->
            AlertDialog(onDismissRequest = { renameTarget = null }, containerColor = Panel, title = { Text("Переименовать чат", color = White) }, text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Название") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Muted, focusedTextColor = White, unfocusedTextColor = White)) }, confirmButton = { TextButton(onClick = { onRename(chat, renameText); renameTarget = null }) { Text("Сохранить", color = accent) } }, dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена", color = Muted) } })
        }
    }
}

@Composable
private fun MessageContent(text: String, accent: Color, onCopy: (String) -> Unit) {
    val pattern = Regex("(?s)```(?:([\\w+#.-]+))?\\s*(.*?)```")
    val matches = pattern.findAll(text).toList()
    if (matches.isEmpty()) Text(text, color = White, fontSize = 15.sp)
    else {
        var cursor = 0
        matches.forEach { match ->
            val before = text.substring(cursor, match.range.first).trim()
            if (before.isNotBlank()) Text(before, color = White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 7.dp))
            val language = match.groupValues.getOrNull(1).orEmpty()
            val code = match.groupValues.getOrNull(2).orEmpty().trim('\n', '\r')
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .32f)).padding(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(language.ifBlank { "code" }, color = Muted, fontSize = 10.sp)
                    TextButton(onClick = { onCopy(code) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("Copy", color = accent, fontSize = 11.sp) }
                }
                Text(code, color = Color(0xFFD8E5FF), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            cursor = match.range.last + 1
        }
        val after = text.substring(cursor).trim()
        if (after.isNotBlank()) Text(after, color = White, fontSize = 15.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ChatSection(messages: List<ChatMessage>, input: String, onInput: (String) -> Unit, generating: Boolean, progress: Int, model: CCodeModel, modelReady: Boolean, greeting: String, ready: Boolean, status: String, workStage: String, metrics: String, showWorkStatus: Boolean, isListening: Boolean, attachedName: String, onModel: () -> Unit, onAttach: () -> Unit, onMic: () -> Unit, onSend: () -> Unit, onStop: () -> Unit, onActivity: (ActivityStep) -> Unit, onFile: (ProjectFile) -> Unit, onSaveFile: (ProjectFile) -> Unit, onSaveZip: (GeneratedProject) -> Unit, onCopy: (String) -> Unit, accent: Color) {
    Box(Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Code, null, tint = accent, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(14.dp))
                AnimatedVisibility(visible = greeting.isNotBlank(), enter = fadeIn() + slideInVertically { 28 }, exit = fadeOut()) {
                    Text(greeting, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                AnimatedVisibility(visible = ready, enter = fadeIn() + slideInVertically { 22 }, exit = fadeOut()) {
                    Text("Что сегодня будем делать?", color = Muted, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(bottom = 78.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { message ->
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel.copy(alpha = if (message.fromUser) 1f else .78f)).padding(12.dp)) { MessageContent(message.text, accent, onCopy) }
                        if (message.text.isNotBlank()) TextButton(onClick = { onCopy(message.text) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("Копировать", color = accent, fontSize = 11.sp) }
                        if (!message.fromUser && showWorkStatus && message.activity.isNotEmpty()) ActivityPanel(message.activity, onActivity, Modifier.padding(top = 5.dp))
                        if (!message.fromUser && message.project != null) ProjectFilesCard(message.project, onFile, onSaveFile, onSaveZip, Modifier.padding(top = 5.dp))
                        if (!message.fromUser && message.sources.isNotEmpty()) SourcesCard(message.sources, Modifier.padding(top = 5.dp))
                    }
                }
                if (generating) item { Text("Code 1.0 печатает… $progress%", color = Muted, fontSize = 13.sp) }
            }
        }
        if (showWorkStatus && (status.isNotEmpty() || workStage.isNotEmpty() || metrics.isNotEmpty())) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (workStage.isNotEmpty()) "Code 1.0 · $workStage" else status, color = Muted, fontSize = 11.sp); if (metrics.isNotEmpty() && !generating) Text(metrics, color = Muted, fontSize = 9.sp) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onModel, enabled = !generating, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(model.title.removePrefix("Code "), color = if (modelReady) accent else Muted, fontSize = 10.sp, maxLines = 1) }
            TextField(value = input, onValueChange = onInput, placeholder = { Text("Введите запрос…", color = Muted, fontSize = 14.sp) }, singleLine = false, maxLines = 2, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = White, unfocusedTextColor = White, cursorColor = accent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            IconButton(onClick = onAttach, enabled = !generating, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.AttachFile, "Файл", tint = if (attachedName.isNotBlank()) accent else Muted, modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onMic, enabled = !generating, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Mic, "Микрофон", tint = if (isListening) accent else Muted, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = if (generating) onStop else onSend, enabled = generating || input.isNotBlank(), modifier = Modifier.size(36.dp)) { Icon(if (generating) Icons.Default.Close else Icons.Default.Send, if (generating) "Остановить" else "Отправить", tint = if (generating || input.isNotBlank()) accent else Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun FilesSection(folders: List<WorkspaceProject>, files: List<Triple<StoredChat, ChatMessage, ProjectFile>>, query: String, onQuery: (String) -> Unit, onOpen: (Triple<StoredChat, ChatMessage, ProjectFile>) -> Unit, onSave: (ProjectFile) -> Unit, onSaveZip: (GeneratedProject) -> Unit, onBuildApk: (WorkspaceProject) -> Unit, onDeleteFolder: (String) -> Unit, onDeleteFile: (String, String) -> Unit, onDeleteGenerated: (Triple<StoredChat, ChatMessage, ProjectFile>) -> Unit, accent: Color) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val generated = files.filterNot { it.first.id.startsWith("workspace:") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, placeholder = { Text("Поиск файлов…", color = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Panel, focusedTextColor = White, unfocusedTextColor = White, cursorColor = accent))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (folders.isNotEmpty()) item { Text("Папки проектов", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            items(folders.filter { query.isBlank() || it.name.contains(query, true) || it.sourceName.contains(query, true) || it.chatName.contains(query, true) || it.files.any { file -> file.path.contains(query, true) } }, key = { it.id }) { project ->
                val isExpanded = project.id in expanded
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (isExpanded) expanded - project.id else expanded + project.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, tint = accent, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(project.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("${project.files.size} файлов · ${project.sourceName}", color = Muted, fontSize = 11.sp); if (project.chatName.isNotBlank()) Text("Чат: ${project.chatName}", color = Muted, fontSize = 10.sp) }
                        Text(if (isExpanded) "Свернуть" else "Открыть", color = accent, fontSize = 11.sp); if (project.files.any { it.path.endsWith(".html", true) || it.path.endsWith(".htm", true) }) TextButton(onClick = { onBuildApk(project) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("Скачать APK-файл", color = accent, fontSize = 10.sp) }; TextButton(onClick = { onDeleteFolder(project.id) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("Удалить папку", color = Color(0xFFFF6B6B), fontSize = 10.sp) }
                    }
                    if (isExpanded) project.files.filter { query.isBlank() || it.path.contains(query, true) }.forEach { file ->
                        val item = Triple(StoredChat("workspace:${project.id}", project.chatName.ifBlank { project.name }, emptyList(), project.createdAt), ChatMessage("${project.name}/${file.path}", false, project = GeneratedProject(project.name, project.files)), file)
                        Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(start = 28.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, tint = accent.copy(alpha = .8f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(file.path, color = White, fontSize = 13.sp); Text("${file.path.substringAfterLast('.', "file")} · ${file.diskPath?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: file.content.toByteArray().size} Б", color = Muted, fontSize = 10.sp) }
                            IconButton(onClick = { onSave(file) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Download, "Скачать файл", tint = accent, modifier = Modifier.size(17.dp)) }; TextButton(onClick = { onDeleteFile(project.id, file.path) }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("Удалить", color = Color(0xFFFF6B6B), fontSize = 10.sp) }
                        }
                    }
                }
            }
            if (generated.isNotEmpty()) item { Text("Файлы, созданные ИИ в чатах", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            items(generated.filter { query.isBlank() || it.third.path.contains(query, true) || it.first.title.contains(query, true) }) { item ->
                val (chat, message, file) = item; val size = file.content.toByteArray().size
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onOpen(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (file.mime == "application/zip") Icons.Default.FolderOpen else Icons.Default.Code, null, tint = accent, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.path.substringAfterLast('/'), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(".${file.path.substringAfterLast('.', "txt")} · ${size} Б · ${chat.title}", color = Muted, fontSize = 11.sp) }
                    IconButton(onClick = { if (file.mime == "application/zip" && message.project != null) onSaveZip(message.project) else onSave(file) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Download, "Скачать", tint = accent, modifier = Modifier.size(18.dp)) }; TextButton(onClick = { onDeleteGenerated(item) }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("Удалить", color = Color(0xFFFF6B6B), fontSize = 10.sp) }
                }
            }
            if (folders.isEmpty() && generated.isEmpty()) item { Text("Созданные Code 1.0 файлы появятся здесь.", color = Muted, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(selectedModel: CCodeModel, modelReady: Boolean, modelLoading: Boolean, sourceLimit: Int, showWorkStatus: Boolean, backgroundHex: String, accentHex: String, textHex: String, mutedTextHex: String, onModel: () -> Unit, onSources: (Int) -> Unit, onActivity: (Boolean) -> Unit, onBackground: (String) -> Unit, onAccent: (String) -> Unit, onText: (String) -> Unit, onMutedText: (String) -> Unit, defaultGameEngine: String, defaultAppEngine: String, onGameEngine: (String) -> Unit, onAppEngine: (String) -> Unit, brandTheme: BrandTheme, onBrandTheme: (BrandTheme) -> Unit, accent: Color) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SettingCard("Модель", Icons.Default.Code, accent) { Text(selectedModel.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(if (modelReady) "Готова к работе" else if (modelLoading) "Загрузка…" else selectedModel.details, color = Muted, fontSize = 12.sp); TextButton(onClick = onModel, contentPadding = PaddingValues(0.dp)) { Text("Выбрать модель", color = accent) } } }
        item { SettingCard("Веб-источники", Icons.Default.Search, accent) { Text("Количество источников: $sourceLimit", color = White, fontSize = 14.sp); Slider(value = sourceLimit.toFloat(), onValueChange = { onSources(it.roundToInt().coerceIn(1, 8)) }, valueRange = 1f..8f, steps = 6, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)) } }
        item { SettingCard("Движок игры по умолчанию", Icons.Default.Code, accent) { EngineChoice(defaultGameEngine, listOf("HTML", "Godot", "Python", "Java/Kotlin"), onGameEngine, accent); Text("Если в запросе не указан движок, CCode выберет этот вариант.", color = Muted, fontSize = 11.sp) } }
        item { SettingCard("Технология приложения по умолчанию", Icons.Default.Code, accent) { EngineChoice(defaultAppEngine, listOf("HTML", "Java", "Kotlin", "Python"), onAppEngine, accent); Text("Используется, когда пользователь просит приложение без уточнения технологии.", color = Muted, fontSize = 11.sp) } }
        item { SettingCard("Отображение активности", Icons.Default.History, accent) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Показывать этапы работы Code 1.0", color = White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(showWorkStatus, onActivity, colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = .35f))) } } }
        item { SettingCard("Цвета интерфейса", Icons.Default.ColorLens, accent) { RgbColorControl("Фон", backgroundHex, onBackground, accent); RgbColorControl("Акцент", accentHex, onAccent, accent); RgbColorControl("Основной текст", textHex, onText, accent); RgbColorControl("Вторичный текст", mutedTextHex, onMutedText, accent); Text("Все четыре цвета настраиваются по каналам RGB", color = Muted, fontSize = 11.sp) } }
        item { SettingCard("Фирменная иконка", Icons.Default.Code, accent) { Text("Стиль значка в шапке CCode", color = Muted, fontSize = 12.sp); BrandTheme.values().forEach { theme -> ThemeChoice(theme, theme == brandTheme, onBrandTheme, accent) } } }
    }
}

@Composable
private fun EngineChoice(value: String, options: List<String>, onChange: (String) -> Unit, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange(options[(options.indexOf(value).coerceAtLeast(0) + 1) % options.size]) }) { Text("Изменить", color = accent) }
    }
}

@Composable
private fun SettingCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(8.dp)); content() } }

@Composable
private fun RgbColorControl(label: String, value: String, onValue: (String) -> Unit, accent: Color) {
    val rgb = rgbFromHex(value, if (label == "Фон") RgbColor(0, 0, 0) else RgbColor(52, 120, 246))
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(22.dp).clip(CircleShape).background(colorFromHex(value, accent))); Spacer(Modifier.width(8.dp)); Text(label, color = White, fontSize = 14.sp); Spacer(Modifier.weight(1f)); Text("RGB ${rgb.r}, ${rgb.g}, ${rgb.b}", color = Muted, fontSize = 11.sp) }
        Text("R", color = Color(0xFFFF6B6B), fontSize = 10.sp); Slider(rgb.r.toFloat(), { onValue(hexFromRgb(rgb.copy(r = it.roundToInt()))) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6B6B), activeTrackColor = Color(0xFFFF6B6B)))
        Text("G", color = Color(0xFF66D17A), fontSize = 10.sp); Slider(rgb.g.toFloat(), { onValue(hexFromRgb(rgb.copy(g = it.roundToInt()))) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color(0xFF66D17A), activeTrackColor = Color(0xFF66D17A)))
        Text("B", color = Color(0xFF6EA8FE), fontSize = 10.sp); Slider(rgb.b.toFloat(), { onValue(hexFromRgb(rgb.copy(b = it.roundToInt()))) }, valueRange = 0f..255f, colors = SliderDefaults.colors(thumbColor = Color(0xFF6EA8FE), activeTrackColor = Color(0xFF6EA8FE)))
    }
}

@Composable
private fun ThemeChoice(theme: BrandTheme, selected: Boolean, onSelect: (BrandTheme) -> Unit, accent: Color) {
    val name = when (theme) { BrandTheme.CCODE -> "CCode"; BrandTheme.CLAUDE -> "Claude"; BrandTheme.GEMINI -> "Gemini"; BrandTheme.CHATGPT -> "ChatGPT" }
    Surface(onClick = { onSelect(theme) }, shape = RoundedCornerShape(14.dp), color = if (selected) accent.copy(alpha = .18f) else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { BrandMark(theme, Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Text(name, color = if (selected) White else Muted, fontSize = 13.sp); Spacer(Modifier.weight(1f)); if (selected) Text("Выбрано", color = accent, fontSize = 11.sp) }
    }
}

@Composable
private fun ColorField(label: String, value: String, onValue: (String) -> Unit, accent: Color) { OutlinedTextField(value, { text -> onValue(text.filter { it in "0123456789abcdefABCDEF#" }.take(7).removePrefix("#")) }, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text(label) }, leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(colorFromHex(value, accent))) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF3A3D46), focusedTextColor = White, unfocusedTextColor = White, focusedLabelColor = accent, unfocusedLabelColor = Muted)) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(chats: List<StoredChat>, currentId: String, onNewChat: () -> Unit, onOpenChat: (StoredChat) -> Unit, onDeleteChat: (StoredChat) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filteredChats = chats.filter { query.isBlank() || it.title.contains(query, true) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("История чатов", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNewChat) { Text("Новый чат", color = Blue) }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, placeholder = { Text("Поиск по названию", color = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue, unfocusedBorderColor = Color(0xFF3A3D46), focusedTextColor = White, unfocusedTextColor = White))
        if (filteredChats.isEmpty()) {
            Text("Здесь появятся сохранённые чаты.", color = Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp).padding(bottom = 12.dp)) {
                items(filteredChats, key = { it.id }) { chat ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenChat(chat) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title + if (chat.id == currentId) "  · открыт" else "", color = White, fontSize = 16.sp, maxLines = 1)
                            Text("${chat.messages.count { it.fromUser }} сообщений", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onDeleteChat(chat) }) { Text("Удалить", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RestoreModelScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            Text("Ccode. Твой ИИ.", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Text("Загружаем сохранённую модель…", color = White, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Модель уже скачана. Подготавливаем её для работы.", color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ProjectFilesCard(project: GeneratedProject, onFileClick: (ProjectFile) -> Unit, onSaveFile: (ProjectFile) -> Unit, onSaveZip: (GeneratedProject) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp)) {
        Text(project.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("${project.files.size} файлов · %.2f МБ".format(project.files.sumOf { it.content.toByteArray().size } / 1_048_576.0), color = Muted, fontSize = 11.sp)
        TextButton(onClick = { onSaveZip(project) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Скачать ZIP проекта", color = Blue, fontSize = 12.sp) }
        Spacer(Modifier.height(6.dp))
        project.files.forEach { file ->
            TextButton(onClick = { onFileClick(file) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 2.dp, horizontal = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✓  ${file.path}", color = Blue, fontSize = 13.sp)
                    Text("Скачать", color = Muted, fontSize = 11.sp)
                }
            }
            TextButton(onClick = { onSaveFile(file) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 0.dp, horizontal = 4.dp)) { Text("Скачать ${file.path.substringAfterLast('/')}", color = White, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SourcesCard(sources: List<WebSource>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(10.dp)) {
        Text("Источники", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        sources.forEachIndexed { index, source ->
            Text("${index + 1}. ${source.title}", color = Blue, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
            Text(source.url, color = Muted, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun ActivityPanel(steps: List<ActivityStep>, onStepClick: (ActivityStep) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (steps.any { it.active }) "Code 1.0 работает" else "Code 1.0 завершил работу", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (steps.any { it.active }) steps.first { it.active }.title else "Нажми, чтобы посмотреть действия", color = Muted, fontSize = 11.sp)
            }
            Text(if (expanded) "Свернуть" else "Открыть", color = Blue, fontSize = 11.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            steps.forEach { step ->
                Row(Modifier.fillMaxWidth().clickable { onStepClick(step) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (step.done) "✓" else if (step.active) "●" else "○", color = if (step.done) Blue else Muted, fontSize = 14.sp, modifier = Modifier.width(24.dp))
                    Text(step.title, color = if (step.active) White else Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Подробнее", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(step: Int, downloadTitle: String, progress: Int, downloadedBytes: Long, totalBytes: Long, onStart: () -> Unit, onDownload: (CCodeModel) -> Unit) {
    val downloadedGb = downloadedBytes / 1_000_000_000.0
    val totalGb = totalBytes / 1_000_000_000.0
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it })
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it })
                }
            },
            label = "first-launch-flow"
        ) { current ->
            when (current) {
                0 -> WelcomeMenu(onStart)
                1 -> WelcomeMessageMenu()
                2 -> ModelMenu(onDownload)
                else -> DownloadMenu(downloadTitle, progress, downloadedGb, totalGb)
            }
        }
    }
}

@Composable
private fun WelcomeMenu(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Добро пожаловать.\nНе желаете начать?", color = White, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        ActionButton("Начать", onStart, height = 40.dp)
    }
}

@Composable
private fun WelcomeMessageMenu() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        Text("Отлично, можем и начинать.", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ModelMenu(onDownload: (CCodeModel) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Выберите модель", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Выбери модель для устройства", color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        MODEL_CATALOG.take(2).forEach { model ->
            Text(model.title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(model.details, color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(model.requirements, color = Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            ActionButton("Скачать", { onDownload(model) }, height = 36.dp)
            Spacer(Modifier.height(18.dp))
        }
        LazyColumn(Modifier.heightIn(max = 260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(MODEL_CATALOG.drop(2)) { model ->
                Text(model.title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(model.details, color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(model.requirements, color = Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                ActionButton("Скачать", { onDownload(model) }, height = 36.dp)
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun DownloadMenu(downloadTitle: String, progress: Int, downloadedGb: Double, totalGb: Double) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ccode. Твой ИИ.", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Text("Скачивается $downloadTitle…", color = White, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("$progress%", color = White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        Text(if (totalGb > 0) String.format("%.2f ГБ из %.2f ГБ", downloadedGb, totalGb) else "Подготавливаем загрузку…", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.width(240.dp).height(4.dp)) {
            drawRect(color = Panel)
            drawRect(color = Blue, size = androidx.compose.ui.geometry.Size(size.width * (progress / 100f).coerceIn(0f, 1f), size.height))
        }
        Spacer(Modifier.height(14.dp))
        Text("Загрузка идёт. Не закрывайте приложение.", color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(selected: CCodeModel, modelReady: Boolean, downloading: Boolean, progress: Int, models: List<CCodeModel>, context: Context, onSelect: (CCodeModel) -> Unit, onDownload: (CCodeModel) -> Unit, onDelete: (CCodeModel) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel) {
        Text("Модель", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        models.forEach { model ->
            val exists = File(context.filesDir, model.fileName).length() > 100_000_000
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.title + if (model.id == selected.id) "  · выбрана" else "", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(model.details, color = Muted, fontSize = 12.sp)
                    Text(model.requirements, color = Muted, fontSize = 11.sp)
                    if (downloading && model.id == selected.id) Text("Загрузка: $progress%", color = Blue, fontSize = 12.sp)
                }
                if (exists && model.id != selected.id) TextButton(onClick = { onSelect(model) }) { Text("Выбрать", color = Blue, fontSize = 12.sp) }
                if (!exists) TextButton(onClick = { onDownload(model) }, enabled = !downloading) { Text("Скачать", color = Blue, fontSize = 12.sp) }
                if (exists) TextButton(onClick = { onDelete(model) }, enabled = !downloading && model.id != selected.id) { Text("Удалить", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(onClose: () -> Unit, onExport: () -> Unit, onMarkdown: () -> Unit, onImport: () -> Unit, showWorkStatus: Boolean, onWorkStatusChange: (Boolean) -> Unit, modelName: String, chatCount: Int, sentCount: Int, answerCount: Int, firstMessageAt: Long, streak: StreakStats, generatedFile: GeneratedFile?, onSaveFile: () -> Unit, onSaveZip: () -> Unit, onSavePdf: () -> Unit, sourceLimit: Int, onSourceLimitChange: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Info", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold); IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть", tint = Muted) } }
        Text("Статистика CCode", color = Muted, modifier = Modifier.padding(24.dp, 8.dp))
        val firstDate = if (firstMessageAt > 0L) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(firstMessageAt)) else "Пока нет данных"
        StatRow("Первое сообщение", firstDate); StatRow("Сообщений отправлено", sentCount.toString()); StatRow("Ответов Code", answerCount.toString()); StatRow("Создано чатов", chatCount.toString()); StatRow("Серия дней", streak.current.toString()); StatRow("Рекорд серии", streak.best.toString()); StatRow("Модель", modelName)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Показывать процесс работы", color = White, fontSize = 15.sp)
            Switch(checked = showWorkStatus, onCheckedChange = onWorkStatusChange)
        }
        Text("Источников при поиске: $sourceLimit", color = White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Slider(value = sourceLimit.toFloat(), onValueChange = { onSourceLimitChange(it.roundToInt().coerceIn(1, 8)) }, valueRange = 1f..8f, steps = 6, modifier = Modifier.padding(horizontal = 24.dp), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(18.dp)) { Text("Экспорт приложения") }
        OutlinedButton(onClick = onMarkdown, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(18.dp)) { Text("Экспорт текущего чата в Markdown") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(18.dp)) { Text("Импорт .ccode") }
        Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .requiredHeight(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun StatRow(title: String, value: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 9.dp), Arrangement.SpaceBetween) { Text(title, color = Muted, fontSize = 16.sp); Text(value, color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
