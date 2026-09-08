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

public class SetBookmarkSyncActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private static SharedPreferences sharedPreferences;
    private EditTextPreference mBookmarkSyncHost;
    private EditTextPreference mBookmarkSyncPort;
    private EditTextPreference mBookmarkSyncUser;
    private EditTextPreference mBookmarkSyncPass;

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

        addPreferencesFromResource(R.xml.setbookmarksync);

        mBookmarkSyncHost = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_BOOKMARKSYNC_HOST);
        mBookmarkSyncPort = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_BOOKMARKSYNC_PORT);
        mBookmarkSyncUser = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_BOOKMARKSYNC_USER);
        mBookmarkSyncPass = (EditTextPreference) getPreferenceScreen().findPreference(DEF.KEY_BOOKMARKSYNC_PASS);
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
        mBookmarkSyncHost.setSummary(sharedPreferences.getString(DEF.KEY_BOOKMARKSYNC_HOST, ""));
        mBookmarkSyncPort.setSummary(sharedPreferences.getString(DEF.KEY_BOOKMARKSYNC_PORT, "8081"));
        mBookmarkSyncUser.setSummary(sharedPreferences.getString(DEF.KEY_BOOKMARKSYNC_USER, ""));
        mBookmarkSyncPass.setSummary(sharedPreferences.getString(DEF.KEY_BOOKMARKSYNC_PASS, "").isEmpty() ? "" : "********");
    }
}
