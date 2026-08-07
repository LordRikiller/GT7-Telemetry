package com.gt7telemetry

import com.gt7telemetry.setup.SetupSheet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serialization round-trip + the parts→settings gating in the briefing text. */
class SetupSheetTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val sheet = SetupSheet(
        carOrdinal = 3241,
        carName = "BMW M3 GT '11",
        parts = SetupSheet.Parts(
            suspension = SetupSheet.SuspensionKind.FULLY_CUSTOM,
            transmission = SetupSheet.TransmissionKind.FULLY_CUSTOM_RACING,
            differential = SetupSheet.DiffKind.FULLY_CUSTOM,
            rearWing = true,
            brakeBalanceController = true,
        ),
        suspension = SetupSheet.Suspension(
            rideHeightF = 90.0, rideHeightR = 95.0, arbF = 5, arbR = 6,
            compF = 60, compR = 60, extF = 70, extR = 70,
            camberF = 3.0, camberR = 2.0, toeF = 0.0, toeR = 0.15,
        ),
        transmission = SetupSheet.Transmission(
            finalDrive = 3.621, topSpeedKmh = 280,
            gears = listOf(2.824, 2.099, 1.679, 1.407, 1.235, 1.135, null, null),
        ),
        differential = SetupSheet.Differential(accelR = 30, brakeR = 15, initialR = 10),
        aero = SetupSheet.Aero(rear = 500),
        brakes = SetupSheet.Brakes(balance = -1),
        notes = "understeers mid-corner",
    )

    @Test
    fun `json round-trip preserves the sheet`() {
        val back = json.decodeFromString<SetupSheet>(json.encodeToString(SetupSheet.serializer(), sheet))
        assertEquals(sheet, back)
        assertTrue(back.hasAnyValues)
        assertFalse(SetupSheet(carOrdinal = 1).hasAnyValues)
    }

    @Test
    fun `briefing text includes unlocked settings and gates locked ones`() {
        val text = sheet.toBriefingText()
        assertTrue(text.contains("Ride height: 90 / 95 mm"))
        assertTrue(text.contains("Anti-roll bar: 5 / 6"))
        assertTrue(text.contains("Camber: 3 / 2°")) // whole numbers print without decimals
        assertTrue(text.contains("Final drive: 3.62"))
        assertTrue(text.contains("1: 2.82"))
        assertTrue(text.contains("Acceleration sensitivity: — / 30"))
        assertTrue(text.contains("rear 500"))
        assertTrue(text.contains("Brake balance: -1 (front)"))
        assertTrue(text.contains("understeers"))

        // Stock suspension: none of the suspension settings may leak through.
        val stock = sheet.copy(parts = sheet.parts.copy(suspension = SetupSheet.SuspensionKind.STOCK))
        assertFalse(stock.toBriefingText().contains("Ride height"))

        // Stock transmission gates gearing even when values are present.
        val stockTrans = sheet.copy(parts = sheet.parts.copy(transmission = SetupSheet.TransmissionKind.STOCK))
        assertFalse(stockTrans.toBriefingText().contains("Final drive"))
    }
}
