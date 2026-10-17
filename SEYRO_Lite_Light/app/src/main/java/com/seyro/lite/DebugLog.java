package com.seyro.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small persistent diagnostic log. Never pass subscription URLs or raw Xray configs here. */
public final class DebugLog {
    private static final String TAG = "SEYRO-DEBUG";
    private static final String PREFS = "seyro_debug_log";
    private static final String KEY = "entries";
    private static final int MAX_CHARS = 80000;
    private static final Object LOCK = new Object();

    private DebugLog() {}

    public static void v(Context context, String area, String message) { if (BuildConfig.DEBUG) write(context, "V", area, message, null); }
    public static void d(Context context, String area, String message) { if (BuildConfig.DEBUG) write(context, "D", area, message, null); }
    public static void i(Context context, String area, String message) { if (!BuildConfig.DEBUG && !isLifecycleEvent(area, message)) return; write(context, "I", area, message, null); }
    private static boolean isLifecycleEvent(String area, String message) { if ("APP".equals(area) || "SERVICE".equals(area) || "STATE".equals(area)) return true; if (!"VPN".equals(area)) return false; String m=message==null?"":message.toLowerCase(Locale.ROOT); return m.contains("отключение завершено") || m.contains("запущен") || m.contains("ошибка") || m.contains("начинается запуск"); }
    public static void w(Context context, String area, String message) { write(context, "W", area, message, null); }
    public static void e(Context context, String area, String message) { write(context, "E", area, message, null); }

    private static void write(Context context, String level, String area, String message, Throwable error) {
        String safe = message == null ? "" : message;
        // Defensive redaction for common credential-bearing values.
        safe = safe.replaceAll("(?i)vless://[^\\s]+", "vless://[REDACTED]")
                   .replaceAll("(?i)(token|authorization|password|uuid|publicKey|privateKey)=?[: ]+[^\\s,;]+", "$1=[REDACTED]");
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + level + "/" + area + ": " + safe;
        if ("E".equals(level)) Log.e(TAG, line, error);
        else if ("W".equals(level)) Log.w(TAG, line);
        else if ("D".equals(level)) Log.d(TAG, line);
        else if ("V".equals(level)) Log.v(TAG, line);
        else Log.i(TAG, line);
        synchronized (LOCK) {
            try {
                SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String old = prefs.getString(KEY, "");
                String all = old + line + "\n";
                if (all.length() > MAX_CHARS) all = all.substring(all.length() - MAX_CHARS);
                prefs.edit().putString(KEY, all).apply();
            } catch (Exception e) {
                Log.e(TAG, "Could not persist diagnostic log", e);
            }
        }
    }

    public static String read(Context context) {
        synchronized (LOCK) {
            return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
        }
        i(context, "LOG", "Журнал очищен");
    }
}
