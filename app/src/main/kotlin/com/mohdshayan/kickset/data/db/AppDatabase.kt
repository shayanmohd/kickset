package com.mohdshayan.kickset.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mohdshayan.kickset.core.jobs.BackupCalc
import com.mohdshayan.kickset.core.jobs.BackupJob
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "saved_calcs",
    foreignKeys = [ForeignKey(entity = Job::class, parentColumns = ["id"], childColumns = ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("jobId")],
)
data class SavedCalc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val kind: String,
    val label: String,
    val inputsJson: String,
    val headline: String,
    val unitSystem: String,
    val createdAt: Long,
)

data class JobSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    @ColumnInfo(name = "calcCount") val calcCount: Int,
)

@Dao
interface JobDao {
    @Query("SELECT j.id, j.name, j.updatedAt, (SELECT COUNT(*) FROM saved_calcs c WHERE c.jobId = j.id) AS calcCount FROM jobs j ORDER BY j.updatedAt DESC")
    fun observeSummaries(): Flow<List<JobSummary>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: Long): Flow<Job?>

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    suspend fun all(): List<Job>

    @Insert
    suspend fun insert(job: Job): Long

    @Query("UPDATE jobs SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE jobs SET name = :name, updatedAt = :at WHERE id = :id")
    suspend fun rename(id: Long, name: String, at: Long)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM jobs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM jobs")
    fun observeCount(): Flow<Int>
}

@Dao
interface CalcDao {
    @Query("SELECT * FROM saved_calcs WHERE jobId = :jobId ORDER BY createdAt ASC")
    fun observeForJob(jobId: Long): Flow<List<SavedCalc>>

    @Query("SELECT * FROM saved_calcs WHERE jobId = :jobId ORDER BY createdAt ASC")
    suspend fun forJob(jobId: Long): List<SavedCalc>

    @Insert
    suspend fun insert(calc: SavedCalc): Long

    @Insert
    suspend fun insertAll(calcs: List<SavedCalc>)

    @Query("DELETE FROM saved_calcs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM saved_calcs")
    fun observeCount(): Flow<Int>
}

@Database(entities = [Job::class, SavedCalc::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun calcDao(): CalcDao

    /** Saves one calculation into an existing job or a new one, in one transaction. Returns the job id. */
    suspend fun saveCalc(existingJobId: Long?, newJobName: String?, calc: SavedCalc, now: Long): Long = withTransaction {
        val jobId = existingJobId ?: jobDao().insert(Job(name = newJobName!!.trim(), createdAt = now, updatedAt = now))
        calcDao().insert(calc.copy(jobId = jobId, createdAt = now))
        jobDao().touch(jobId, now)
        jobId
    }

    /** Restores backup jobs. Replace wipes every job first; merge adds them alongside. All or nothing. */
    suspend fun restore(jobs: List<BackupJob>, replace: Boolean) = withTransaction {
        if (replace) jobDao().deleteAll()
        for (j in jobs) {
            val id = jobDao().insert(Job(name = j.name, notes = j.notes, createdAt = j.createdAt, updatedAt = j.updatedAt))
            calcDao().insertAll(j.calcs.map { SavedCalc(0, id, it.kind, it.label, it.inputsJson, it.headline, it.unitSystem, it.createdAt) })
        }
    }

    suspend fun snapshot(): List<BackupJob> = withTransaction {
        jobDao().all().map { j ->
            BackupJob(j.name, j.notes, j.createdAt, j.updatedAt, calcDao().forJob(j.id).map { BackupCalc(it.kind, it.label, it.inputsJson, it.headline, it.unitSystem, it.createdAt) })
        }
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kickset.db")
                    .build().also { instance = it }
            }
    }
}
