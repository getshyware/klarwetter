package de.klarwetter.app;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

final class Fmt {
    static int t(double c, String unit) {
        return (int) Math.round("F".equals(unit) ? c * 9 / 5 + 32 : c);
    }

    static String cond(int c, boolean day) {
        if (!day && c == 0) return "Klar";
        if (!day && c == 1) return "Überwiegend klar";
        switch (c) {
            case 0: return "Sonnig";
            case 1: return "Überwiegend sonnig";
            case 2: return "Teilweise bewölkt";
            case 3: return "Bedeckt";
            case 45: return "Nebel";
            case 48: return "Reifnebel";
            case 51: return "Leichter Nieselregen";
            case 53: return "Nieselregen";
            case 55: return "Starker Nieselregen";
            case 56: case 57: return "Gefrierender Niesel";
            case 61: return "Leichter Regen";
            case 63: return "Regen";
            case 65: return "Starker Regen";
            case 66: case 67: return "Gefrierender Regen";
            case 71: return "Leichter Schneefall";
            case 73: return "Schneefall";
            case 75: return "Starker Schneefall";
            case 77: return "Schneegriesel";
            case 80: case 81: return "Regenschauer";
            case 82: return "Heftige Schauer";
            case 85: case 86: return "Schneeschauer";
            case 95: return "Gewitter";
            case 96: case 99: return "Gewitter mit Hagel";
            default: return "–";
        }
    }

    /** Kurzes Wort unter dem Widget-Symbol, z. B. „Regen“ oder „Nebel“. */
    static String condShort(int c, boolean day) {
        if (c <= 1) return day ? (c == 0 ? "Sonnig" : "Heiter") : "Klar";
        if (c == 2) return "Bewölkt";
        if (c == 3) return "Bedeckt";
        if (c == 45 || c == 48) return "Nebel";
        if (c >= 51 && c <= 57) return "Niesel";
        if (c >= 61 && c <= 67) return "Regen";
        if (c >= 80 && c <= 82) return "Schauer";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "Schnee";
        if (c >= 95) return "Gewitter";
        return "";
    }

    static Icons.Kind kind(int c, boolean day) {
        Icons.Kind k;
        if (c <= 1) k = Icons.Kind.SUN;
        else if (c == 2) k = Icons.Kind.PARTLY;
        else if (c == 3) k = Icons.Kind.CLOUD;
        else if (c == 45 || c == 48) k = Icons.Kind.FOG;
        else if ((c >= 71 && c <= 77) || c == 85 || c == 86) k = Icons.Kind.SNOW;
        else if (c >= 95) k = Icons.Kind.STORM;
        else k = Icons.Kind.RAIN;
        if (!day && k == Icons.Kind.SUN) k = Icons.Kind.MOON;
        if (!day && k == Icons.Kind.PARTLY) k = Icons.Kind.MOON_PARTLY;
        return k;
    }

    static LocalDate date(String s) {
        return LocalDate.parse(s.substring(0, 10));
    }

    static String weekdayLong(String s) {
        return date(s).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }

    static String weekdayShort(String s) {
        String w = date(s).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN);
        if (w.length() > 2) w = w.substring(0, 2);
        return w + ".";
    }

    static final int MAX_LINE = 20;

    /**
     * Lange Ortsnamen auf höchstens zwei Zeilen: Umbruch am Bindestrich oder Leerzeichen
     * („Hartmannsdorf-" / „Reichenau"), jede Zeile ab 20 Zeichen mit „…" gekürzt.
     */
    static String wrapName(String name) {
        if (name == null) return "";
        if (name.length() <= 14) return name;
        int best = -1;
        for (int i = 1; i < name.length() - 1; i++) {
            char c = name.charAt(i);
            if (c == '-' || c == ' ') {
                if (best < 0 || Math.abs(i - name.length() / 2) < Math.abs(best - name.length() / 2)) best = i;
            }
        }
        if (best < 0) return cut(name);
        String first = name.charAt(best) == '-' ? name.substring(0, best + 1) : name.substring(0, best);
        return cut(first.trim()) + "\n" + cut(name.substring(best + 1).trim());
    }

    private static String cut(String s) {
        return s.length() <= MAX_LINE ? s : s.substring(0, MAX_LINE - 1).trim() + "…";
    }

    /** Temperatur mit hochgestelltem, kleinem „°C"/„°F" (z. B. für das Widget). */
    static CharSequence tempWithUnit(double c, String unit) {
        String num = String.valueOf(t(c, unit)).replace('-', '\u2212');
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(num + "°" + unit);
        int start = num.length();
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.42f), start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.SuperscriptSpan(), start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    static String hm(String s) {
        return s.length() >= 16 ? s.substring(11, 16) : "";
    }
}
