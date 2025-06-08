package org.dba

import java.io.Serializable

data class Part(
    val code: String,
    val description: String,
    val category: String,
    val hint: String,
    val slots: ArrayList<String>
) : Serializable


