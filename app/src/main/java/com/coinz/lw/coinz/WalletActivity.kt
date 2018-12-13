package com.coinz.lw.coinz

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.coinz.lw.coinz.Account.Companion.payCoinIntoAccount
import com.coinz.lw.coinz.Constants.Companion.WALLET_COINS
import kotlinx.android.synthetic.main.activity_wallet.*

class WalletActivity: AppCompatActivity() {
    init {

    }
    private lateinit var listView: ListView
    private var coinList = WALLET_COINS
    private lateinit var adapter: CoinsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        listView = wallet_list_view

        adapter = CoinsAdapter(coinList, this)
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, position, id ->
            payCoinIntoAccount(coinList[position], this)
            adapter.notifyDataSetChanged()
        }
    }
}


class CoinsAdapter(var coinList:List<CoinModel>, var activity: Activity) : BaseAdapter() {

    override fun getView(position: Int, convertView:  View?, parent: ViewGroup?): View? {
        // inflate data model layout to View object
        val view: View = View.inflate(activity, R.layout.coin, null)
        val goldVal = view.findViewById<TextView>(R.id.gold_value) as TextView
        val collectionDate = view.findViewById<TextView>(R.id.collection_date) as TextView
        // set value to View object
        goldVal.text = String.format("Gold: %d", coinList[position].goldVal)
        collectionDate.text = String.format("Collection Date: %s",  coinList[position].collectionDate)
        return view
    }

    override fun getItem(position: Int): Any {
        // return item at 'position'
        return coinList.get(position)
    }

    override fun getItemId(position: Int): Long {
        // return item Id by Long datatype
        return position.toLong()
    }

    override fun getCount(): Int {
        // return quantity of the list
        return coinList.size
    }
}