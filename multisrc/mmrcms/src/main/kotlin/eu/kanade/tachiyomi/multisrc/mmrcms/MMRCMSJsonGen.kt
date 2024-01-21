package eu.kanade.tachiyomi.multisrc.mmrcms

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.PrintWriter
import java.security.cert.CertificateException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * This class generates the sources for MMRCMS.
 * Credit to nulldev for writing the original shell script
 *
 * CMS: https://getcyberworks.com/product/manga-reader-cms/
 */
class MMRCMSJsonGen {
    // private var preRunTotal: String

    init {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3")
        // preRunTotal = Regex("""-> (\d+)""").findAll(File(relativePath).readText(Charsets.UTF_8)).last().groupValues[1]
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun generate() {
        val buffer = StringBuffer()
        val dateTime = ZonedDateTime.now()
        val formattedDate = dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        buffer.append("package eu.kanade.tachiyomi.multisrc.mmrcms")
        buffer.append("\n\n// GENERATED FILE, DO NOT MODIFY!\n// Generated $formattedDate\n\n")
        buffer.append("object SourceData {\n")
        buffer.append("    fun giveMetaData(url: String) = when (url) {\n")
        var number = 1
        sources.forEach {
            println("Generating ${it.name}")
            try {
                val advancedSearchDocument = getDocument("${it.baseUrl}/advanced-search", false)

                var parseCategories = mutableListOf<Map<String, String>>()
                if (advancedSearchDocument != null) {
                    parseCategories = parseCategories(advancedSearchDocument)
                }

                val homePageDocument = getDocument(it.baseUrl)

                val itemUrl = getItemUrl(homePageDocument, it.baseUrl)

                var prefix = itemUrl.substringAfterLast("/").substringBeforeLast("/")

                // Sometimes itemUrl is the root of the website, and thus the prefix found is the website address.
                // In this case, we set the default prefix as "manga".
                if (prefix.startsWith("www") || prefix.startsWith("wwv")) {
                    prefix = "manga"
                }

                val mangaListDocument = getDocument("${it.baseUrl}/$prefix-list")!!

                if (parseCategories.isEmpty()) {
                    parseCategories = parseCategories(mangaListDocument)
                }

                val tags = parseTags(mangaListDocument)

                val source = SourceDataModel(
                    name = it.name,
                    base_url = it.baseUrl,
                    supports_latest = supportsLatest(it.baseUrl),
                    item_url = "$itemUrl/",
                    categories = parseCategories,
                    tags = if (tags.size in 1..49) tags else null,
                )

                if (!itemUrl.startsWith(it.baseUrl)) println("**Note: ${it.name} URL does not match! Check for changes: \n ${it.baseUrl} vs $itemUrl")

                buffer.append("        \"${it.baseUrl}\" -> \"\"\"${Json.encodeToString(source)}\"\"\"\n")
                number++
            } catch (e: Exception) {
                println("error generating source ${it.name} ${e.printStackTrace()}")
            }
        }

        buffer.append("        else -> \"\"\n")
        buffer.append("    }\n")
        buffer.append("}\n")
        // println("Pre-run sources: $preRunTotal")
        println("Post-run sources: ${number - 1}")
        PrintWriter(relativePath).use {
            it.write(buffer.toString())
        }
    }

    private fun getDocument(url: String, printStackTrace: Boolean = true): Document? {
        val serverCheck = arrayOf("cloudflare-nginx", "cloudflare")

        try {
            val request = Request.Builder().url(url)
            getOkHttpClient().newCall(request.build()).execute().let { response ->
                // Bypass Cloudflare ("Please wait 5 seconds" page)
                if (response.code == 503 && response.header("Server") in serverCheck) {
                    var cookie = "${response.header("Set-Cookie")!!.substringBefore(";")}; "
                    Jsoup.parse(response.body.string()).let { document ->
                        val path = document.select("[id=\"challenge-form\"]").attr("action")
                        val chk = document.select("[name=\"s\"]").attr("value")
                        getOkHttpClient().newCall(Request.Builder().url("$url/$path?s=$chk").build()).execute().let { solved ->
                            cookie += solved.header("Set-Cookie")!!.substringBefore(";")
                            request.addHeader("Cookie", cookie).build().let {
                                return Jsoup.parse(getOkHttpClient().newCall(it).execute().body.string())
                            }
                        }
                    }
                }
                if (response.code == 200) {
                    return Jsoup.parse(response.body.string())
                }
            }
        } catch (e: Exception) {
            if (printStackTrace) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun parseTags(mangaListDocument: Document): List<Map<String, String>> {
        val elements = mangaListDocument.select("div.tag-links a")
        return elements.map {
            mapOf(
                "id" to it.attr("href").substringAfterLast("/"),
                "name" to it.text(),
            )
        }
    }

    private fun getItemUrl(document: Document?, url: String): String {
        document ?: throw Exception("Couldn't get document for: $url")
        return document.toString().substringAfter("showURL = \"").substringAfter("showURL=\"").substringBefore("/SELECTION\";")

        // Some websites like mangasyuri use javascript minifiers, and thus "showURL = " becomes "showURL="https://mangasyuri.net/manga/SELECTION""
        // (without spaces). Hence the double substringAfter.
    }

    private fun supportsLatest(third: String): Boolean {
        val document = getDocument("$third/latest-release?page=1", false) ?: return false
        return document.select("div.mangalist div.manga-item a, div.grid-manga tr").isNotEmpty()
    }

    private fun parseCategories(document: Document): MutableList<Map<String, String>> {
        val elements = document.select("select[name^=categories] option, a.category")
        return elements.mapIndexed { index, element ->
            mapOf(
                "id" to (index + 1).toString(),
                "name" to element.text(),
            )
        }.toMutableList()
    }

    @Throws(Exception::class)
    private fun getOkHttpClient(): OkHttpClient {
        // Create all-trusting host name verifier
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            },
        )

        // Install the all-trusting trust manager
        val sc = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
        val sslSocketFactory = sc.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()
    }

    @Serializable
    private data class SourceDataModel(
        val name: String,
        val base_url: String,
        val supports_latest: Boolean,
        val item_url: String,
        val categories: List<Map<String, String>>,
        val tags: List<Map<String, String>>? = null,
    )

    companion object {
        sealed class ThemeSourceData {
            abstract val name: String
            abstract val baseUrl: String
            abstract val isNsfw: Boolean
            abstract val className: String
            abstract val pkgName: String

            /**
             * Override it if for some reason the name attribute inside the source class
             * should be different from the extension name. Useful in cases where the
             * extension name should be romanized and the source name should be the one
             * in the source language. Defaults to the extension name if not specified.
             */
            abstract val sourceName: String

            /**
             * overrideVersionCode defaults to 0, if a source changes their source override code or
             * a previous existing source suddenly needs source code overrides, overrideVersionCode
             * should be increased.
             * When a new source is added with overrides, overrideVersionCode should still be set to 0
             *
             * Note: source code overrides are located in "multisrc/overrides/src/<themeName>/<sourceName>"
             */
            abstract val overrideVersionCode: Int
        }

        data class SingleLang(
            override val name: String,
            override val baseUrl: String,
            val lang: String,
            override val isNsfw: Boolean = false,
            override val className: String = name.replace(" ", ""),
            override val pkgName: String = className.lowercase(Locale.ENGLISH),
            override val sourceName: String = name,
            override val overrideVersionCode: Int = 0,
        ) : ThemeSourceData()

        data class MultiLang(
            override val name: String,
            override val baseUrl: String,
            val langs: List<String>,
            override val isNsfw: Boolean = false,
            override val className: String = name.replace(" ", "") + "Factory",
            override val pkgName: String = className.substringBefore("Factory").lowercase(Locale.ENGLISH),
            override val sourceName: String = name,
            override val overrideVersionCode: Int = 0,
        ) : ThemeSourceData()

        val sources = listOf(
            SingleLang("مانجا اون لاين", "https://onma.top", "ar", className = "onma"),
            SingleLang("Read Comics Online", "https://readcomicsonline.ru", "en"),
            SingleLang("Fallen Angels", "https://manga.fascans.com", "en", overrideVersionCode = 2),
            SingleLang("Scan FR", "https://www.scan-fr.org", "fr", overrideVersionCode = 2),
            SingleLang("Scan VF", "https://www.scan-vf.net", "fr", overrideVersionCode = 1),
            SingleLang("Komikid", "https://www.komikid.com", "id"),
            SingleLang("Mangadoor", "https://mangadoor.com", "es", overrideVersionCode = 1),
            SingleLang("Utsukushii", "https://manga.utsukushii-bg.com", "bg", overrideVersionCode = 1),
            SingleLang("Phoenix-Scans", "https://phoenix-scans.pl", "pl", className = "PhoenixScans", overrideVersionCode = 1),
            SingleLang("Lelscan-VF", "https://lelscanvf.cc", "fr", className = "LelscanVF", overrideVersionCode = 2),
            SingleLang("AnimaRegia", "https://animaregia.net", "pt-BR", overrideVersionCode = 4),
            SingleLang("MangaID", "https://mangaid.click", "id", overrideVersionCode = 1),
            SingleLang("Jpmangas", "https://jpmangas.xyz", "fr", overrideVersionCode = 2),
            SingleLang("Manga-FR", "https://manga-fr.cc", "fr", className = "MangaFR", overrideVersionCode = 2),
            SingleLang("Manga-Scan", "https://mangascan.cc", "fr", className = "MangaScan", overrideVersionCode = 2),
            SingleLang("Ama Scans", "https://amascan.com", "pt-BR", isNsfw = true, overrideVersionCode = 2),
            SingleLang("Bentoscan", "https://bentoscan.com", "fr"),
            // NOTE: THIS SOURCE CONTAINS A CUSTOM LANGUAGE SYSTEM (which will be ignored)!
            SingleLang("HentaiShark", "https://www.hentaishark.com", "all", isNsfw = true),
        )

        val relativePath = System.getProperty("user.dir")!! + "/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/mmrcms/SourceData.kt"

        @JvmStatic
        fun main(args: Array<String>) {
            MMRCMSJsonGen().generate()
        }
    }
}
