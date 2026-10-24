package app.spamshield.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telecom.TelecomManager
import app.spamshield.SpamShieldApp
import app.spamshield.core.Action
import app.spamshield.core.IncomingCall
import app.spamshield.core.PhoneNumbers
import app.spamshield.core.Verdict
import app.spamshield.core.Verification
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Android calls this for every incoming call from a number that isn't in your contacts,
 * once Spam Shield is set as the "Caller ID & spam" app. We must answer within a few seconds.
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val app = SpamShieldApp.get(this)
        val call = describe(details)
        app.scope.launch {
            val verdict = try {
                withTimeout(4_000) { app.repository.evaluate(call) }
            } catch (t: Throwable) {
                Verdict(Action.OFF, null, "Couldn't check in time, so it rang through")
            }
            respondToCall(details, response(verdict))
            runCatching { app.repository.record(call, verdict) }
        }
    }

    private fun describe(details: Call.Details): IncomingCall {
        val raw = details.handle?.schemeSpecificPart
        val number = PhoneNumbers.normalize(raw)
        val hidden = number == null || details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (details.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_FAILED -> Verification.FAILED
                Connection.VERIFICATION_STATUS_PASSED -> Verification.PASSED
                else -> Verification.NOT_VERIFIED
            }
        } else {
            Verification.NOT_VERIFIED
        }
        return IncomingCall(if (hidden) null else number, hidden, verification)
    }

    private fun response(verdict: Verdict): CallResponse = when (verdict.action) {
        Action.BLOCK -> CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()
        Action.SILENCE -> CallResponse.Builder()
            .setSilenceCall(true)
            .build()
        Action.OFF -> CallResponse.Builder().build()
    }
}
