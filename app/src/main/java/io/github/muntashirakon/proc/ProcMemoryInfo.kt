// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

import io.github.muntashirakon.io.Paths

class ProcMemoryInfo(private val mMemInfo: Map<String, Long>) {
    companion object {
        private const val MEMINFO_MEM_TOTAL = "MemTotal"
        private const val MEMINFO_MEM_FREE = "MemFree"
        private const val MEMINFO_MEM_AVAILABLE = "MemAvailable"
        private const val MEMINFO_BUFFERS = "Buffers"
        private const val MEMINFO_CACHED = "Cached"
        private const val MEMINFO_SWAP_CACHED = "SwapCached"
        private const val MEMINFO_ACTIVE = "Active"
        private const val MEMINFO_INACTIVE = "Inactive"
        private const val MEMINFO_ACTIVE_ANON = "Active(anon)"
        private const val MEMINFO_INACTIVE_ANON = "Inactive(anon)"
        private const val MEMINFO_ACTIVE_FILE = "Active(file)"
        private const val MEMINFO_INACTIVE_FILE = "Inactive(file)"
        private const val MEMINFO_UNEVICTABLE = "Unevictable"
        private const val MEMINFO_MLOCKED = "Mlocked"
        private const val MEMINFO_SWAP_TOTAL = "SwapTotal"
        private const val MEMINFO_SWAP_FREE = "SwapFree"
        private const val MEMINFO_DIRTY = "Dirty"
        private const val MEMINFO_WRITEBACK = "Writeback"
        private const val MEMINFO_ANON_PAGES = "AnonPages"
        private const val MEMINFO_MAPPED = "Mapped"
        private const val MEMINFO_SHMEM = "Shmem"
        private const val MEMINFO_SLAB = "Slab"
        private const val MEMINFO_S_RECLAIMABLE = "SReclaimable"
        private const val MEMINFO_S_UNRECLAIM = "SUnreclaim"
        private const val MEMINFO_KERNEL_STACK = "KernelStack"
        private const val MEMINFO_PAGE_TABLES = "PageTables"
        private const val MEMINFO_NFS_UNSTABLE = "NFS_Unstable"
        private const val MEMINFO_BOUNCE = "Bounce"
        private const val MEMINFO_WRITEBACK_TMP = "WritebackTmp"
        private const val MEMINFO_COMMIT_LIMIT = "CommitLimit"
        private const val MEMINFO_COMMITTED_AS = "Committed_AS"
        private const val MEMINFO_VMALLOC_TOTAL = "VmallocTotal"
        private const val MEMINFO_VMALLOC_USED = "VmallocUsed"
        private const val MEMINFO_VMALLOC_CHUNK = "VmallocChunk"
        private const val MEMINFO_CMA_TOTAL = "CmaTotal"
        private const val MEMINFO_CMA_FREE = "CmaFree"

        @JvmStatic
        fun parse(data: String): ProcMemoryInfo {
            val result = HashMap<String, Long>(36)
            for (line in data.split("\n")) {
                val idxOfColon = line.indexOf(':')
                val idxOfKb = line.lastIndexOf("kB")
                if (idxOfColon != -1 && idxOfKb != -1) {
                    result[line.substring(0, idxOfColon).trim()] =
                        java.lang.Long.decode(line.substring(idxOfColon + 1, idxOfKb).trim()) shl 10
                }
            }
            return ProcMemoryInfo(result)
        }
    }

    fun get(key: String, defaultValue: Long): Long = mMemInfo[key] ?: defaultValue

    fun getTotalMemory(): Long = get(MEMINFO_MEM_TOTAL, 0)

    fun getFreeMemory(): Long = get(MEMINFO_MEM_FREE, 0)

    fun getUsedMemory(): Long = getTotalMemory() - getFreeMemory()

    fun getAvailableMemory(): Long = get(MEMINFO_MEM_AVAILABLE, 0)

    fun getBuffers(): Long = get(MEMINFO_BUFFERS, 0)

    fun getCachedMemory(): Long = get(MEMINFO_CACHED, 0) + get(MEMINFO_SLAB, 0)

    fun getApplicationMemory(): Long = getUsedMemory() - getBuffers() - getCachedMemory()

    fun getTotalSwap(): Long = get(MEMINFO_SWAP_TOTAL, 0)

    fun getFreeSwap(): Long = get(MEMINFO_SWAP_FREE, 0)

    fun getUsedSwap(): Long = getTotalSwap() - getFreeSwap()

    fun getTotalZram(): Long {
        // https://www.kernel.org/doc/html/latest/_sources/admin-guide/blockdev/zram.rst.txt
        val zramPaths = Paths.get("/sys/block/").listFiles { _, name -> name.startsWith("zram") }
        var totalZram = 0L
        for (zramPath in zramPaths) {
            val mmStat = Paths.build(zramPath, "mm_stat")
            if (mmStat != null && mmStat.canRead()) {
                totalZram += java.lang.Long.decode(mmStat.getContentAsString().split("\\s".toRegex())[2].trim())
            }
        }
        return totalZram
    }
}
