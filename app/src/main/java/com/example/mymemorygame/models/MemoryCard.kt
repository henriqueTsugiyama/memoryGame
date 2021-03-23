package com.example.mymemorygame.models

data class MemoryCard(
        val id: Int,
        val isFaceUp: Boolean = false,
        val isMatched: Boolean = false
)