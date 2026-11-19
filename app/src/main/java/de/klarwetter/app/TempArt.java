package de.klarwetter.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Zeichnet die große Temperatur fürs Widget: dünne Ziffern, „°C" klein schräg rechts oben
 * dicht an der Zahl. Die Größe passt sich der verfügbaren Breite an (auch für „−22°C").
 */
final class TempArt {
    static Bitmap make(String number, String unitText, int maxWidthPx, int heightPx, int color) {
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        num.setColor(color);
        Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        unit.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(color);

        float size = heightPx * 1.05f, numW, unitW, overlap;
        while (true) {
            num.setTextSize(size);
            unit.setTextSize(size * 0.36f);
            numW = num.measureText(number);
            unitW = unit.measureText(unitText);
            overlap = size * 0.07f; // „°C" rückt leicht in die Zahl hinein
            if (numW - overlap + unitW <= maxWidthPx || size < heightPx * 0.35f) break;
            size *= 0.94f;
        }

        // Nie abschneiden: ist die Zahl breiter als der Platz, wird das Bild breiter
        // und die Anzeige verkleinert es passend (scaleType fitCenter).
        int width = Math.max(maxWidthPx, (int) Math.ceil(numW - overlap + unitW) + 2);
        Bitmap b = Bitmap.createBitmap(Math.max(1, width), Math.max(1, heightPx), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cap = size * 0.71f;                 // Höhe der Ziffern
        float baseline = (heightPx + cap) / 2f;   // senkrecht mittig
        c.drawText(number, 0, baseline, num);

        float uCap = unit.getTextSize() * 0.71f;
        float numTop = baseline - cap;
        float uBaseline = numTop + uCap - unit.getTextSize() * 0.12f; // etwas über die Oberkante
        c.drawText(unitText, numW - overlap, uBaseline, unit);
        return b;
    }
}
