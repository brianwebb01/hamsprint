package org.brianwebb.hamsprint.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionProgress

data class ExamRecord(
    val takenAtMillis: Long,
    val score: Int,
    val total: Int,
    val passingScore: Int,
    val durationMillis: Long,
) {
    val passed: Boolean get() = score >= passingScore
}

/**
 * The only place study progress is persisted. Every query is scoped to one
 * license's pool, which also bounds it (a pool has at most ~600 questions).
 *
 * All operations go through one fair (FIFO) lock, so a read always observes
 * every write that was requested before it, even across screens.
 */
class ProgressStore(
    context: Context,
    private val io: CoroutineDispatcher,
    name: String? = DB_NAME,
) {
    private val helper = Helper(context.applicationContext, name)
    private val _version = MutableStateFlow(0)
    private val lock = Mutex()

    /** Increments after every write so screens can refresh derived state. */
    val version: StateFlow<Int> = _version.asStateFlow()

    suspend fun progress(license: LicenseClass): Map<String, QuestionProgress> = lock.withLock { withContext(io) {
        helper.readableDatabase.query(
            T_PROGRESS, null, "pool_id = ?", arrayOf(license.poolId), null, null, null,
        ).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val p = QuestionProgress(
                        questionId = c.getString(c.getColumnIndexOrThrow("question_id")),
                        stage = c.getInt(c.getColumnIndexOrThrow("stage")),
                        dueAtMillis = c.getLong(c.getColumnIndexOrThrow("due_at")),
                        timesSeen = c.getInt(c.getColumnIndexOrThrow("seen")),
                        timesCorrect = c.getInt(c.getColumnIndexOrThrow("correct")),
                        lapses = c.getInt(c.getColumnIndexOrThrow("lapses")),
                        lastAnsweredAtMillis = c.getLong(c.getColumnIndexOrThrow("last_answered_at")),
                    )
                    put(p.questionId, p)
                }
            }
        }
    }}

    /** Upserts [updates] and adds [studiedMillis] to the study clock, atomically. */
    suspend fun save(license: LicenseClass, updates: List<QuestionProgress>, studiedMillis: Long = 0) {
        lock.withLock {
            withContext(io) { helper.writableDatabase.inTransaction { writeProgress(license, updates, studiedMillis) } }
        }
        _version.update { it + 1 }
    }

    /** Records a finished practice exam together with the progress it changed. */
    suspend fun recordExam(license: LicenseClass, record: ExamRecord, updates: List<QuestionProgress>) {
        lock.withLock { withContext(io) {
            helper.writableDatabase.inTransaction {
                insertOrThrow(
                    T_EXAMS, null,
                    ContentValues().apply {
                        put("pool_id", license.poolId)
                        put("taken_at", record.takenAtMillis)
                        put("score", record.score)
                        put("total", record.total)
                        put("passing", record.passingScore)
                        put("duration_ms", record.durationMillis)
                    },
                )
                writeProgress(license, updates, record.durationMillis)
            }
        } }
        _version.update { it + 1 }
    }

    suspend fun recentExams(license: LicenseClass, limit: Int = 10): List<ExamRecord> = lock.withLock { withContext(io) {
        helper.readableDatabase.query(
            T_EXAMS, null, "pool_id = ?", arrayOf(license.poolId), null, null, "taken_at DESC", limit.toString(),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ExamRecord(
                            takenAtMillis = c.getLong(c.getColumnIndexOrThrow("taken_at")),
                            score = c.getInt(c.getColumnIndexOrThrow("score")),
                            total = c.getInt(c.getColumnIndexOrThrow("total")),
                            passingScore = c.getInt(c.getColumnIndexOrThrow("passing")),
                            durationMillis = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
                        ),
                    )
                }
            }
        }
    }}

    suspend fun studyTimeMillis(license: LicenseClass): Long = lock.withLock { withContext(io) {
        helper.readableDatabase.rawQuery(
            "SELECT millis FROM $T_STUDY WHERE pool_id = ?", arrayOf(license.poolId),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }}

    suspend fun reset(license: LicenseClass) {
        lock.withLock { withContext(io) {
            helper.writableDatabase.inTransaction {
                val args = arrayOf(license.poolId)
                delete(T_PROGRESS, "pool_id = ?", args)
                delete(T_EXAMS, "pool_id = ?", args)
                delete(T_STUDY, "pool_id = ?", args)
            }
        } }
        _version.update { it + 1 }
    }

    fun close() = helper.close()

    private fun SQLiteDatabase.writeProgress(
        license: LicenseClass,
        updates: List<QuestionProgress>,
        studiedMillis: Long,
    ) {
        for (p in updates) {
            insertWithOnConflict(
                T_PROGRESS, null,
                ContentValues().apply {
                    put("question_id", p.questionId)
                    put("pool_id", license.poolId)
                    put("stage", p.stage)
                    put("due_at", p.dueAtMillis)
                    put("seen", p.timesSeen)
                    put("correct", p.timesCorrect)
                    put("lapses", p.lapses)
                    put("last_answered_at", p.lastAnsweredAtMillis)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        if (studiedMillis > 0) {
            // UPSERT syntax needs SQLite 3.24 (API 30); minSdk 26 ships 3.19.
            val updated = compileStatement("UPDATE $T_STUDY SET millis = millis + ? WHERE pool_id = ?").use {
                it.bindLong(1, studiedMillis)
                it.bindString(2, license.poolId)
                it.executeUpdateDelete()
            }
            if (updated == 0) {
                insertOrThrow(T_STUDY, null, ContentValues().apply {
                    put("pool_id", license.poolId)
                    put("millis", studiedMillis)
                })
            }
        }
    }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private class Helper(context: Context, name: String?) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $T_PROGRESS (
                    question_id TEXT PRIMARY KEY NOT NULL,
                    pool_id TEXT NOT NULL,
                    stage INTEGER NOT NULL,
                    due_at INTEGER NOT NULL,
                    seen INTEGER NOT NULL,
                    correct INTEGER NOT NULL,
                    lapses INTEGER NOT NULL,
                    last_answered_at INTEGER NOT NULL)""",
            )
            db.execSQL("CREATE INDEX idx_progress_pool ON $T_PROGRESS(pool_id)")
            db.execSQL(
                """CREATE TABLE $T_EXAMS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pool_id TEXT NOT NULL,
                    taken_at INTEGER NOT NULL,
                    score INTEGER NOT NULL,
                    total INTEGER NOT NULL,
                    passing INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL)""",
            )
            db.execSQL("CREATE INDEX idx_exams_pool ON $T_EXAMS(pool_id, taken_at)")
            db.execSQL("CREATE TABLE $T_STUDY (pool_id TEXT PRIMARY KEY NOT NULL, millis INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DB_NAME = "hamsprint.db"
        const val T_PROGRESS = "progress"
        const val T_EXAMS = "exam_results"
        const val T_STUDY = "study_time"
    }
}
