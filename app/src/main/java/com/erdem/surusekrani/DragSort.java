package com.erdem.surusekrani;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Dikey listede satırı tutup sürükleyerek sıralama.
 * Tutamağa (≡) dokununca hemen, satırın boş yerine basılı tutunca sürükleme başlar.
 * Sürüklerken diğer satırlar kayarak yer açar; bırakınca {@link Moved#moved} ile yeni sıra bildirilir
 * (çağıran listeyi kaydedip yeniden çizer). Sadece {@link #make} ile işaretlenen satırlar sıralanır.
 */
final class DragSort {
    interface Moved { void moved(int from, int to); }

    private static final int TAG = R.id.drag_row;

    private final LinearLayout list;
    private final View row;
    private final Moved cb;
    private final List<View> rows = new ArrayList<>();
    private boolean dragging;
    private float startRaw, lastRaw;
    private int from, target, step;

    private DragSort(LinearLayout list, View row, Moved cb) {
        this.list = list;
        this.row = row;
        this.cb = cb;
    }

    /** Satırı sürüklenebilir yap. handle null olabilir (o zaman sadece basılı tutarak). */
    static void make(LinearLayout list, View row, View handle, Moved cb) {
        row.setTag(TAG, Boolean.TRUE);
        DragSort s = new DragSort(list, row, cb);
        row.setOnTouchListener((v, e) -> {
            s.lastRaw = e.getRawY();
            if (!s.dragging) return false;
            return s.track(e);
        });
        row.setLongClickable(true);
        row.setOnLongClickListener(v -> { s.start(s.lastRaw); return true; });
        if (handle != null) {
            handle.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) s.start(e.getRawY());
                return s.track(e);
            });
        }
    }

    private void start(float raw) {
        rows.clear();
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c.getTag(TAG) != null && c.getVisibility() == View.VISIBLE) rows.add(c);
        }
        from = target = rows.indexOf(row);
        if (from < 0 || rows.size() < 2) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
        step = row.getHeight() + lp.topMargin + lp.bottomMargin;
        dragging = true;
        startRaw = lastRaw = raw;
        row.getParent().requestDisallowInterceptTouchEvent(true); // kaydırma çubuğu araya girmesin
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        row.setElevation(10 * row.getResources().getDisplayMetrics().density);
        row.setAlpha(0.92f);
        row.setScaleX(1.02f);
        row.setScaleY(1.02f);
    }

    private boolean track(MotionEvent e) {
        if (!dragging) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                move(e.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finish();
                return true;
        }
        return true;
    }

    private void move(float raw) {
        View first = rows.get(0), last = rows.get(rows.size() - 1);
        float dy = raw - startRaw;
        dy = Math.max(first.getTop() - row.getTop(), Math.min(last.getBottom() - row.getBottom(), dy));
        row.setTranslationY(dy);
        float center = row.getTop() + row.getHeight() / 2f + dy;
        target = from;
        for (int j = 0; j < rows.size(); j++) {
            if (j == from) continue;
            View o = rows.get(j);
            float oc = o.getTop() + o.getHeight() / 2f;
            float shift = 0;
            if (j > from && center > oc) { shift = -step; target = Math.max(target, j); }
            if (j < from && center < oc) { shift = step; target = Math.min(target, j); }
            if (o.getTranslationY() != shift) o.animate().translationY(shift).setDuration(120).start();
        }
    }

    private void finish() {
        dragging = false;
        for (View o : rows) {
            o.animate().cancel();
            o.setTranslationY(0);
        }
        row.setElevation(0);
        row.setAlpha(1f);
        row.setScaleX(1f);
        row.setScaleY(1f);
        int f = from, t = target;
        if (f >= 0 && t != f) list.post(() -> cb.moved(f, t));
    }

    /** Listede from'daki öğeyi to'ya taşı. */
    static <T> void move(List<T> l, int from, int to) {
        T x = l.remove(from);
        l.add(to, x);
    }
}
