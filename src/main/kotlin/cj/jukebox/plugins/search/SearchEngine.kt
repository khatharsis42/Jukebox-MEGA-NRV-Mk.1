package cj.jukebox.plugins.search

import cj.jukebox.config
import cj.jukebox.database.TrackData
import cj.jukebox.utils.Loggers
import cj.jukebox.utils.runCommand
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.DurationUnit


/**
 * Pour créer facilement des regex à partir d'un nom de domaine.
 */
private fun urlRegexMaker(domain: String) = Regex("^(https?://)?((www\\.)?$domain)/.+$")

/**
 * Enumération de toutes les sources prises en compte.
 * NB: Ce n'est pas forcément très beau, mais ça marche très bien.
 *
 * @param[urlRegex] Un regex permettant de reconnaître une URL.
 * @author khatharsis
 */
enum class SearchEngine(val urlRegex: Regex) {
    /**
     * Correspond à une URL directe vers une musique de Jamendo.
     */
    JAMENDO(urlRegexMaker("jamendo\\.com")),

    /**
     * Correspond à une URL directe vers une vidéo de Twitch.
     */
    TWITCH(urlRegexMaker("twitch\\.tv")),

    /**
     * Correspond à une URL directe vers une musique Bandcamp.
     */
    BANDCAMP(urlRegexMaker("(.+\\.)?bandcamp\\.com")),

    /**
     * Correspond à une URL directe vers une musique.
     */
    DIRECT_FILE(Regex("^(https?://)?.*\\.(mp3|mp4|ogg|flac|wav|webm)")) {
//        override val queryRegex = Regex("^!direct .+\$")
    },

    /**
     * Correspond à une recherche Soundcloud.
     */
    SOUNDCLOUD(urlRegexMaker("soundcloud\\.com")) {
        override fun downloadMultiple(request: String) =
            searchYoutubeDL("ytsearch5:\"${request.removePrefix("!sc ")}\"").map { jsonToTrack(it) }

        override val queryRegex = Regex("^!sc .+\$")
    },

    /**
     * Correspond à la recherche avec YouTube.
     */
    YOUTUBE(urlRegexMaker("youtube\\.com|youtu\\.be")) {
        override fun downloadSingle(url: String): List<TrackData> {
            if ("list" in url) return searchYoutubeAPI(url, true)
            val newUrl = "https://www.youtube.com/watch?v=" +
                    if ("youtu.be" in url) {
                        url.substringAfter("youtu.be/").substringBefore("?")
                    } else {
                        url.substringAfter("youtube.com/watch?v=").substringBefore("&")
                    }
            return searchYoutubeDL(newUrl).map { jsonToTrack(it) }
        }

        override fun downloadMultiple(request: String) = searchYoutubeAPI(request, false)

        private fun searchYoutubeAPI(query: String, searchPlaylist: Boolean): List<TrackData> {
            Loggers.DL.info("Searching request using Youtube API")
            for (key in config.data.YT_KEYS) {
                val (validResponse, response) = if (searchPlaylist) {
                    getRequest(
                        "https://www.googleapis.com/youtube/v3/playlistItems",
                        mapOf(
                            "key" to key,
                            "part" to "snippet",
                            "maxResults" to "50",
                            "playlistId" to query.substringAfter("list=").substringBefore("&")
                        )
                    )
                } else getRequest(
                    "https://www.googleapis.com/youtube/v3/search",
                    mapOf(
                        "key" to key,
                        "q" to URLEncoder.encode(query, StandardCharsets.UTF_8.toString()),
                        "part" to "snippet",
                        "type" to "video",
                        "maxResults" to "5"
                    )
                )
                if (!validResponse) {
                    // On essaye les autres clefs
                    continue
                }
                if (response.isEmpty() || (response["items"] as JsonArray).isEmpty()) {
                    Loggers.DL.warn("Nothing found on Youtube for query $query")
                }
                val videoIds: List<String> = if (searchPlaylist) {
                    (response["items"] as JsonArray).map {
                        Json.decodeFromJsonElement((((it as JsonObject)["id"] as JsonObject)["resourceId"] as JsonObject)["videoId"]!!)
                    }
                } else {
                    (response["items"] as JsonArray).map {
                        Json.decodeFromJsonElement(((it as JsonObject)["id"] as JsonObject)["videoId"]!!)
                    }
                }

                val (secondValidResponse, secondResponse) = getRequest(
                    "https://www.googleapis.com/youtube/v3/videos",
                    mapOf(
                        "part" to "snippet,contentDetails",
                        "key" to key,
                        "id" to videoIds.reduce { acc, s -> "$acc,$s" }
                    )
                )
                if (!secondValidResponse) {
                    // On essaye les autres clefs
                    continue
                }
                return (secondResponse["items"] as JsonArray)
                    .map { it as JsonObject }
                    .map { metadata ->
                        val snippet = metadata["snippet"] as JsonObject
                        TrackData(
                            url = "https://www.youtube.com/watch?v=" + Json.decodeFromJsonElement<String>(metadata["id"]!!),
                            source = name,
                            track = Json.decodeFromJsonElement(snippet["title"]!!),
                            artist = Json.decodeFromJsonElement(snippet["channelTitle"]!!),
                            album = null,
                            albumArtUrl = Json.decodeFromJsonElement(((snippet["thumbnails"] as JsonObject)["medium"] as JsonObject)["url"]!!),
                            duration = Duration.parseIsoString(
                                Json.decodeFromJsonElement((metadata["contentDetails"] as JsonObject)["duration"]!!)
                            ).toInt(DurationUnit.SECONDS),
                            blacklisted = false,
                            obsolete = false
                        )
                    }
            }
            // On a plus de clefs et aucune n'a fonctionné
            return searchYoutubeDL("ytsearch5:${query.removePrefix("!yt ")}").map { jsonToTrack(it) }
        }

        private fun getRequest(url: String, params: Map<String, String>): Pair<Boolean, JsonObject> {
            val connection = URL(
                "$url?" +
                        params
                            .map { (key, value) -> "$key=$value&" }
                            .reduce(String::plus))
                .openConnection() as HttpURLConnection
            if (connection.responseCode != 200 && connection.responseCode != 403) {
                throw Exception("Error ${connection.responseCode}: ${connection.responseMessage}")
            }
            val responseString = if (connection.responseCode == 200)
                connection.inputStream.bufferedReader().lines().reduce(String::plus).get()
            else "{}"
            return (connection.responseCode == 200) to Json.decodeFromString(responseString)
        }

        override val youtubeArgs: Map<String, String> = mapOf("yes-playlist" to "")
        override val queryRegex = "^!yt .+\$".toRegex()
    };

