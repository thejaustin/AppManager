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
            return Shizuku.isPreV11() ? Shizuku.checkPermission(0) == 0 : Shizuku.checkSelfPermission() == 0;
        } catch (Exception e) {
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
    public static String runCommand(@NonNull String command) {
        if (!isShizukuAvailable()) {
            return null;
        }
        try {
            ShizukuRemoteProcess process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
            if (process != null) {
                // Read the output and error streams
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                return output.toString();
            }
        } catch (RemoteException | InterruptedException e) {
            return null;
        }
        return null;
    }
}
