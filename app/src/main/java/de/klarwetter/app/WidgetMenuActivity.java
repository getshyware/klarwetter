package de.klarwetter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Menü hinter „•••" im Widget: Ort auf den Startbildschirm holen oder davon entfernen. */
public class WidgetMenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Store.places(this).isEmpty()) {
            startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_TO_WIDGET, true));
            finish();
            return;
        }
        showMain();
    }

    private void showMain() {
        new AlertDialog.Builder(this)
                .setTitle("Startbildschirm")
                .setItems(new String[]{"Ort auf Startbildschirm hinzufügen", "Ort vom Startbildschirm löschen"}, (d, which) -> {
                    if (which == 0) showAdd(); else showRemove();
                })
                .setNegativeButton("Abbrechen", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void showAdd() {
        final List<Place> candidates = new ArrayList<>();
        for (Place pl : Store.places(this)) if (!Store.inWidget(this, pl)) candidates.add(pl);
        final boolean canAddNew = Store.places(this).size() < Store.MAX_PLACES;
        List<String> labels = new ArrayList<>();
        for (Place pl : candidates) labels.add(pl.label());
        if (canAddNew) labels.add("Neuen Ort suchen …");
        if (labels.isEmpty()) {
            Toast.makeText(this, "Alle gespeicherten Orte stehen schon auf dem Startbildschirm.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (candidates.isEmpty()) { openSearch(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Welchen Ort hinzufügen?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which < candidates.size()) {
                        Place pl = candidates.get(which);
                        Store.addToWidget(this, pl);
                        updateWidget(pl);
                        if (WeatherWidget.tooNarrow(this)) {
                            new AlertDialog.Builder(this)
                                    .setTitle(pl.label() + " hinzugefügt")
                                    .setMessage(WeatherWidget.RESIZE_HINT)
                                    .setPositiveButton("OK", (d2, w2) -> finish())
                                    .setOnCancelListener(d2 -> finish())
                                    .show();
                        } else {
                            Toast.makeText(this, pl.label() + " steht jetzt auf dem Startbildschirm", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        openSearch();
                    }
                })
                .setNegativeButton("Abbrechen", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void openSearch() {
        startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_TO_WIDGET, true));
        finish();
    }

    private void showRemove() {
        final List<Place> inWidget = Store.widgetPlaces(this);
        if (inWidget.size() <= 1) {
            Toast.makeText(this, "Ein Ort bleibt immer auf dem Startbildschirm.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String[] labels = new String[inWidget.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = inWidget.get(i).label();
        new AlertDialog.Builder(this)
                .setTitle("Welchen Ort vom Startbildschirm löschen?")
                .setItems(labels, (d, which) -> {
                    Place pl = inWidget.get(which);
                    Store.removeFromWidget(this, pl);
                    WeatherWidget.render(getApplicationContext());
                    Toast.makeText(this, pl.label() + " ist nicht mehr auf dem Startbildschirm. In der App bleibt der Ort gespeichert.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .setNegativeButton("Abbrechen", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    /** Widget sofort zeichnen und fehlende Daten nachladen. */
    private void updateWidget(Place pl) {
        final Context app = getApplicationContext();
        WeatherWidget.render(app);
        if (Store.isStale(app, pl)) {
            Ui.BG.execute(() -> {
                try { Store.refresh(app, pl); } catch (Exception ignored) { }
                Ui.MAIN.post(() -> WeatherWidget.render(app));
            });
        }
    }
}
