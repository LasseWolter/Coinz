package com.coinz.lw.coinz

import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), AnkoLogger{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val url = "http://homepages.inf.ed.ac.uk/stg/coinz/2018/10/03/coinzmap.geojson"
        debug("testtesttest")
        test_conn.onClick {
            Log.d("hello", "hello")
            debug("buttonClicke")
            downloadMap(url)
        }
    }

    fun downloadMap(urlStr: String) {
        doAsync {
            var result = URL(urlStr).readText()
            uiThread {
                Log.d("Request", result)
                toast("async computation finished")
            }
        }
    }

    // Classes needed for network download
    interface DownloadCompleteListener {
        fun downloadComplete(result: String)
    }

    object DonwloadCompleteRunner : DownloadCompleteListener {
        var result: String? = null
        override fun downloadComplete(result: String) {
            this.result = result
        }
    }

    inner class DownloadFileTask(private val caller : DownloadCompleteListener) :
            AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg urls: String): String = try {
            loadFileFromNetwork(urls[0])
        } catch (e: IOException) {
            "Unable to load content. Check your network connection"
        }

        private fun loadFileFromNetwork(urlString: String): String {
            val stream: InputStream = downloadUrl(urlString)
            return stream.bufferedReader().use { it.readText() }

        }

        // Given a string representation of a URL, sets up a connection and gets an input stream
        @Throws(IOException::class)
        private fun downloadUrl(urlString: String): InputStream {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()  // starts the query
            return conn.inputStream
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            caller.downloadComplete(result)
            req_result.text = result
        }
    } // end class DownloadFileTask

}
