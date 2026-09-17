package com.mohdshayan.kickset

import com.mohdshayan.kickset.core.jobs.BACKUP_FORMAT
import com.mohdshayan.kickset.core.jobs.BackupCalc
import com.mohdshayan.kickset.core.jobs.BackupCodec
import com.mohdshayan.kickset.core.jobs.BackupFile
import com.mohdshayan.kickset.core.jobs.BackupJob
import com.mohdshayan.kickset.core.jobs.BackupPrefs
import com.mohdshayan.kickset.core.jobs.BackupRead
import com.mohdshayan.kickset.core.jobs.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val sample = BackupFile(
        BACKUP_FORMAT, 1, 1_789_000_000_000L, BackupPrefs(unitSystem = "INCH", rootGapMm = 3.2),
        listOf(BackupJob("Unit 3 cooling water", "North rack", 1L, 2L, listOf(
            BackupCalc("CUT_LENGTH", "Spool 14", """{"nps":"4","centreToCentre":"48 3/8"}""", "Cut 42 1/16 in", "INCH", 3L),
            BackupCalc("ROLLING_OFFSET", "Kick at rack 2", """{"set":"12","roll":"16"}""", "Travel 28 5/16 in", "INCH", 4L),
        ))),
    )

    @Test
    fun backupRoundTripsEveryField() {
        val bytes = BackupCodec.encode(sample).toByteArray()
        val read = BackupCodec.decode(bytes)
        assertTrue(read is BackupRead.Valid)
        assertEquals(sample, (read as BackupRead.Valid).file)
    }

    @Test
    fun hostileAndTruncatedFilesAreRejectedWhole() {
        val good = BackupCodec.encode(sample)
        fun rejected(s: String) = assertTrue(s.take(80), BackupCodec.decode(s.toByteArray()) is BackupRead.Invalid)
        rejected(good.substring(0, good.length / 2))
        rejected(good.dropLast(1))
        rejected("")
        rejected("[]")
        rejected(good.replace(BACKUP_FORMAT, "other-app"))
        rejected(good.replace("\"schema\":1", "\"schema\":2"))
        rejected(good.replace("CUT_LENGTH", "DROP_TABLE"))
        rejected(good.replace("\"INCH\",\"inchPrecision\":16", "\"INCH\",\"inchPrecision\":7"))
        rejected(good.replace("Spool 14", "x".repeat(5000)))
        rejected(good.replace("\"Unit 3 cooling water\"", "\"  \""))
        rejected(good.replace("""{\"nps\":\"4\",\"centreToCentre\":\"48 3/8\"}""", "not json"))
        assertTrue(BackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)) is BackupRead.Invalid)
    }

    @Test
    fun csvQuotesCommasQuotesAndFormulaStarts() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a, b\"", CsvExporter.escape("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"'=SUM(A1)\"", CsvExporter.escape("=SUM(A1)"))
        val csv = CsvExporter.write("Unit 3", sample.jobs[0].calcs)
        assertTrue(csv.startsWith("job,label,kind,unit,headline,inputs\r\n"))
        assertEquals(3, csv.trimEnd().split("\r\n").size)
    }
}
