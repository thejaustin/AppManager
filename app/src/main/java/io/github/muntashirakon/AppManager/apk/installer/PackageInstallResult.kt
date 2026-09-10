// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class PackageInstallResult(
    val operationId: String,
    val packageName: String,
    @PackageInstallerCompat.Status val status: Int,
    val blockingPackage: String?,
    val statusMessage: String?
) : Parcelable
