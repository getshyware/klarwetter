package de.klarwetter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/** Verlaufsdiagramm für Temperatur, Niederschlag und Wind (8 Werte im 3-Stunden-Abstand). */
public class ChartView extends View {
    public static final int TEMP = 0, RAIN = 1, WIND = 2;

    private int mode = TEMP;
    private String[] xLabels = new String[0];
    private double[] values = new double[0];
    private double[] dirs = new double[0];
    private boolean boldFirst;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public ChartView(Context c) { this(c, null); }

    public ChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
        axis.setStrokeWidth(dp);
    }

    void set(int mode, String[] x, double[] v, double[] dirs, boolean boldFirst) {
        this.mode = mode;
        this.xLabels = x;
        this.values = v;
        this.dirs = dirs;
        this.boldFirst = boldFirst;
        StringBuilder sb = new StringBuilder(mode == TEMP ? "Temperaturverlauf: " : mode == RAIN ? "Niederschlag: " : "Wind: ");
        for (int i = 0; i < v.length; i++) sb.append(x[i]).append(' ').append(label(i)).append(", ");
        setContentDescription(sb.toString());
        invalidate();
    }

    private String label(int i) {
        long v = Math.round(values[i]);
        return mode == RAIN ? v + "%" : mode == WIND ? v + " km/h" : String.valueOf(v);
    }

    private int col(int id) { return getContext().getColor(id); }

    @Override
    protected void onDraw(Canvas c) {
        int n = values.length;
        if (n == 0) return;
        float W = getWidth(), H = getHeight();
        float step = W / 8f;
        float labelH = 24 * dp, bottom = H - labelH, top = 22 * dp;
        int muted = col(R.color.muted), fg = col(R.color.fg);

        // Zeitachse
        text.setTypeface(Typeface.DEFAULT);
        text.setColor(muted);
        float tSize = text.getTextSize();
        text.setTextSize(tSize * 1.08f);
        for (int i = 0; i < n; i++) c.drawText(xLabels[i], (i + .5f) * step, H - 6 * dp, text);
        text.setTextSize(tSize);

        float[] xs = new float[n];
        for (int i = 0; i < n; i++) xs[i] = (i + .5f) * step;

        if (mode == RAIN) {
            float bw = Math.min(28 * dp, step * .45f), maxH = bottom - top - 18 * dp;
            axis.setColor(col(R.color.line));
            c.drawLine(0, bottom, W, bottom, axis);
            fill.setColor(col(R.color.rain));
            for (int i = 0; i < n; i++) {
                float bh = Math.max(2 * dp, (float) (values[i] / 100.0 * maxH));
                fill.setAlpha(values[i] > 0 ? 217 : 90);
                c.drawRoundRect(xs[i] - bw / 2, bottom - bh, xs[i] + bw / 2, bottom, 3 * dp, 3 * dp, fill);
                text.setColor(muted);
                c.drawText(label(i), xs[i], bottom - bh - 6 * dp, text);
            }
            return;
        }

        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : values) { lo = Math.min(lo, Math.round(v)); hi = Math.max(hi, Math.round(v)); }
        if (hi - lo < 4) { double m = (hi + lo) / 2; lo = m - 2; hi = m + 2; }
        float yTop = top + 12 * dp, yBot = bottom - 32 * dp;
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) ys[i] = (float) (yBot - (Math.round(values[i]) - lo) / (hi - lo) * (yBot - yTop));

        Path p = new Path();
        float y0 = n > 1 ? ys[0] - (ys[1] - ys[0]) / 2 : ys[0];
        float yN = n > 1 ? ys[n - 1] + (ys[n - 1] - ys[n - 2]) / 2 : ys[n - 1];
        p.moveTo(0, y0);
        for (int i = 0; i < n; i++) p.lineTo(xs[i], ys[i]);
        p.lineTo(W, yN);
        Path area = new Path(p);
        area.lineTo(W, bottom);
        area.lineTo(0, bottom);
        area.close();
        fill.setColor(col(mode == TEMP ? R.color.fill : R.color.rainfill));
        c.drawPath(area, fill);
        line.setColor(col(mode == TEMP ? R.color.accent : R.color.wind));
        c.drawPath(p, line);

        for (int i = 0; i < n; i++) {
            boolean strong = boldFirst && i == 0;
            text.setColor(strong ? fg : muted);
            text.setTypeface(strong ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(label(i), xs[i], ys[i] - 8 * dp, text);
        }
        if (mode == WIND && dirs.length == n) {
            fill.setColor(col(R.color.wind));
            for (int i = 0; i < n; i++) {
                c.save();
                c.translate(xs[i], bottom - 14 * dp);
                c.rotate((float) ((dirs[i] + 180) % 360));
                Path a = new Path();
                a.moveTo(0, -7 * dp); a.lineTo(4.5f * dp, 5 * dp); a.lineTo(0, 2.5f * dp); a.lineTo(-4.5f * dp, 5 * dp); a.close();
                c.drawPath(a, fill);
                c.restore();
            }
        }
    }
}
