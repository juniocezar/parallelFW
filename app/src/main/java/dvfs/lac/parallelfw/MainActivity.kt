package dvfs.lac.parallelfw

import android.content.Context
import java.util.concurrent.Executors
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.os.AsyncTask
import java.net.MalformedURLException
import java.net.URL
import android.content.Context.MODE_PRIVATE
import android.os.Environment
import android.util.Log
import java.io.*
import android.widget.Toast
//import android.support.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import android.content.ContentValues.TAG
//import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil.getOutputStream
import java.net.HttpURLConnection


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

            val graph = buildGraph() // milestone 2
            updateScreen(screen,  "Graph built")

            val result = run(graph) // milestone 3
            updateScreen(screen,  "Result Computed")

            serializeResult(result) // milestone 4

            updateScreen(screen,  "File serialized")

            sendFileToServer() // milestone 5
            updateScreen(screen,  "Data uploaded")
            // milestone 5 - send to network
            return ""
        }

        override fun onPostExecute(result: String) {
        }

        fun sendFileToServer(): Int {
            var selectedFilePath = "" + Environment.getExternalStorageDirectory() + "/result.txt"
            var serverResponseCode = 0

            val connection: HttpURLConnection
            val dataOutputStream: DataOutputStream
            val lineEnd = "\r\n"
            val twoHyphens = "--"
            val boundary = "*****"

            var bytesRead: Int
            var bytesAvailable: Int
            var bufferSize: Int
            val buffer: ByteArray
            val maxBufferSize = 1 * 1024 * 1024
            val selectedFile = File(selectedFilePath)


            val parts = selectedFilePath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val fileName = parts[parts.size - 1]

            if (!selectedFile.isFile) {
                //dialog.dismiss()

                updateScreen(screen, "Source File Doesn't Exist: " + selectedFilePath)
                return 0
            } else {
                try {
                    val fileInputStream = FileInputStream(selectedFile)
                    val url = URL("http://homepages.dcc.ufmg.br/~juniocezar/uploadFile.php")
                    connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true //Allow Inputs
                    connection.doOutput = true//Allow Outputs
                    connection.useCaches = false//Don't use a cached Copy
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Connection", "Keep-Alive")
                    connection.setRequestProperty("ENCTYPE", "multipart/form-data")
                    connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary)
                    connection.setRequestProperty("uploaded_file", selectedFilePath)

                    //creating new dataoutputstream
                    dataOutputStream = DataOutputStream(connection.getOutputStream())

                    //writing bytes to data outputstream
                    dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd)
                    dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                            + selectedFilePath + "\"" + lineEnd)

                    dataOutputStream.writeBytes(lineEnd)

                    //returns no. of bytes present in fileInputStream
                    bytesAvailable = fileInputStream.available()
                    //selecting the buffer size as minimum of available bytes or 1 MB
                    bufferSize = Math.min(bytesAvailable, maxBufferSize)
                    //setting the buffer as byte array of size of bufferSize
                    buffer = ByteArray(bufferSize)

                    //reads bytes from FileInputStream(from 0th index of buffer to buffersize)
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize)

                    //loop repeats till bytesRead = -1, i.e., no bytes are left to read
                    while (bytesRead > 0) {
                        //write the bytes read from inputstream
                        dataOutputStream.write(buffer, 0, bufferSize)
                        bytesAvailable = fileInputStream.available()
                        bufferSize = Math.min(bytesAvailable, maxBufferSize)
                        bytesRead = fileInputStream.read(buffer, 0, bufferSize)
                    }

                    dataOutputStream.writeBytes(lineEnd)
                    dataOutputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)

                    serverResponseCode = connection.getResponseCode()
                    val serverResponseMessage = connection.getResponseMessage()

                    Log.i(TAG, "Server Response is: $serverResponseMessage: $serverResponseCode")

                    //response code of 200 indicates the server status OK
                    if (serverResponseCode == 200) {
                        updateScreen(screen, "File Upload completed.")
                    }

                    //closing the input and output streams
                    fileInputStream.close()
                    dataOutputStream.flush()
                    dataOutputStream.close()


                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    runOnUiThread { Toast.makeText(this@MainActivity, "File Not Found", Toast.LENGTH_SHORT).show() }
                } catch (e: MalformedURLException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "URL error!", Toast.LENGTH_SHORT).show()

                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Cannot Read/Write File!", Toast.LENGTH_SHORT).show()
                }

                return serverResponseCode
            }

        }

        private fun serializeResult(data: DoubleArray?) {
            val file = File(Environment.getExternalStorageDirectory(), "result.txt")
            val stream = FileOutputStream(file)
            val size = data!!.size

            try {
                for(i in 0 until size) {
                    var out = data!![i].toString() + " "
                    stream.write(out.toByteArray())
                }
            } catch (e: IOException) {
                Log.e("Exception", "File write failed: " + e.toString())
            } finally {
                stream.close()
            }

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
            for (i in 0 until nV) {
                for (j in 0 until nV) {
                    dist[i][j] = Double.POSITIVE_INFINITY
                }
            }

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
                    //print("(" + x + ")(" + y + ")(" + w + ") = ")
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

        fun run (graph: Pair<Array<DoubleArray>, Int>): DoubleArray? {


            run {
                val numThreads = 8
                val exec = Executors.newFixedThreadPool(numThreads)
                val apspMulti = ParallelFloydWarshall(graph.second, graph.first, exec, numThreads)
                println("Starting multi threaded")
                val startTime = System.currentTimeMillis()
                apspMulti.solve()
                val endTime = System.currentTimeMillis()
                val seconds = endTime - startTime

                println("Time in milliseconds multi threaded mode: " + seconds)
                updateScreen(screen,  "Time in milliseconds multi threaded mode: " + seconds)
                /*println("SP: " + apspMulti.shorestPathLength(218, 297));
                exec.shutdown()
                println("OLHA: " + apspMulti.current!![20])
                for (index in 0 until graph.second) {
                    print("" + apspMulti.current!![index] + " - ")
                }*/
                return apspMulti.current
            }
        }
    }
}
