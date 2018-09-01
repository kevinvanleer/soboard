package kvl.android.kvl.soboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

/**
 * Created by kvl on 3/5/17.
 */
public class Draper {
    InterstitialAd interstitialAd;
    boolean interstitialAdsEnabled = false;
    SharedPreferences settings;
    final String AD_SETTINGS = "AdPrefs";
    final String TRIAL_PERIOD_COUNT = "trial_period_count";
    final int TRIAL_INITIAL_COUNT = 10;
    final String LOG_TAG = "Draper";

    Draper(Context context, boolean newUser) {
        settings = context.getSharedPreferences(AD_SETTINGS, Context.MODE_PRIVATE);
        if (!newUser && !settings.contains(TRIAL_PERIOD_COUNT)) {
            settings.edit().putInt(TRIAL_PERIOD_COUNT, 0).apply();
        }

        //DEBUG: Uncomment to reset counter
        //settings.edit().putInt(TRIAL_PERIOD_COUNT, TRIAL_INITIAL_COUNT).apply();
        int trialCount = settings.getInt(TRIAL_PERIOD_COUNT, TRIAL_INITIAL_COUNT);
        if (trialCount == 0) {
            Log.v(LOG_TAG, "Trial over. Enabling interstitial ads");
            interstitialAdsEnabled = true;
        }

        interstitialAd = new InterstitialAd(context);
        interstitialAd.setAdUnitId(context.getResources().getString(R.string.interstitial_ad_unit_id));
    }

    public void requestNewInterstitial() {
        //if(interstitialAdsEnabled) {
            Log.v(LOG_TAG, "Requesting new interstitial ad");
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("A11960FBF8D4DAB9AFC3DE56A7D7C0D8")
                    .build();

            interstitialAd.loadAd(adRequest);
        //} else {
        //    Log.v(LOG_TAG, "Interstitial ads are not enabled");
        //}
    }

    public boolean interstitialReady() {
        if (!interstitialAdsEnabled) {
            Log.v(LOG_TAG, "Interstitial ads are currently disabled");
            decrementTrialPeriod();
        }
        if(!interstitialAd.isLoaded()) {
            Log.v(LOG_TAG, "Interstitial ad has not loaded yet.");
        }
        return interstitialAdsEnabled && interstitialAd.isLoaded();
    }

    private void decrementTrialPeriod() {
        int trialCount = settings.getInt(TRIAL_PERIOD_COUNT, TRIAL_INITIAL_COUNT);
        if (trialCount > 0) {
            Log.v(LOG_TAG, String.format("Decrementing trial period, %d ad free actions remaining", trialCount));
            settings.edit().putInt(TRIAL_PERIOD_COUNT, (--trialCount)).apply();
        }
        if (trialCount == 0) {
            Log.v(LOG_TAG, "Trial expired. Enabling interstitial ads");
            interstitialAdsEnabled = true;
        }
    }

    public void showInterstitialAd() {
        Log.v(LOG_TAG, "Displaying interstitial ad.");
        interstitialAd.show();
    }

    public void setInterstitialListener(AdListener adListener) {
        interstitialAd.setAdListener(adListener);
    }
}
