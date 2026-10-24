package com.limelight;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.utils.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The licenses this app is distributed under, and those of what it is built from.
 *
 * The app is free software, so the text of the license it is under belongs in front of the user
 * rather than only on a website. It is one long scroll of text, which is what a license wants to
 * be.
 */
public class OpenSourceLicensesActivity extends Activity {
    private static final String LICENSES_ASSET = "licenses.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);

        text.setText(readLicenses());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setPadding(dp(16), dp(16), dp(16), dp(16));

        ScrollView scroll = new ScrollView(this);

        scroll.addView(text);
        setContentView(scroll);

        // The same padding every other screen of the app gets, so the text starts below the
        // status bar rather than under it
        UiHelper.notifyNewRootView(this);
    }

    private String readLicenses() {
        try {
            InputStream in = getResources().openRawResource(R.raw.licenses);

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;

                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }

                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return getString(R.string.title_open_source_licenses);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
