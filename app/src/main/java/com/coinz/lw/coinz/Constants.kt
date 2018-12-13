package com.coinz.lw.coinz

import android.annotation.SuppressLint
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*


// This class contains functions that should be accessible by all classes
class Constants {
    companion object {
        // Just initialise UserModel here without an actual email address
        // During Login/SignUp this is changed to the actual user
        var USER: UserModel = UserModel("init")
        var COINS = mutableListOf<CoinModel>()

        fun getUserRef(): DocumentReference? {
            val db = FirebaseFirestore.getInstance()
            val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return null
            return db.document("Users/$userEmail")
        }

        fun getCoinsRef(): CollectionReference? {
            return getUserRef()?.collection("Coins")
        }

        @SuppressLint("SimpleDateFormat")
        fun getTodaysDate() : String{
            val df = SimpleDateFormat("yyyy/MM/dd")  // Choose format that matches the URL
            return df.format(Calendar.getInstance().time)
        }
    }
}