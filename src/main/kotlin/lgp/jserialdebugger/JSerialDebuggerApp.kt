package lgp.jserialdebugger

import javafx.stage.Stage
import tornadofx.*


class JSerialDebuggerApp : App(MainScreen::class) {}

fun main(args: Array<String>) {
    launch<JSerialDebuggerApp>(args)
}