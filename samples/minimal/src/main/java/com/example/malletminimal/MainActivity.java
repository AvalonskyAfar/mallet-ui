package com.example.malletminimal;

import android.app.Activity;
import android.os.Bundle;

import io.github.avalonskyafar.mallet.overlay.MalletDialog;
import io.github.avalonskyafar.mallet.overlay.MalletToast;
import io.github.avalonskyafar.mallet.theme.MalletWindow;
import io.github.avalonskyafar.mallet.widget.MalletButton;
import io.github.avalonskyafar.mallet.widget.MalletControlRow;
import io.github.avalonskyafar.mallet.widget.MalletEntryRow;
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack;
import io.github.avalonskyafar.mallet.widget.MalletSwitch;
import io.github.avalonskyafar.mallet.widget.MalletText;
import io.github.avalonskyafar.mallet.widget.MalletTextField;

/**
 * A plain Activity, a layout file and listeners. No base class, no setup call, no Kotlin.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optional: a canvas-coloured window that draws behind the system bars.
        MalletWindow.apply(this);
        setContentView(R.layout.activity_main);

        MalletText status = findViewById(R.id.status);
        MalletControlRow strength = findViewById(R.id.strength);
        MalletSwitch wifi = findViewById(R.id.wifi);
        MalletEntryRow exceptions = findViewById(R.id.exceptions);
        MalletTextField profile = findViewById(R.id.profile);
        MalletButton save = findViewById(R.id.save);
        MalletButton restore = findViewById(R.id.restore);

        // The row built its track from app:malletOptions.
        MalletSegmentedTrack track = strength.getTrack();
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
                MalletToast.show(this, getString(R.string.status_saved, name), MalletToast.Tone.SUCCESS);
            }
        });

        restore.setOnClickListener(view -> MalletDialog.confirm(
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
