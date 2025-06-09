package org.dba

import java.io.Serializable

class BuildPart : Serializable{
    val code: String
    val description: String
    val category: String
    val slots: ArrayList<String>
    var parentHash: Int
    var buildCount: Int
    var totalCount: Int

    constructor(code: String, description: String, category: String, slots: ArrayList<String>) {
        this.code = code
        this.description = description
        this.category = category
        this.slots = slots
        this.buildCount = 0
        this.parentHash = 0
        this.totalCount = 0

    }
    constructor(part: Part) {
        code = part.code
        description = part.description
        category = part.category
        slots = part.slots
        parentHash  = 0
        buildCount = 0
        totalCount = 0
    }

}