package com.nuvio.app.features.calendar

internal actual object CalendarStorage {
    private var memoryPayload: String? = null

    actual fun loadPayload(): String? = memoryPayload

    actual fun savePayload(payload: String) {
        memoryPayload = payload
    }
}
