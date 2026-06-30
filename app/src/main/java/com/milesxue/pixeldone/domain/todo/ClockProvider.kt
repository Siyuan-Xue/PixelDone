package com.milesxue.pixeldone.domain.todo

/**
 * 时间来源接口。
 *
 * 教学说明：把 System.currentTimeMillis() 包起来后，测试可以注入固定时间，
 * DDL 倒计时、重复提醒和 snooze 规则就更容易验证。
 */
interface ClockProvider {
    fun nowMillis(): Long
}

object SystemClockProvider : ClockProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
