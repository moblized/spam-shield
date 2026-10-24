package app.spamshield

import android.app.Application
import android.content.Context
import app.spamshield.data.AppDatabase
import app.spamshield.data.Repository
import app.spamshield.data.SettingsStore
import app.spamshield.notify.Notifier
import app.spamshield.work.FtcSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SpamShieldApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        repository = Repository(this, db, settings)
        Notifier.createChannel(this)
        // Runs right away the first time, then about once a day.
        FtcSyncWorker.schedule(this)
    }

    companion object {
        fun get(context: Context): SpamShieldApp = context.applicationContext as SpamShieldApp
    }
}
