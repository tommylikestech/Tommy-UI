package com.android.packageinstaller.v2.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class InstallStorageDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = requireActivity() as InstallLaunch
        val args = requireArguments()
        return AlertDialog.Builder(host)
            .setTitle("Install " + args.getString("package") + " on…")
            .setItems(args.getStringArray("labels")) { _, index -> host.selectStorage(index) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> host.finish() }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        requireActivity().finish()
    }
}
