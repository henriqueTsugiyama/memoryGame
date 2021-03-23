package com.example.mymemorygame.models

import com.example.mymemorygame.utils.DEFAULT_ICONS

class MemoryGame (private val boardSize: BoardSize){
    val cards: List<MemoryCard>
    val numOfPairs = 0

    init {
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        var randomizedImages = (chosenImages + chosenImages).shuffled()
        cards = randomizedImages.map { MemoryCard(it) }
    }

}