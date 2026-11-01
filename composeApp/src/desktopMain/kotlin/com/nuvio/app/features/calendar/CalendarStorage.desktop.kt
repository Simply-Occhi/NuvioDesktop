package com.nuvio.app.features.calendar

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object CalendarStorage {
    private val store = DesktopStorage.store("nuvio_calendar_cache")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("calendar_episodes_payload"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("calendar_episodes_payload"), payload)
    }
}
