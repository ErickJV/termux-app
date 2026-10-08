package com.termux.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;

import com.termux.shared.logger.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility class to interact with Samsung DeX hardware key capturing APIs.
 * Uses reflection to access hidden Samsung framework APIs safely without crashing
 * on non-Samsung devices or incompatible Android versions.
 */
public final class SamsungDexUtils {

    private static final String LOG_TAG = "SamsungDexUtils";

    private static Class<?> sSemWindowManagerClass;
    private static Method sGetInstanceMethod;
    private static Method sRequestMetaKeyEventMethod;
    private static boolean sInitialized = false;
    private static boolean sIsAvailable = false;

    private SamsungDexUtils() {
        // Utility class
    }

    private static synchronized void init() {
        if (sInitialized) return;
        try {
            sSemWindowManagerClass = Class.forName("com.samsung.android.view.SemWindowManager");
            sGetInstanceMethod = sSemWindowManagerClass.getMethod("getInstance");
            sRequestMetaKeyEventMethod = sSemWindowManagerClass.getMethod("requestMetaKeyEvent", ComponentName.class, boolean.class);
            sIsAvailable = true;
            Logger.logDebug(LOG_TAG, "Samsung SemWindowManager meta key API found and initialized");
        } catch (ClassNotFoundException e) {
            Logger.logVerbose(LOG_TAG, "SemWindowManager not found on this device");
            sIsAvailable = false;
        } catch (NoSuchMethodException e) {
            Logger.logVerbose(LOG_TAG, "SemWindowManager requestMetaKeyEvent method not found: " + e.getMessage());
            sIsAvailable = false;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error initializing SamsungDexUtils", t);
            sIsAvailable = false;
        } finally {
            sInitialized = true;
        }
    }

    /**
     * Checks if Samsung SemWindowManager and requestMetaKeyEvent method are available.
     *
     * @return true if SemWindowManager meta key capture is supported, false otherwise.
     */
    public static boolean isAvailable() {
        if (!sInitialized) {
            init();
        }
        return sIsAvailable;
    }

    /**
     * Requests or releases capture of the Meta (Win/Cmd) key and system desktop shortcuts
     * in Samsung DeX mode for the specified activity.
     *
     * When enabled, DeX stops intercepting Meta/Win key, Alt+Tab, and global shortcuts,
     * routing raw keyboard events directly to the Termux window.
     *
     * @param activity The activity to receive or release key events.
     * @param enable   True to request exclusive meta key capture, false to release.
     */
    public static void dexMetaKeyCapture(Activity activity, boolean enable) {
        if (activity == null) return;
        if (!isAvailable()) return;

        try {
            Object manager = sGetInstanceMethod.invoke(null);
            if (manager != null) {
                ComponentName componentName = activity.getComponentName();
                sRequestMetaKeyEventMethod.invoke(manager, componentName, enable);
                Logger.logVerbose(LOG_TAG, "dexMetaKeyCapture(" + enable + ") requested for " + componentName);
            }
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to invoke requestMetaKeyEvent(" + enable + ")", t);
        }
    }

    /**
     * Checks if Samsung DeX desktop mode is currently enabled on the device.
     *
     * @param ctx The context to inspect configuration from.
     * @return true if Samsung DeX desktop mode is active, false otherwise.
     */
    public static boolean checkDeXEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            Configuration config = ctx.getResources().getConfiguration();
            Class<?> configClass = config.getClass();
            Field semDesktopModeEnabledField = configClass.getField("semDesktopModeEnabled");
            int mode = semDesktopModeEnabledField.getInt(config);

            int enabledConst = 1;
            try {
                Field semDesktopModeEnabledConst = configClass.getField("SEM_DESKTOP_MODE_ENABLED");
                enabledConst = semDesktopModeEnabledConst.getInt(null);
            } catch (Throwable ignored) {
            }

            boolean isDex = (mode == enabledConst);
            Logger.logVerbose(LOG_TAG, "checkDeXEnabled: " + isDex + " (mode=" + mode + ", enabledConst=" + enabledConst + ")");
            return isDex;
        } catch (Throwable t) {
            return false;
        }
    }
}
