package dvfs.lac.parallelfw

/**
 * Created by juniocezar on 28/02/18.
 */

import java.util.ArrayList
import java.util.Arrays
import java.util.List
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class ParallelFloydWarshall @JvmOverloads constructor(private val numNodes: Int, distances: Array<DoubleArray>, private val mode: Mode = Mode.SINGLE_THREADED, private val exec: ExecutorService? = null, private val numThreads: Int = 1) {
    private var current: DoubleArray? = null
    private var next: DoubleArray? = null

    private val maxIndex: IntArray
    private var solved: Boolean = false

    enum class Mode {
        SINGLE_THREADED, MULTI_THREADED
    }

    private fun getIndex(i: Int, j: Int): Int {
        return i * numNodes + j
    }

    private fun getI(index: Int): Int {
        return index / numNodes
    }

    private fun getJ(index: Int): Int {
        return index % numNodes
    }


    /**
     *
     * @param numNodes the number of nodes in the graph
     * @param distances the matrix of distances between nodes, indexed from 0 to numNodes-1.  distances[i][j] cost of a directed edge from i to j.  Must be Double.POSITIVE_INFINITY if the edge is not present.  distance[i][i] is a self arc (allowed)
     * @param exec
     * @param numThreads
     */
    constructor(numNodes: Int, distances: Array<DoubleArray>, exec: ExecutorService, numThreads: Int) : this(numNodes, distances, Mode.MULTI_THREADED, exec, numThreads) {}


    init {
        this.next = DoubleArray(numNodes * numNodes)
        this.maxIndex = IntArray(numNodes * numNodes)
        Arrays.fill(maxIndex, -1)

        var currentLocal = DoubleArray(numNodes * numNodes)

        for (i in 0 until numNodes) {
            for (j in 0 until numNodes) {
                currentLocal[getIndex(i, j)] = distances[i][j]
            }
        }
        this.current = currentLocal
        this.solved = false
    }


    private fun updateMatrixSingleThreaded(k: Int) {
        update(0, current!!.size, k)
    }

    private fun updateMatrixMultiThreaded(k: Int) {
        val tasks = ArrayList<Callable<Boolean>>()
        if (current!!.size < numThreads) {
            for (i in current!!.indices) {
                tasks.add(FloydJob(i, i + 1, k))
            }
        } else {
            for (t in 0 until numThreads) {
                val lo = t * current!!.size / numThreads
                val hi = (t + 1) * current!!.size / numThreads
                tasks.add(FloydJob(lo, hi, k))
            }
        }
        try {
            val results = this.exec!!.invokeAll(tasks)
            for (result in results) {
                if (!result.get()) {
                    throw RuntimeException()
                }
            }
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }

    }

    fun solve() {
        if (solved) {
            throw RuntimeException("Already solved")
        }
        for (k in 0 until numNodes) {
            if (this.mode == Mode.MULTI_THREADED) {
                updateMatrixMultiThreaded(k)
            } else if (this.mode == Mode.SINGLE_THREADED) {
                updateMatrixSingleThreaded(k)
            } else {
                throw RuntimeException("unexpected mode for parallel floyd warshall: " + mode)
            }
            val temp = current
            current = next
            next = temp
        }
        next = null
        solved = true
    }

    /**
     *
     * @param i must lie in in [0,numNodes)
     * @param j must lie in in [0,numNodes)
     * @return the length of the shortest directed path from node i to node j.  If i == j, gives the shortest directed cycle starting at node i (note that the graph may contain nodes with self loops).  Returns Double.POSITIVE_INFINITY if there is no path from i to j.
     */
    fun shorestPathLength(i: Int, j: Int): Double {
        if (!solved) {
            throw RuntimeException("Must solve first")
        }
        return this.current!![getIndex(i, j)]
    }

    /**
     * Example: If the path from node 2 to node 5 is an edge from 2 to 3 and then an edge from 3 to 5, the return value will be Arrays.asList(Integer.valueOf(2),Integer.valueOf(3),Integer.valueOf(5));
     *
     * @param i the start of the directed path
     * @param j the end of the directed path
     * @return The shortest path starting at node i and ending at node j, or null if no such path exists.
     */
    fun shortestPath(i: Int, j: Int): List<Int>? {
        if (current!![getIndex(i, j)] == java.lang.Double.POSITIVE_INFINITY) {
            return null
        } else {
            val ans = ArrayList<Int>()
            ans.add(Integer.valueOf(i))
            shortestPathHelper(i, j, ans)
            return ans as List<Int>?
        }
    }

    fun shortestPathHelper(i: Int, j: Int, partialPath: MutableList<Int>) {
        val index = getIndex(i, j)
        if (this.maxIndex[index] < 0) {
            partialPath.add(Integer.valueOf(j))
        } else {
            shortestPathHelper(i, this.maxIndex[index], partialPath)
            shortestPathHelper(this.maxIndex[index], j, partialPath)
        }
    }

    private inner class FloydJob(private val lo: Int, private val hi: Int, private val k: Int) : Callable<Boolean> {


        @Throws(Exception::class)
        override fun call(): Boolean? {
            return update(this.lo, this.hi, this.k)
        }
    }

    private fun update(lo: Int, hi: Int, k: Int): Boolean {
        for (index in lo until hi) {
            val i = getI(index)
            val j = getJ(index)
            val alternatePathValue = current!![getIndex(i, k)] + current!![getIndex(k, j)]

            if (alternatePathValue < current!![index]) {
                next!![index] = alternatePathValue
                maxIndex[index] = k
            } else {
                next!![index] = current!![index]
            }
        }
        return true
    }

}