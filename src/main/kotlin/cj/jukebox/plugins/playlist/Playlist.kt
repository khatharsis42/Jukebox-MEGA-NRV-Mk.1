package cj.jukebox.plugins.playlist

import cj.jukebox.database.*
import cj.jukebox.utils.UserSession

typealias Playlist = MutableList<Log>

enum class Direction { UP, DOWN }

/**
 * Échange les [Track] étant aux emplacements [from] et [to].
 * @author Ukabi
 */
fun Playlist.move(from: Int, to: Int) = apply {
    this[from] = this[to].also { this[to] = this[from] }
}

/**
 * Échange l'élément [from] de la [Playlist] avec l'élément au-dessus ou en dessous,
 * selon la valeur de [direction].
 * Ne procède pas à l'échange si le bord de la [Playlist] est dépassé.
 * @author Ukabi
 */
fun Playlist.move(from: Int, direction: Direction) =
    when (direction) {
        Direction.UP -> from - 1
        Direction.DOWN -> from + 1
    }.let { if ((it !in arrayOf(-1, size))) move(from, it) }

/**
 * Retrouve [log] dans la [Playlist], puis l'échange avec l'élément au-dessus ou en dessous,
 * selon la valeur de [direction]. Le cas échéant, ne fait rien.
 * Ne procède pas à l'échange si le bord de la [Playlist] est dépassé.
 * @author Ukabi
 */
fun Playlist.move(log: Log, direction: Direction) =
    indexOf(log).let { if (it != -1) move(it, direction) }

/**
 * Vérifie si la [track] fournie peut être jouée, puis l'ajoute à la [Playlist].
 * Garde aussi la trace de l'[user] l'ayant requêtée.
 * Créé aussi une nouvelle occurrence dans la table [Logs].
 * @author Ukabi
 */
fun Playlist.addIfPossible(track: Track, user: User): Boolean =
    !track.blacklisted && !track.obsolete && add(Log.createLog(track, user))

/**
 * Vérifie si la [Track] correspondant à la [trackId] fournie peut être jouée, puis l'ajoute à la [Playlist].
 * Garde aussi la trace de l'[user] l'ayant requêtée.
 * Créé aussi une nouvelle occurrence dans la table [Logs].
 * @author Ukabi
 */
fun Playlist.addIfPossible(trackId: Int, user: User): Boolean =
    Track.importFromId(trackId).let { (it != null) && addIfPossible(it, user) }

/**
 * Vérifie si la [Track] correspondant à la [trackId] fournie peut être jouée, puis l'ajoute à la [Playlist].
 * Garde aussi la trace de l'[user] l'ayant requêtée.
 * Créé aussi une nouvelle occurrence dans la table [Logs].
 * @author Ukabi
 */
fun Playlist.addIfPossible(trackId: Int, user: UserSession): Boolean = addIfPossible(trackId, user.toUser())

/**
 * Vérifie si la [track] fournie peut être jouée, puis l'ajoute à la [Playlist].
 * Garde aussi la trace de l'[user] l'ayant requêtée.
 * Créé aussi une nouvelle occurrence dans la table [Logs].
 * @author Ukabi
 */
fun Playlist.addIfPossible(track: Track, user: UserSession): Boolean = addIfPossible(track, user.toUser())

/**
 * Vérifie si la [track] fournie fait partie de la [Playlist], puis la supprime.
 * Efface aussi l'occurrence de [Log] précédemment créée si [delete] est fourni.
 * @author Ukabi
 */
fun Playlist.removeIfPossible(track: Track, delete: Boolean = true): Boolean =
    Log
        .getTrackLogs(track)
        .firstOrNull { it in this }
        ?.let { if (delete) it.delete(); it }
        .let { (it != null) && remove(it) }

/**
 * Vérifie si la [Track] correspondant à la [trackId] fournie fait partie de la [Playlist], puis la supprime.
 * Efface aussi l'occurrence de [Log] précédemment créée si [delete] est fourni.
 * @author Ukabi
 */
fun Playlist.removeIfPossible(trackId: Int, delete: Boolean = false): Boolean =
    Track.importFromId(trackId).let { (it != null) && removeIfPossible(it, delete) }

/**
 * Renvoie la somme des durées (en secondes) de chacune des [Track] de la [Playlist].
 * @author Ukabi
 */
fun Playlist.duration(): Int = mapNotNull { it.trackId.duration }.reduce { acc, i -> acc + i }

/**
 * Renvoie une [List] de [n] des précédents [Log] dont la [Track] peut être jouée.
 * @author Ukabi
 */
fun suggest(n: Int = 5): List<Log> = Log.getRandom(n)
