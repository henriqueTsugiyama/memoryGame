package com.example.mymemorygame.models

import com.example.mymemorygame.utils.DEFAULT_ICONS

class MemoryGame (private val boardSize: BoardSize){
    val cards: List<MemoryCard>
    var numOfPairsFound = 0

    private var indexOfSelectedCard: Int? = null
    private var numOfCardsFlipped: Int = 0
    init {
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        var randomizedImages = (chosenImages + chosenImages).shuffled()
        cards = randomizedImages.map { MemoryCard(it) }
    }

    fun flipCard(position: Int) : Boolean {
        numOfCardsFlipped++
        var card = cards[position]
        // 0 card faced up => flip over selected card
        //1 card faced up => flip over selected card + check if they matched
        //2 cards faced up => restore all cards + flip next selected card
        var foundMatch: Boolean = false
        if (indexOfSelectedCard == null) {
            restoreCards()
            indexOfSelectedCard = position
        }
        else{
            foundMatch = checkForMatch(indexOfSelectedCard!!, position)
            indexOfSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if(cards[position1].id != cards[position2].id){
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numOfPairsFound++
        return true
    }

    private fun restoreCards() {
        for (card in cards ){
            if(!card.isMatched) card.isFaceUp = false
        }
    }

    fun haveWon(): Boolean {
        return numOfPairsFound == boardSize.getNumPairs()
    }

    fun isCardFacedUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numOfCardsFlipped/2
    }


}