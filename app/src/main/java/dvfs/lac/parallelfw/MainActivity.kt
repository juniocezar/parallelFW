package dvfs.lac.parallelfw

import java.util.Random
import java.util.concurrent.Executors
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.text.TextUtils
import android.os.AsyncTask
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL


class MainActivity : AppCompatActivity() {
    private var screen: TextView ?= null

    private fun updateScreen(ui: TextView?, text: String) {
        runOnUiThread {
            val current = "" + ui!!.text
            ui!!.text = current + "\n* " + text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screen = findViewById(R.id.msg)
        screen!!.text = ""
        updateScreen(screen, "Initiating experiment")

        /*Experiments will run on an AsyncTask, so we can update the screen
        * at the right time*/
        var exp = Experiment()
        exp.execute()
    }

    private inner class Experiment : AsyncTask<URL, Void, String>() {

        var map: BufferedReader ?= null

        override fun doInBackground(vararg urls: URL): String {
            downloadMap() // milestone 1
            updateScreen(screen, "Map Downloaded")
            run() // milestone 2 and 3
            updateScreen(screen,  "END")
            // milestone 4 - serialize result in a file
            // milestone 5 - send to network
            return ""
        }

        override fun onPostExecute(result: String) {
        }

        private fun downloadMap() {
            var mUrl: URL ?= null

            try {
                mUrl = URL("http://homepages.dcc.ufmg.br/~juniocezar/map1.in")
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
            try {
                assert(mUrl != null)
                val connection = mUrl!!.openConnection()
                map = BufferedReader(InputStreamReader(connection.getInputStream()))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        private fun buildGraph(): Pair<Array<DoubleArray>, Int> {
            // check if buffer is null

            var nV = Integer.parseInt(map!!.readLine())
            var dist = Array(nV) { DoubleArray(nV) }

            try {
                // if not null
                var nE = map!!.readLine()
                // if not null
                var line = map!!.readLine()
                while (line != null) {
                    val edge: List<String> = line.split(",").map { it.trim() }
                    val x = edge[0].toInt()
                    val y = edge[1].toInt()
                    val w = edge[2].toDouble()
                    dist[x][y] = w
                    line = map!!.readLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                map!!.close()
            }

            return Pair(dist, nV)
        }

        fun run () {

            var graph = buildGraph()

            println("Graph built")
            updateScreen(screen,  "Graph built")

            run {
                val numThreads = 1
                val exec = Executors.newFixedThreadPool(numThreads)
                val apspMulti = ParallelFloydWarshall(graph.second, graph.first, exec, numThreads)
                println("Starting multi threaded")
                val startTime = System.currentTimeMillis()
                apspMulti.solve()
                val endTime = System.currentTimeMillis()
                val seconds = endTime - startTime

                println("Time in milliseconds multi threaded mode: " + seconds)
                updateScreen(screen,  "Time in milliseconds multi threaded mode: " + seconds)
                exec.shutdown()
            }
        }

        private fun genDistanceMatrix(nV: Int, nE: Int, maxW: Int): Array<DoubleArray> {
            var nE = nE
            if (nE > nV.toLong() * (nV - 1) / 2) throw IllegalArgumentException("Too many edges")
            if (nE < 0) throw IllegalArgumentException("Too few edges")

            val dist = Array(nV) { DoubleArray(nV) }
            val ran = Random()

            for (i in 0 until nV) {
                for (j in 0 until nV) {
                    dist[i][j] = java.lang.Double.POSITIVE_INFINITY
                }
            }

            while (nE > 0) {
                val x = ran.nextInt(nV) + 0
                val y = ran.nextInt(nV) + 0
                val w = ran.nextInt(maxW) + 10
                if (dist[x][y] == java.lang.Double.POSITIVE_INFINITY) {
                    dist[x][y] = w.toDouble()
                    nE--
                }
            }

            return dist
        }

    }



}
