package cj.jukebox.config

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

const val nb0to255 = """(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"""
val regIP = Regex("""$nb0to255\.$nb0to255\.$nb0to255\.$nb0to255""")

@Serializable
data class ConfigData(
    @EncodeDefault val APP_NAME: String = "Jukebox",
    @EncodeDefault val DEBUG: Boolean = true,

    @EncodeDefault val LISTEN_ADDRESS: String = "0.0.0.0",
    @EncodeDefault val LISTEN_PORT: Int = 8080,

    @EncodeDefault val YT_KEYS: ArrayList<String> = arrayListOf(),
    @EncodeDefault val AMIXER_CHANNEL: String = "Master",

    @EncodeDefault val DATABASE_PATH: String = "src/main/resources/jukebox.sqlite3",
    @EncodeDefault val TMP_PATH: String = "src/backend/tmp/",

    @EncodeDefault val SECRET_ENCRYPT_KEY: String = "6b6287991d47e783e3a261cca1a0a1b9",
    @EncodeDefault val SECRET_SIGN_KEY: String = "9cb8d9913aebc815cb58562aa479",
) {
    init {
        require(this.APP_NAME.isNotEmpty()) { "App name can't be empty" }
        require(this.LISTEN_ADDRESS.matches(regIP)) { "Listen address doesn't look like an IP address" }
        require(this.LISTEN_PORT in (0..65535)) { "Invalid port" }
        require(this.DATABASE_PATH.endsWith(".sqlite3")) { "Requiring a sqlite3 reference for database file" }
        // TODO: check if file paths (database, tmp) are valid paths

        if (this.DEBUG && this.YT_KEYS.isEmpty()) println("WARNING: no YT key registered")
    }
}