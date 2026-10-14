package com.ccode.code

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.android.apksig.ApkSigner
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import java.io.FilterOutputStream

/** Builds a small, self-contained WebView APK from a saved HTML workspace. */
object HtmlApkBuilder {
    data class BuildOptions(val appName: String, val packageName: String, val versionName: String, val versionCode: Int, val fullscreen: Boolean = false)
    private const val TEMPLATE_ASSET = "ccode_html_template.apk"
    private const val KEYSTORE = "CCodeBuilderKeyStore"
    private const val KEY_ALIAS = "ccode-html-builder"

    fun build(context: Context, project: WorkspaceProject, options: BuildOptions = BuildOptions(project.name, "com.ccode.htmlbuilder", "1.0", 1)): File {
        val index = project.files.firstOrNull { it.path.equals("index.html", true) || it.path.endsWith("/index.html", true) }
            ?: error("HTML-проект должен содержать index.html")
        val workDir = File(context.cacheDir, "html-build-${System.currentTimeMillis()}").apply { mkdirs() }
        val unsigned = File(workDir, "${safeName(project.name)}-unsigned.apk")
        val outputDir = File(context.filesDir, "workspace/${project.id}").apply { mkdirs() }
        require(options.packageName.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "Неверный package name" }
        require(options.versionName.matches(Regex("[0-9]+(\\.[0-9]+){0,3}"))) { "Неверная версия" }
        require(options.versionCode > 0) { "Version code должен быть больше нуля" }
        val output = File(outputDir, nextOutputName(outputDir, options.appName, options.versionName))
        try {
            context.assets.open(TEMPLATE_ASSET).use { input -> unsigned.outputStream().use { input.copyTo(it) } }
            ZipInputStream(unsigned.inputStream()).use { input ->
                CountingOutputStream(File(workDir, "payload.apk").outputStream()).use { counted ->
                ZipOutputStream(counted).use { zip ->
                    val writtenEntries = mutableSetOf<String>()
                    var entry = input.nextEntry
                    while (entry != null) {
                        val name = entry!!.name
                        if (!name.startsWith("assets/www/") && name != "assets/ccode_meta.txt" && name != "res/drawable/ic_launcher.png" && !name.startsWith("META-INF/") && writtenEntries.add(name)) {
                            // Android 11+ rejects compressed resources.arsc and requires it to be aligned.
                            // Keep the manifest and resource table stored; zipalign adds the final padding later.
                            val bytes = input.readBytes().let { raw -> if (name == "AndroidManifest.xml") patchManifest(raw, options) else raw }
                            val copy = ZipEntry(name)
                            copy.time = entry!!.time
                            if (name == "AndroidManifest.xml" || name == "resources.arsc") {
                                val crc = CRC32().apply { update(bytes) }.value
                                copy.method = ZipEntry.STORED
                                copy.size = bytes.size.toLong()
                                copy.compressedSize = bytes.size.toLong()
                                copy.crc = crc
                                val headerWithoutExtra = counted.count + 30L + name.toByteArray(Charsets.UTF_8).size
                                val padding = ((4L - (headerWithoutExtra % 4L)) % 4L).toInt()
                                if (padding > 0) copy.extra = ByteArray(padding)
                            }
                            zip.putNextEntry(copy)
                            zip.write(bytes)
                            zip.closeEntry()
                        }
                        entry = input.nextEntry
                    }
                    project.files.filter { it.path.isNotBlank() && !it.path.startsWith("../") }.forEach { file ->
                        val relative = file.path.replace("\\", "/").trimStart('/').removePrefix("./")
                        val target = "assets/www/$relative"
                        if (writtenEntries.add(target)) {
                            zip.putNextEntry(ZipEntry(target))
                            openProjectFile(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    if (writtenEntries.add("assets/ccode_meta.txt")) {
                        zip.putNextEntry(ZipEntry("assets/ccode_meta.txt"))
                        zip.write("${options.appName}\n${options.packageName}\n${options.versionName}\n${options.versionCode}\n${options.fullscreen}".toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                    project.files.firstOrNull { isImage(it) }?.let { icon ->
                        val raw = openProjectFile(icon).use { it.readBytes() }
                        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        if (bitmap != null && writtenEntries.add("res/drawable/ic_launcher.png")) {
                            zip.putNextEntry(ZipEntry("res/drawable/ic_launcher.png"))
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                            zip.closeEntry()
                            bitmap.recycle()
                        }
                    }
                }
                }
            }
            val payload = File(workDir, "payload.apk")
            sign(context, payload, output)
            if (!output.exists() || output.length() < 4096) error("APK не был создан")
            return output
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun openProjectFile(file: ProjectFile): java.io.InputStream {
        file.diskPath?.let { path -> File(path).takeIf { it.exists() }?.let { return it.inputStream() } }
        file.binaryBase64?.let { return Base64.decode(it, Base64.DEFAULT).inputStream() }
        return file.content.toByteArray(Charsets.UTF_8).inputStream()
    }

    private fun isImage(file: ProjectFile): Boolean = file.mime?.startsWith("image/") == true || file.path.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp")

    private fun patchManifest(source: ByteArray, options: BuildOptions): ByteArray {
        val out = source.copyOf(); var foundPackage = false; var foundVersion = false
        var p = 8
        while (p + 8 <= out.size) {
            val type = (out[p].toInt() and 255) or ((out[p + 1].toInt() and 255) shl 8)
            val size = leInt(out, p + 4)
            if (type == 0x0001 && p + 28 <= out.size) {
                val count = leInt(out, p + 8); val flags = leInt(out, p + 16); val start = leInt(out, p + 20)
                if (flags and 0x100 != 0) error("Manifest string pool UTF-8 format is unsupported")
                for (i in 0 until count) { val pos = p + start + leInt(out, p + 28 + i * 4); if (pos + 2 > out.size) continue; val len = leShort(out, pos); val text = String(out, pos + 2, len * 2, Charsets.UTF_16LE); val replacement = when { text == "com.ccode.htmlbuilder" -> options.packageName.also { foundPackage = true }; text == "1.0" -> options.versionName.also { foundVersion = true }; text == "CCode HTML App" || text.startsWith("CCode HTML App [Custom App Name Placeholder") -> options.appName else -> null }; if (replacement != null) { require(replacement.length <= len) { "Название приложения слишком длинное: максимум $len символов" }; out[pos] = replacement.length.toByte(); out[pos + 1] = 0; java.util.Arrays.fill(out, pos + 2, pos + 2 + len * 2, 0); replacement.toByteArray(Charsets.UTF_16LE).copyInto(out, pos + 2) } }
            }
            if (size <= 0) break; p += size
        }
        val versionId = 0x0101021b
        for (i in 0 until out.size - 20) if (leInt(out, i) == versionId) {
            val data = i + 12
            out[data] = options.versionCode.toByte(); out[data + 1] = (options.versionCode shr 8).toByte(); out[data + 2] = (options.versionCode shr 16).toByte(); out[data + 3] = (options.versionCode shr 24).toByte()
            break
        }
        require(foundPackage && foundVersion) { "Не удалось обновить package/version в Manifest" }
        return out
    }
    private fun leInt(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8) or ((b[p+2].toInt() and 255) shl 16) or ((b[p+3].toInt() and 255) shl 24)
    private fun leShort(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8)

    private fun sign(context: Context, input: File, output: File) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=CCode HTML Builder"))
                .setCertificateSerialNumber(java.math.BigInteger.ONE)
                .setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86_400_000L))
                .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build())
            generator.generateKeyPair()
        }
        val privateKey = store.getKey(KEY_ALIAS, null) as PrivateKey
        val certificate = store.getCertificate(KEY_ALIAS) as X509Certificate
        val signer = ApkSigner.SignerConfig.Builder("CCode HTML", privateKey, listOf(certificate)).build()
        output.parentFile?.mkdirs()
        // Align before signing, then keep v1/v2/v3 for compatibility with older and newer Android installers.
        ApkSigner.Builder(listOf(signer)).setInputApk(input).setOutputApk(output).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign()
    }

    private class CountingOutputStream(out: java.io.OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "HtmlProject" }
    private fun nextOutputName(dir: File, name: String, version: String): String {
        val base = "${safeName(name)}-${safeName(version)}.apk"
        if (!File(dir, base).exists()) return base
        var i = 2
        while (File(dir, "${safeName(name)}-${safeName(version)}-updated-$i.apk").exists()) i++
        return "${safeName(name)}-${safeName(version)}-updated-$i.apk"
    }
}
