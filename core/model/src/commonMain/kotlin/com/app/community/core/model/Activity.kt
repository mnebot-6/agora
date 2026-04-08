package com.app.community.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SlotMode {
    @SerialName("unlimited") UNLIMITED,
    @SerialName("limited") LIMITED,
    @SerialName("limited_with_positions") LIMITED_WITH_POSITIONS,
}

@Serializable
enum class ActivityStatus {
    @SerialName("active") ACTIVE,
    @SerialName("cancelled") CANCELLED,
    @SerialName("completed") COMPLETED,
}

@Serializable
data class Activity(
    val id: String,
    @SerialName("community_id") val communityId: String,
    val name: String,
    val description: String? = null,
    val datetime: Instant,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("location_lat") val locationLat: Double? = null,
    @SerialName("location_lng") val locationLng: Double? = null,
    @SerialName("cost_description") val costDescription: String? = null,
    @SerialName("slot_mode") val slotMode: SlotMode,
    @SerialName("max_slots") val maxSlots: Int? = null,
    @SerialName("created_by") val createdBy: String,
    val status: ActivityStatus = ActivityStatus.ACTIVE,
) {
    val location: Location?
        get() = if (locationName != null && locationLat != null && locationLng != null) {
            Location(locationName, locationLat, locationLng)
        } else null
}

@Serializable
data class Location(
    val name: String,
    val lat: Double,
    val lng: Double,
)
