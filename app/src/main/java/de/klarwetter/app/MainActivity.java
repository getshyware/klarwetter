package de.klarwetter.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/** Startseite: nur die gespeicherten Orte. Hinzufügen, Sortieren und Entfernen im Fenster „Orte bearbeiten“. */
public class MainActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        Ui.edgeToEdge(this, findViewById(R.id.root));
        list = findViewById(R.id.list);
        findViewById(R.id.edit).setOnClickListener(v -> openEdit());
        findViewById(R.id.add_first).setOnClickListener(v -> openEdit());
        findViewById(R.id.refresh_all).setOnClickListener(v -> refresh(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Place> places = Store.places(this);
        if (places.isEmpty() && !getSharedPreferences("klarwetter", MODE_PRIVATE).getBoolean("editOpened", false)) {
            // Erster Start: direkt zum Hinzufügen.
            openEdit();
            return;
        }
        render();
        refresh(false);
        WidgetPin.askOnce(this);
    }

    private void openEdit() {
        getSharedPreferences("klarwetter", MODE_PRIVATE).edit().putBoolean("editOpened", true).apply();
        startActivity(new Intent(this, EditActivity.class));
    }

    private void render() {
        List<Place> places = Store.places(this);
        findViewById(R.id.empty).setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);
        PlaceCards.render(this, list, places, false, new PlaceCards.Actions() {
            @Override public void open(int index) {
                startActivity(new Intent(MainActivity.this, DetailActivity.class).putExtra(DetailActivity.EXTRA_INDEX, index));
            }
            @Override public void move(int from, int to) { }
            @Override public void remove(int index, Place place) { }
        });
    }

    /** Lädt Orte neu: beim Öffnen nur veraltete, über ↻ alle. */
    private void refresh(boolean all) {
        final Context app = getApplicationContext();
        final List<Place> places = Store.places(app);
        final View btn = findViewById(R.id.refresh_all);
        btn.setEnabled(false);
        btn.setAlpha(.4f);
        if (all) ((android.widget.TextView) findViewById(R.id.updated)).setText("Wird aktualisiert …");
        Ui.BG.execute(() -> {
            boolean any = false, failed = false;
            for (Place pl : places) {
                if (!all && !Store.isStale(app, pl)) continue;
                try {
                    Store.refresh(app, pl);
                    any = true;
                    Ui.MAIN.post(() -> { if (!isFinishing()) render(); });
                } catch (Exception e) {
                    failed = true;
                }
            }
            final boolean fAny = any, fFailed = failed;
            Ui.MAIN.post(() -> {
                if (fAny) WeatherWidget.render(app);
                if (isFinishing()) return;
                btn.setEnabled(true);
                btn.setAlpha(1f);
                showUpdated();
                if (fFailed)
                    Toast.makeText(app, "Nicht alle Orte konnten aktualisiert werden. Prüfe die Internetverbindung.", Toast.LENGTH_LONG).show();
            });
        });
    }

    /** „Aktualisiert um 09:15“ unter der Kopfzeile. */
    private void showUpdated() {
        long newest = 0;
        for (Place pl : Store.places(this)) newest = Math.max(newest, Store.cachedAt(this, pl));
        android.widget.TextView t = findViewById(R.id.updated);
        if (newest == 0) { t.setText(""); return; }
        t.setText("Aktualisiert um " + java.time.Instant.ofEpochMilli(newest).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " Uhr");
    }
}
