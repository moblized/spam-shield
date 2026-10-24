package app.spamshield.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

object ListKind {
    const val BLOCK = "block"
    const val ALLOW = "allow"
}

@Entity(tableName = "list_entries", indices = [Index(value = ["kind", "pattern"], unique = true)])
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val pattern: String,
    val label: String?,
    val createdAt: Long,
)

/** Complaint count for one number on one FTC daily file. */
@Entity(tableName = "ftc_reports", primaryKeys = ["number", "day"], indices = [Index("day")])
data class FtcReport(
    val number: String,
    val day: String,
    val reports: Int,
    val subject: String?,
    val robocall: Boolean,
)

/** Which FTC daily files have been downloaded (or don't exist). */
@Entity(tableName = "ftc_days")
data class FtcDay(
    @PrimaryKey val day: String,
    val status: String,
    val numbers: Int,
    val fetchedAt: Long,
) {
    companion object {
        const val LOADED = "loaded"
        const val MISSING = "missing"
    }
}

@Entity(tableName = "call_log", indices = [Index("timestamp")])
data class LoggedCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String?,
    val timestamp: Long,
    val action: String,
    val rule: String?,
    val reason: String,
)

@Dao
interface ListDao {
    @Query("SELECT * FROM list_entries WHERE kind = :kind ORDER BY createdAt DESC")
    fun observe(kind: String): Flow<List<ListEntry>>

    @Query("SELECT * FROM list_entries WHERE kind = :kind")
    suspend fun all(kind: String): List<ListEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ListEntry)

    @Delete
    suspend fun delete(entry: ListEntry)

    @Query("DELETE FROM list_entries WHERE kind = :kind AND pattern = :pattern")
    suspend fun deletePattern(kind: String, pattern: String)
}

@Dao
abstract class FtcDao {
    @Query("SELECT COALESCE(SUM(reports), 0) FROM ftc_reports WHERE number = :number AND day >= :since")
    abstract suspend fun reportCount(number: String, since: String): Int

    @Query("SELECT * FROM ftc_reports WHERE number = :number AND day >= :since ORDER BY day DESC LIMIT 1")
    abstract suspend fun latest(number: String, since: String): FtcReport?

    @Query("SELECT EXISTS(SELECT 1 FROM ftc_reports WHERE number = :number AND day >= :since AND robocall = 1)")
    abstract suspend fun anyRobocall(number: String, since: String): Boolean

    @Query("SELECT COUNT(DISTINCT number) FROM ftc_reports WHERE day >= :since")
    abstract fun observeNumberCount(since: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT number) FROM ftc_reports WHERE day >= :since")
    abstract suspend fun numberCount(since: String): Int

    @Query("SELECT * FROM ftc_days")
    abstract suspend fun days(): List<FtcDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDay(day: FtcDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReports(reports: List<FtcReport>)

    @Query("DELETE FROM ftc_reports WHERE day = :day")
    abstract suspend fun deleteDay(day: String)

    @Query("DELETE FROM ftc_reports WHERE day < :cutoff")
    abstract suspend fun pruneReports(cutoff: String)

    @Query("DELETE FROM ftc_days WHERE day < :cutoff")
    abstract suspend fun pruneDays(cutoff: String)

    @Transaction
    open suspend fun replaceDay(day: String, reports: List<FtcReport>, record: FtcDay) {
        deleteDay(day)
        reports.chunked(2_000).forEach { insertReports(it) }
        upsertDay(record)
    }
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_log ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<LoggedCall>>

    @Query("SELECT COUNT(*) FROM call_log WHERE action = :action AND timestamp >= :since")
    fun observeCount(action: String, since: Long): Flow<Int>

    @Insert
    suspend fun insert(call: LoggedCall): Long

    @Query("DELETE FROM call_log WHERE id NOT IN (SELECT id FROM call_log ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trim()

    @Delete
    suspend fun delete(call: LoggedCall)

    @Query("DELETE FROM call_log")
    suspend fun clear()
}

@Database(
    entities = [ListEntry::class, FtcReport::class, FtcDay::class, LoggedCall::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lists(): ListDao
    abstract fun ftc(): FtcDao
    abstract fun callLog(): CallLogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spamshield.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
