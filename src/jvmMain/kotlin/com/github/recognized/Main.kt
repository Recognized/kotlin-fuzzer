@file:JvmName("Main")

package com.github.recognized

import com.github.recognized.rpc.serve
import com.github.recognized.runtime.logger
import com.github.recognized.service.Fuzzer
import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.html.*

private val log = logger("Main")

fun main() {
    val server = embeddedServer(Netty, port = 8081) {
        log.info { "Server is starting" }

        routing {
            get("/") {
                call.respondHtml {
                    head {
                        meta {
                            charset = "utf-8"
                        }
                        title {
                            +"Kotlin fuzzer"
                        }
                    }
                    body {
                        div {
                            id = "react-app"
                            +"Loading..."
                        }
                        script(src = "/main.bundle.js") {
                        }
                    }
                }
            }

            static("/") {
                files("build/bundle")
            }

            route("/api") {
                serve(Fuzzer::class)
            }

            get("code") {
                val id = context.request.queryParameters["id"]
                val sample = Server.generation().find {
                    it.id == id
                }?.tree?.text
                if (sample == null) {
                    context.respond(HttpStatusCode.NotFound)
                } else {
                    context.respondText {
                        sample
                    }
                }
            }
        }
    }
    server.start(true)
}
