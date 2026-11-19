package de.klarwetter.app;

import org.json.JSONException;
import org.json.JSONObject;

/** Ein gespeicherter Ort. Wird nur lokal auf dem Gerät abgelegt. */
final class Place {
    final String name, plz, admin, country;
    final double lat, lon;

    Place(String name, String plz, String admin, String country, double lat, double lon) {
        this.name = name;
        this.plz = plz == null ? "" : plz;
        this.admin = admin == null ? "" : admin;
        this.country = country == null ? "" : country;
        this.lat = lat;
        this.lon = lon;
    }

    /** Eindeutiger Schlüssel für den Zwischenspeicher dieses Ortes. */
    String key() {
        return String.format(java.util.Locale.US, "%.3f,%.3f", lat, lon);
    }

    String label() {
        return plz.isEmpty() ? name : name + " (" + plz + ")";
    }

    String toJson() {
        try {
            return new JSONObject().put("name", name).put("plz", plz).put("admin", admin)
                    .put("country", country).put("lat", lat).put("lon", lon).toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    static Place fromJson(String s) {
        try {
            JSONObject o = new JSONObject(s);
            return new Place(o.getString("name"), o.optString("plz"), o.optString("admin"),
                    o.optString("country"), o.getDouble("lat"), o.getDouble("lon"));
        } catch (JSONException e) {
            return null;
        }
    }
}
