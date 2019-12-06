package view

import contrib.ringui.header.ringHeader
import kotlinx.css.padding
import kotlinx.css.px
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import styled.StyleSheet

private object ApplicationStyles : StyleSheet("ApplicationStyles", isStatic = true) {
    val wrapper by css {
        padding(32.px, 16.px)
    }

    val post by css {
        marginBottom = 32.px
    }
}

class ApplicationState : RState
class ApplicationProps : RProps

class ApplicationComponent : RComponent<ApplicationProps, ApplicationState>() {
    init {
        state = ApplicationState()
    }

    override fun RBuilder.render() {
        ringHeader {
            +"Kotlin fuzzer"
        }
    }
}
