package org.dba

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.stream.JsonReader
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.stage.Stage
import javafx.util.Callback
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import kotlin.collections.HashMap


class Javelin {

    lateinit var labelStatus: Label
    lateinit var buttonQuit: Button
    lateinit var treeViewPart: TreeView<Any>
    lateinit var tabPaneMain: TabPane
    lateinit var labelJDK: Label

    val partsFile = "parts.json"
    val slotsFile = "slots.json"


    val catHashMap: HashMap<String, TreeItem<Any>> = HashMap()

    val folderContext: ContextMenu = ContextMenu()
    val partContext: ContextMenu = ContextMenu()

    companion object {
        var selectedTreeItem: TreeItem<Any>? = null
        val partHashMap: HashMap<String, Part> = HashMap()
        val slotHashMap: HashMap<String, Slot> = HashMap()
    }

    private val dfPart: DataFormat = DataFormat("org.dba.javelin.Part")
    private val dfSlot: DataFormat = DataFormat("org.dba.javelin.Slot")
    private val dfParentPart: DataFormat = DataFormat("org.dba.javelin.ParentPart")
    private val dfParentSlot: DataFormat = DataFormat("org.dba.javelin.ParentSlot")

    private lateinit var partTreeRootItem: TreeItem<Any>

    val logger: Logger = LoggerFactory.getLogger("Javelin")

    @FXML
    fun initialize() {
        labelJDK.text = "Java SDK %s".format(System.getProperty("java.version"))

        definePartsTreeView()
        addPartsSlots()
        loadPartTree()
        addContext()

    }

    @FXML
    fun partDragDetected(event: MouseEvent) {

        val selectedItem = treeViewPart.selectionModel.selectedItem
        if (selectedItem.value != null) {
            val dataToDrag: Any = selectedItem.value
            val db: Dragboard = treeViewPart.startDragAndDrop(TransferMode.COPY)
            val content = ClipboardContent()

            when (dataToDrag) {

                // Drag a Part

                is Part -> {
                    content.put(dfPart, dataToDrag)
                    selectedItem.parent?.value?.let { parent ->
                        if (parent !is Folder) {
                            content.put(dfParentSlot, parent)
                        }
                    }
                }

                // Drag a Slot

                is Slot -> {
                    content.put(dfSlot, dataToDrag)
                    selectedItem.parent?.value?.let { parentObj ->
                        if (parentObj is Part) {
                            content.put(dfParentPart, parentObj)
                        }
                    }
                }
            }
            db.setContent(content)
        }
        event.consume()

    }

