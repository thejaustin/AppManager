// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc

import io.github.muntashirakon.io.Path

class ProcMappedFiles(mappedFiles: Array<Path>) {
    class MappedFile(val memoryPath: Path, val realPath: String) {
        val vmStart: Long
        val vmEnd: Long

        init {
            val vmAreaStruct = memoryPath.name.split("-")
            vmStart = java.lang.Long.decode("0x" + vmAreaStruct[0])
            vmEnd = java.lang.Long.decode("0x" + vmAreaStruct[1])
        }
    }

    private val mMappedFiles: MutableMap<String, MutableList<MappedFile>>

    init {
        mMappedFiles = HashMap(mappedFiles.size)
        for (file in mappedFiles) {
            try {
                val realPath = requireNotNull(file.getRealFilePath())
                val mappedFile = MappedFile(file, realPath)
                mMappedFiles.getOrPut(realPath) { mutableListOf() }.add(mappedFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getRealFiles(): Collection<String> = mMappedFiles.keys

    fun getMappedFiles(realPath: String): List<MappedFile>? = mMappedFiles[realPath]
}
