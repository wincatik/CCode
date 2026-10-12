package com.ccode.code

import android.content.Context
import android.content.ComponentName
import android.content.ClipData
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
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

private data class GeneratedFile(val name: String, val content: String)
private data class ActivityStep(val title: String, val detail: String, val done: Boolean, val active: Boolean, val code: String = "")
private data class ProjectFile(val path: String, val content: String, val ready: Boolean = true)
private data class GeneratedProject(val name: String, val files: List<ProjectFile>)
private data class WebSource(val title: String, val url: String)
private data class WebContext(val text: String, val sources: List<WebSource>)

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
    } else if (mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in setOf("gd", "gdscript", "py", "po", "js", "ts", "kt", "java", "cpp", "h", "c", "cs", "txt", "json", "xml", "html", "css", "md", "ini", "cfg", "toml", "yaml", "yml")) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(120_000).toByteArray().toString(Charsets.UTF_8) } ?: ""
        "Файл: $name\nСодержимое:\n${text.take(12000)}"
    } else {
        "Файл: $name\nТип: бинарный или неизвестный файл ($mime). Передано только имя и тип; содержимое не читается, чтобы избежать вылета."
    }
}.getOrElse { "Не удалось прочитать вложение: ${it.message ?: "неизвестная ошибка"}" }

private fun plannedFiles(prompt: String): List<String> {
    val godot = prompt.contains("godot", true) || prompt.contains("гдскрипт", true)
    val creation = prompt.contains("создай", true) || prompt.contains("напиши", true) || prompt.contains("сделай", true) || prompt.contains("файл", true) || prompt.contains("скрипт", true) || prompt.contains("проект", true)
    val multiple = prompt.contains("нескольк", true) || prompt.contains("проект", true) || (prompt.contains("игр", true) && creation)
    if (!creation && !godot) return emptyList()
    return when {
        godot && multiple -> listOf("project.godot", "scenes/main.tscn", "scripts/main.gd", "scripts/player.gd")
        godot -> listOf("scripts/main.gd")
        prompt.contains("python", true) || prompt.contains("пайтон", true) -> listOf("main.py")
        prompt.contains("javascript", true) || prompt.contains("js", true) -> listOf("main.js")
        else -> emptyList()
    }
}

private fun extractGeneratedFile(text: String, prompt: String): GeneratedFile? {
    val block = Regex("(?s)(?:FILE\\s*:\\s*|Файл\\s*:\\s*)?([\\w.\\- ]+\\.(?:py|gd|js|ts|cpp|h|kt|java|cs|txt|json|xml|html|css))?\\s*```(?:[\\w+#.-]+)?\\s*(.*?)```").find(text)
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
        val namedBlocks = Regex("(?s)([\\w./ -]+\\.(?:gd|py|js|ts|cpp|h|kt|java|cs|json|xml|html|css))\\s*:?\\s*```(?:[\\w+#.-]+)?\\s*(.*?)```").findAll(text).map { ProjectFile(it.groupValues[1].trim(), it.groupValues[2].trim()) }.filter { it.content.isNotBlank() }.toList()
        files = namedBlocks
    }
    if (files.isEmpty()) return null
    val name = if (prompt.contains("godot", true) || prompt.contains("гдскрипт", true)) "GodotProject" else "CCodeProject"
    return GeneratedProject(name, files)
}

private data class CCodeModel(val id: String, val title: String, val details: String, val fileName: String, val url: String)

private val MODEL_CATALOG = listOf(
    CCodeModel("code1", "Code 1.0", "Qwen2.5 Coder · 3B · около 1,7 ГБ", "model.gguf", MODEL_URL),
    CCodeModel("lite", "Code 1.0 Lite", "Qwen2.5 Coder · 1.5B · около 824 МБ", "model-lite.gguf", LITE_MODEL_URL)
)

