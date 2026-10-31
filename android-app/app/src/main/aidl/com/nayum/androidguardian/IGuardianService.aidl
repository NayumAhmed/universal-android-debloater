package com.nayum.androidguardian;

interface IGuardianService {
    void destroy() = 16777114;
    String uninstallForUser(String packageName) = 1;
    String restoreForUser(String packageName) = 2;
}
