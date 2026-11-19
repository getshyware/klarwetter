package de.klarwetter.app;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Zeichnet die Ortskarten – auf der Startseite zum Antippen, im Bearbeiten-Fenster mit Knöpfen. */
final class PlaceCards {
    interface Actions {
        void open(int index);
        void move(int from, int to);
        void remove(int index, Place place);
    }

    static void render(Activity a, LinearLayout list, List<Place> places, boolean editing, Actions act) {
        String unit = Store.unit(a);
        list.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(a);
        for (int i = 0; i < places.size(); i++) {
            final int idx = i;
            final Place pl = places.get(i);
            Weather w = Store.cached(a, pl);
            View item = inf.inflate(R.layout.item_place, list, false);
            TextView name = item.findViewById(R.id.p_name), sub = item.findViewById(R.id.p_sub),
                    temp = item.findViewById(R.id.p_temp), cond = item.findViewById(R.id.p_cond), hl = item.findViewById(R.id.p_hl);
            ImageView icon = item.findViewById(R.id.p_icon);

            name.setText(Fmt.wrapName(pl.name));
            String extra = (pl.plz.isEmpty() ? "" : " · " + pl.plz) + (Store.inWidget(a, pl) ? " · Widget" : "");
            if (w == null) {
                sub.setText("Wird geladen …" + extra);
                temp.setText("–");
                cond.setText("");
                hl.setText("");
                icon.setImageDrawable(null);
                item.setContentDescription(pl.name + ", Wetter wird geladen");
            } else {
                sub.setText(w.localTime() + extra);
                temp.setText(Fmt.t(w.curTemp, unit) + "°");
                cond.setText(Fmt.cond(w.curCode, w.curDay));
                hl.setText("H:" + Fmt.t(w.dMax[0], unit) + "°  T:" + Fmt.t(w.dMin[0], unit) + "°");
                icon.setImageBitmap(Icons.make(Fmt.kind(w.curCode, w.curDay), Ui.dp(a, 44), true));
                item.setContentDescription(pl.name + ": " + Fmt.t(w.curTemp, unit) + " Grad, " + Fmt.cond(w.curCode, w.curDay)
                        + ", höchstens " + Fmt.t(w.dMax[0], unit) + ", mindestens " + Fmt.t(w.dMin[0], unit));
            }

            View editRow = item.findViewById(R.id.p_edit);
            editRow.setVisibility(editing ? View.VISIBLE : View.GONE);
            if (editing) {
                View up = item.findViewById(R.id.p_up), down = item.findViewById(R.id.p_down);
                boolean canUp = i > 0, canDown = i < places.size() - 1;
                up.setEnabled(canUp);
                up.setAlpha(canUp ? 1f : .4f);
                down.setEnabled(canDown);
                down.setAlpha(canDown ? 1f : .4f);
                up.setOnClickListener(v -> act.move(idx, idx - 1));
                down.setOnClickListener(v -> act.move(idx, idx + 1));
                View rm = item.findViewById(R.id.p_remove);
                boolean canRemove = places.size() > 1; // ein Ort bleibt immer
                rm.setEnabled(canRemove);
                rm.setAlpha(canRemove ? 1f : .4f);
                rm.setOnClickListener(v -> act.remove(idx, pl));
                item.setClickable(false);
            } else {
                item.setOnClickListener(v -> act.open(idx));
            }
            list.addView(item);
        }
    }
}
