// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.security.cert.X509Certificate
import java.util.regex.Pattern

object CertUtils {
    @JvmStatic
    fun getReadableSubject(cert: X509Certificate): String {
        val x500Name = X500Name.getInstance(cert.subjectX500Principal.encoded)
        return listOf(BCStyle.CN, BCStyle.O, BCStyle.L, BCStyle.ST, BCStyle.C)
            .map { oid -> x500Name.getRDNs(oid) }
            .filter { it.isNotEmpty() }
            .map { it[0].first.value.toString() }
            .joinToString(", ")
    }

    @JvmStatic
    fun getReadableSubject(subjectLine: String): String {
        val rdnMap = mutableMapOf<String, String>()
        val matcher = Pattern.compile("([^=,]+)=(((?<=\\\\),|[^,])+)").matcher(subjectLine)
        while (matcher.find()) {
            val key = matcher.group(1)!!.trim().uppercase()
            val value = matcher.group(2)!!.trim().replace("\\,", ",")
            rdnMap[key] = value
        }
        return listOf("CN", "O", "L", "ST", "C")
            .mapNotNull { rdnMap[it]?.takeIf { v -> v.isNotEmpty() } }
            .joinToString(", ")
    }
}
