package org.dba

import java.io.Serializable

class Slot (
    val name: String,
    val type: String,
    val quantity: Int,
    val description: String,
    val parts: ArrayList<String>
) : Serializable
