package de.klarwetter.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alle Netzwerkzugriffe. Gesendet wird nur der Suchbegriff (Ort/PLZ) bzw. die Koordinaten.
 * Wetter: Open-Meteo. Ortssuche: Open-Meteo, bei Postleitzahlen zuerst OpenStreetMap (Nominatim).
 */
final class Api {
    static final class LookupException extends Exception {
        LookupException(String m) { super(m); }
    }

    private static final String UA = "Klarwetter-Android/1.1 (private Wetter-App)";

    static String forecast(double lat, double lon) throws IOException {
        String u = "https://api.open-meteo.com/v1/forecast?latitude=" + fmt(lat) + "&longitude=" + fmt(lon)
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
                + "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&forecast_days=9";
        return get(u);
    }

    /** Sucht nach Ort, Postleitzahl oder beidem. */
    static List<Place> geocode(String ort, String plz, String land) throws LookupException {
        if (ort.isEmpty() && plz.isEmpty()) throw new LookupException("Bitte Ort oder Postleitzahl eingeben.");
        boolean reached = false;
        List<Place> r;

        // Mit Postleitzahl: OpenStreetMap kennt deutsche PLZ vollständig.
        // Nur Ortsname: Open-Meteo liefert eine gute Auswahlliste.
        for (int pass = 0; pass < 2; pass++) {
            boolean osm = !plz.isEmpty() ? pass == 0 : pass == 1;
            try {
                r = osm ? nominatim(ort, plz, land) : openMeteo(ort, plz, land);
                reached = true;
                if (!r.isEmpty()) {
                    if (!plz.isEmpty() && land.isEmpty()) preferDach(r);
                    return r;
                }
            } catch (IOException ignored) {
                // nächsten Dienst versuchen
            }
        }
        if (!reached) throw new LookupException("Der Ortsdienst ist gerade nicht erreichbar. Prüfe die Internetverbindung.");
        throw new LookupException(!plz.isEmpty() && ort.isEmpty()
                ? "Zu dieser Postleitzahl wurde kein Ort gefunden. Gib zusätzlich den Ortsnamen ein."
                : "Kein Ort gefunden. Prüfe die Schreibweise oder wähle „Alle Länder“.");
    }

    // ---------- Open-Meteo (GeoNames) ----------

    private static List<Place> openMeteo(String ort, String plz, String land) throws IOException {
        List<JSONObject> res = openMeteoSearch(!plz.isEmpty() ? plz : ort, land);

        if (!plz.isEmpty()) {
            List<JSONObject> f = new ArrayList<>();
            for (JSONObject r : res) if (hasPostcode(r, plz)) f.add(r);
            res = f;
            if (!ort.isEmpty()) {
                List<JSONObject> m = new ArrayList<>();
                for (JSONObject r : res) if (matchesName(r, ort)) m.add(r);
                if (!m.isEmpty()) res = m;
                if (res.isEmpty()) res = openMeteoSearch(ort, land);
            }
        }
        // Doppelnamen wie „Hartmannsdorf-Reichenau“: auch nach den Teilen suchen.
        if (res.isEmpty() && !ort.isEmpty() && (ort.contains("-") || ort.contains(" "))) {
            for (String part : ort.split("[- ]+")) {
                if (part.length() < 3) continue;
                res = openMeteoSearch(part, land);
                if (!res.isEmpty()) break;
            }
        }

        List<Place> out = new ArrayList<>();
        for (int i = 0; i < Math.min(6, res.size()); i++) {
            JSONObject r = res.get(i);
            JSONArray pcs = r.optJSONArray("postcodes");
            String p = !plz.isEmpty() ? plz : (pcs != null && pcs.length() == 1 ? pcs.optString(0) : "");
            String cc = r.optString("country_code");
            String country = r.optString("country");
            if (country.isEmpty()) country = countryName(cc);
            String admin = join(r.optString("admin1"), country);
            out.add(new Place(r.optString("name"), p, admin, cc,
                    r.optDouble("latitude"), r.optDouble("longitude")));
        }
        return out;
    }

    private static List<JSONObject> openMeteoSearch(String q, String land) throws IOException {
        String u = "https://geocoding-api.open-meteo.com/v1/search?count=20&language=de&format=json&name="
                + enc(q) + (land.isEmpty() ? "" : "&countryCode=" + land);
        List<JSONObject> res = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(get(u)).optJSONArray("results");
            if (arr != null) for (int i = 0; i < arr.length(); i++) res.add(arr.optJSONObject(i));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        return res;
    }

