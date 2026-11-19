package de.klarwetter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Fenster „Orte bearbeiten“: Orte hinzufügen, sortieren und entfernen (max. 10). */
public class EditActivity extends Activity {
    private static final String[] LAND_NAMES = {"Alle Länder", "Deutschland", "Österreich", "Schweiz"};
    private static final String[] LAND_CODES = {"", "DE", "AT", "CH"};

    private EditText inOrt, inPlz;
    private Spinner inLand;
    private Button save;
    private TextView err, count, formTitle;
    private LinearLayout choices, list;
    /** Aus dem Widget-Menü geöffnet: neuer Ort kommt direkt aufs Widget. */
    static final String EXTRA_TO_WIDGET = "toWidget";
    private boolean toWidget;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_edit);
        toWidget = getIntent().getBooleanExtra(EXTRA_TO_WIDGET, false);
        Ui.edgeToEdge(this, findViewById(R.id.root));

        inOrt = findViewById(R.id.in_ort);
        inPlz = findViewById(R.id.in_plz);
        inLand = findViewById(R.id.in_land);
        save = findViewById(R.id.save);
        err = findViewById(R.id.err);
        choices = findViewById(R.id.choices);
        list = findViewById(R.id.list);
        count = findViewById(R.id.count);
        formTitle = findViewById(R.id.form_title);

        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, LAND_NAMES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inLand.setAdapter(ad);

        save.setOnClickListener(v -> search());
        inPlz.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_DONE) { search(); return true; }
            return false;
        });
        findViewById(R.id.done).setOnClickListener(v -> finish());
        findViewById(R.id.add_widget).setOnClickListener(v -> WidgetPin.request(this, true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        List<Place> places = Store.places(this);
        formTitle.setText(places.isEmpty() ? "Deinen ersten Ort hinzufügen" : "Ort hinzufügen");
        boolean full = places.size() >= Store.MAX_PLACES;
        save.setEnabled(!full);
        save.setAlpha(full ? .5f : 1f);
        count.setText(places.isEmpty() ? "Noch keine Orte gespeichert."
                : places.size() + " von " + Store.MAX_PLACES + " Orten · Ein Ort bleibt immer");
        if (toWidget) formTitle.setText("Ort für den Startbildschirm hinzufügen");
        PlaceCards.render(this, list, places, true, new PlaceCards.Actions() {
            @Override public void open(int index) { }
            @Override public void move(int from, int to) { EditActivity.this.move(from, to); }
            @Override public void remove(int index, Place place) { confirmRemove(index, place); }
        });
    }

    private void move(int from, int to) {
        Store.movePlace(this, from, to);
        render();
    }

    private void confirmRemove(int idx, Place pl) {
        if (Store.places(this).size() <= 1) {
            Toast.makeText(this, "Ein Ort bleibt immer gespeichert. Füge zuerst einen anderen hinzu.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ort entfernen?")
                .setMessage(pl.label() + " wird aus der Liste entfernt.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Entfernen", (d, which) -> {
                    boolean wasInWidget = Store.inWidget(this, pl);
                    Store.removePlace(this, idx);
                    render();
                    if (wasInWidget) widgetChanged();
                })
                .show();
    }

    /** Der Widget-Ort hat sich geändert: Widget neu zeichnen und ggf. Daten laden. */
    private void widgetChanged() {
        final Context app = getApplicationContext();
        WeatherWidget.render(app);
        Ui.BG.execute(() -> {
            for (Place pl : Store.widgetPlaces(app)) {
                if (!Store.isStale(app, pl)) continue;
                try { Store.refresh(app, pl); } catch (Exception ignored) { }
            }
            Ui.MAIN.post(() -> { WeatherWidget.render(app); if (!isFinishing()) render(); });
        });
    }

    // ---------- Ortssuche ----------

    private void search() {
        if (Store.places(this).size() >= Store.MAX_PLACES) {
            showError("Es sind schon " + Store.MAX_PLACES + " Orte gespeichert. Entferne zuerst einen.");
            return;
        }
        final String ort = inOrt.getText().toString().trim();
        final String plz = inPlz.getText().toString().trim();
        final String land = LAND_CODES[Math.max(0, inLand.getSelectedItemPosition())];
        err.setVisibility(View.GONE);
        choices.removeAllViews();
        save.setEnabled(false);
        save.setText("Suche …");
        Ui.BG.execute(() -> {
            List<Place> res = null;
            String msg = null;
            try { res = Api.geocode(ort, plz, land); } catch (Api.LookupException e) { msg = e.getMessage(); }
            final List<Place> fr = res;
            final String fm = msg;
            Ui.MAIN.post(() -> {
                if (isFinishing()) return;
                save.setText("Hinzufügen");
                save.setEnabled(true);
                if (fm != null) showError(fm);
                else if (fr.size() == 1 || !plz.isEmpty()) choose(fr.get(0));
                else showChoices(fr);
            });
        });
    }

    private void showError(String m) {
        err.setText(m);
        err.setVisibility(View.VISIBLE);
    }

    private void showChoices(List<Place> res) {
        TextView q = new TextView(this);
        q.setText("Welcher Ort ist gemeint?");
        q.setTextColor(getColor(R.color.fg));
        q.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        choices.addView(q);
        for (Place p : res) {
            TextView b = new TextView(this);
            b.setText(p.name + "\n" + p.admin);
            b.setTextColor(getColor(R.color.fg));
            b.setTextSize(15);
            b.setBackgroundResource(R.drawable.input_bg);
            int pad = Ui.dp(this, 12);
            b.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 6);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> choose(p));
            choices.addView(b);
        }
    }

    private void choose(Place p) {
        choices.removeAllViews();
        String problem = Store.addPlace(this, p);
        if (problem != null) {
            Place existing = toWidget ? Store.find(this, p) : null;
            if (existing != null) {
                // Ort gibt es schon in der App: nur aufs Widget setzen.
                Store.addToWidget(this, existing);
                WeatherWidget.render(getApplicationContext());
                String msg = existing.label() + " steht jetzt auf dem Startbildschirm.";
                if (WeatherWidget.tooNarrow(this)) msg += "\n" + WeatherWidget.RESIZE_HINT;
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            showError(problem);
            return;
        }
        if (toWidget) Store.addToWidget(this, p);
        inOrt.setText("");
        inPlz.setText("");
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(inOrt.getWindowToken(), 0);
        Toast.makeText(this, p.label() + " hinzugefügt", Toast.LENGTH_SHORT).show();
        render();
        final Context app = getApplicationContext();
        final boolean isFirst = Store.places(app).size() == 1;
        Ui.BG.execute(() -> {
            try { Store.refresh(app, p); } catch (Exception ignored) { }
            Ui.MAIN.post(() -> {
                if (isFirst || toWidget) WeatherWidget.render(app);
                if (isFinishing()) return;
                render();
                if (isFirst && !toWidget) WidgetPin.askOnce(this);
            });
        });
        if (toWidget) {
            WeatherWidget.render(app);
            String msg = p.label() + " steht jetzt auf dem Startbildschirm.";
            if (WeatherWidget.tooNarrow(app)) msg += "\n" + WeatherWidget.RESIZE_HINT;
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
