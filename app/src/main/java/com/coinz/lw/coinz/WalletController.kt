package com.coinz.lw.coinz

import android.app.Activity
import android.util.Log
import com.coinz.lw.coinz.Constants.Companion.BANK_COINS
import com.coinz.lw.coinz.Constants.Companion.USER
import com.coinz.lw.coinz.Constants.Companion.WALLET_COINS
import com.coinz.lw.coinz.Constants.Companion.getBankGoldVal
import com.coinz.lw.coinz.Constants.Companion.getTodaysDate
import com.coinz.lw.coinz.Constants.Companion.getWalletGoldVal
import com.google.gson.JsonParser
import kotlinx.android.synthetic.main.activity_map.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import kotlin.math.roundToLong

// The Wallet controller  handles all actions around the collection, conversion and pay-ins of coins
// All methods are in the companion object as there shouldn't be several instances of Wallet Controllers
// so it basically functions as a singleton class
class WalletControl {
    companion object {
        private val baseTag = "WalletControler"
        private var rates = hashMapOf<Currency, Double>()

        fun payCoinIntoBank(coin: CoinModel, activity: Activity) {
            val tag = "$baseTag [payCoinIntoBank]"
            var alreadyCollected = false
            for (colCoin in  BANK_COINS) {
                if (coin.id == colCoin.id) {
                    Log.d(tag, "A coin with this id has already been payed in. id: ${coin.id}")
                    activity.toast("You've already payed in this coin.")
                    alreadyCollected = true
                    break
                }
            }
            if (!alreadyCollected) {
                updateUser(coin, activity)
            }
        }

        // Update USER fields
        private fun updateUser(coin: CoinModel, activity: Activity) {
            // Check if the user has already payedIn getTodaysDate() and if so if he has payIns left otherwise reset payIns
            if (USER.lastPayIn != getTodaysDate()) {
                USER.lastPayIn = getTodaysDate()
                USER.payInsLeft = 25
            } else if (USER.payInsLeft <= 0) {
                activity.longToast("You cannot pay in any more coins for today. Please come back tomorrow.")
                return
            }
            // Update local lists of Coins (Wallet and Bank)
            BANK_COINS.add(coin)
            WALLET_COINS.remove(coin)
            // The coin deletion is directly send to DB to avoid querying for which coins were deleted later
            Constants.getWalletCoinsRef()?.document(coin.id)?.delete()

            // Update USER fields locally - the DB is updated in onStop()
            USER.gold += coin.goldVal
            USER.payInsLeft--

            activity.longToast("Coin was payed into bank. You have ${USER.payInsLeft} pay-ins left for today")
        }

        // Adds coin to the local wallet and asks if the user wants to pay it into the bank directly
        fun addCoinToWallet(coin: Coin, activity: Activity) {
            val tag = "$baseTag [addCoinToWallet]"
            val coinGoldVal = convert(coin.value, coin.currency)
            val dbCoin = CoinModel(coin.id, coinGoldVal, getTodaysDate())

            activity.alert("Congratulations you just found a coin worth $coinGoldVal") {
                isCancelable = false
                positiveButton("Continue") {
                    WALLET_COINS.add(dbCoin)
                    val newWalletGold = getWalletGoldVal()
                    activity.gold_counter.text = String.format("Wallet: %d", newWalletGold)
                    Log.d(tag, "New Wallet-Gold Value: $newWalletGold")
                }
                negativeButton("Pay into Bank") {
                    val newBankGold = getBankGoldVal() + coinGoldVal
                    activity.bank_gold.text = String.format("Bank: %d", newBankGold)
                    payCoinIntoBank(dbCoin, activity)
                    Log.d(tag, "New Bank-Gold Value: $newBankGold")
                }
            }.show()
        }

        // Update currency rates to most recent ones available
        fun updateRates(jsonStr: String) {
            val tag = "$baseTag [updateRates]"
            val parser = JsonParser()
            val obj = parser.parse(jsonStr).asJsonObject
            val curRates = obj.get("rates").asJsonObject

            enumValues<Currency>().forEach {
                this.rates[it] = curRates.get(it.name).asDouble
            }
            Log.d(tag, "Updated Conversion rates. New rates: $rates")
        }
        // Convert currency value to Gold value
        private fun convert(value: Double, currency: Currency): Long {
            val tag = "$baseTag [updateRates]"
            var goldValue: Long = 0

            // If there is no rate then we don't want to add any gold value so we goldValue stays 0.0
            if (rates[currency] == null) {
                Log.d(tag, "No rate available for currency $currency. Is it a valid currency? value of 0.0 returned")
            } else {
                goldValue = (value * rates[currency]!!).roundToLong()    // by this point we know that rates[currency] cannot be null
            }
            return goldValue
        }

    }
}