private fun webSearchQuery(prompt: String): String? {
    val lower = prompt.lowercase()
    val markers = listOf("найди в интернете", "поищи в интернете", "узнай в интернете", "узнать в интернете", "посмотри в интернете", "что такое в интернете", "посмотри гайд", "найди гайд", "посмотри документацию", "найди документацию", "через интернет", "проверь в интернете", "если не знаешь", "если не понимаешь")
    val hasUrl = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE).containsMatchIn(prompt)
    val naturalInternetQuestion = lower.contains("в интернете") && (lower.contains("что такое") || lower.contains("кто такой") || lower.contains("кто такая") || lower.contains("узнай") || lower.contains("проверь") || lower.contains("найди") || lower.contains("посмотри"))
    if (!hasUrl && markers.none { lower.contains(it) } && !naturalInternetQuestion) return null
    val cleaned = prompt.replace(Regex("(?i)найди в интернете|поищи в интернете|узнай в интернете|узнать в интернете|посмотри в интернете|что такое в интернете|посмотри гайд|найди гайд|посмотри документацию|найди документацию|через интернет|проверь в интернете"), "").trim(' ', '.', ',', ':', '?', '!')
    return cleaned.ifBlank { prompt }
}

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
                    ProjectFile(file.optString("path"), file.optString("content"))
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
                messages.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser).put("projectName", message.project?.name).put("project", message.project?.files?.let { files -> JSONArray().also { json -> files.forEach { file -> json.put(JSONObject().put("path", file.path).put("content", file.content)) } }}).put("sources", JSONArray().also { json -> message.sources.forEach { source -> json.put(JSONObject().put("title", source.title).put("url", source.url)) } }))
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
    var exportFileTarget by remember { mutableStateOf<GeneratedFile?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf(settings.getString("selected_model", "code1") ?: "code1") }
    var showWorkStatus by remember { mutableStateOf(settings.getBoolean("show_work_status", true)) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var showImportWarning by remember { mutableStateOf(false) }
    var selectedProjectFile by remember { mutableStateOf<ProjectFile?>(null) }
    var editingProjectFile by remember { mutableStateOf<ProjectFile?>(null) }
    var attachedName by remember { mutableStateOf("") }
    var attachedContext by remember { mutableStateOf("") }
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val data = JSONObject().put("format", "ccode").put("version", 1).put("chatId", currentChatId).put("messages", JSONArray().also { array -> messages.forEach { array.put(JSONObject().put("text", it.text).put("fromUser", it.fromUser)) } }).toString(2).toByteArray(Charsets.UTF_8)
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
            withContext(Dispatchers.Main) { attachedName = description.substringAfter("Файл: ").substringBefore('\n'); attachedContext = description; status = "Файл прикреплён: $attachedName" }
        }
    }
    val fileExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val file = exportFileTarget ?: lastGeneratedFile
        if (uri != null && file != null) scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(file.content.toByteArray()) } }
    }
    val zipExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = lastGeneratedProject
        if (uri != null && project != null) scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip -> project.files.forEach { file -> zip.putNextEntry(ZipEntry(file.path)); zip.write(file.content.toByteArray()); zip.closeEntry() } } } }
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
        exportFileTarget = GeneratedFile(file.path.substringAfterLast('/'), file.content)
        fileExportLauncher.launch(file.path.substringAfterLast('/'))
    }
    fun exportProjectZip(project: GeneratedProject) {
        lastGeneratedProject = project
        zipExportLauncher.launch("${project.name}.zip")
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
        generating = true
        cancelRequested = false
        workStage = if (prompt.length > 120) "Пишет код" else "Анализирует запрос"
        status = if (prompt.length > 120) "Code 1.0 готовит длинный ответ. Это может занять некоторое время…" else "Code 1.0 готовит ответ…"
        generationProgress = 0
        val planned = plannedFiles(prompt)
        val webRequested = webSearchQuery(promptWithAttachment) != null
        val fileRequested = planned.isNotEmpty() || Regex("(?i)создай|напиши|сделай|файл|скрипт|проект").containsMatchIn(prompt)
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
            val fileInstruction = if (fileRequested) " Если просят игру или скрипт, создавай файлы, а не вставляй огромный код в обычное сообщение. ОБЯЗАТЕЛЬНЫЙ ФОРМАТ: для каждого файла напиши отдельной строкой FILE: путь/имя.ext, следующей строкой один блок ```язык с полным содержимым```. Для нескольких файлов повтори эту пару для каждого файла. Не добавляй код вне блоков. В конце дай только короткое пояснение." else " Отвечай кратко и по существу обычным текстом. Не создавай файлы."
            val godotInstruction = if (promptWithAttachment.contains("godot", true) || promptWithAttachment.contains("гдскрипт", true)) " Для Godot используй указанную пользователем версию, включая Godot 4.7. Не отказывайся от задачи и не говори, что не умеешь работать с этой версией: создай совместимый GDScript 2.0 и project.godot, а неизвестные детали обозначь кратко." else ""
            val webBlock = webContext?.let { "\n\nПРОЧИТАННЫЙ ВЕБ-КОНТЕНТ\nИсточник: ${it.sources.joinToString { source -> source.url }}\n${it.text}\nКОНЕЦ ВЕБ-КОНТЕНТА" }.orEmpty()
            val formattedPrompt = "<|im_start|>system\nТы — ${selectedModel.title}, локальный помощник по программированию. Отвечай по-русски.$godotInstruction$fileInstruction$onlineInstruction<|im_end|>\n<|im_start|>user\n$promptWithAttachment$webBlock<|im_end|>\n<|im_start|>assistant\n"
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
            val parsedProject = if (fileRequested) extractGeneratedProject(cleanAnswer, prompt) else null
            val project = parsedProject ?: file?.let { GeneratedProject("CCodeProject", listOf(ProjectFile(it.name, it.content))) }
            lastGeneratedProject = project
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
            val shortAnswer = if (project != null) "Готово. Создано файлов: ${project.files.size}. Размер: %.2f МБ.".format(project.files.sumOf { it.content.toByteArray().size } / 1_048_576.0) else compactFileAnswer(cleanAnswer, file)
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
    val allFiles = chatHistory.flatMap { chat ->
        chat.messages.flatMap { message -> message.project?.files.orEmpty().map { file -> Triple(chat, message, file) } }
    }.distinctBy { "${it.first.id}:${it.third.path}" }

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
                            attachedName = attachedName, onModel = { modelOpen = true }, onAttach = { attachmentLauncher.launch(arrayOf("text/*", "application/zip", "application/vnd.android.package-archive", "application/octet-stream")) },
                            onMic = ::toggleMicrophone, onSend = ::send, onStop = { cancelRequested = true; LocalCodeEngine.cancel(); status = "Останавливаю…" },
                            onActivity = { selectedActivity = it }, onFile = { selectedProjectFile = it },
                            onSaveFile = ::exportProjectFile, onSaveZip = ::exportProjectZip, onCopy = { text -> context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("CCode", text)); Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show() }, accent = accentColor
                        )
                        AppSection.FILES -> FilesSection(allFiles, fileSearch, { fileSearch = it }, { selectedProjectFile = it.third }, ::exportProjectFile, ::exportProjectZip, accentColor)
                        AppSection.SETTINGS -> SettingsSection(
                            selectedModel = selectedModel, modelReady = modelReady, modelLoading = modelLoading,
                            sourceLimit = sourceLimit, showWorkStatus = showWorkStatus, backgroundHex = backgroundHex, accentHex = accentHex, textHex = textHex, mutedTextHex = mutedTextHex,
                            onModel = { modelOpen = true }, onSources = { sourceLimit = it; settings.edit().putInt("web_source_limit", it).apply() },
                            onActivity = { showWorkStatus = it; settings.edit().putBoolean("show_work_status", it).apply() },
                            onBackground = { backgroundHex = it; settings.edit().putString("background_color", it).apply() },
                            onAccent = { accentHex = it; settings.edit().putString("accent_color", it).apply() },
                            onText = { textHex = it; settings.edit().putString("text_color", it).apply() }, onMutedText = { mutedTextHex = it; settings.edit().putString("muted_text_color", it).apply() },
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
            else OnboardingScreen(step = if (modelLoading) 3 else onboardingStep, progress = downloadProgress, downloadedBytes = downloadedBytes, totalBytes = totalBytes,
                onStart = { onboardingStep = 1; scope.launch { kotlinx.coroutines.delay(2000); onboardingStep = 2 } }, onDownload = { model -> onboardingStep = 3; downloadModel(model) })
        }
        if (modelOpen) ModelPickerSheet(selectedModel, modelReady, modelLoading, downloadProgress, MODEL_CATALOG, context, onSelect = ::chooseModel, onDownload = ::downloadModel, onDelete = { model ->
            val file = File(context.filesDir, model.fileName)
            if (model.id == selectedModelId) { LocalCodeEngine.release(); modelReady = false }
            file.delete(); modelOpen = true
        }, onClose = { modelOpen = false })
        if (showImportWarning) AlertDialog(onDismissRequest = { showImportWarning = false }, title = { Text("Импорт .ccode", color = White) }, text = { Text("Текущие чаты и статистика будут заменены данными из резервной копии.", color = Muted) }, confirmButton = { Button(onClick = { showImportWarning = false; val uri = pendingImport; scope.launch { val imported = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri!!)?.bufferedReader()?.use { it.readText() } ?: "" }.getOrDefault("") }; runCatching { val array = JSONObject(imported).getJSONArray("messages"); messages = List(array.length()) { i -> val item = array.getJSONObject(i); ChatMessage(item.getString("text"), item.getBoolean("fromUser")) } }; pendingImport = null } }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("ОК") } }, dismissButton = { TextButton(onClick = { showImportWarning = false; pendingImport = null }) { Text("Нет", color = White) } }, containerColor = Panel)
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
                        Text(message.text, color = White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel.copy(alpha = if (message.fromUser) 1f else .78f)).padding(12.dp))
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
private fun FilesSection(files: List<Triple<StoredChat, ChatMessage, ProjectFile>>, query: String, onQuery: (String) -> Unit, onOpen: (Triple<StoredChat, ChatMessage, ProjectFile>) -> Unit, onSave: (ProjectFile) -> Unit, onSaveZip: (GeneratedProject) -> Unit, accent: Color) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, placeholder = { Text("Поиск файлов…", color = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = Panel, focusedTextColor = White, unfocusedTextColor = White, cursorColor = accent))
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) Text("Созданные Code 1.0 файлы появятся здесь.", color = Muted, modifier = Modifier.padding(12.dp))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(files.filter { query.isBlank() || it.third.path.contains(query, true) || it.first.title.contains(query, true) }) { item ->
                val (chat, _, file) = item; val size = file.content.toByteArray().size
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onOpen(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = accent, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.path.substringAfterLast('/'), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(".${file.path.substringAfterLast('.', "txt")} · ${size} Б · ${chat.title}", color = Muted, fontSize = 11.sp) }
                    IconButton(onClick = { onSave(file) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Download, "Скачать", tint = accent, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(selectedModel: CCodeModel, modelReady: Boolean, modelLoading: Boolean, sourceLimit: Int, showWorkStatus: Boolean, backgroundHex: String, accentHex: String, textHex: String, mutedTextHex: String, onModel: () -> Unit, onSources: (Int) -> Unit, onActivity: (Boolean) -> Unit, onBackground: (String) -> Unit, onAccent: (String) -> Unit, onText: (String) -> Unit, onMutedText: (String) -> Unit, brandTheme: BrandTheme, onBrandTheme: (BrandTheme) -> Unit, accent: Color) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SettingCard("Модель", Icons.Default.Code, accent) { Text(selectedModel.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(if (modelReady) "Готова к работе" else if (modelLoading) "Загрузка…" else selectedModel.details, color = Muted, fontSize = 12.sp); TextButton(onClick = onModel, contentPadding = PaddingValues(0.dp)) { Text("Выбрать модель", color = accent) } } }
        item { SettingCard("Веб-источники", Icons.Default.Search, accent) { Text("Количество источников: $sourceLimit", color = White, fontSize = 14.sp); Slider(value = sourceLimit.toFloat(), onValueChange = { onSources(it.roundToInt().coerceIn(1, 8)) }, valueRange = 1f..8f, steps = 6, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)) } }
        item { SettingCard("Отображение активности", Icons.Default.History, accent) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Показывать этапы работы Code 1.0", color = White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Switch(showWorkStatus, onActivity, colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = .35f))) } } }
        item { SettingCard("Цвета интерфейса", Icons.Default.ColorLens, accent) { RgbColorControl("Фон", backgroundHex, onBackground, accent); RgbColorControl("Акцент", accentHex, onAccent, accent); RgbColorControl("Основной текст", textHex, onText, accent); RgbColorControl("Вторичный текст", mutedTextHex, onMutedText, accent); Text("Все четыре цвета настраиваются по каналам RGB", color = Muted, fontSize = 11.sp) } }
        item { SettingCard("Фирменная иконка", Icons.Default.Code, accent) { Text("Стиль значка в шапке CCode", color = Muted, fontSize = 12.sp); BrandTheme.values().forEach { theme -> ThemeChoice(theme, theme == brandTheme, onBrandTheme, accent) } } }
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
private fun OnboardingScreen(step: Int, progress: Int, downloadedBytes: Long, totalBytes: Long, onStart: () -> Unit, onDownload: (CCodeModel) -> Unit) {
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
                else -> DownloadMenu(progress, downloadedGb, totalGb)
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
        MODEL_CATALOG.forEach { model ->
            Text(model.title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(model.details, color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            ActionButton("Скачать", { onDownload(model) }, height = 36.dp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DownloadMenu(progress: Int, downloadedGb: Double, totalGb: Double) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ccode. Твой ИИ.", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Text("Скачивается Code 1.0 Lite…", color = White, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
