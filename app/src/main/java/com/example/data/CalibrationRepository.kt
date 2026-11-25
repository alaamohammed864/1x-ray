package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BodyCalibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationStore: DataStore<Preferences> by preferencesDataStore(name = "xray_calibration")

class CalibrationRepository(private val context: Context) {
    private object Keys {
        val RW = floatPreferencesKey("cal_rw")
        val RA = floatPreferencesKey("cal_ra")
        val RF = floatPreferencesKey("cal_rf")
        val RT = floatPreferencesKey("cal_rt")
        val RS = floatPreferencesKey("cal_rs")
    }

    val calibrationFlow: Flow<BodyCalibration?> = context.calibrationStore.data.map { prefs ->
        val rw = prefs[Keys.RW] ?: 0f
        if (rw <= 0f) {
            null
        } else {
            BodyCalibration(
                rW = rw,
                rA = prefs[Keys.RA] ?: 0f,
                rF = prefs[Keys.RF] ?: 0f,
                rT = prefs[Keys.RT] ?: 0f,
                rS = prefs[Keys.RS] ?: 0f,
            )
        }
    }

    suspend fun saveCalibration(calibration: BodyCalibration) {
        context.calibrationStore.edit { prefs ->
            prefs[Keys.RW] = calibration.rW
            prefs[Keys.RA] = calibration.rA
            prefs[Keys.RF] = calibration.rF
            prefs[Keys.RT] = calibration.rT
            prefs[Keys.RS] = calibration.rS
        }
    }

    suspend fun clearCalibration() {
        context.calibrationStore.edit { it.clear() }
    }
}
