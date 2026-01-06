package me.ikate.findmy.data.model

import com.google.android.gms.maps.model.LatLng

data class Contact(
    val id: String,
    val name: String,
    val location: LatLng? = null,
    val avatarUrl: String? = null,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isSharingLocation: Boolean = false
)
