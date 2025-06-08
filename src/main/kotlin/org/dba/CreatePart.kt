package org.dba

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.dba.Javelin.Companion.selectedTreeItem
import org.dba.Javelin.Companion.partHashMap


class CreatePart {
    lateinit var textFieldPartCode: TextField
    lateinit var textFieldPartDescription: TextField
    lateinit var textFieldPartCat: TextField
    lateinit var textFieldPartHint: TextField
    lateinit var labelAddStatus: Label
    lateinit var buttonAddPart: Button
    lateinit var buttonAddPartDone: Button

    fun initialize() {
        val folder: Folder  = (selectedTreeItem?.value) as Folder
        textFieldPartCat.text = folder.category
    }

    fun buttonAddPartAction() {
        val code = textFieldPartCode.text
        val description = textFieldPartDescription.text
        val category = textFieldPartCat.text
        val hint = textFieldPartHint.text

        if (code.isEmpty() || description.isEmpty() || category.isEmpty() ) {
            labelAddStatus.text = "Fields cannot be NULL"
            labelAddStatus.style = "-fx-text-fill: status-error-color"
            return
        }
        val slots: ArrayList<String> = ArrayList()
        val newPart = Part(code, description, category, hint, slots)
        if ( partHashMap.containsKey(newPart.code)) {
            labelAddStatus.text = "Duplicate part code %s".format(code)
            labelAddStatus.style = "-fx-text-fill: status-error-color"
        } else {
            partHashMap[newPart.code] = newPart
            labelAddStatus.text = "New Part %s - %s created".format(code, description)
            labelAddStatus.style = "-fx-text-fill: status-good-color"

        }
    }

    fun buttonAddPartDoneAction() {

        val stage = buttonAddPartDone.scene.window as Stage
        stage.close()
    }

}