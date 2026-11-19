package de.klarwetter.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Zeichnet die Wettersymbole: Stil „hell & klar“ für blaue Flächen (Widget, Ortskarten)
 * und den Stil der hellen Detailansicht.
 */
final class Icons {
    enum Kind { SUN, MOON, PARTLY, MOON_PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

    /**
     * @param onBlue true = Stil „hell & klar“ für blaue Flächen (Widget, Ortskarten):
     *               weiße Wolken, hellblaue senkrechte Regenstriche, weißer Schnee/Nebel.
     *               false = Stil der hellen Detailansicht.
     */
    static Bitmap make(Kind k, int px, boolean onBlue) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = px / 64f;
        c.scale(s, s);
        int light1 = 0xFFFFFFFF, light2 = onBlue ? 0xFFE2EAF3 : 0xFFCFD6DC;
        int grey1 = onBlue ? 0xFFFAFBFD : 0xFFD9DDE1, grey2 = onBlue ? 0xFFCFD8E2 : 0xFF9AA1A8;
        int dark1 = onBlue ? 0xFFECEFF3 : 0xFF8D949C, dark2 = onBlue ? 0xFFB0BAC6 : 0xFF5B626A;
        switch (k) {
            case SUN: sun(c, 32, 32, 20); break;
            case MOON: moon(c, 32, 32, 18); break;
            case PARTLY: sun(c, 25, 25, 16); cloud(c, 17, 28, .95f, light1, light2); break;
            case MOON_PARTLY: moon(c, 26, 26, 14); cloud(c, 17, 28, .95f, light1, light2); break;
            case CLOUD: cloud(c, 6, 16, 1.12f, grey1, grey2); break;
            case FOG:
                cloud(c, 7, 8, 1.08f, grey1, grey2);
                lines(c, onBlue ? 0xEBFFFFFF : 0xFF9AA1A8, 3, new float[]{12, 46, 52, 46, 18, 53, 48, 53});
                break;
            case RAIN:
                cloud(c, 7, 6, 1.1f, grey1, grey2);
                if (onBlue) lines(c, 0xFF96E1FF, 3.4f, new float[]{20, 44, 20, 53, 32, 44, 32, 55, 44, 44, 44, 53});
                else lines(c, 0xFF3B82C4, 3, new float[]{21, 44, 18, 51, 32, 44, 29, 51, 43, 44, 40, 51});
                break;
            case SNOW: {
                cloud(c, 7, 6, 1.1f, grey1, grey2);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(onBlue ? 0xFFFFFFFF : 0xFF8FB8DE);
                float r = onBlue ? 3f : 2.6f;
                c.drawCircle(20, 47, r, p);
                c.drawCircle(32, 52, r, p);
                c.drawCircle(44, 47, r, p);
                break;
            }
            case STORM: {
                cloud(c, 7, 6, 1.1f, dark1, dark2);
                Path bolt = new Path();
                bolt.moveTo(33, 38); bolt.lineTo(25, 50); bolt.lineTo(32, 50); bolt.lineTo(28, 60);
                bolt.lineTo(39, 46); bolt.lineTo(32, 46); bolt.lineTo(36, 38); bolt.close();
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(onBlue ? 0xFFFFD54F : 0xFFFFC107);
                c.drawPath(bolt, p);
                break;
            }
        }
        return b;
    }

    private static void sun(Canvas c, float cx, float cy, float r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx - r * .25f, cy - r * .35f, r * 1.6f,
                new int[]{0xFFFFD54F, 0xFFFFA726, 0xFFF57C00}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        p.setShadowLayer(1.2f, 0, 1f, 0x38000000);
        Path path = new Path();
        for (int i = 0; i <= 180; i++) {
            double t = i / 180.0 * Math.PI * 2;
            double rr = r + r * .08 * Math.cos(12 * t);
            float x = (float) (cx + rr * Math.cos(t)), y = (float) (cy + rr * Math.sin(t));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, p);
    }

    private static void moon(Canvas c, float cx, float cy, float r) {
        Path a = new Path();
        a.addCircle(cx, cy, r, Path.Direction.CW);
        Path b = new Path();
        b.addCircle(cx + r * .45f, cy - r * .35f, r * .8f, Path.Direction.CW);
        a.op(b, Path.Op.DIFFERENCE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx - r * .3f, cy - r * .3f, r * 1.4f,
                0xFFFFF3C4, 0xFFF2C94C, Shader.TileMode.CLAMP));
        p.setShadowLayer(1.2f, 0, 1f, 0x38000000);
        c.drawPath(a, p);
    }

    private static void cloud(Canvas c, float x, float y, float s, int top, int bottom) {
        Path p = new Path();
        p.addCircle(14, 19, 9, Path.Direction.CW);
        p.addCircle(25, 13, 12, Path.Direction.CW);
        p.addCircle(37, 18, 10, Path.Direction.CW);
        p.addRoundRect(12, 16, 39, 28, 6, 6, Path.Direction.CW);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 1, 0, 28, top, bottom, Shader.TileMode.CLAMP));
        paint.setShadowLayer(1.1f, 0, 1.2f, 0x40000000);
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.drawPath(p, paint);
        c.restore();
    }

    private static void lines(Canvas c, int color, float width, float[] pts) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLines(pts, p);
    }
}
