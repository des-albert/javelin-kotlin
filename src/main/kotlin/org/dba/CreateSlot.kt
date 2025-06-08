package org.dba

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import org.dba.Javelin.Companion.selectedTreeItem
import org.dba.Javelin.Companion.partHashMap
import org.dba.Javelin.Companion.slotHashMap

class CreateSlot {
    lateinit var CountGroup: ToggleGroup
    lateinit var textFieldSlotName: TextField
    lateinit var textFieldSlotDescription: TextField
    lateinit var textFieldSlotCount: TextField
    lateinit var radioButtonMax: RadioButton
    lateinit var radioButtonExact: RadioButton
    lateinit var radioButtonUnlimited: RadioButton
    lateinit var buttonAddSlot: Button
    lateinit var buttonCreateSlotDone: Button
    lateinit var labelCreateSlotStatus: Label

    fun initialize() {

    }

    fun buttonAddSlotOnAction() {
        val name = textFieldSlotName.text
        val description = textFieldSlotDescription.text
        val type = CountGroup.selectedToggle.userData.toString()
        var count = 0

        if (name.isEmpty() || type.isEmpty() || textFieldSlotCount.text.isEmpty()) {
            labelCreateSlotStatus.text = "Fields cannot be null"
            labelCreateSlotStatus.style = "-fx-text-fill: status-error-color"
            return
        }
        if (slotHashMap.containsKey(name)) {
            labelCreateSlotStatus.text = "Duplicate slot name %s".format(name)
            labelCreateSlotStatus.style = "-fx-text-fill: status-error-color"
            return
        }
        if (radioButtonUnlimited.isSelected()) {
            count = 0
        } else {
            count = textFieldSlotCount.text.toInt()
        }
        val parts = ArrayList<String>()
        val newSlot = Slot(name, type, count, description, parts)

        slotHashMap[newSlot.name] = newSlot
        val part: Part = selectedTreeItem?.value as Part
        part.slots.add(newSlot.name)
        partHashMap[part.code] = part

        labelCreateSlotStatus.text = "New Slot %s - %s created".format(name, description)
        labelCreateSlotStatus.style = "-fx-text-fill: status-good-color"
    }


    fun buttonCreateSlotDoneOnAction() {
        val stage = buttonCreateSlotDone.scene.window as javafx.stage.Stage
        stage.close()
    }

}