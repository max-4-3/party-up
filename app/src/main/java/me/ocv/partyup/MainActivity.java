package me.ocv.partyup;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import androidx.appcompat.app.AppCompatActivity;

import me.ocv.partyup.databinding.ActivityMainBinding;
import me.ocv.partyup.utils.PermissionUtils;
import me.ocv.partyup.utils.ToastUtils;

public final class MainActivity extends AppCompatActivity {
    @Override
    protected void onStart() {
        super.onStart();
        if (!PermissionUtils.hasAllPermissions(this)) {
            PermissionUtils.requestAllPermissions(this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        String txt = String.format(getString(R.string.welcome_text), BuildConfig.VERSION_NAME);

        binding.mainText.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));
        binding.mainText.setMovementMethod(LinkMovementMethod.getInstance());

        binding.uploadsBtn.setOnClickListener(v -> launchActivity(UploadInfoActivity.class));
        binding.uploadFileBtn.setOnClickListener(v -> chooseAndUploadFile());
        binding.settingsBtn.setOnClickListener(v -> launchActivity(SettingsActivity.class));
    }

    private void chooseAndUploadFile() {
        ToastUtils.show(this, "Not implemented!");
    }

    private void launchActivity(Class<? extends AppCompatActivity> klass) {
        startActivity(new Intent(this, klass));
    }
}
