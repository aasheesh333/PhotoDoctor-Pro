package com.dhanuk.photodoctorpro.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dhanuk.photodoctorpro.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {

    private const val TAG = "ConsentManager"

    @Volatile var isConsentObtained: Boolean = false
        private set

    private var consentInformation: ConsentInformation? = null

    fun init(activity: Activity) {
        // Non-GMS devices (OPPO/ColorOS review units, HMS-only handsets): UMP
        // and AdMob depend on Google Play Services — running the consent flow
        // there can surface the system "Download Google Play services" prompt
        // that OPPO rejects as "Mandatory download from Google Play". Skip the
        // whole flow; the app runs fully ad-free on such devices.
        val gmsStatus = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(activity)
        if (gmsStatus != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Log.d(TAG, "GMS unavailable — skipping consent flow and ads")
            return
        }
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation?.requestConsentInfoUpdate(
            activity,
            params,
            {
                val info = consentInformation ?: return@requestConsentInfoUpdate
                if (info.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity)
                } else {
                    checkConsentAndInitAds(activity)
                }
            },
            { formError: FormError ->
                Log.e(TAG, "Consent info request failed: ${formError.message}")
                checkConsentAndInitAds(activity)
            }
        )
    }

    private fun loadAndShowConsentForm(activity: Activity) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { consentForm ->
                if (activity.isFinishing || activity.isDestroyed) {
                    checkConsentAndInitAds(activity)
                } else {
                    consentForm.show(activity) { formError ->
                        if (formError != null) {
                            Log.e(TAG, "Consent form error: ${formError.message}")
                        }
                        checkConsentAndInitAds(activity)
                    }
                }
            },
            { formError: FormError ->
                Log.e(TAG, "Consent form load failed: ${formError.message}")
                checkConsentAndInitAds(activity)
            }
        )
    }

    private fun checkConsentAndInitAds(context: Context) {
        val info = consentInformation
        if (info != null) {
            isConsentObtained = info.canRequestAds()
        }
        if (canRequestAds() && isGmsAvailable(context)) {
            AdManager.initialize(context)
        }
    }

    /** True when Google Play Services is usable on this device. */
    private fun isGmsAvailable(context: Context): Boolean {
        val status = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return status == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    fun canRequestAds(): Boolean {
        val info = consentInformation
        return if (info != null) {
            info.canRequestAds()
        } else {
            isConsentObtained
        }
    }

    fun resetConsent(activity: Activity) {
        consentInformation?.reset()
        isConsentObtained = false
        init(activity)
    }
}
