package org.dba

import java.io.Serializable

class BuildSlot : Serializable{
    val name: String
    val type: String
    val quantity: Int
    val description: String
    val parts: ArrayList<String>
    var parentHash: Int
    var content: Int

    constructor(slot: Slot) {
        name = slot.name
        type = slot.type
        quantity = slot.quantity
        description = slot.description
        parts = slot.parts
        parentHash = 0
        content = 0
    }

}