package com.coinz.lw.coinz

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.TextView
import com.coinz.lw.coinz.Constants.Companion.WALLET_COINS
import com.coinz.lw.coinz.Constants.Companion.updateDb
import kotlinx.android.synthetic.main.activity_wallet.*

// This Activity displays all coins in the wallet with their gold value and collection date
// By clicking on a coin the user pays it into the bank
class WalletActivity: AppCompatActivity() {
    private lateinit var coinList : MutableList<CoinModel>
    private lateinit var adapter: CoinsAdapter
    private val baseTag = "WALLET_ACTIVITY"

    override fun onCreate(savedInstanceState: Bundle?) {
        val tag = "$baseTag [onCreate]"
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        // Store all WALLET_COINS in the list view and populate it using a CoinsAdapter
        coinList = WALLET_COINS
        adapter = CoinsAdapter(coinList, this)
        wallet_list_view.adapter = adapter

        // Wait for a click on one of the coins. If clicked it tries to pay it into the bank
        wallet_list_view.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            Log.d(tag, "Clicked on a coin. Trying to pay it into the bank. Coin: ${coinList[position]}")
            WalletControl.payCoinIntoBank(coinList[position], this)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onStop() {
        super.onStop()
        updateDb()
    }
}

// Adapter that handles the list view which displays coins in the wallet
// mostly taken from https://www.w3adda.com/kotlin-android-tutorial/kotlin-listview
class CoinsAdapter(private var coinList:List<CoinModel>, private var activity: Activity) : BaseAdapter() {

    override fun getView(position: Int, convertView:  View?, parent: ViewGroup?): View? {
        // Inflate data model layout to View object
        val view: View = View.inflate(activity, R.layout.coin, null)
        val goldVal = view.findViewById(R.id.gold_value) as TextView
        val collectionDate = view.findViewById(R.id.collection_date) as TextView

        // Set value to View object
        goldVal.text = String.format("Gold: %d", coinList[position].goldVal)
        collectionDate.text = String.format("Collection Date: %s",  coinList[position].collectionDate)
        return view
    }

    override fun getItem(position: Int): Any {
        // Return item at 'position'
        return coinList.get(position)
    }

    override fun getItemId(position: Int): Long {
        // Return item Id by Long datatype
        return position.toLong()
    }

    override fun getCount(): Int {
        // Return quantity of the list
        return coinList.size
    }
}