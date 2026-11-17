package com.example.sancaiminimal;

import android.app.Activity;
import android.os.Bundle;

import io.github.avalonskyafar.sancai.overlay.SancaiDialog;
import io.github.avalonskyafar.sancai.overlay.SancaiToast;
import io.github.avalonskyafar.sancai.theme.SancaiWindow;
import io.github.avalonskyafar.sancai.widget.SancaiButton;
import io.github.avalonskyafar.sancai.widget.SancaiControlRow;
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow;
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack;
import io.github.avalonskyafar.sancai.widget.SancaiSwitch;
import io.github.avalonskyafar.sancai.widget.SancaiText;
import io.github.avalonskyafar.sancai.widget.SancaiTextField;

/**
 * A plain Activity, a layout file and listeners. No base class, no setup call, no Kotlin.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optional: a canvas-coloured window that draws behind the system bars.
        SancaiWindow.apply(this);
        setContentView(R.layout.activity_main);

        SancaiText status = findViewById(R.id.status);
        SancaiControlRow strength = findViewById(R.id.strength);
        SancaiSwitch wifi = findViewById(R.id.wifi);
        SancaiEntryRow exceptions = findViewById(R.id.exceptions);
        SancaiTextField profile = findViewById(R.id.profile);
        SancaiButton save = findViewById(R.id.save);
        SancaiButton restore = findViewById(R.id.restore);

        // The row built its track from app:sancaiOptions.
        SancaiSegmentedTrack track = strength.getTrack();
        track.setOnSelectionChangeListener((view, index) ->
                status.setText(getString(R.string.status_level, view.getOptions().get(index))));

        wifi.setOnCheckedChangeListener((view, checked) ->
                status.setText(checked ? R.string.status_wifi_on : R.string.status_wifi_off));

        exceptions.setOnClickListener(view -> status.setText(R.string.status_exceptions));

        // An error stays until the text it complained about changes.
        profile.setOnTextChangeListener((field, text) -> field.setError(null));

        save.setOnClickListener(view -> {
            boolean named = profile.validate(text ->
                    text.toString().trim().isEmpty() ? getString(R.string.profile_error) : null);
            if (named) {
                String name = profile.getText().toString().trim();
                SancaiToast.show(this, getString(R.string.status_saved, name), SancaiToast.Tone.SUCCESS);
            }
        });

        restore.setOnClickListener(view -> SancaiDialog.confirm(
                this,
                getString(R.string.restore_title),
                getString(R.string.restore_message),
                getString(R.string.restore_action),
                true,
                () -> {
                    // Setters never call listeners, so nothing here needs a guard flag.
                    track.select(2);
                    wifi.setChecked(true);
                    profile.setText("");
                    profile.setError(null);
                    status.setText(R.string.status_restored);
                }));
    }
}
