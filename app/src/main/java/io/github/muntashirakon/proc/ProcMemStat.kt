// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

import android.util.Log

/**
 * Used for accessing contents from /proc/$PID/statm. See Table 1-3 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
class ProcMemStat private constructor(private val mMemStat: Array<String>) {
    companion object {
        const val MEM_STAT_SIZE = 0
        const val MEM_STAT_RESIDENT = 1
        const val MEM_STAT_SHARED = 2
        const val MEM_STAT_TRS = 3
        const val MEM_STAT_LRS = 4
        const val MEM_STAT_DRS = 5
        const val MEM_STAT_DT = 6

        private const val MEM_STAT_COUNT = 7

        @JvmStatic
        fun parse(data: String): ProcMemStat {
            val result = data.split("\\s".toRegex())
            if (result.size != MEM_STAT_COUNT) {
                Log.w(ProcMemStat::class.java.simpleName, "Field counts did not match, expected: $MEM_STAT_COUNT, actual: ${result.size}")
            }
            return ProcMemStat(result.toTypedArray())
        }
    }

    fun getLong(index: Int): Long = java.lang.Long.decode(mMemStat[index])
}
