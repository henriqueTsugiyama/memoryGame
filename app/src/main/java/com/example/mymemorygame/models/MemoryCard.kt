package com.example.mymemorygame.models

data class MemoryCard(
        val id: Int,
        val imgUrl: String? = null,
        var isFaceUp: Boolean = false,
        var isMatched: Boolean = false
)