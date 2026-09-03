/*
 * Copyright (C) 2026 FundamentalOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package org.derpfest.clocks

import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Locale

abstract class TimespecHandler(val cal: Calendar) {
    /** Test hook: freeze the clock at a fixed time. */
    var fakeTimeMills: Long? = null

    fun setTimeZone(timeZone: TimeZone) {
        cal.timeZone = timeZone
        onTimeZoneChanged()
    }

    fun updateTime() {
        cal.timeInMillis = fakeTimeMills ?: System.currentTimeMillis()
    }

    protected open fun onTimeZoneChanged() {}
}

class DigitalTimespecHandler(
    val timespec: DigitalTimespec,
    private val timeFormat: String,
    cal: Calendar = Calendar.getInstance(),
) : TimespecHandler(cal) {
    var is24Hr: Boolean = false
        set(value) {
            field = value
            applyPattern()
        }

    private var dateFormat: DateFormat = updateSimpleDateFormat(Locale.getDefault())
    private var contentDescriptionFormat: DateFormat? = getContentDescriptionFormat(Locale.getDefault())

    init {
        applyPattern()
    }

    override fun onTimeZoneChanged() {
        dateFormat.timeZone = TimeZone.getTimeZone(cal.timeZone.id)
        contentDescriptionFormat?.timeZone = TimeZone.getTimeZone(cal.timeZone.id)
        applyPattern()
    }

    fun updateLocale(locale: Locale) {
        dateFormat = updateSimpleDateFormat(locale)
        contentDescriptionFormat = getContentDescriptionFormat(locale)
        onTimeZoneChanged()
    }

    private fun updateSimpleDateFormat(locale: Locale): DateFormat {
        // Time is always rendered with the literal json pattern; only dates get localized.
        return if (locale.language == Locale.ENGLISH.language || timespec != DigitalTimespec.DATE_FORMAT) {
            SimpleDateFormat(timeFormat, timeFormat, ULocale.forLocale(locale))
        } else {
            SimpleDateFormat.getInstanceForSkeleton(timeFormat, locale)
        }
    }

    private fun getContentDescriptionFormat(locale: Locale): DateFormat? =
        when (timespec) {
            DigitalTimespec.TIME_FULL_FORMAT -> SimpleDateFormat.getInstanceForSkeleton("hh:mm", locale)
            DigitalTimespec.DATE_FORMAT -> SimpleDateFormat.getInstanceForSkeleton("EEEE MMMM d", locale)
            else -> null
        }

    private fun applyPattern() {
        val pattern = if (is24Hr) timeFormat.replace("hh", "h").replace("h", "HH") else timeFormat
        if (timespec != DigitalTimespec.DATE_FORMAT) {
            (dateFormat as SimpleDateFormat).applyPattern(pattern)
            (contentDescriptionFormat as? SimpleDateFormat)?.applyPattern(if (is24Hr) "HH:mm" else "hh:mm")
        }
    }

    private fun getSingleDigit(): String {
        val isFirst = timespec == DigitalTimespec.FIRST_DIGIT
        val text = dateFormat.format(cal.time).toString()
        return if (isFirst) text.substring(0, text.length - 1) else text.substring(text.length - 1)
    }

    fun getDigitString(): String =
        when (timespec) {
            DigitalTimespec.TIME_FULL_FORMAT -> dateFormat.format(cal.time).toString()
            DigitalTimespec.DATE_FORMAT -> dateFormat.format(cal.time).toString().uppercase(Locale.ROOT)
            DigitalTimespec.FIRST_DIGIT,
            DigitalTimespec.SECOND_DIGIT -> getSingleDigit()
            DigitalTimespec.DIGIT_PAIR -> dateFormat.format(cal.time).toString()
        }

    fun getContentDescription(): String? =
        when (timespec) {
            DigitalTimespec.TIME_FULL_FORMAT,
            DigitalTimespec.DATE_FORMAT -> contentDescriptionFormat?.format(cal.time).toString()
            else -> null
        }
}