    private fun definePartsTreeView() {
        val partRoot = Folder("Root Parts Folder")
        partTreeRootItem = TreeItem<Any>(partRoot)
        treeViewPart.root = partTreeRootItem
        treeViewPart.isShowRoot = false

        // Cell Factory for Parts treeView

        treeViewPart.cellFactory = Callback { _ ->
            val cell = object : TreeCell<Any>() {
                override fun updateItem(item: Any?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        graphic = null
                        tooltip = null

                    } else {
                        when (item) {
                            is Folder -> {
                                val folder: Folder = item
                                text = folder.category
                                style = "-fx-text-fill: folder-leaf-color"
                                contextMenu = folderContext
                            }

                            is Part -> {
                                val part: Part = item
                                text = part.description
                                graphic = treeItem.graphic
                                val parent = this.treeItem.parent.value
                                style = if (parent is Folder)
                                    "-fx-text-fill: part-leaf-color"
                                else
                                    "-fx-text-fill: part-link-color"
                                tooltip = Tooltip(part.code + " - " + part.hint)
                                contextMenu = partContext
                            }

                            is Slot -> {
                                val slot: Slot = item
                                text = slot.name
                                style = "-fx-text-fill: slot-leaf-color"
                                tooltip = Tooltip(slot.type + "-" + slot.quantity + " - " + slot.description)
                            }

                        }
                    }
                }
            }
            cell.setOnDragOver { event ->
                event.acceptTransferModes(TransferMode.COPY)
                event.consume()
            }
            cell.setOnDragDropped { event ->
                val target = cell.treeItem.value
                when (target) {
                    is Slot -> {

                        // Add part to slot

                        if (event.dragboard.hasContent(dfPart)) {
                            val part: Part = event.dragboard.getContent(dfPart) as Part
                            val code = part.code

                            val slot = target

                            if ( ! slot.parts.contains(code)) {
                                slot.parts.add(code)
                                slotHashMap[slot.name] = slot
                                loadPartTree()
                                labelStatus.text = "part $code added to slot ${slot.name}"
                                labelStatus.styleClass.clear()
                                labelStatus.styleClass.add("label-success")

                            }
                        }
                    }
                    is Part -> {
                        if (event.dragboard.hasContent(dfSlot)) {
                            val slot: Slot = event.dragboard.getContent(dfSlot) as Slot
                            val part = target
                            if (!part.slots.contains(slot.name)) {
                                part.slots.add(slot.name)
                                partHashMap[part.code] = part
                                loadPartTree()
                                labelStatus.text = "slot ${slot.name} added to part ${part.code}"
                                labelStatus.styleClass.clear()
                                labelStatus.styleClass.add("label-success")
                            }

                        }
                    }
                }
                event.isDropCompleted = true
                event.consume()


            }
            return@Callback cell

        }
    }

    @FXML
    fun buttonQuitOnAction() {
        saveParts()
        saveSlots()
        val stage: Stage = buttonQuit.scene.window as Stage
        stage.close()
    }


    private fun addPartsSlots() {
        val gson = Gson()

        try {
            var fr = FileReader(partsFile)
            var jsonReader = JsonReader(fr)
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val part: Part = gson.fromJson(jsonReader, Part::class.java)
                partHashMap[part.code] = part
                // logger.info("addParts - hash %d %d part %s".format(part.id, part.code.hashCode(), part.description))

            }
            jsonReader.endArray()
            jsonReader.close()
            fr.close()

            fr = FileReader(slotsFile)
            jsonReader = JsonReader(fr)
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val slot: Slot = gson.fromJson(jsonReader, Slot::class.java)
                slotHashMap[slot.name] = slot
                // logger.info("addSlots - hash %d %d part %s".format(slot.id, slot.name.hashCode(), slot.description))
            }
            jsonReader.endArray()
            jsonReader.close()
            fr.close()


        } catch (e: IOException) {
            logger.error("addParts - {}", e.message)
        }

    }

    private fun loadPartTree() {

        partTreeRootItem.children.clear()
        catHashMap.clear()
        var treeItem: TreeItem<Any>
        var slot: Slot
        var category: String

        // Add Parts to partTreeView

        for (part in partHashMap.values) {
            category = part.category
            if (!catHashMap.containsKey(category)) {
                val folder = Folder(category)
                treeItem = TreeItem(folder)
                catHashMap[category] = treeItem
                partTreeRootItem.children.add(treeItem)
            } else {
                treeItem = catHashMap[category]!!
            }

            val leafItem = newTreeItem(part)
            treeItem.children.add(leafItem)

            // Add Slots to Part

            if (!part.slots.isEmpty()) {
                for (code: String in part.slots) {
                    if (slotHashMap.containsKey(code)) {
                        slot = slotHashMap[code]!!
                        val slotItem: TreeItem<Any> = newTreeItem(slot)
                        leafItem.children.add(slotItem)

                        // Add Parts to each Slot

                        if (!slot.parts.isEmpty()) {
                            for (code: String in slot.parts) {
                                val slotPart: Part = partHashMap[code]!!
                                val slotPartItem: TreeItem<Any> = newTreeItem(slotPart)
                                slotItem.children.add(slotPartItem)

                            }
                        }
                    }
                }
            }
        }
    }

    private fun newTreeItem(value: Any): TreeItem<Any> {
        var treeItem: TreeItem<Any>
        when (value) {
            is Part -> {
                val part: Part = value
                val image = part.category
                val iconPath = "/img/$image.png"
                val iconStream = javaClass.getResourceAsStream(iconPath)
                if (iconStream != null) {
                    val icon = Image(iconStream)
                    treeItem = TreeItem(part, ImageView(icon))
                } else {
                    treeItem = TreeItem(part)
                }
            }

            else ->
                treeItem = TreeItem<Any>(value)

        }

        return treeItem
    }

    // Context Menus

    private fun addContext() {

        // Create new part

        val newPart = MenuItem("create part")
        newPart.setOnAction {
            selectedTreeItem = treeViewPart.selectionModel.selectedItem
            try {
                val fxmlLoader = FXMLLoader(javaClass.getResource("createPart.fxml"))
                val partForm: Parent = fxmlLoader.load()
                val partStage = Stage()
                partStage.title = "Create New Part"
                partStage.setOnHiding {
                    loadPartTree()
                }
                partStage.scene = Scene(partForm)
                partStage.show()


            } catch (e: IOException) {
                labelStatus.text = e.message
                labelStatus.styleClass.clear()
                labelStatus.styleClass.add("label-failure")


            }
        }
        val newSlot = MenuItem("create slot")
        newSlot.setOnAction {
            selectedTreeItem = treeViewPart.selectionModel.selectedItem
            try {
                val fxmlLoader = FXMLLoader(javaClass.getResource("createSlot.fxml"))
                val slotForm: Parent = fxmlLoader.load()
                val slotStage = Stage()
                slotStage.title = "Create New Slot"
                slotStage.setOnHiding {
                    loadPartTree()
                }
                slotStage.scene = Scene(slotForm)
                slotStage.show()
            } catch (e: IOException) {
                labelStatus.text = e.message
                labelStatus.styleClass.clear()
                labelStatus.styleClass.add("label-failure")

            }
        }

        folderContext.items.add(newPart)
        partContext.items.add(newSlot)
    }


    private fun saveParts() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val file = File(partsFile)
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    labelStatus.text = "file create error $partsFile"
                    labelStatus.styleClass.clear()
                    labelStatus.styleClass.add("label-failure")
                }
            }
            val fw = FileWriter(partsFile)
            val jsonArray = JsonArray()
            for (part in partHashMap.values) {
                jsonArray.add(gson.toJsonTree(part))
            }
            gson.toJson(jsonArray, fw)
            fw.close()


        } catch (e: IOException) {
            logger.error("saveParts - {}", e.message)
        }
    }

    private fun saveSlots() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val file = File(slotsFile)
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    labelStatus.text = "file create error $slotsFile"
                    labelStatus.styleClass.clear()
                    labelStatus.styleClass.add("label-failure")
                }
            }
            val fw = FileWriter(slotsFile)
            val jsonArray = JsonArray()
            for (slot in slotHashMap.values) {
                jsonArray.add(gson.toJsonTree(slot))
            }
            gson.toJson(jsonArray, fw)
            fw.close()

        } catch (e: IOException) {
            logger.error("saveSlots - {}", e.message)
        }
    }

}