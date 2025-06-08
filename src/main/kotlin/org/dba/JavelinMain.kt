package org.dba

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class JavelinMain : Application() {
    override fun start(stage: Stage) {
        val fxmlLoader = FXMLLoader(JavelinMain::class.java.getResource("javelin.fxml"))
        val scene = Scene(fxmlLoader.load(), 1280.0, 850.0)
        stage.title = "Javelin"
        stage.scene = scene
        stage.show()
    }
}

fun main() {
    Application.launch(JavelinMain::class.java)
}