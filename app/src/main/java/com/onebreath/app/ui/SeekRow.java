package com.onebreath.app.ui;

import android.content.Context;
import android.widget.SeekBar;
import android.widget.TextView;

import com.onebreath.app.R;

/** 一條有範圍（min..max）的 SeekBar 加上即時數值文字。SeekBar 本身從 0 起算，這裡做位移。 */
final class SeekRow {

    interface Format {
        String label(int value);
    }

    interface OnChange {
        void changed(int value);
    }

    private final SeekBar bar;
    private final TextView label;
    private final int min, max;
    private final Format format;

    SeekRow(SeekBar bar, TextView label, int min, int max, Format format, OnChange onChange) {
        this.bar = bar;
        this.label = label;
        this.min = min;
        this.max = max;
        this.format = format;
        bar.setMax(max - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = progress + SeekRow.this.min;
                SeekRow.this.label.setText(SeekRow.this.format.label(v));
                if (fromUser && onChange != null) onChange.changed(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
    }

    void set(int value) {
        int v = clamp(value);
        bar.setProgress(v - min);
        label.setText(format.label(v));
    }

    int get() {
        return bar.getProgress() + min;
    }

    int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    // c 必須是已套用 App 語言的 Context（Activity 本身即可）

    static String seconds(Context c, int s) {
        return s == 0 ? c.getString(R.string.value_no_wait)
                : c.getResources().getQuantityString(R.plurals.value_seconds, s, s);
    }

    static String minutes(Context c, int m) {
        return c.getResources().getQuantityString(R.plurals.value_minutes, m, m);
    }

    static String everyMinutes(Context c, int m) {
        return c.getResources().getQuantityString(R.plurals.value_every_minutes, m, m);
    }
}
