package com.github.recognized

import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.flex
import kotlinx.css.px
import kotlinx.html.DIV
import react.RBuilder
import styled.StyledDOMBuilder
import styled.css
import styled.styledDiv

fun RBuilder.vertical(builder: StyledDOMBuilder<DIV>.() -> Unit) {
    styledDiv {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        builder()
    }
}

fun RBuilder.horizontal(builder: StyledDOMBuilder<DIV>.() -> Unit) {
    styledDiv {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
        }

        builder()
    }
}

fun RBuilder.spring() {
    vertical {
        css {
            flex(1.0, 1.0, 0.0.px)
        }
    }
}