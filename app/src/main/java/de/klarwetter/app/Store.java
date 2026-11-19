package de.klarwetter.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Lokale Speicherung: Orte (max. 10), Einheit und je Ort die zuletzt geladene Vorhersage. Nichts verlässt das Gerät. */
final class Store {
    static final int MAX_PLACES = 10;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("klarwetter", Context.MODE_PRIVATE);
    }

    // ---------- Orte ----------

    static List<Place> places(Context c) {
        SharedPreferences sp = p(c);
        List<Place> out = new ArrayList<>();
        String s = sp.getString("places", null);
        if (s == null) {
            // Übernahme aus Version 1.0 (nur ein Ort)
            String old = sp.getString("place", null);
            Place pl = old == null ? null : Place.fromJson(old);
            if (pl != null) {
                out.add(pl);
                SharedPreferences.Editor e = sp.edit();
                String oldCache = sp.getString("cache", null);
                if (oldCache != null) e.putString("cache_" + pl.key(), oldCache).putLong("cacheAt_" + pl.key(), sp.getLong("cacheAt", 0));
                e.remove("place").remove("cache").remove("cacheAt").apply();
                savePlaces(c, out);
            }
            return out;
        }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                Place pl = Place.fromJson(a.getString(i));
                if (pl != null) out.add(pl);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void savePlaces(Context c, List<Place> list) {
        JSONArray a = new JSONArray();
        for (Place pl : list) a.put(pl.toJson());
        p(c).edit().putString("places", a.toString()).apply();
    }

    /** Der erste Ort der Liste wird im Widget angezeigt. */
    static Place place(Context c) {
        List<Place> l = places(c);
        return l.isEmpty() ? null : l.get(0);
    }

    /** @return null wenn hinzugefügt, sonst der Grund. */
    static String addPlace(Context c, Place pl) {
        List<Place> l = places(c);
        for (Place x : l) {
            if (x.key().equals(pl.key()) || (x.name.equalsIgnoreCase(pl.name) && x.plz.equals(pl.plz)))
                return pl.label() + " ist schon in der Liste.";
        }
        if (l.size() >= MAX_PLACES) return "Es sind schon " + MAX_PLACES + " Orte gespeichert. Entferne zuerst einen.";
        l.add(pl);
        savePlaces(c, l);
        addToWidget(c, pl); // neue Orte erscheinen automatisch auch im Widget
        return null;
    }

    static void removePlace(Context c, int index) {
        List<Place> l = places(c);
        if (index < 0 || index >= l.size()) return;
        Place pl = l.remove(index);
        savePlaces(c, l);
        boolean stillUsed = false;
        for (Place x : l) if (x.key().equals(pl.key())) stillUsed = true;
        if (!stillUsed) p(c).edit().remove("cache_" + pl.key()).remove("cacheAt_" + pl.key()).apply();
    }

    static void movePlace(Context c, int from, int to) {
        List<Place> l = places(c);
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size()) return;
        l.add(to, l.remove(from));
        savePlaces(c, l);
    }

    // ---------- Orte auf dem Startbildschirm-Widget ----------

    /** Orte, die im Widget stehen (mindestens einer, sobald es Orte gibt). */
    static List<Place> widgetPlaces(Context c) {
        List<Place> all = places(c);
        if (!p(c).getBoolean("widgetAllV2", false)) {
            // Einmalig: alle gespeicherten Orte ins Widget übernehmen
            JSONArray keys = new JSONArray();
            for (Place pl : all) keys.put(pl.key());
            p(c).edit().putString("widgetKeys", keys.toString()).putBoolean("widgetAllV2", true).apply();
        }
        List<Place> out = new ArrayList<>();
        try {
            JSONArray keys = new JSONArray(p(c).getString("widgetKeys", "[]"));
            for (int i = 0; i < keys.length(); i++) {
                for (Place pl : all) if (pl.key().equals(keys.optString(i)) && !out.contains(pl)) { out.add(pl); break; }
            }
        } catch (Exception ignored) { }
        if (out.isEmpty() && !all.isEmpty()) out.add(all.get(0));
        return out;
    }

    static boolean inWidget(Context c, Place pl) {
        for (Place x : widgetPlaces(c)) if (x.key().equals(pl.key())) return true;
        return false;
    }

    private static void saveWidget(Context c, List<Place> list) {
        JSONArray a = new JSONArray();
        for (Place pl : list) a.put(pl.key());
        p(c).edit().putString("widgetKeys", a.toString()).apply();
    }

    static void addToWidget(Context c, Place pl) {
        List<Place> l = widgetPlaces(c);
        for (Place x : l) if (x.key().equals(pl.key())) return;
        l.add(pl);
        saveWidget(c, l);
    }

    /** @return false, wenn es der letzte Ort im Widget ist (ein Ort bleibt immer). */
    static boolean removeFromWidget(Context c, Place pl) {
        List<Place> l = widgetPlaces(c);
        if (l.size() <= 1) return false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).key().equals(pl.key())) { l.remove(i); break; }
        saveWidget(c, l);
        return true;
    }

    /** Index eines Ortes in der App-Liste (für die Detailansicht). */
    static int indexOf(Context c, Place pl) {
        List<Place> all = places(c);
        for (int i = 0; i < all.size(); i++) if (all.get(i).key().equals(pl.key())) return i;
        return 0;
    }

    /** Sucht einen gespeicherten Ort mit gleichem Namen/PLZ oder gleicher Lage. */
    static Place find(Context c, Place pl) {
        for (Place x : places(c))
            if (x.key().equals(pl.key()) || (x.name.equalsIgnoreCase(pl.name) && x.plz.equals(pl.plz))) return x;
        return null;
    }

    // ---------- Einheit ----------

    static String unit(Context c) {
        return p(c).getString("unit", "C");
    }

    static void setUnit(Context c, String u) {
        p(c).edit().putString("unit", u).apply();
    }

    // ---------- Wetterdaten je Ort ----------

    static Weather cached(Context c, Place pl) {
        if (pl == null) return null;
        String s = p(c).getString("cache_" + pl.key(), null);
        if (s == null) return null;
        try {
            return Weather.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    static long cachedAt(Context c, Place pl) {
        return pl == null ? 0 : p(c).getLong("cacheAt_" + pl.key(), 0);
    }

    static boolean isStale(Context c, Place pl) {
        return System.currentTimeMillis() - cachedAt(c, pl) > 15 * 60 * 1000;
    }

    /** Lädt neue Daten für einen Ort (im Hintergrund aufrufen). */
    static Weather refresh(Context c, Place pl) throws Exception {
        if (pl == null) return null;
        String json = Api.forecast(pl.lat, pl.lon);
        Weather w = Weather.parse(json);
        p(c).edit().putString("cache_" + pl.key(), json).putLong("cacheAt_" + pl.key(), System.currentTimeMillis()).commit();
        return w;
    }

    /** Lädt alle Widget-Orte neu (im Hintergrund aufrufen). */
    static void refreshWidget(Context c) {
        for (Place pl : widgetPlaces(c)) {
            try { refresh(c, pl); } catch (Exception ignored) { }
        }
    }
}