    private static boolean hasPostcode(JSONObject r, String plz) {
        JSONArray pcs = r.optJSONArray("postcodes");
        if (pcs == null) return false;
        for (int i = 0; i < pcs.length(); i++) if (plz.equals(pcs.optString(i))) return true;
        return false;
    }

    private static boolean matchesName(JSONObject r, String ort) {
        String o = ort.toLowerCase(Locale.GERMAN);
        for (String k : new String[]{"name", "admin3", "admin4"}) {
            String v = r.optString(k).toLowerCase(Locale.GERMAN);
            if (!v.isEmpty() && (v.startsWith(o) || o.startsWith(v))) return true;
        }
        return false;
    }

    // ---------- OpenStreetMap (Nominatim) ----------

    private static List<Place> nominatim(String ort, String plz, String land) throws IOException {
        List<String> tries = new ArrayList<>();
        if (!plz.isEmpty()) {
            if (!ort.isEmpty()) tries.add("postalcode=" + enc(plz) + "&city=" + enc(ort));
            tries.add("postalcode=" + enc(plz));
            if (!ort.isEmpty()) tries.add("q=" + enc(plz + " " + ort));
        } else {
            tries.add("q=" + enc(ort));
        }
        String base = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5&accept-language=de"
                + (land.isEmpty() ? "" : "&countrycodes=" + land.toLowerCase(Locale.ROOT)) + "&";

        for (int t = 0; t < tries.size(); t++) {
            if (t > 0) {
                try { Thread.sleep(1100); } catch (InterruptedException e) { break; } // höchstens 1 Anfrage pro Sekunde
            }
            JSONArray arr;
            try {
                arr = new JSONArray(get(base + tries.get(t)));
            } catch (org.json.JSONException e) {
                throw new IOException(e);
            }
            List<Place> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Place p = fromNominatim(arr.optJSONObject(i), ort, plz);
                if (p != null) out.add(p);
            }
            if (!out.isEmpty()) return out;
        }
        return new ArrayList<>();
    }

    private static Place fromNominatim(JSONObject o, String ort, String plz) {
        if (o == null) return null;
        double lat, lon;
        try {
            lat = Double.parseDouble(o.optString("lat"));
            lon = Double.parseDouble(o.optString("lon"));
        } catch (NumberFormatException e) {
            return null;
        }
        JSONObject a = o.optJSONObject("address");
        if (a == null) a = new JSONObject();
        String name = ort;
        if (name.isEmpty()) {
            for (String k : new String[]{"city", "town", "village", "municipality", "suburb", "hamlet"}) {
                if (!a.optString(k).isEmpty()) { name = a.optString(k); break; }
            }
        }
        if (name.isEmpty()) {
            for (String part : o.optString("display_name").split(",\\s*")) {
                if (!part.isEmpty() && !part.equals(plz)) { name = part; break; }
            }
        }
        if (name.isEmpty()) name = plz;
        String pc = !plz.isEmpty() ? plz : a.optString("postcode");
        String cc = a.optString("country_code").toUpperCase(Locale.ROOT);
        String country = a.optString("country");
        if (country.isEmpty()) country = countryName(cc);
        return new Place(name, pc, join(a.optString("state"), country), cc, lat, lon);
    }

    // ---------- Hilfsfunktionen ----------

    /** „Region, Land“ – z. B. „Bar, Montenegro“ oder „Sachsen, Deutschland“. */
    private static String join(String region, String country) {
        if (region == null || region.isEmpty() || region.equals(country)) return country == null ? "" : country;
        return country == null || country.isEmpty() ? region : region + ", " + country;
    }

    private static String countryName(String cc) {
        if (cc == null || cc.isEmpty()) return "";
        return new Locale("", cc).getDisplayCountry(Locale.GERMAN);
    }

    /** Bei Postleitzahlen ohne Länderwahl: Deutschland, dann Österreich/Schweiz zuerst. */
    private static void preferDach(List<Place> list) {
        java.util.Collections.sort(list, (x, y) -> Integer.compare(rank(x.country), rank(y.country)));
    }

    private static int rank(String cc) {
        if ("DE".equalsIgnoreCase(cc)) return 0;
        if ("AT".equalsIgnoreCase(cc) || "CH".equalsIgnoreCase(cc)) return 1;
        return 2;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String fmt(double d) {
        return String.format(Locale.US, "%.4f", d);
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", UA);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return b.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
