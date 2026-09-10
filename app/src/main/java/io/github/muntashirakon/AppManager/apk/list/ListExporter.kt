// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandleHidden
import android.util.Xml
import androidx.annotation.IntDef
import androidx.core.content.pm.PackageInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.csv.CsvWriter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.Writer

object ListExporter {
    const val EXPORT_TYPE_CSV = 0
    const val EXPORT_TYPE_JSON = 1
    const val EXPORT_TYPE_XML = 2
    const val EXPORT_TYPE_MARKDOWN = 3

    @IntDef(EXPORT_TYPE_CSV, EXPORT_TYPE_JSON, EXPORT_TYPE_XML, EXPORT_TYPE_MARKDOWN)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ExportType

    @JvmStatic
    @Throws(IOException::class)
    fun export(context: Context, writer: Writer, @ExportType exportType: Int, packageInfoList: List<PackageInfo>) {
        val appListItems = getAppListItems(context, packageInfoList)
        when (exportType) {
            EXPORT_TYPE_CSV -> exportCsv(writer, appListItems)
            EXPORT_TYPE_JSON -> try {
                exportJson(writer, appListItems)
            } catch (e: JSONException) {
                ExUtils.rethrowAsIOException(e)
            }
            EXPORT_TYPE_XML -> exportXml(writer, appListItems)
            EXPORT_TYPE_MARKDOWN -> exportMarkdown(context, writer, appListItems)
            else -> throw IllegalArgumentException("Invalid export type: $exportType")
        }
    }

