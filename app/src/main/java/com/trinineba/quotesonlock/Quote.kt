package com.trinineba.quotesonlock

data class Quote(
    val text: String,
    val author: String?,
    val custom: Boolean = false,
    val id: String = "",
)
