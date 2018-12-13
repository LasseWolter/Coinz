package com.coinz.lw.coinz

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*


// This class contains functions that should be accessible by all classes
class Constants {
    companion object {
        lateinit var USER: UserModel
        var COINS = mutableListOf<CoinModel>()
        private var db = FirebaseFirestore.getInstance()

        fun getUserRef(): DocumentReference? {
            var userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return null
            return db.document("Users/$userEmail")
        }

        fun getCoinsRef(): CollectionReference? {
            return getUserRef()?.collection("Coins")
        }

        fun getTodaysDate() : String{
            val df = SimpleDateFormat("yyyy/MM/dd")  // Choose format that matches the URL
            return df.format(Calendar.getInstance().time)
        }
    }
}