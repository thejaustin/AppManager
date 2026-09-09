// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.LangUtils

class UidOption : FilterOption("uid") {
    override val keysWithType: Map<String, Int> = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "uid_eq" to TYPE_INT,
        "uid_le" to TYPE_INT,
        "uid_ge" to TYPE_INT,
        "with_shared" to TYPE_NONE,
        "without_shared" to TYPE_NONE,
        "shared_uid_name" to TYPE_STR_SINGLE,
        "shared_uid_names" to TYPE_STR_MULTIPLE,
        "shared_uid_name_regex" to TYPE_REGEX,
    )

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult = when (key) {
        KEY_ALL -> result.setMatched(false)
        "uid_eq" -> result.setMatched(info.uid == intValue)
        "uid_le" -> result.setMatched(info.uid <= intValue)
        "uid_ge" -> result.setMatched(info.uid >= intValue)
        "with_shared" -> result.setMatched(info.sharedUserId != null)
        "without_shared" -> result.setMatched(info.sharedUserId == null)
        "shared_uid_name" -> result.setMatched(info.sharedUserId == value)
        "shared_uid_names" -> result.setMatched(ArrayUtils.contains(stringValues, info.sharedUserId))
        "shared_uid_name_regex" -> result.setMatched(regexValue.matcher(info.sharedUserId).matches())
        else -> throw UnsupportedOperationException("Invalid key $key")
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("UID")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "uid_eq" -> sb.append(" = ").append(intValue.toString())
            "uid_le" -> sb.append(" ≤ ").append(intValue.toString())
            "uid_ge" -> sb.append(" ≥ ").append(intValue.toString())
            "with_shared" -> "Only the apps with a shared UID"
            "without_shared" -> "Only the apps without a shared UID"
            "shared_uid_name" -> "Only the apps with the shared UID $value"
            "shared_uid_names" -> "Only the apps with the shared UID (exclusive) ${stringValues.joinToString(", ")}"
            "shared_uid_name_regex" -> "Only the apps with the shared UID that matches '$value'"
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
