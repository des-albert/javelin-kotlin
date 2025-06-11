package org.dba

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Callback
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.Optional
import kotlin.collections.HashMap

data class Params(
    val product: String
)

class Javelin {

    lateinit var labelFileStatus: Label
    lateinit var toggleBuild: ToggleButton
    lateinit var togglePart: ToggleButton
    lateinit var imageViewTrash: ImageView
    lateinit var labelJavaFX: Label
    lateinit var labelStatus: Label
    lateinit var buttonQuit: Button
    lateinit var treeViewPart: TreeView<Any>
    lateinit var treeViewBuild: TreeView<Any>
    lateinit var tabPaneMain: TabPane
    lateinit var labelJDK: Label

    lateinit var partsFile: String
    lateinit var slotsFile: String

    val folderContext: ContextMenu = ContextMenu()
    val partContext: ContextMenu = ContextMenu()
    val quantityContext: ContextMenu = ContextMenu()

    val buildHashMap: HashMap<String, TreeItem<Any>> = HashMap()
    val catHashMap: HashMap<String, TreeItem<Any>> = HashMap()

    companion object {
        var selectedTreeItem: TreeItem<Any>? = null
        val partHashMap: HashMap<String, Part> = HashMap()
        val slotHashMap: HashMap<String, Slot> = HashMap()
    }

    private val dfPart: DataFormat = DataFormat("org.dba.javelin.Part")
    private val dfSlot: DataFormat = DataFormat("org.dba.javelin.Slot")
    private val dfParentPart: DataFormat = DataFormat("org.dba.javelin.ParentPart")
    private val dfParentSlot: DataFormat = DataFormat("org.dba.javelin.ParentSlot")
    private val dfBuild: DataFormat = DataFormat("org.dba.javelin.BuildPart")

    private lateinit var partTreeRootItem: TreeItem<Any>
    private lateinit var buildTreeRootItem: TreeItem<Any>

    val logger: Logger = LoggerFactory.getLogger("Javelin")

    @FXML
    fun initialize() {
        labelJDK.text = "Java SDK %s".format(System.getProperty("java.version"))
        labelJavaFX.text = "JavaFX version %s".format(System.getProperty("javafx.runtime.version"))

        definePartsTreeView()
        defineTrash()
        defineBuildTreeView()
        addPartsSlots()
        loadPartTree()
        addContext()
    }

