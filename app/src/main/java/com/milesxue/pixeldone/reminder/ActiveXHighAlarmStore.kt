package com.milesxue.pixeldone.reminder

import android.content.Context
import android.content.SharedPreferences

internal class ActiveXHighAlarmStore(
    private val sharedPreferences: SharedPreferences,
) {
    fun load(nowMillis: Long = System.currentTimeMillis()): ActiveXHighAlarm? {
        val payload = sharedPreferences.getString(KEY_ACTIVE_ALARM, null) ?: return null
        val alarm = ActiveXHighAlarmCodec.decode(payload)
        if (alarm == null || alarm.isStale(nowMillis)) {
            clear()
            return null
        }
        return alarm
    }

    fun save(alarm: ActiveXHighAlarm) {
        sharedPreferences.edit()
            .putString(KEY_ACTIVE_ALARM, ActiveXHighAlarmCodec.encode(alarm))
            .apply()
    }

    fun clear() {
        sharedPreferences.edit()
            .remove(KEY_ACTIVE_ALARM)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pixeldone_active_xhigh_alarm"
        private const val KEY_ACTIVE_ALARM = "active_alarm"

        fun create(context: Context): ActiveXHighAlarmStore {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return ActiveXHighAlarmStore(prefs)
        }
    }
}
