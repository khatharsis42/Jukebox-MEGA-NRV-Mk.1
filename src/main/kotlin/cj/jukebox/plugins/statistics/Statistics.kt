package cj.jukebox.plugins.statistics

import cj.jukebox.database.*
import cj.jukebox.utils.getParam
import cj.jukebox.utils.getUserSession

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Module de statistiques du jukebox.
 * Gère les statistiques globales ou spécifiques à un·e [User], une [Track]
 * ainsi que l'historique des dernières [Track].
 * @author Ukabi
 */
fun Application.statistics() {
    routing {
        authenticate("auth-session") {
            route("/statistics") {
                get {
                    val user = call.getUserSession()!!
                    call.respondHtmlTemplate(GlobalStatistics(user)) {}
                }

                get("/user/{username}") {
                    val username = call.getParam("username")
                    // TODO: proper failure redirection
                    val lookedUpUser = User.findUser(username) ?: return@get call.respondText("Invalid username")

                    val user = call.getUserSession()!!
                    call.respondHtmlTemplate(UserStatistics(user, lookedUpUser)) {}
                }

                get("/track/{track}") {
                    val trackId = call.getParam("track").toInt()
                    // TODO: proper failure redirection
                    val track = Track.importFromId(trackId) ?: return@get call.respondText("Invalid track !")

                    val user = call.getUserSession()!!
                    call.respondHtmlTemplate(TrackStatistics(user, track)) {}
                }
            }

            route("/history") {
                get("/{count}") {
                    val user = call.getUserSession()!!

                    val count = call.getParam("count").toInt()
                    call.respondHtmlTemplate(History(user, count)) {}
                }
                get {
                    call.respondRedirect("/history/50")
                }
            }
        }
    }
}

fun giveTestArray() = arrayOf(
    arrayOf("Name 1", "Name 2").toList(),
    arrayOf("Test1", "1").toList(),
    arrayOf("Test2", "6").toList(),
    arrayOf("Test3", "9").toList()
).toList()