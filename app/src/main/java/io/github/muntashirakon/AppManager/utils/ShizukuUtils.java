// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuUtils {

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            Shizuku.requestPermission(requestCode);
        } catch (IllegalStateException e) {
            // Shizuku is not installed or not running
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static Integer runCommand(@NonNull String command) {
        if (!isShizukuAvailable()) {
            return null;
        }
        try {
            ShizukuRemoteProcess process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, "/");
            if (process != null) {
                process.waitFor();
                return process.exitValue();
            }
        } catch (RemoteException | InterruptedException e) {
            return null;
        }
        return null;
    }
}
