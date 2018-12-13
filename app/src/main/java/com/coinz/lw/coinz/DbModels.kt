package com.coinz.lw.coinz

// This class contains all the DB models

data class CoinModel(val id: String="", val goldVal: Long=0, val collectionDate: String = "")


data class UserModel(val email: String = "",
                     var gold: Long = 0,  // use long instead of int as firestore uses Longs internally
                     var payInsLeft: Long = 25,
                     var lastPayIn: String = "",
                     var lastLogin: String = Constants.getTodaysDate(),
                     var mapJson: String = "")