    @FXML
    fun partDragDetected(event: MouseEvent) {

        val selectedItem = treeViewPart.selectionModel.selectedItem
        if (event.isDragDetect) {
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
                        selectedItem.parent?.value?.let { parent ->
                            if (parent is Part) {
                                content.put(dfParentPart, parent)
                            }
                        }
                    }
                }
                db.setContent(content)
            }
        }
        event.consume()
    }

    @FXML
    fun buildDragDetected(event: MouseEvent) {
        val selectedItem = treeViewBuild.selectionModel.selectedItem
        if (event.isDragDetect) {
            if (selectedItem.value != null) {
                val dataToDrag: Any = selectedItem.value
                val db: Dragboard = treeViewBuild.startDragAndDrop(TransferMode.COPY)
                val content = ClipboardContent()

                when (dataToDrag) {
                    is BuildPart -> {
                        content.put(dfBuild, dataToDrag)
                        selectedItem.parent?.value?.let { parent ->
                            if (parent !is Folder) {
                                content.put(dfParentSlot, parent)
                            }
                        }
                    }

                    is BuildSlot -> {
                        labelStatus.text = "build slot cannot be removed"
                        labelStatus.styleClass.clear()
                        labelStatus.styleClass.add("label-failure")
                    }
                }
                db.setContent(content)
            }
        }
    }

    // Initialize Parts treeView

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
                            if (!slot.parts.contains(code)) {
                                slot.parts.add(code)
                                slotHashMap[slot.name] = slot
                                loadPartTree()
                                labelStatus.text = "part $code added to slot ${slot.name}"
                                labelStatus.styleClass.clear()
                                labelStatus.styleClass.add("label-success")
                            }
                        }
                    }

                    // Add slot to part

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

    // Trash handler

    fun defineTrash() {

        imageViewTrash.setOnDragOver { event ->
            event.acceptTransferModes(TransferMode.COPY)
            event.consume()
        }

        imageViewTrash.setOnDragEntered { event ->
            val iconPath = "/img/glass trash.png"
            val icon = Image(javaClass.getResourceAsStream(iconPath))
            imageViewTrash.image = icon
            event.consume()
        }

        imageViewTrash.setOnDragExited { event ->
            val iconPath = "/img/trash.png"
            val icon = Image(javaClass.getResourceAsStream(iconPath))
            imageViewTrash.image = icon
            event.consume()
        }

        imageViewTrash.setOnDragDropped { event ->

            if (event.dragboard.hasContent(dfPart)) {
                if (event.dragboard.hasContent(dfParentSlot)) {

                    // Remove Part from Slot in Part TreeView

                    val part: Part = event.dragboard.getContent(dfPart) as Part
                    val slot: Slot = event.dragboard.getContent(dfParentSlot) as Slot
                    val slotParts = slot.parts
                    val code = part.code
                    if (slotParts.contains(code)) {
                        slotParts.remove(code)
                        slotHashMap[slot.name] = slot
                        loadPartTree()
                    }
                    labelStatus.text = "part $code removed from slot ${slot.name}"
                    labelStatus.styleClass.clear()
                    labelStatus.styleClass.add("label-success")

                    // Delete Part

                } else {
                    val part: Part = event.dragboard.getContent(dfPart) as Part
                    val code = part.code
                    partHashMap.remove(code)

                    // Remove part from containing slots

                    for (slot in slotHashMap.values) {
                        if (slot.parts.contains(code)) {
                            val slotParts = slot.parts
                            slotParts.remove(code)
                            slotHashMap[slot.name] = slot
                        }
                    }
                    labelStatus.text = "part $code removed"
                    labelStatus.styleClass.clear()
                    labelStatus.styleClass.add("label-success")

                    loadPartTree()
                }
            } else if (event.dragboard.hasContent(dfSlot)) {

                // Remove Slot from Part

                val slot: Slot = event.dragboard.getContent(dfSlot) as Slot
                val part: Part = event.dragboard.getContent(dfParentPart) as Part
                val partSlots = part.slots
                partSlots.remove(slot.name)
                loadPartTree()


                // Remove Build Part

            } else if (event.dragboard.hasContent(dfBuild)) {

                val part: BuildPart = event.dragboard.getContent(dfBuild) as BuildPart
                val slot: BuildSlot = event.dragboard.getContent(dfParentSlot) as BuildSlot
                val parentItem = buildHashMap[slot.name]
                val partItem = buildHashMap[part.code]
                parentItem?.children?.remove(partItem)
                buildHashMap.remove(part.code)

                slot.content = 0
                parentItem?.value = slot
                treeViewBuild.refresh()

                labelStatus.text = "${slot.content} parts removed from slot ${slot.name}"
                labelStatus.styleClass.clear()
                labelStatus.styleClass.add("label-success")
            }
            event.isDropCompleted
            event.consume()
        }
    }

    // Build treeView

    fun defineBuildTreeView() {
        val parts = ArrayList<String>()
        val buildSlot = Slot("build", "U", 0, "", parts)
        slotHashMap["build"] = buildSlot
        val slots = ArrayList<String>()
        slots.add("build")
        val buildPart = BuildPart("build", "build", "", slots, false)

        buildTreeRootItem = TreeItem<Any>(buildPart)
        treeViewBuild.root = buildTreeRootItem
        treeViewBuild.isShowRoot = true

        treeViewBuild.cellFactory = Callback { _ ->
            val cell = object : TreeCell<Any>() {
                override fun updateItem(item: Any?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        graphic = null
                        tooltip = null

                    } else {
                        when (item) {
                            is BuildPart -> {
                                val part: BuildPart = item
                                tooltip = Tooltip(part.totalCount.toString() + " x " + part.code)
                                text = if (part.buildCount > 1) {
                                    "${part.buildCount} x ${part.description}"
                                } else {
                                    part.description
                                }
                                style = "-fx-text-fill: part-leaf-color"
                                graphic = treeItem.graphic
                                contextMenu = quantityContext
                            }

                            is BuildSlot -> {
                                val slot: BuildSlot = item
                                text = slot.name
                                style = "-fx-text-fill: slot-leaf-color"
                                val parts = slot.parts
                                var toolText = StringBuilder()
                                if (slot.type == "E")
                                    toolText = StringBuilder("exact - " + slot.quantity)
                                else if (slot.type == "M")
                                    toolText = StringBuilder("max - " + slot.quantity)
                                toolText.append("current - ")
                                toolText.append(slot.content)
                                toolText.append("\n")
                                for (code in parts) {
                                    val slotPart = partHashMap[code]
                                    toolText.append(slotPart!!.description)
                                    toolText.append("\n")
                                }
                                tooltip = Tooltip(toolText.toString())
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
                val targetItem = cell.treeItem
                if (event.dragboard.hasContent(dfPart)) {
                    val part: Part = event.dragboard.getContent(dfPart) as Part
                    val target = targetItem.value

                    // Add Part to Build Base

                    when (target) {
                        is BuildPart -> {
                            val targetPart: BuildPart = target
                            var addQty = 0
                            val countDialog = TextInputDialog()
                            countDialog.headerText = "enter part quantity : "
                            val addCount: Optional<String> = countDialog.showAndWait()
                            if (addCount.isPresent)
                                addQty = addCount.get().toInt()
                            val addPart = BuildPart(part)
                            addPart.buildCount = addQty
                            addPart.totalCount = addQty
                            addPart.parent = targetPart.code
                            val partTreeItem = newTreeItem(addPart)
                            buildHashMap[part.code] = partTreeItem
                            buildTreeRootItem.children.add(partTreeItem)

                            if (!addPart.slots.isEmpty()) {
                                for (slotName: String in addPart.slots) {
                                    val slot = slotHashMap[slotName]
                                    val addSlot = BuildSlot(slot!!)
                                    addSlot.parent = addPart.code
                                    val slotTreeItem = newTreeItem(addSlot)
                                    buildHashMap[slotName] = slotTreeItem
                                    partTreeItem.children.add(slotTreeItem)
                                }
                                labelStatus.text = "$addQty ${addPart.code} parts added to base"
                                labelStatus.styleClass.clear()
                                labelStatus.styleClass.add("label-success")
                            }
                        }

                        // Add Part to Slot

                        is BuildSlot -> {
                            val targetSlot: BuildSlot = target
                            if (!targetSlot.parts.isEmpty()) {
                                val slotParts = targetSlot.parts
                                if (slotParts.contains(part.code)) {
                                    var addQty = 0
                                    val addPart = BuildPart(part)
                                    val currentQty = targetSlot.content
                                    val maxQty = targetSlot.quantity
                                    if (targetSlot.type == "U" || currentQty < maxQty) {
                                        if (targetSlot.type == "E") {
                                            addQty = targetSlot.quantity
                                        } else {
                                            val countDialog = TextInputDialog()
                                            val limitQty = maxQty - currentQty
                                            if (targetSlot.type == "U")
                                                countDialog.headerText = "enter part quantity : "
                                            else
                                                countDialog.headerText = "enter part quantity <= $limitQty : "
                                            val addCount: Optional<String> = countDialog.showAndWait()
                                            if (addCount.isPresent)
                                                addQty = addCount.get().toInt()
                                            if (targetSlot.type == "M" && addQty > limitQty)
                                                addQty = limitQty
                                        }

                                        addPart.buildCount = addQty
                                        val targetPart: BuildPart = targetItem.parent.value as BuildPart
                                        addPart.totalCount = targetPart.totalCount * addQty
                                        targetSlot.content = currentQty + addQty
                                        addPart.parent = targetSlot.name
                                        val partTreeItem = newTreeItem(addPart)
                                        buildHashMap[addPart.code] = partTreeRootItem
                                        targetItem.children.add(partTreeItem)

                                        // Add slots to new part

                                        if (!addPart.slots.isEmpty()) {
                                            for (slotName: String in addPart.slots) {
                                                val slot = slotHashMap[slotName]
                                                val addSlot = BuildSlot(slot!!)
                                                addSlot.parent = addPart.code
                                                val slotTreeItem = newTreeItem(addSlot)
                                                buildHashMap[slotName] = slotTreeItem
                                                partTreeItem.children.add(slotTreeItem)
                                            }

                                        }
                                        labelStatus.text = "$addQty parts added to slot ${targetSlot.name}"
                                        labelStatus.styleClass.clear()
                                        labelStatus.styleClass.add("label-success")
                                    }
                                }
                            }
                        }
                    }
                }
                event.isDropCompleted
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
            FileReader("settings.json").use { fr ->
                val jsonReader = JsonReader(fr)
                jsonReader.beginArray()

                while (jsonReader.hasNext()) {
                    val settings: Params = gson.fromJson(jsonReader, Params::class.java)
                    partsFile = "json/products/" + settings.product + "/parts.json"
                    slotsFile = "json/products/" + settings.product + "/slots.json"
                }

                jsonReader.endArray()
                jsonReader.close()
                fr.close()
            }

            FileReader(partsFile).use { fr ->
                val jsonReader = JsonReader(fr)
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val part: Part = gson.fromJson(jsonReader, Part::class.java)
                    partHashMap[part.code] = part
                    // logger.info("addParts - hash %d %d part %s".format(part.id, part.code.hashCode(), part.description))

                }
                jsonReader.endArray()
                jsonReader.close()
                fr.close()
            }

            FileReader(slotsFile).use { fr ->
                val jsonReader = JsonReader(fr)
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val slot: Slot = gson.fromJson(jsonReader, Slot::class.java)
                    slotHashMap[slot.name] = slot
                    // logger.info("addSlots - hash %d %d part %s".format(slot.id, slot.name.hashCode(), slot.description))
                }
                jsonReader.endArray()
                jsonReader.close()
                fr.close()
            }

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

    private fun loadBuildTree() {
        for (item in buildTreeRootItem.children) {
            item.isExpanded
        }
        treeViewBuild.refresh()
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

            is BuildPart -> {
                val buildPart: BuildPart = value
                val image = buildPart.category
                val iconPath = "/img/$image.png"
                val iconStream = javaClass.getResourceAsStream(iconPath)
                if (iconStream != null) {
                    val icon = Image(iconStream)
                    treeItem = TreeItem(buildPart, ImageView(icon))
                } else {
                    treeItem = TreeItem(buildPart)
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
                partStage.title = "Create a part"
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

        // Create new slot context menu

        val newSlot = MenuItem("create slot")
        newSlot.setOnAction {
            selectedTreeItem = treeViewPart.selectionModel.selectedItem
            try {
                val fxmlLoader = FXMLLoader(javaClass.getResource("createSlot.fxml"))
                val slotForm: Parent = fxmlLoader.load()
                val slotStage = Stage()
                slotStage.title = "Create a slot"
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

        // Create a context menu to change build quantity

        val partQty = MenuItem("change quantity ")
        partQty.setOnAction {
            selectedTreeItem = treeViewBuild.selectionModel.selectedItem
            try {
                val fxmlLoader = FXMLLoader(javaClass.getResource("changeQty.fxml"))
                val qtyForm: Parent = fxmlLoader.load()
                val qtyStage = Stage()

                qtyStage.setOnHiding {
                    loadBuildTree()
                }
                qtyStage.title = "Change part quantity"
                qtyStage.scene = Scene(qtyForm)
                qtyStage.show()

            } catch (e: IOException) {
                labelStatus.text = e.message
                labelStatus.styleClass.clear()
                labelStatus.styleClass.add("label-failure")
            }
        }

        folderContext.items.add(newPart)
        partContext.items.add(newSlot)
        quantityContext.items.add(partQty)
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

    fun buttonExportOnAction() {
        val exportHash: HashMap<String, Int> = HashMap()
        try {
            val fileChooser = FileChooser()
            fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("csv file (*.csv)", "*.csv"))
            fileChooser.title = "save config as csv file"
            val exportFile = fileChooser.showSaveDialog(tabPaneMain.scene.window)
            if (exportFile.exists()) {
                if (!exportFile.delete()) {
                    labelFileStatus.text = "file delete error $exportFile"
                    labelFileStatus.styleClass.clear()
                    labelFileStatus.styleClass.add("label-failure")
                }
            }
            if (exportFile.createNewFile()) {

                val fw = FileWriter(exportFile)
                val bw = BufferedWriter(fw)

                exportTree(buildTreeRootItem, exportHash)

                for (entry: MutableMap.MutableEntry<String, Int> in exportHash.entries) {
                    val part = buildHashMap[entry.key]?.value as BuildPart
                    val description = part.description
                    val od1 = part.od1
                    bw.write(entry.value.toString() + "," + entry.key + "," + description)
                    bw.newLine()
                    if (od1) {
                        val code = part.code.padEnd(13, ' ') + "0D1"
                        bw.write(entry.value.toString() + "," + code + "," + "Factory Integrated")
                        bw.newLine()
                    }
                }

                bw.close()
                fw.close()
            }
            labelFileStatus.text = "file exported to $exportFile"
            labelFileStatus.styleClass.clear()
            labelFileStatus.styleClass.add("label-success")

        } catch (e: IOException) {
            logger.error("export - {}", e.message)
        }
    }

    private fun exportTree(item: TreeItem<Any>, map: HashMap<String, Int>) {
        val value = item.value
        if (value is BuildPart) {
            val part: BuildPart = value
            if (part.totalCount > 0)
                updateTotal(part, map)
        }

        for (childItem: TreeItem<Any> in item.children) {
            if (childItem.children.isEmpty()) {
                val childValue = childItem.value
                if (childValue is BuildPart) {
                    val part = childValue
                    updateTotal(part, map)
                }
            } else
                exportTree(childItem, map)
        }
    }

    private fun updateTotal(part: BuildPart, map: HashMap<String, Int>) {
        if (map.containsKey(part.code))
            map[part.code] = map[part.code]!! + part.totalCount
        else
            map[part.code] = part.totalCount
    }

    fun buttonSaveConfigOnAction() {
        val fileChooser = FileChooser().apply {
            extensionFilters.add(FileChooser.ExtensionFilter("json file (*.json)", "*.json"))
            title = "save config as JSON file"
            initialFileName = "build_config.json"
        }
        val jsonFile = fileChooser.showSaveDialog(tabPaneMain.scene.window)
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonArray = JsonArray()
            if (jsonFile.exists()) {
                if (!jsonFile.delete()) {
                    labelFileStatus.text = "file delete error $jsonFile"
                    labelFileStatus.styleClass.clear()
                    labelFileStatus.styleClass.add("label-failure")
                }
            }
            if (jsonFile.createNewFile()) {

                addTreeItemsToJsonArray(buildTreeRootItem, jsonArray, gson)

                FileWriter(jsonFile).use { fw ->
                    BufferedWriter(fw).use { bw ->
                        gson.toJson(jsonArray, bw)
                    }
                }
            }
            labelFileStatus.text = "Configuration saved to ${jsonFile.name}"
            labelFileStatus.styleClass.clear()
            labelFileStatus.styleClass.add("label-success")

        } catch (e: IOException) {
            logger.error("save config - {}", e.message)
        }
    }

    private fun addTreeItemsToJsonArray(item: TreeItem<Any>, jsonArray: JsonArray, gson: Gson) {
        try {
            item.value?.let {
                val jsonElement = gson.toJsonTree(it)
                jsonArray.add(jsonElement)
            }

            for (child in item.children) {
                addTreeItemsToJsonArray(child, jsonArray, gson)
            }
        } catch (e: IOException) {
            logger.error("save Tree - ${item.value} {}", e.message)
        }
    }

    fun buttonLoadConfigOnAction() {

        val fileChooser = FileChooser().apply {
            extensionFilters.add(FileChooser.ExtensionFilter("json file (*.json)", "*.json"))
            title = "open config JSON file"
        }
        val jsonFile = fileChooser.showOpenDialog(tabPaneMain.scene.window)
        val gson = Gson()
        try {
            FileReader(jsonFile).use { fr ->
                BufferedReader(fr).use { br ->
                    readTree(br, gson)
                }
            }

        } catch (e: IOException) {
            logger.error("load config - {}", e.message)
        }

        labelFileStatus.text = "Configuration loaded from ${jsonFile.name}"
        labelFileStatus.styleClass.clear()
        labelFileStatus.styleClass.add("label-success")

        treeViewBuild.refresh()
    }

    private fun readTree(br: BufferedReader, gson: Gson) {
        buildTreeRootItem.children.clear()
        buildHashMap.clear()

        try {
            val jsonReader = JsonReader(br)
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
                val jsonElement = JsonParser.parseReader(jsonReader)
                val jsonObject = jsonElement.asJsonObject

                if (jsonObject.has("code")) {
                    val buildPart = gson.fromJson<BuildPart>(jsonObject, BuildPart::class.java)
                    if (buildPart.code == "build") {
                        buildTreeRootItem = TreeItem<Any>(buildPart)
                        buildHashMap[buildPart.code] = buildTreeRootItem
                        treeViewBuild.root = buildTreeRootItem
                        treeViewBuild.isShowRoot = true
                    } else {
                        val partItem = newTreeItem(buildPart)
                        buildHashMap[buildPart.code] = partItem
                        val parentItem = buildHashMap[buildPart.parent]
                        parentItem?.children?.add(partItem)
                    }
                } else if (jsonObject.has("name")) {
                    val slot = gson.fromJson<BuildSlot>(jsonObject, BuildSlot::class.java)
                    val slotItem = newTreeItem(slot)
                    buildHashMap[slot.name] = slotItem
                    val parentItem = buildHashMap[slot.parent]
                    parentItem?.children?.add(slotItem)
                }
            }
            jsonReader.endArray()
            jsonReader.close()


        } catch (e: IOException) {
            logger.error("readTree - {}", e.message)
        }
    }

    fun buttonPartCollapseOnAction() {
        if (togglePart.isSelected) {
            for (item: TreeItem<Any> in partTreeRootItem.children) {
                item.isExpanded = true
            }
            togglePart.text = "collapse"
        } else {
            for (item: TreeItem<Any> in partTreeRootItem.children) {
                item.isExpanded = false
            }
            togglePart.text = "expand"
        }
    }


    fun buttonBuildCollapseOnAction() {
        if (toggleBuild.isSelected) {
            expandTreeView(buildTreeRootItem, true)
            toggleBuild.text = "collapse"
        } else {
            expandTreeView(buildTreeRootItem, false)
            toggleBuild.text = "expand"
        }
    }


    private fun expandTreeView(item: TreeItem<Any>?, expand: Boolean) {
        if (item != null && !item.isLeaf) {
            item.isExpanded = true
        }
        for (child in item?.children!!) {
            expandTreeView(child, expand)
        }
    }
}