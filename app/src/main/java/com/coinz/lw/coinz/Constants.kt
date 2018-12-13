package com.coinz.lw.coinz

import android.annotation.SuppressLint
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.jetbrains.anko.doAsync
import java.text.SimpleDateFormat
import java.util.*


// This class contains functions that should be accessible by all classes
class Constants {
    companion object {
        // Just initialise UserModel here without an actual email address
        // During Login/SignUp this is changed to the actual user
        var USER: UserModel = UserModel("init")
        var WALLET_COINS = mutableListOf<CoinModel>()
        var BANK_COINS = mutableListOf<CoinModel>()
        var baseTag = "CONSTANTS"


        fun getUserRef(): DocumentReference? {
            val db = FirebaseFirestore.getInstance()
            val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return null
            return db.document("Users/$userEmail")
        }

        fun getBankCoinsRef(): CollectionReference? {
            return getUserRef()?.collection("BankCoins")
        }

        fun getWalletCoinsRef(): CollectionReference? {
            return getUserRef()?.collection("WalletCoins")
        }

        @SuppressLint("SimpleDateFormat")
        fun getTodaysDate() : String{
            val df = SimpleDateFormat("yyyy/MM/dd")  // Choose format that matches the URL
            return df.format(Calendar.getInstance().time)
        }

        fun getWalletGoldVal(): Int {
            return WALLET_COINS.sumBy{ coin -> coin.goldVal.toInt()}
        }

        fun getBankGoldVal(): Int {
            return BANK_COINS.sumBy{ coin -> coin.goldVal.toInt() }
        }

        // Update USER and COIN data in db
        fun updateDb() {
            val tag = "$baseTag [updateDb]"
            doAsync {
                try {
                    if (getUserRef() != null) {
                        Tasks.await(getUserRef()!!.set(USER, SetOptions.merge()))
                    }
                    // Store all coins according to their id - if there is a problem jump to next iteration
                    for (coin in WALLET_COINS) {
                        Tasks.await(getWalletCoinsRef()?.document(coin.id)?.set(coin) ?: continue)
                    }
                    for (coin in BANK_COINS) {
                        Tasks.await(getBankCoinsRef()?.document(coin.id)?.set(coin) ?: continue)
                    }
                    Log.d(tag,"Successfully updated USER and COIN date in db")
                } catch (e: Exception) {
                    Log.d(tag, "Couldn't update db with USER and COIN data: $e")
                    e.printStackTrace()
                }
            }
        }
    }
}