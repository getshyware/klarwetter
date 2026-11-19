package de.klarwetter.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Locale;

/**
 * Startbildschirm-Widget. Zeigt eine Kachel je Widget-Ort; reicht die Breite nicht,
 * geht es in der nächsten Zeile weiter. „•••" öffnet das Widget-Menü.
 */
public class WeatherWidget extends AppWidgetProvider {

    private static final int TILE_MIN_DP = 160;
    private static final int[] DAY = {R.id.w_d1, R.id.w_d2, R.id.w_d3};
    private static final int[] ICON = {R.id.w_i1, R.id.w_i2, R.id.w_i3};
    private static final int[] TEMP = {R.id.w_t1, R.id.w_t2, R.id.w_t3};

    static final String ACTION_PAGE = "de.klarwetter.app.WIDGET_PAGE";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_PAGE.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            int delta = intent.getIntExtra("delta", 1);
            android.content.SharedPreferences sp = ctx.getSharedPreferences("klarwetter", Context.MODE_PRIVATE);
            sp.edit().putInt("page_" + id, sp.getInt("page_" + id, 0) + delta).apply();
            render(ctx.getApplicationContext());
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        final Context app = ctx.getApplicationContext();
        render(app);
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Store.refreshWidget(app);
            } finally {
                render(app);
                pr.finish();
            }
        }).start();
    }

    /** Größe geändert (breiter/schmaler gezogen): Kacheln neu verteilen. */
    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        render(ctx.getApplicationContext());
    }

    /** Zeichnet alle Widgets mit den gespeicherten Daten neu. */
    static void render(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidget.class));
        if (ids == null) return;
        for (int id : ids) {
            Bundle o = mgr.getAppWidgetOptions(id);
            int width = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int height = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            mgr.updateAppWidget(id, build(ctx, id, width, height));
        }
    }

    private static final int TILE_MIN_H_DP = 160;

    /**
     * Verteilt die Orte auf Kacheln in voller Größe. Passen nicht alle hinein, wird geblättert
     * (‹ 1/2 ›) statt die Kacheln zu verkleinern.
     */
    private static RemoteViews build(Context ctx, int widgetId, int widthDp, int heightDp) {
        RemoteViews root = new RemoteViews(ctx.getPackageName(), R.layout.widget_weather);
        root.removeAllViews(R.id.w_rows);
        List<Place> places = Store.widgetPlaces(ctx);
        int n = Math.max(1, places.size());
        int perRow = widthDp <= 0 ? 1 : Math.max(1, Math.min(n, widthDp / TILE_MIN_DP));
        int rowsNeeded = (n + perRow - 1) / perRow;
        int rowsFit = heightDp <= 0 ? 1 : Math.max(1, (heightDp - 20) / TILE_MIN_H_DP);
        int rows = Math.min(rowsNeeded, rowsFit);
        int capacity = perRow * rows;
        int pages = (n + capacity - 1) / capacity;

        android.content.SharedPreferences sp = ctx.getSharedPreferences("klarwetter", Context.MODE_PRIVATE);
        int page = Math.floorMod(sp.getInt("page_" + widgetId, 0), pages);
        int first = page * capacity, last = Math.min(n, first + capacity);
        int tempDp = tempWidthDp(widthDp, perRow);
        int tileH = heightDp <= 0 ? 0 : heightDp / rows - 6;

        RemoteViews row = null;
        int inRow = 0;
        for (int i = first; i < last; i++) {
            if (row == null) {
                row = new RemoteViews(ctx.getPackageName(), R.layout.widget_row);
                inRow = 0;
            }
            Place pl = places.isEmpty() ? null : places.get(i);
            RemoteViews t = tile(ctx, pl, i, i == first, tempDp, tileH);
            if (i == first && pages > 1) {
                t.setViewVisibility(R.id.w_nav, View.VISIBLE);
                t.setTextViewText(R.id.nav_label, (page + 1) + "/" + pages);
                t.setOnClickPendingIntent(R.id.nav_prev, pageIntent(ctx, widgetId, -1));
                t.setOnClickPendingIntent(R.id.nav_next, pageIntent(ctx, widgetId, 1));
            }
            row.addView(R.id.w_row, t);
            inRow++;
            if (inRow == perRow) {
                root.addView(R.id.w_rows, row);
                row = null;
            }
        }
        if (row != null) {
            for (; inRow < perRow; inRow++) row.addView(R.id.w_row, new RemoteViews(ctx.getPackageName(), R.layout.widget_spacer));
            root.addView(R.id.w_rows, row);
        }

        return root;
    }

    private static PendingIntent pageIntent(Context ctx, int widgetId, int delta) {
        Intent i = new Intent(ctx, WeatherWidget.class).setAction(ACTION_PAGE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId).putExtra("delta", delta);
        return PendingIntent.getBroadcast(ctx, 5000 + widgetId * 2 + (delta > 0 ? 1 : 0), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** true, wenn nicht alle Orte gleichzeitig zu sehen sind (dann wird geblättert). */
    static boolean tooNarrow(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidget.class));
        int n = Store.widgetPlaces(ctx).size();
        if (ids == null || n < 2) return false;
        for (int id : ids) {
            Bundle o = mgr.getAppWidgetOptions(id);
            int w = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            int perRow = Math.max(1, w / TILE_MIN_DP), rowsFit = Math.max(1, (h - 20) / TILE_MIN_H_DP);
            if (perRow * rowsFit < n) return true;
        }
        return false;
    }

    static final String RESIZE_HINT = "Mit ‹ › oben im Widget blätterst du zwischen den Orten. "
            + "Willst du sie nebeneinander sehen, ziehe das Widget einmal breiter: "
            + "lange darauf tippen, loslassen und den Rahmen am Rand nach links oder rechts ziehen.";

    /** Platz für die große Temperatur: Kachelbreite minus Ränder und 3-Tage-Spalten. */
    private static int tempWidthDp(int widthDp, int perRow) {
        int tile = (widthDp > 0 ? widthDp / perRow : 170) - 6 - 20;
        return Math.max(40, tile - 3 * 40 - 4);
    }

    /**
     * Passt die Kachel an ihre Höhe an, statt etwas abzuschneiden:
     * zu niedrig → erst „Gefühlte Temperatur“, dann die 3 Tage, dann „Tags/Nachts“ ausblenden.
     */
    private static void fitHeight(RemoteViews v, int tileH) {
        if (tileH <= 0 || tileH >= 212) return;
        v.setViewVisibility(R.id.w_feel, View.GONE);
        if (tileH >= 198) return;
        v.setViewVisibility(R.id.w_fc, View.GONE);
        if (tileH >= 162) return;
        v.setViewVisibility(R.id.w_hl, View.GONE);
    }

    private static RemoteViews tile(Context ctx, Place pl, int slot, boolean withMenu, int tempDp, int tileH) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_tile);
        Weather w = Store.cached(ctx, pl);
        String unit = Store.unit(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        // Menü „•••" nur in der ersten Kachel
        v.setViewVisibility(R.id.w_menu, withMenu ? View.VISIBLE : View.INVISIBLE);
        if (withMenu) {
            Intent menu = new Intent(ctx, WidgetMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            v.setOnClickPendingIntent(R.id.w_menu, PendingIntent.getActivity(ctx, 1, menu, flags));
        }

        Intent open;
        if (pl != null && w != null) {
            open = new Intent(ctx, DetailActivity.class).putExtra(DetailActivity.EXTRA_INDEX, Store.indexOf(ctx, pl));
        } else {
            open = new Intent(ctx, EditActivity.class);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 100 + slot, open, flags));

        if (pl == null || w == null) {
            v.setTextViewText(R.id.w_place, pl == null ? "Kein Ort" : Fmt.wrapName(pl.name));
            v.setImageViewBitmap(R.id.w_temp, TempArt.make("–", "", Math.round(tempDp * density), Math.round(56 * density), 0xFFFFFFFF));
            v.setTextViewText(R.id.w_hl, pl == null ? "Tippe hier, um einen Ort festzulegen" : "Wetter wird geladen …");
            v.setTextViewText(R.id.w_feel, "");
            v.setViewVisibility(R.id.w_icon, View.INVISIBLE);
            v.setTextViewText(R.id.w_cond, "");
            v.setViewVisibility(R.id.w_fc, View.INVISIBLE);
            fitHeight(v, tileH);
            return v;
        }
        v.setImageViewBitmap(R.id.w_icon, Icons.make(Fmt.kind(w.curCode, w.curDay), Math.round(52 * density), true));
        v.setTextViewText(R.id.w_place, Fmt.wrapName(pl.name));
        v.setTextViewText(R.id.w_cond, Fmt.condShort(w.curCode, w.curDay));
        String num = String.valueOf(Fmt.t(w.curTemp, unit)).replace('-', '\u2212');
        v.setImageViewBitmap(R.id.w_temp, TempArt.make(num, "°" + unit, Math.round(tempDp * density), Math.round(56 * density), 0xFFFFFFFF));
        v.setTextViewText(R.id.w_hl, "Tags: " + Fmt.t(w.dMax[0], unit) + "° ↑ • Nachts: " + Fmt.t(w.dMin[0], unit) + "° ↓");
        v.setTextViewText(R.id.w_feel, "Gefühlte Temperatur: " + Fmt.t(w.curFeel, unit) + "°");

        // Heute und die nächsten zwei Tage
        int iconPx = Math.round(26 * density);
        for (int k = 0; k < 3 && k < w.dTime.length; k++) {
            v.setTextViewText(DAY[k], Fmt.weekdayShort(w.dTime[k]).replace(".", "").toUpperCase(Locale.GERMAN));
            v.setImageViewBitmap(ICON[k], Icons.make(Fmt.kind(w.dCode[k], true), iconPx, true));
            v.setTextViewText(TEMP[k], Fmt.t(w.dMin[k], unit) + "/" + Fmt.t(w.dMax[k], unit) + "°");
        }
        v.setContentDescription(R.id.w_root, "Wetter in " + pl.name + ": " + Fmt.t(w.curTemp, unit) + " Grad, "
                + Fmt.cond(w.curCode, w.curDay) + ". Tippen öffnet die Details.");
        fitHeight(v, tileH);
        return v;
    }
}
