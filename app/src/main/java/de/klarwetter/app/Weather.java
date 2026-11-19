package de.klarwetter.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Vorhersage von Open-Meteo, so wie die App sie braucht. */
final class Weather {
    String timezone;
    String curTime;
    double curTemp, curFeel, curHum, curWind;
    int curCode;
    boolean curDay;

    String[] hTime;
    double[] hTemp, hHum, hWind, hDir;
    int[] hRain, hCode;

    String[] dTime;
    int[] dCode, dRain;
    double[] dMax, dMin;

    static Weather parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        Weather w = new Weather();
        w.timezone = o.optString("timezone", "");
        JSONObject c = o.getJSONObject("current");
        w.curTime = c.getString("time");
        w.curTemp = c.getDouble("temperature_2m");
        w.curFeel = c.optDouble("apparent_temperature", w.curTemp);
        w.curHum = c.optDouble("relative_humidity_2m", 0);
        w.curWind = c.optDouble("wind_speed_10m", 0);
        w.curCode = c.optInt("weather_code", 0);
        w.curDay = c.optInt("is_day", 1) == 1;

        JSONObject h = o.getJSONObject("hourly");
        w.hTime = strs(h.getJSONArray("time"));
        w.hTemp = nums(h.optJSONArray("temperature_2m"), w.hTime.length);
        w.hHum = nums(h.optJSONArray("relative_humidity_2m"), w.hTime.length);
        w.hWind = nums(h.optJSONArray("wind_speed_10m"), w.hTime.length);
        w.hDir = nums(h.optJSONArray("wind_direction_10m"), w.hTime.length);
        w.hRain = ints(h.optJSONArray("precipitation_probability"), w.hTime.length);
        w.hCode = ints(h.optJSONArray("weather_code"), w.hTime.length);

        JSONObject d = o.getJSONObject("daily");
        w.dTime = strs(d.getJSONArray("time"));
        w.dCode = ints(d.optJSONArray("weather_code"), w.dTime.length);
        w.dRain = ints(d.optJSONArray("precipitation_probability_max"), w.dTime.length);
        w.dMax = nums(d.optJSONArray("temperature_2m_max"), w.dTime.length);
        w.dMin = nums(d.optJSONArray("temperature_2m_min"), w.dTime.length);
        return w;
    }

    /** Aktuelle Uhrzeit am Ort (HH:mm). */
    String localTime() {
        try {
            if (timezone != null && !timezone.isEmpty())
                return java.time.LocalTime.now(java.time.ZoneId.of(timezone)).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) { }
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** Index der aktuellen Stunde in den Stundenwerten. */
    int currentHour() {
        String key = curTime.substring(0, Math.min(13, curTime.length())) + ":00";
        for (int i = 0; i < hTime.length; i++) if (hTime[i].equals(key)) return i;
        return 0;
    }

    int hourIndex(String time) {
        for (int i = 0; i < hTime.length; i++) if (hTime[i].equals(time)) return i;
        return -1;
    }

    /** 8 Zeitpunkte im 3-Stunden-Abstand: heute ab der nächsten Stunde, sonst ab 00:00. */
    int[] hoursFor(int day) {
        int start = day == 0 ? currentHour() + 1 : hourIndex(dTime[day] + "T00:00");
        if (start < 0) start = 0;
        int n = 0;
        int[] tmp = new int[8];
        for (int k = 0; k < 8; k++) {
            int i = start + k * 3;
            if (i < hTime.length) tmp[n++] = i;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    private static String[] strs(JSONArray a) throws JSONException {
        String[] r = new String[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.getString(i);
        return r;
    }

    private static double[] nums(JSONArray a, int n) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = a == null ? 0 : a.optDouble(i, 0);
        return r;
    }

    private static int[] ints(JSONArray a, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = a == null ? 0 : (int) Math.round(a.optDouble(i, 0));
        return r;
    }
}
