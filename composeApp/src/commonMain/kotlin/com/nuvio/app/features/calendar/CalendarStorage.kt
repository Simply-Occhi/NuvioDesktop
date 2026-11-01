package com.nuvio.app.features.calendar

internal expect object CalendarStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
