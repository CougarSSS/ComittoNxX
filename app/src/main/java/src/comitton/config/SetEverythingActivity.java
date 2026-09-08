package src.comitton.config;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import src.comitton.common.DEF;
import jp.dip.muracoro.comittonx.R;

public class SetEverythingActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static SharedPreferences sharedPreferences;
    private EditTextPreference mEverythingHost;
    private EditTextPreference mEverythingPort;
    private EditTextPreference mEverythingUser;
    private EditTextPreference mEverythingPass;
    private EditTextPreference mEverythingReplaceFrom;
    private EditTextPreference mEverythingReplaceTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean notice = SetCommonActivity.getForceHideStatusBar(sharedPreferences);
        if (notice) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        boolean immEnable = SetCommonActivity.getForceHideNavigationBar(sharedPreferences);
        if (immEnable && android.os.Build.VERSION.SDK_INT >= 19) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
        SetCommonActivity.SetOrientationEventListener(this, sharedPreferences);

        addPreferencesFromResource(R.xml.seteverything);

        mEverythingHost = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_HOST);
        mEverythingPort = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_PORT);
        mEverythingUser = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_USER);
        mEverythingPass = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_PASS);
        mEverythingReplaceFrom = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_REPLACE_FROM);
        mEverythingReplaceTo = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_EVERYTHING_REPLACE_TO);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SetCommonActivity.SetOrientationEventListenerEnable(sharedPreferences);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        updateSummaries();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        SetCommonActivity.SetOrientationEventListenerDisable(sharedPreferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateSummaries();
    }

    private void updateSummaries() {
        mEverythingHost.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_HOST, ""));
        mEverythingPort.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_PORT, "8080"));
        mEverythingUser.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_USER, ""));
        mEverythingPass.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_PASS, "").isEmpty() ? "" : "********");
        mEverythingReplaceFrom.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_REPLACE_FROM, ""));
        mEverythingReplaceTo.setSummary(sharedPreferences.getString(DEF.KEY_EVERYTHING_REPLACE_TO, ""));
    }
}
