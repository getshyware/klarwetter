package de.klarwetter.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Vollansicht: aktuelle Werte, Verlauf (Temperatur/Niederschlag/Wind) und 8 Tage. Wischen wechselt den Ort. */
public class DetailActivity extends Activity {
    static final String EXTRA_INDEX = "index";

    private List<Place> places;
    private int index;
    private GestureDetector swipe;
    private Weather w;
    private Place place;
    private String unit;
    private int tab = ChartView.TEMP;
    private int sel = 0;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_detail);
        Ui.edgeToEdge(this, findViewById(R.id.root));
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (s != null) { tab = s.getInt("tab", tab); sel = s.getInt("sel", 0); index = s.getInt("index", index); }

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.refresh).setOnClickListener(v -> refresh(true));
        findViewById(R.id.u_c).setOnClickListener(v -> setUnit("C"));
        findViewById(R.id.u_f).setOnClickListener(v -> setUnit("F"));
        findViewById(R.id.tab_temp).setOnClickListener(v -> { tab = ChartView.TEMP; render(); });
        findViewById(R.id.tab_rain).setOnClickListener(v -> { tab = ChartView.RAIN; render(); });
        findViewById(R.id.tab_wind).setOnClickListener(v -> { tab = ChartView.WIND; render(); });

        unit = Store.unit(this);
        places = Store.places(this);
        if (places.isEmpty()) { finish(); return; }
        final float minDist = 90 * getResources().getDisplayMetrics().density;
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < minDist || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                showPlace(index + (dx < 0 ? 1 : -1));
                return true;
            }
        });
        showPlace(index);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipe != null && places != null && places.size() > 1 && swipe.onTouchEvent(ev)) {
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Wechselt zum Ort an Position i (Wischen nach links/rechts). */
    private void showPlace(int i) {
        if (i < 0 || i >= places.size()) return;
        index = i;
        place = places.get(i);
        w = Store.cached(this, place);
        sel = 0;
        renderDots();
        render();
        if (w == null || Store.isStale(this, place)) refresh(false);
    }

    private void renderDots() {
        LinearLayout dots = findViewById(R.id.dots);
        dots.removeAllViews();
        if (places.size() < 2) return;
        for (int i = 0; i < places.size(); i++) {
            TextView d = new TextView(this);
            d.setText("●");
            d.setTextSize(i == index ? 11 : 8);
            d.setTextColor(getColor(i == index ? R.color.fg : R.color.line));
            d.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            dots.addView(d);
        }
        dots.setContentDescription("Ort " + (index + 1) + " von " + places.size() + ". Zum Wechseln nach links oder rechts wischen.");
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putInt("tab", tab);
        o.putInt("sel", sel);
        o.putInt("index", index);
    }

    private void setUnit(String u) {
        unit = u;
        Store.setUnit(this, u);
        render();
        WeatherWidget.render(getApplicationContext());
    }

    private void refresh(boolean manual) {
        final android.content.Context app = getApplicationContext();
        final Place target = place;
        final boolean isWidgetPlace = index == 0;
        Ui.BG.execute(() -> {
            Weather nw = null;
            try { nw = Store.refresh(app, target); } catch (Exception ignored) { }
            final Weather f = nw;
            Ui.MAIN.post(() -> {
                if (isFinishing()) return;
                if (isWidgetPlace && f != null) WeatherWidget.render(app);
                if (target != place) return; // inzwischen weitergewischt
                if (f != null) { w = f; render(); }
                else if (manual || w == null)
                    Toast.makeText(app, "Keine Verbindung zum Wetterdienst. Angezeigt werden die zuletzt geladenen Werte.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void render() {
        TextView tPlace = findViewById(R.id.d_place), tTime = findViewById(R.id.d_time), tCond = findViewById(R.id.d_cond),
                tTemp = findViewById(R.id.d_temp), tFacts = findViewById(R.id.d_facts), uc = findViewById(R.id.u_c),
                uf = findViewById(R.id.u_f), foot = findViewById(R.id.d_foot);
        tPlace.setText(place.label());
        uc.setTextColor(getColor("C".equals(unit) ? R.color.fg : R.color.muted));
        uf.setTextColor(getColor("F".equals(unit) ? R.color.fg : R.color.muted));
        if (w == null) {
            tTime.setText("Wetter wird geladen …");
            return;
        }
        if (sel >= w.dTime.length) sel = 0;

        double temp, hum, wind;
        int code, rain;
        boolean day;
        String time;
        if (sel == 0) {
            int ci = w.currentHour();
            temp = w.curTemp; code = w.curCode; day = w.curDay; hum = w.curHum; wind = w.curWind; rain = w.hRain[ci];
            time = cap(Fmt.weekdayLong(w.curTime)) + ", " + Fmt.hm(w.curTime);
        } else {
            int i = Math.max(0, w.hourIndex(w.dTime[sel] + "T12:00"));
            temp = w.hTemp[i]; code = w.dCode[sel]; day = true; hum = w.hHum[i]; wind = w.hWind[i]; rain = w.dRain[sel];
            time = cap(Fmt.weekdayLong(w.dTime[sel])) + ", 12:00";
        }
        ((ImageView) findViewById(R.id.d_icon)).setImageBitmap(Icons.make(Fmt.kind(code, day), Ui.dp(this, 64), false));
        tTemp.setText(String.valueOf(Fmt.t(temp, unit)));
        tTime.setText(time);
        tCond.setText(Fmt.cond(code, day));
        tFacts.setText("Niederschlag: " + rain + "%\nLuftfeuchte: " + Math.round(hum) + "%\nWind: " + Math.round(wind) + " km/h");

        findViewById(R.id.tab_temp_line).setVisibility(tab == ChartView.TEMP ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.tab_rain_line).setVisibility(tab == ChartView.RAIN ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.tab_wind_line).setVisibility(tab == ChartView.WIND ? View.VISIBLE : View.INVISIBLE);

        int[] idx = w.hoursFor(sel);
        String[] x = new String[idx.length];
        double[] v = new double[idx.length], d = new double[idx.length];
        for (int k = 0; k < idx.length; k++) {
            int i = idx[k];
            x[k] = Fmt.hm(w.hTime[i]);
            v[k] = tab == ChartView.TEMP ? Fmt.t(w.hTemp[i], unit) : tab == ChartView.RAIN ? w.hRain[i] : w.hWind[i];
            d[k] = w.hDir[i];
        }
        ((ChartView) findViewById(R.id.chart)).set(tab, x, v, d, sel == 0 && tab == ChartView.TEMP);

        renderDays();
        String at = Instant.ofEpochMilli(Store.cachedAt(this, place)).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        foot.setText("Stand: " + at + " Uhr · Daten: Open-Meteo.com (CC BY 4.0) · Kein Tracking, keine Cookies.");
    }

    private void renderDays() {
        LinearLayout box = findViewById(R.id.days);
        box.removeAllViews();
        int n = Math.min(8, w.dTime.length);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        int perRow = wide ? 8 : 4;
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            final int di = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = Ui.dp(this, 8);
            cell.setPadding(0, p, 0, p + Ui.dp(this, 2));
            if (i == sel) cell.setBackgroundResource(R.drawable.day_sel);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(this, 1), Ui.dp(this, 3), Ui.dp(this, 1), Ui.dp(this, 3));
            cell.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(Fmt.weekdayShort(w.dTime[i]));
            name.setTextColor(getColor(R.color.fg));
            name.setTextSize(16);
            cell.addView(name);

            ImageView ic = new ImageView(this);
            ic.setImageBitmap(Icons.make(Fmt.kind(w.dCode[i], true), Ui.dp(this, 40), false));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            ilp.topMargin = Ui.dp(this, 4);
            ilp.bottomMargin = Ui.dp(this, 4);
            cell.addView(ic, ilp);

            String max = Fmt.t(w.dMax[i], unit) + "°", min = Fmt.t(w.dMin[i], unit) + "°";
            SpannableStringBuilder sb = new SpannableStringBuilder(max + "  " + min);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, max.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.muted)), max.length() + 2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView mm = new TextView(this);
            mm.setText(sb);
            mm.setTextColor(getColor(R.color.fg));
            mm.setTextSize(13);
            cell.addView(mm);

            cell.setContentDescription(cap(Fmt.weekdayLong(w.dTime[i])) + ": " + Fmt.cond(w.dCode[i], true)
                    + ", höchstens " + max + ", mindestens " + min);
            cell.setClickable(true);
            cell.setOnClickListener(v -> { sel = di; render(); });
            row.addView(cell);
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
