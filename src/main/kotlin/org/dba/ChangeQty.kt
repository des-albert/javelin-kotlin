package org.dba

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TreeItem
import javafx.stage.Stage
import org.dba.Javelin.Companion.selectedTreeItem

class ChangeQty {
    lateinit var buttonChangeQtyDone: Button
    lateinit var buttonChangeQty: Button
    lateinit var textFieldNewQuantity: TextField
    lateinit var labelCurrentQuantity: Label
    lateinit var labelPartCode: Label
    lateinit var labelStatus: Label

    lateinit var part: BuildPart
    var prevTotal: Int = 0
    var prevQty: Int = 0


    fun initialize() {
        part = (selectedTreeItem?.value) as BuildPart
        prevQty = part.buildCount
        prevTotal = part.totalCount
        labelCurrentQuantity.text = prevQty.toString()
        labelPartCode.text = part.code

    }

    fun buttonChangeQtyAction() {
        val quantity = textFieldNewQuantity.text
        if (quantity.isEmpty()) {
            labelStatus.text = "Quantity cannot be null"
            labelStatus.style = "-fx-text-fill: status-error-color"
            return
        }
        val nextQty = textFieldNewQuantity.text.toInt()
        part.buildCount = nextQty

        updateTree(selectedTreeItem, nextQty, prevQty)

    }

    fun updateTree(item: TreeItem<Any>?,  next: Int, prev: Int) {
        updateTotal(item, next, prev)

        for (child in item?.children!!) {
            if (child.children.isEmpty())
                updateTotal(child, next, prev)
            else
                updateTree(child, next, prev)
        }

    }

    fun updateTotal(item: TreeItem<Any>?, after: Int, before: Int) {
        if (item?.value is BuildPart) {
            val part: BuildPart = item.value as BuildPart
            val beforeTotal: Int = part.totalCount
            part.totalCount = beforeTotal * after / before
        }
    }


    fun buttonChangeQtyDoneAction() {
        val stage: Stage = textFieldNewQuantity.scene.window as Stage
        stage.close()
    }

}