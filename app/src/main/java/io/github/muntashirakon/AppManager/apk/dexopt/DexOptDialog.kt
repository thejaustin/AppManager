// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchDexOptOptions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter

class DexOptDialog : DialogFragment() {
    private val mOptions = DexOptOptions.getDefault()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mOptions.setPackages(requireArguments().getStringArray(ARG_PACKAGES))
        val uid = Users.getSelfOrRemoteUid()
        val isRootOrSystem = uid == Ops.SYSTEM_UID || uid == Ops.ROOT_UID
        val view = View.inflate(requireContext(), R.layout.dialog_dexopt, null)
        val compilerFilterSelectionView = view.findViewById<android.widget.AutoCompleteTextView>(R.id.compiler_filter)
        val compileLayoutsCheck = view.findViewById<MaterialCheckBox>(R.id.compile_layouts)
        val clearProfileDataCheck = view.findViewById<MaterialCheckBox>(R.id.clear_profile_data)
        val checkProfilesCheck = view.findViewById<MaterialCheckBox>(R.id.check_profiles)
        val forceCompilationCheck = view.findViewById<MaterialCheckBox>(R.id.force_compilation)
        val forceDexOptCheck = view.findViewById<MaterialCheckBox>(R.id.force_dexopt)
        compilerFilterSelectionView.setText(mOptions.getCompilerFilter())
        checkProfilesCheck.isChecked = mOptions.getCheckProfiles()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            compileLayoutsCheck.visibility = View.GONE
        }
        if (!isRootOrSystem) {
            clearProfileDataCheck.visibility = View.GONE
            forceDexOptCheck.visibility = View.GONE
        }

        compilerFilterSelectionView.setAdapter(
            AnyFilterArrayAdapter(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, COMPILER_FILTERS)
        )
        compileLayoutsCheck.setOnCheckedChangeListener { _, isChecked -> mOptions.setCompileLayouts(isChecked) }
        clearProfileDataCheck.setOnCheckedChangeListener { _, isChecked -> mOptions.setClearProfileData(isChecked) }
        checkProfilesCheck.setOnCheckedChangeListener { _, isChecked -> mOptions.setCheckProfiles(isChecked) }
        forceCompilationCheck.setOnCheckedChangeListener { _, isChecked -> mOptions.setForceCompilation(isChecked) }
        forceDexOptCheck.setOnCheckedChangeListener { _, isChecked -> mOptions.setForceDexOpt(isChecked) }
        if (isRootOrSystem) {
            forceDexOptCheck.isChecked = true
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_perform_runtime_optimization_to_apps)
            .setView(view)
            .setPositiveButton(R.string.action_run) { _, _ ->
                val compilerFilterRaw = compilerFilterSelectionView.text
                if (compilerFilterRaw.isNullOrEmpty()) return@setPositiveButton
                val compilerFilter = compilerFilterRaw.toString().trim()
                if (!COMPILER_FILTERS.contains(compilerFilter)) return@setPositiveButton
                mOptions.setCompilerFilter(compilerFilter)
                launchOp()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default) { _, _ ->
                mOptions.setCompilerFilter(DexOptOptions.getDefaultCompilerFilterForInstallation())
                mOptions.setForceCompilation(true)
                mOptions.setClearProfileData(true)
                launchOp()
            }
            .create()
    }

    private fun launchOp() {
        val options = BatchDexOptOptions(mOptions)
        val queueItem = BatchQueueItem.getBatchOpQueue(BatchOpsManager.OP_DEXOPT, null, null, options)
        val intent = BatchOpsService.getServiceIntent(requireContext(), queueItem)
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    companion object {
        const val TAG: String = "DexOptDialog"
        private const val ARG_PACKAGES = "pkg"

        private val COMPILER_FILTERS: List<String> = buildList {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                add("verify-none")
                add("verify-at-runtime")
                add("verify-profile")
                add("interpret-only")
                add("time")
                add("balanced")
            } else {
                add("assume-verified")
                add("extract")
                add("verify")
                add("quicken")
            }
            add("space")
            add("space-profile")
            add("speed")
            add("speed-profile")
            add("everything")
            add("everything-profile")
        }

        @JvmStatic
        fun getInstance(packages: Array<String>?): DexOptDialog = DexOptDialog().apply {
            arguments = Bundle().apply { putStringArray(ARG_PACKAGES, packages) }
        }
    }
}
