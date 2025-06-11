package org.dba

import java.io.Serializable

data class Part(
    val code: String,
    val description: String,
    val category: String,
    val slots: ArrayList<String>,
    val hint: String,
    val od1: Boolean
) : Serializable


