// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.app

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import android.content.Context
import java.util.Optional

open class AndroidFragment : Fragment() {
    protected fun getFragmentContext(): Optional<Context> = Optional.ofNullable(context)

    protected fun getFragmentActivity(): Optional<FragmentActivity> = Optional.ofNullable(activity)

    protected fun getActionBar(): Optional<ActionBar> {
        val activity = activity
        if (activity is AppCompatActivity) {
            return Optional.ofNullable(activity.supportActionBar)
        }
        return Optional.empty()
    }
}
