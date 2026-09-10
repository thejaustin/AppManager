// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.Path

class ProcFdInfoList(fdFiles: Array<Path>, fdInfoList: Array<String?>) {
    class ProcFdInfo(val id: Int, val realPath: Path, info: Array<String>) {
        val offset: Long
        val mode: Int
        val mountId: Int
        val extras: Array<String?>

        init {
            extras = arrayOfNulls(info.size - 3) // Ignore three mandatory fields
            var i = 0
            var tmpOffset = -1L
            var tmpMode = -1
            var tmpMountId = -1
            for (line in info) {
                when {
                    line.startsWith("pos:") -> tmpOffset = java.lang.Long.decode(line.substring(4).trim())
                    line.startsWith("flags:") -> tmpMode = Integer.decode(line.substring(6).trim())
                    line.startsWith("mnt_id:") -> tmpMountId = Integer.decode(line.substring(7).trim())
                    else -> extras[i++] = line
                }
            }
            assert(tmpOffset != -1L)
            assert(tmpMode != -1)
            assert(tmpMountId != -1)
            offset = tmpOffset
            mode = tmpMode
            mountId = tmpMountId
        }

        fun getModeString(): String = FileUtils.translateModePosixToString(mode)
    }

    private val mFdInfoMap: MutableMap<Int, ProcFdInfo>

    init {
        assert(fdFiles.size == fdInfoList.size)
        mFdInfoMap = HashMap(fdFiles.size)
        for (i in fdFiles.indices) {
            val fdInfo = fdInfoList[i] ?: continue // FD no longer exists
            val fdFile = fdFiles[i]
            val fd = Integer.decode(fdFile.name)
            val procFdInfo = ProcFdInfo(fd, fdFile, fdInfo.split("\n").toTypedArray())
            mFdInfoMap[fd] = procFdInfo
        }
    }

    fun getFds(): Collection<Int> = mFdInfoMap.keys

    fun getFdInfo(fd: Int): ProcFdInfo? = mFdInfoMap[fd]
}
