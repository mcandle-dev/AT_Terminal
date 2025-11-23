package com.example.bleattest.models

data class AtCommandResult(
    val success: Boolean,
    val response: String,
    val errorMessage: String? = null,
    val executionTime: Long = 0 // ms
)
