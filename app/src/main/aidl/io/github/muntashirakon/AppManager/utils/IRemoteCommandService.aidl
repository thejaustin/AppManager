// IRemoteCommandService.aidl
package io.github.muntashirakon.AppManager.utils;

interface IRemoteCommandService {
    int runCommand(String command);
    Bundle executeJavaCode(String className, String methodName, in Bundle args);
}