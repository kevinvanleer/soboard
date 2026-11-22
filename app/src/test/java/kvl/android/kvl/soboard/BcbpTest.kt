package kvl.android.kvl.soboard

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcbpTest {
    private val sample = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100"

    @Test
    fun parsesCarrierFlightRouteAndDate() {
        assertEquals("AC 834 · YUL→FRA · Nov 22", parseBcbp(sample, LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun picksNearestYearAcrossNewYear() {
        val january = sample.replace(" 326J", " 002J")
        assertEquals("AC 834 · YUL→FRA · Jan 2", parseBcbp(january, LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun rejectsOtherBarcodes() {
        assertNull(parseBcbp("https://example.com/some/long/url/that/is/not/a/boarding/pass/at/all"))
        assertNull(parseBcbp("M1short"))
    }
}
