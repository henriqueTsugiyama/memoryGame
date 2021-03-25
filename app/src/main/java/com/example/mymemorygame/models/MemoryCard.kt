package com.example.mymemorygame.models

data class MemoryCard(
        val id: Int,
        var isFaceUp: Boolean = false,
        var isMatched: Boolean = false
)