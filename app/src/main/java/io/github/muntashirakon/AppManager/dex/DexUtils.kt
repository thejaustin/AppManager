// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dex

import androidx.annotation.AnyThread
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.DexBackedOdexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.MultiDexContainer
import com.android.tools.smali.dexlib2.writer.io.DexDataStore
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.io.Path
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.core.utils.files.FileUtils
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.RecognitionException
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.math.BigInteger
import java.nio.charset.StandardCharsets

object DexUtils {
    @JvmStatic
    fun getClassNameWithoutInnerClasses(className: String): String {
        val idx = findFirstInnerClassIndex(className)
        return if (idx >= 0) className.substring(0, idx) else className
    }

    @JvmStatic
    fun findFirstInnerClassIndex(className: String): Int {
        var validDollarIndex = -1
        for (i in className.indices.reversed()) {
            when (className[i]) {
                '.' -> return validDollarIndex
                '$' -> validDollarIndex = i
            }
        }
        return validDollarIndex
    }

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun isDex(path: Path): Boolean {
        val headerBytes = ByteArray(4)
        path.openInputStream().use { it.read(headerBytes) }
        return BigInteger(headerBytes).toInt() == 0x6465780A
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadApk(apkFile: File, apiLevel: Int): MultiDexContainer<out DexBackedDexFile> =
        DexFileFactory.loadDexContainer(apkFile, if (apiLevel < 0) Opcodes.getDefault() else Opcodes.forApi(apiLevel))

    @JvmStatic
    @Throws(IOException::class)
    fun storeDex(classDefList: List<ClassDef>, dataStore: DexDataStore, apiLevel: Int) {
        val opcodes = if (apiLevel < 0) Opcodes.getDefault() else Opcodes.forApi(apiLevel)
        val dexPool = DexPool(opcodes)
        for (classDef in classDefList) dexPool.internClass(classDef)
        dexPool.writeTo(dataStore)
    }

    @JvmStatic
    @Throws(IOException::class, RecognitionException::class)
    fun toClassDef(smaliFile: File, apiLevel: Int): ClassDef =
        InputStreamReader(FileInputStream(smaliFile), StandardCharsets.UTF_8).use { toClassDef(it, apiLevel) }

    @JvmStatic
    @Throws(IOException::class, RecognitionException::class)
    fun toClassDef(smaliContents: String, apiLevel: Int): ClassDef =
        StringReader(smaliContents).use { toClassDef(it, apiLevel) }

    @JvmStatic
    @Throws(IOException::class, RecognitionException::class)
    fun toClassDef(smaliReader: Reader, apiLevel: Int): ClassDef {
        val opcodes = if (apiLevel < 0) Opcodes.getDefault() else Opcodes.forApi(apiLevel)
        val lexer = smaliFlexLexer(smaliReader, opcodes.api)
        val tokens = CommonTokenStream(lexer)
        val parser = smaliParser(tokens).apply {
            setVerboseErrors(false)
            setAllowOdex(false)
            setApiLevel(opcodes.api)
        }
        val result = parser.smali_file()
        if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
            throw IOException("${parser.numberOfSyntaxErrors + lexer.numberOfSyntaxErrors} syntax errors during parsing and/or lexing.")
        }
        val treeStream = CommonTreeNodeStream(result.tree).apply { setTokenStream(tokens) }
        val dexBuilder = com.android.tools.smali.dexlib2.writer.builder.DexBuilder(opcodes)
        val dexGen = smaliTreeWalker(treeStream).apply {
            setApiLevel(opcodes.api)
            setVerboseErrors(false)
            setDexBuilder(dexBuilder)
        }
        val classDef = dexGen.smali_file()
        if (dexGen.numberOfSyntaxErrors > 0) {
            throw IOException("${dexGen.numberOfSyntaxErrors} syntax errors during dex creation")
        }
        return classDef ?: throw IOException("Could not generate class from smali.")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun toJavaCode(classDefs: List<ClassDef>, opcodes: Opcodes): String {
        val tmp = FileUtils.createTempFile(".dex")
        return try {
            val pool = DexPool(opcodes)
            for (classDef in classDefs) pool.internClass(classDef)
            pool.writeTo(FileDataStore(tmp))
            toJavaCode(tmp)
        } finally {
            tmp.delete()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun toJavaCode(classDef: ClassDef, opcodes: Opcodes): String {
        val tmp = FileUtils.createTempFile(".dex")
        return try {
            val pool = DexPool(opcodes)
            pool.internClass(classDef)
            pool.writeTo(FileDataStore(tmp))
            toJavaCode(tmp)
        } finally {
            tmp.delete()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun toJavaCode(smaliContents: List<String>, api: Int): String {
        val opcodes = if (api < 0) Opcodes.getDefault() else Opcodes.forApi(api)
        return try {
            val classDefs = smaliContents.map { toClassDef(it, api) }
            toJavaCode(classDefs, opcodes)
        } catch (e: RecognitionException) {
            throw IOException(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun toJavaCode(smaliContent: String, api: Int): String {
        val opcodes = if (api < 0) Opcodes.getDefault() else Opcodes.forApi(api)
        return try {
            toJavaCode(toClassDef(smaliContent, api), opcodes)
        } catch (e: RecognitionException) {
            throw IOException(e)
        }
    }

    @JvmStatic
    fun toJavaCode(dexFile: File): String {
        val args = JadxArgs().apply {
            setInputFile(dexFile)
            isSkipResources = true
            isShowInconsistentCode = true
            isDebugInfo = BuildConfig.DEBUG
        }
        return JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            val javaClass = decompiler.classes.iterator().next()
            javaClass.decompile()
            javaClass.code
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadDexContainer(inputStream: InputStream, api: Int): DexBackedDexFile {
        val opcodes = if (api < 0) Opcodes.getDefault() else Opcodes.forApi(api)
        try {
            return DexBackedDexFile.fromInputStream(opcodes, inputStream)
        } catch (_: DexBackedDexFile.NotADexFile) {
        }
        try {
            return DexBackedOdexFile.fromInputStream(opcodes, inputStream)
        } catch (_: DexBackedOdexFile.NotAnOdexFile) {
        }
        throw DexFileFactory.UnsupportedFileTypeException("InputStream is not a dex, odex file.")
    }
}