    @Throws(IOException::class)
    private fun exportXml(writer: Writer, appListItems: List<AppListItem>) {
        val xmlSerializer = Xml.newSerializer()
        xmlSerializer.setOutput(writer)
        xmlSerializer.startDocument("UTF-8", true)
        xmlSerializer.docdecl("packages SYSTEM \"https://raw.githubusercontent.com/MuntashirAkon/AppManager/master/schema/packages.dtd\"")
        xmlSerializer.startTag("", "packages")
        xmlSerializer.attribute("", "version", "1")
        for (item in appListItems) {
            xmlSerializer.startTag("", "package")
            xmlSerializer.attribute("", "name", item.getPackageName())
            xmlSerializer.attribute("", "label", item.getPackageLabel())
            xmlSerializer.attribute("", "versionCode", item.getVersionCode().toString())
            xmlSerializer.attribute("", "versionName", item.getVersionName())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                xmlSerializer.attribute("", "minSdk", item.getMinSdk().toString())
            }
            xmlSerializer.attribute("", "targetSdk", item.getTargetSdk().toString())
            xmlSerializer.attribute("", "signature", item.getSignatureSha256())
            xmlSerializer.attribute("", "firstInstallTime", item.getFirstInstallTime().toString())
            xmlSerializer.attribute("", "lastUpdateTime", item.getLastUpdateTime().toString())
            val installerPackageName = item.getInstallerPackageName()
            if (installerPackageName != null) {
                xmlSerializer.attribute("", "installerPackageName", installerPackageName)
                val installerPackageLabel = item.getInstallerPackageLabel()
                if (installerPackageLabel != null) {
                    xmlSerializer.attribute("", "installerPackageLabel", installerPackageLabel)
                }
            }
            xmlSerializer.endTag("", "package")
        }
        xmlSerializer.endTag("", "packages")
        xmlSerializer.endDocument()
        xmlSerializer.flush()
    }

    @Throws(IOException::class)
    private fun exportCsv(writer: Writer, appListItems: List<AppListItem>) {
        val csvWriter = CsvWriter(writer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            csvWriter.addLine(arrayOf("name", "label", "versionCode", "versionName", "minSdk",
                "targetSdk", "signature", "firstInstallTime", "lastUpdateTime",
                "installerPackageName", "installerPackageLabel"))
        } else {
            csvWriter.addLine(arrayOf("name", "label", "versionCode", "versionName",
                "targetSdk", "signature", "firstInstallTime", "lastUpdateTime",
                "installerPackageName", "installerPackageLabel"))
        }
        for (item in appListItems) {
            val installerPackage = item.getInstallerPackageName() ?: ""
            val installerLabel = item.getInstallerPackageLabel() ?: ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                csvWriter.addLine(arrayOf(item.getPackageName(), item.getPackageLabel(),
                    item.getVersionCode().toString(), item.getVersionName(),
                    item.getMinSdk().toString(), item.getTargetSdk().toString(),
                    item.getSignatureSha256(), item.getFirstInstallTime().toString(),
                    item.getLastUpdateTime().toString(), installerPackage, installerLabel))
            } else {
                csvWriter.addLine(arrayOf(item.getPackageName(), item.getPackageLabel(),
                    item.getVersionCode().toString(), item.getVersionName(),
                    item.getTargetSdk().toString(), item.getSignatureSha256(),
                    item.getFirstInstallTime().toString(), item.getLastUpdateTime().toString(),
                    installerPackage, installerLabel))
            }
        }
    }

    @Throws(JSONException::class, IOException::class)
    private fun exportJson(writer: Writer, appListItems: List<AppListItem>) {
        val array = JSONArray()
        for (item in appListItems) {
            val obj = JSONObject()
            obj.put("name", item.getPackageName())
            obj.put("label", item.getPackageLabel())
            obj.put("versionCode", item.getVersionCode())
            obj.put("versionName", item.getVersionName())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                obj.put("minSdk", item.getMinSdk())
            }
            obj.put("targetSdk", item.getTargetSdk())
            obj.put("signature", item.getSignatureSha256())
            obj.put("firstInstallTime", item.getFirstInstallTime())
            obj.put("lastUpdateTime", item.getLastUpdateTime())
            val installerPackageName = item.getInstallerPackageName()
            if (installerPackageName != null) {
                obj.put("installerPackageName", installerPackageName)
                val installerPackageLabel = item.getInstallerPackageLabel()
                if (installerPackageLabel != null) {
                    obj.put("installerPackageLabel", installerPackageLabel)
                }
            }
            array.put(obj)
        }
        writer.write(array.toString(4))
    }

    @Throws(IOException::class)
    private fun exportMarkdown(context: Context, writer: Writer, appListItems: List<AppListItem>) {
        writer.write("# Package Info\n\n")
        for (item in appListItems) {
            writer.append("## ").append(item.getPackageLabel()).append("\n\n")
                .append("**Package name:** ").append(item.getPackageName()).append("\n")
                .append("**Version:** ").append(item.getVersionName()).append(" (")
                .append(item.getVersionCode().toString()).append(")\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                writer.append("**Min SDK:** ").append(item.getMinSdk().toString()).append(", ")
            }
            writer.append("**Target SDK:** ").append(item.getTargetSdk().toString()).append("\n")
                .append("**Date installed:** ")
                .append(DateUtils.formatDateTime(context, item.getFirstInstallTime()))
                .append(", **Date updated:** ")
                .append(DateUtils.formatDateTime(context, item.getLastUpdateTime()))
                .append("\n")
            val installerPackageName = item.getInstallerPackageName()
            if (installerPackageName != null) {
                writer.append("**Installer:** ")
                val installerPackageLabel = item.getInstallerPackageLabel()
                if (installerPackageLabel != null) {
                    writer.append(installerPackageLabel).append(" (")
                }
                writer.append(installerPackageName)
                if (installerPackageLabel != null) {
                    writer.append(")")
                }
            }
            writer.append("\n\n")
        }
    }

    private fun getAppListItems(context: Context, packageInfoList: List<PackageInfo>): List<AppListItem> {
        val pm = context.packageManager
        return packageInfoList.map { packageInfo ->
            val applicationInfo = packageInfo.applicationInfo
            AppListItem(packageInfo.packageName).apply {
                setIcon(UIUtils.getBitmapFromDrawable(applicationInfo.loadIcon(pm)))
                setPackageLabel(applicationInfo.loadLabel(pm).toString())
                setVersionCode(PackageInfoCompat.getLongVersionCode(packageInfo))
                setVersionName(packageInfo.versionName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setMinSdk(applicationInfo.minSdkVersion)
                }
                setTargetSdk(applicationInfo.targetSdkVersion)
                val signatureSha256 = PackageUtils.getSigningCertSha256Checksum(packageInfo, false)
                setSignatureSha256(signatureSha256.joinToString(","))
                setFirstInstallTime(packageInfo.firstInstallTime)
                setLastUpdateTime(packageInfo.lastUpdateTime)
                val installerPkg = PackageManagerCompat.getInstallerPackageName(
                    packageInfo.packageName, UserHandleHidden.getUserId(applicationInfo.uid))
                if (installerPkg != null) {
                    setInstallerPackageName(installerPkg)
                    try {
                        val installerLabel = pm.getApplicationInfo(installerPkg, 0).loadLabel(pm).toString()
                        if (installerLabel != installerPkg) {
                            setInstallerPackageLabel(installerLabel)
                        }
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
        }
    }
}