    /**
     * Permet de télécharger des metadatas depuis une URL. Utilise quasi exclusivement youtube-dl.
     * @param[url] Une URL vers une musique ou une playlist.
     * @return Une [List] de [TrackData], correspondant à l'URL.
     */
    open fun downloadSingle(url: String): List<TrackData> = searchYoutubeDL(url).map { jsonToTrack(it) }

    /**
     * Convertit un objet JSON en une TrackData.
     */
    open fun jsonToTrack(metadata: JsonObject): TrackData = TrackData(
        url = Json.decodeFromJsonElement(metadata["webpage_url"]!!),
        source = name,
        track = (metadata["title"] ?: metadata["track"])?.let { Json.decodeFromJsonElement(it) },
        artist = (metadata["artist"] ?: metadata["uploader"])?.let { Json.decodeFromJsonElement(it) },
        album = (metadata["album"])?.let { Json.decodeFromJsonElement(it) },
        albumArtUrl = metadata["thumbnail"]?.let { Json.decodeFromJsonElement(it) },
        duration = try {
            Integer.parseInt(
                metadata["duration"]
                    .toString()
                    .removeSurrounding("\"")
                    .substringBefore(".")
            )
        } catch (e: Exception) {
            Loggers.DL.error(e)
            Loggers.DL.error(metadata["duration"])
            0
        },
        blacklisted = false,
        obsolete = false
    )

    /**
     * Permet de télécharger des metadatas depuis une requête textuelle.
     * Est implémenté ssi [queryRegex] est non null.
     * @param[request] Une URL vers une musique ou une playlist.
     * @return Une [List] de [TrackData], correspondant à la requête.
     */
    open fun downloadMultiple(request: String): List<TrackData> {
        throw NotImplementedError()
    }

    /**
     * Les arguments donnés à youtube-dl.
     */
    protected open val youtubeArgs: Map<String, String> = mapOf()

    /**
     * Un regex permettant de reconnaître une query.
     */
    open val queryRegex: Regex? = null

    private val tmpDir = File(config.data.TMP_PATH)

    /**
     * Exécute une recherche via youtube-dl. Une liste des métadonnées en Json.
     * @param[request] Une [List]<[JsonObject]> correspondant aux métadonnées de la requête.
     */
    protected fun searchYoutubeDL(request: String): List<JsonObject> {
        Loggers.DL.info("Searching for request using youtube-dlp")
        val wholeRequest = listOf("yt-dlp", "--id", "--write-info-json", "--skip-download") +
                youtubeArgs.map { (key, value) ->
                    """--$key${if (value.isNotBlank()) " $value" else ""}"""
                } +
                listOf(request)
        Loggers.DL.info(wholeRequest.reduce { a, b -> "$a $b" })
        val randomValue = (0..Int.MAX_VALUE).random()
        val workingDir = tmpDir.resolve(randomValue.toString())
        workingDir.mkdirs()
        wholeRequest.runCommand(workingDir, logger = Loggers.DL)

        return workingDir
            .listFiles { _, s -> s.endsWith(".info.json") }
            ?.sortedBy { it.lastModified() }
            ?.map { Json.decodeFromString<JsonObject>(it.readText(Charsets.UTF_8)) }
            ?.filter { Json.decodeFromJsonElement<String>(it["_type"]!!) != "playlist" }
            .also { workingDir.deleteRecursively() }  // cleaning before return
            ?: listOf()
    }
}