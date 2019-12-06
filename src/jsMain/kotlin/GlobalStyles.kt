import kotlinext.js.invoke
import kotlinx.css.*
import kotlinx.css.properties.lh
import styled.StyledComponents

object GlobalStyles {
    fun inject() {
        val styles = CSSBuilder(allowClasses = false).apply {
            body {
                margin(0.px)
                padding(0.px)

                fontSize = 13.px
                fontFamily = "-system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Oxygen, Ubuntu, Cantarell, Droid Sans, Helvetica Neue, BlinkMacSystemFont, Segoe UI, Roboto, Oxygen, Ubuntu, Cantarell, Droid Sans, Helvetica Neue, Arial, sans-serif"

                lineHeight = 20.px.lh
            }

        }

        StyledComponents.injectGlobal(styles.toString())
    }
}