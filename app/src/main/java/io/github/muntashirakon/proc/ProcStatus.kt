// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

/**
 * Used for accessing contents from /proc/$PID/status. See Table 1-2 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
class ProcStatus private constructor(private val mStatus: Map<String, String>) {
    companion object {
        const val STATUS_NAME = "Name"
        const val STATUS_UMASK = "Umask"
        const val STATUS_STATE = "State"
        const val STATUS_TGID = "Tgid"
        const val STATUS_NGID = "Ngid"
        const val STATUS_PID = "Pid"
        const val STATUS_PPID = "PPid"
        const val STATUS_TRACER_PID = "TracerPid"
        const val STATUS_UID = "Uid"  // Real, effective, saved set, and file system UIDs
        const val STATUS_GID = "Gid"  // Real, effective, saved set, and file system GIDs
        const val STATUS_FD_SIZE = "FDSize"
        const val STATUS_GROUPS = "FDSize"
        const val STATUS_NS_TGID = "NStgid"
        const val STATUS_NS_PID = "NSpid"
        const val STATUS_NS_PGID = "NSpgid"
        const val STATUS_NS_SID = "NSsid"
        const val STATUS_VM_PEAK = "VmPeak"
        const val STATUS_VM_SIZE = "VmSize"
        const val STATUS_VM_LCK = "VmLck"
        const val STATUS_VM_PIN = "VmPin"
        const val STATUS_VM_HWM = "VmHWM"
        const val STATUS_VM_RSS = "VmRSS"
        const val STATUS_RSS_ANON = "RssAnon"
        const val STATUS_RSS_FILE = "RssFile"
        const val STATUS_RSS_SHMEM = "RssShmem"
        const val STATUS_VM_DATA = "VmData"
        const val STATUS_VM_STK = "VmStk"
        const val STATUS_VM_EXE = "VmExe"
        const val STATUS_VM_LIB = "VmLib"
        const val STATUS_VM_PTE = "VmPTE"
        const val STATUS_VM_SWAP = "VmSwap"
        const val STATUS_HUGETLB_PAGES = "HugetlbPages"
        const val STATUS_CORE_DUMPING = "CoreDumping"
        const val STATUS_THP_ENABLED = "THP_enabled"
        const val STATUS_THREADS = "Threads"
        const val STATUS_SIG_Q = "SigQ"
        const val STATUS_SIG_PND = "SigPnd"
        const val STATUS_SHD_PND = "ShdPnd"
        const val STATUS_SIG_BLK = "SigBlk"
        const val STATUS_SIG_IGN = "SigIgn"
        const val STATUS_SIG_CGT = "SigCgt"
        const val STATUS_CAP_INH = "CapInh"
        const val STATUS_CAP_PRM = "CapPrm"
        const val STATUS_CAP_EFF = "CapEff"
        const val STATUS_CAP_BND = "CapBnd"
        const val STATUS_CAP_AMB = "CapAmb"
        const val STATUS_NO_NEW_PRIVS = "NoNewPrivs"
        const val STATUS_SECCOMP = "Seccomp"
        const val STATUS_SPECULATION_STORE_BYPASS = "Speculation_Store_Bypass"
        const val STATUS_CPUS_ALLOWED = "Cpus_allowed"
        const val STATUS_CPUS_ALLOWED_LIST = "Cpus_allowed_list"
        const val STATUS_MEMS_ALLOWED = "Mems_allowed"
        const val STATUS_MEMS_ALLOWED_LIST = "Mems_allowed_list"
        const val STATUS_VOLUNTARY_CTX_SWITCHES = "voluntary_ctxt_switches"
        const val STATUS_NON_VOLUNTARY_CTX_SWITCHES = "nonvoluntary_ctxt_switches"

        @JvmStatic
        fun parse(data: String): ProcStatus {
            val result = HashMap<String, String>(56)
            for (line in data.split("\n")) {
                val idxOfColon = line.indexOf(':')
                if (idxOfColon != -1) {
                    result[line.substring(0, idxOfColon).trim()] = line.substring(idxOfColon + 1).trim()
                }
            }
            return ProcStatus(result)
        }
    }

    fun getString(key: String): String? = mStatus[key]

    fun getInteger(key: String, defaultValue: Int): Int =
        getString(key)?.let { Integer.decode(it) } ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        getString(key)?.let { java.lang.Long.decode(it) } ?: defaultValue
}
