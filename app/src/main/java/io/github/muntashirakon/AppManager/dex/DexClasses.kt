// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex

import androidx.annotation.IntRange
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliFormatter
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.analysis.InlineMethodResolver
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.DexBackedOdexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import io.github.muntashirakon.AppManager.BuildConfig
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter

// FIXME: 8/2/22 Add support for lower SDKs by fixing Smali/Baksmali
class DexClasses : Closeable {
    private val mClassNameClassDefMap = HashMap<String, ClassDef>()
    private val mBaseClassNestedClassMap = HashMap<String, MutableList<String>>()
    // TODO: 18/10/21 Load frameworks.jar and add its dex files as options.classPath
    private val mOptions: BaksmaliOptions
    private val mOpcodes: Opcodes

    constructor(apkFile: File, @IntRange(from = -1) apiLevel: Int) {
        mOpcodes = if (apiLevel < 0) Opcodes.getDefault() else Opcodes.forApi(apiLevel)
        mOptions = buildOptions()
        val formatter = BaksmaliFormatter()
        val container = DexUtils.loadApk(apkFile, apiLevel)
        for (dexEntryName in container.dexEntryNames) {
            val dexEntry = requireNotNull(container.getEntry(dexEntryName))
            val dexFile = dexEntry.dexFile
            loadClasses(dexFile, formatter)
            if (dexFile.supportsOptimizedOpcodes()) throw IOException("ODEX isn't supported.")
            if (dexFile is DexBackedOdexFile) {
                mOptions.inlineResolver = InlineMethodResolver.createInlineMethodResolver(dexFile.odexVersion)
            }
        }
    }

    constructor(inputStream: InputStream, @IntRange(from = -1) apiLevel: Int) {
        mOpcodes = if (apiLevel < 0) Opcodes.getDefault() else Opcodes.forApi(apiLevel)
        mOptions = buildOptions()
        val formatter = BaksmaliFormatter()
        val dexFile = DexUtils.loadDexContainer(BufferedInputStream(inputStream), apiLevel)
        loadClasses(dexFile, formatter)
        if (dexFile.supportsOptimizedOpcodes()) throw IOException("ODEX isn't supported.")
        if (dexFile is DexBackedOdexFile) {
            mOptions.inlineResolver = InlineMethodResolver.createInlineMethodResolver(dexFile.odexVersion)
        }
    }

    fun getClassNames(): List<String> = ArrayList(mClassNameClassDefMap.keys)

    fun getBaseClassNames(): List<String> = ArrayList(mBaseClassNestedClassMap.keys)

    @Throws(ClassNotFoundException::class)
    fun getClassDef(className: String): ClassDef =
        mClassNameClassDefMap[className] ?: throw ClassNotFoundException("$className could not be found.")

    @Throws(ClassNotFoundException::class)
    fun getJavaCode(className: String): String {
        return try {
            val baseClass = DexUtils.getClassNameWithoutInnerClasses(className)
            val classes = mBaseClassNestedClassMap[baseClass]
            if (classes.isNullOrEmpty() || !classes.contains(className)) throw ClassNotFoundException()
            val classDefs = classes.map { getClassDef(it) }
            DexUtils.toJavaCode(classDefs, mOpcodes)
        } catch (e: IOException) {
            throw ClassNotFoundException(e.message, e)
        }
    }

    @Throws(ClassNotFoundException::class)
    fun getClassContents(className: String): String = getClassContents(getClassDef(className))

    @Throws(ClassNotFoundException::class)
    fun getClassContents(classdef: ClassDef): String {
        val stringWriter = StringWriter()
        return try {
            BaksmaliWriter(stringWriter).use { baksmaliWriter ->
                ClassDefinition(mOptions, classdef).writeTo(baksmaliWriter)
                stringWriter.toString()
            }
        } catch (e: IOException) {
            throw ClassNotFoundException(e.message, e)
        }
    }

    override fun close() {}

    private fun buildOptions(): BaksmaliOptions = BaksmaliOptions().apply {
        deodex = false
        implicitReferences = false
        parameterRegisters = true
        localsDirective = true
        sequentialLabels = true
        debugInfo = BuildConfig.DEBUG
        codeOffsets = false
        accessorComments = false
        registerInfo = 0
        inlineResolver = null
    }

    private fun loadClasses(dexFile: DexBackedDexFile, formatter: BaksmaliFormatter) {
        for (classDef in dexFile.classes) {
            var name = formatter.getType(classDef.type)
            if (name.endsWith(";")) name = name.substring(0, name.length - 1)
            if (name.startsWith("L")) name = name.substring(1).replace('/', '.')
            mClassNameClassDefMap[name] = classDef
            val baseClass = DexUtils.getClassNameWithoutInnerClasses(name)
            mBaseClassNestedClassMap.getOrPut(baseClass) { ArrayList() }.add(name)
        }
    }
}
