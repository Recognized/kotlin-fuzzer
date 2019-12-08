package view

import Fuzzer
import com.github.recognized.App
import com.github.recognized.horizontal
import com.github.recognized.service.Statistics
import com.github.recognized.spring
import contrib.ringui.header.ringHeader
import kotlinx.coroutines.*
import kotlinx.css.flex
import kotlinx.css.padding
import kotlinx.css.properties.LineHeight
import kotlinx.css.px
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.*
import styled.StyleSheet
import styled.css
import kotlin.browser.window
import kotlin.coroutines.CoroutineContext

private object ApplicationStyles : StyleSheet("ApplicationStyles", isStatic = true) {
    val wrapper by css {
        padding(32.px, 16.px)
    }

    val post by css {
        marginBottom = 32.px
    }
}

class ApplicationState(val stat: Statistics) : RState
class ApplicationProps : RProps

class ApplicationComponent : RComponent<ApplicationProps, ApplicationState>(), CoroutineScope {
    init {
        state = ApplicationState(Statistics(0, 0, 0.0))
    }

    override fun componentDidMount() {
        updateLoop()
    }

    override fun componentWillUnmount() {
        coroutineContext.cancel()
    }

    override val coroutineContext: CoroutineContext = Job()

    private fun updateLoop() {
        launch {
            while (true) {
                setState(ApplicationState(App.client.Fuzzer.stat()))
                delay(1000)
            }
        }
    }

    override fun RBuilder.render() {
        ringHeader {
            horizontal {
                css {
                    flex(1.0)
                    padding(horizontal = 16.px)
                    lineHeight = LineHeight("14px")
                }
                h3 {
                    +"Kotlin fuzzer"
                }

                spring()

                table {
                    thead {
                        td {
                            tr {
                                +"Uptime"
                            }
                            tr {
                                +"State"
                            }
                        }
                    }
                    tbody {
                        td {
                            tr {
                                +"00:00:00"
                            }
                            tr {
                                +"Paused"
                            }
                        }
                    }
                }
            }
        }
    }
}
