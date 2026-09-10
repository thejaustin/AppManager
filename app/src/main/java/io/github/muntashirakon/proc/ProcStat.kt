// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

import android.util.Log

/**
 * Used for accessing contents from /proc/$PID/stat. See Table 1-4 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
class ProcStat private constructor(private val mStat: Array<String?>) {
    companion object {
        const val STAT_PID = 0
        const val STAT_TCOMM = 1
        const val STAT_STATE = 2
        const val STAT_PPID = 3
        const val STAT_PGRP = 4
        const val STAT_SID = 5
        const val STAT_TTY_NR = 6
        const val STAT_TTY_PGRP = 7
        const val STAT_FLAGS = 8
        const val STAT_MIN_FLT = 9
        const val STAT_CMIN_FLT = 10
        const val STAT_MAJ_FLT = 11
        const val STAT_CMAJ_FLT = 12
        const val STAT_UTIME = 13
        const val STAT_STIME = 14
        const val STAT_CUTIME = 15
        const val STAT_CSTIME = 16
        const val STAT_PRIORITY = 17
        const val STAT_NICE = 18
        const val STAT_NUM_THREADS = 19
        const val STAT_IT_REAL_VALUE = 20
        const val STAT_START_TIME = 21
        const val STAT_VSIZE = 22
        const val STAT_RSS = 23
        const val STAT_RSSLIM = 24
        const val STAT_START_CODE = 25
        const val STAT_END_CODE = 26
        const val STAT_START_STACK = 27
        const val STAT_ESP = 28
        const val STAT_EIP = 29
        const val STAT_PENDING = 30
        const val STAT_BLOCKED = 31
        const val STAT_SIGIGN = 32
        const val STAT_SIGCATCH = 33
        const val STAT_PLACEHOLDER_0 = 34
        const val STAT_PLACEHOLDER_1 = 35
        const val STAT_PLACEHOLDER_2 = 36
        const val STAT_EXIT_SIGNAL = 37
        const val STAT_TASK_CPU = 38
        const val STAT_RT_PRIORITY = 39
        const val STAT_POLICY = 40
        const val STAT_BLKIO_TICKS = 41
        const val STAT_GTIME = 42
        const val STAT_CGTIME = 43
        const val STAT_START_DATA = 44
        const val STAT_END_DATA = 45
        const val STAT_START_BRK = 46
        const val STAT_ARG_START = 47
        const val STAT_ARG_END = 48
        const val STAT_ENV_START = 49
        const val STAT_ENV_END = 50
        const val STAT_EXIT_CODE = 51

        private const val STAT_COUNT = 52

        @JvmStatic
        fun parse(data: CharArray): ProcStat {
            val result = arrayOfNulls<String>(STAT_COUNT)
            // See: https://www.openwall.com/lists/oss-security/2022/12/21/6
            var bracketCount = 0
            var fieldIndex = 0
            var lastOffset = 0
            for (i in data.indices) {
                val c = data[i]
                when {
                    c == '(' -> ++bracketCount
                    c == ')' -> --bracketCount
                    bracketCount == 0 && c == ' ' -> {
                        result[fieldIndex] = String(data, lastOffset, i - lastOffset)
                        ++fieldIndex
                        lastOffset = i + 1
                    }
                }
            }
            // Add last field
            result[fieldIndex] = String(data, lastOffset, data.size - lastOffset)
            if (fieldIndex + 1 != STAT_COUNT) {
                Log.w(ProcStat::class.java.simpleName, "Field counts did not match, expected: $STAT_COUNT, actual: ${fieldIndex + 1}")
            }
            return ProcStat(result)
        }
    }

    fun getString(index: Int): String = mStat[index]!!

    fun getInteger(index: Int): Int = Integer.decode(mStat[index])

    fun getLong(index: Int): Long = java.lang.Long.decode(mStat[index])
}
