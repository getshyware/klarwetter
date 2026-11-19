package de.klarwetter.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.widget.Toast;

/** Bietet das Startbildschirm-Widget an. Android verlangt dafür immer eine Bestätigung durch dich. */
final class WidgetPin {
    static boolean exists(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, WeatherWidget.class));
        return ids != null && ids.length > 0;
    }

    /** Fragt einmalig automatisch nach, sobald ein Ort gespeichert ist und noch kein Widget existiert. */
    static void askOnce(Activity a) {
        android.content.SharedPreferences sp = a.getSharedPreferences("klarwetter", Context.MODE_PRIVATE);
        if (sp.getBoolean("widgetAsked", false) || exists(a) || Store.places(a).isEmpty()) return;
        sp.edit().putBoolean("widgetAsked", true).apply();
        request(a, false);
    }

    /** Öffnet das Android-Fenster „Widget hinzufügen“. */
    static void request(Activity a, boolean manual) {
        if (manual && exists(a)) {
            Toast.makeText(a, "Das Klarwetter-Widget ist schon auf deinem Startbildschirm.", Toast.LENGTH_SHORT).show();
        }
        AppWidgetManager mgr = a.getSystemService(AppWidgetManager.class);
        if (mgr != null && mgr.isRequestPinAppWidgetSupported()) {
            mgr.requestPinAppWidget(new ComponentName(a, WeatherWidget.class), null, null);
        } else if (manual) {
            Toast.makeText(a, "Lange auf den Startbildschirm tippen, dann „Widgets“ → Klarwetter wählen.", Toast.LENGTH_LONG).show();
        }
    }
}
