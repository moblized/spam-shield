package app.spamshield.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.spamshield.MainActivity
import app.spamshield.R
import app.spamshield.SpamShieldApp
import app.spamshield.core.PhoneNumbers
import app.spamshield.core.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Notifier {
    const val CHANNEL_ID = "blocked_calls"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Blocked calls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A quiet note each time Spam Shield hangs up on a call"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun blocked(context: Context, number: String?, verdict: Verdict) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val id = (number ?: "hidden").hashCode()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (number == null) "Blocked a private number" else "Blocked ${PhoneNumbers.display(number)}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(verdict.reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verdict.reason))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (number != null) {
            val notSpam = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, NotSpamReceiver::class.java)
                    .putExtra(EXTRA_NUMBER, number)
                    .putExtra(EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Not spam", notSpam)
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

/** "Not spam" on a blocked-call notification: adds the number to the allow list. */
class NotSpamReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(Notifier.EXTRA_NUMBER) ?: return
        val id = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, 0)
        val app = SpamShieldApp.get(context)
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.allowNumber(number)
                NotificationManagerCompat.from(context).cancel(id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${PhoneNumbers.display(number)} will always ring", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
