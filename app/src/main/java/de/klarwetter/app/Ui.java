package de.klarwetter.app;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Ui {
    static final ExecutorService BG = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Inhalt von Rand zu Rand, mit Abstand zu Statusleiste, Navigationsleiste und Tastatur. */
    static void edgeToEdge(Activity a, View root) {
        if (Build.VERSION.SDK_INT >= 30) edgeToEdge30(a, root);
    }

    @android.annotation.TargetApi(30)
    private static void edgeToEdge30(Activity a, View root) {
        a.getWindow().setDecorFitsSystemWindows(false);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
            v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom);
            return ins;
        });
        root.requestApplyInsets();
    }

    static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
