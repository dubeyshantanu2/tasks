package org.tasks.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.data.LocationDao
import org.tasks.data.MergedGeofence
import org.tasks.data.Place
import org.tasks.preferences.PermissionChecker
import timber.log.Timber
import javax.inject.Inject

class GeofenceApi @Inject constructor(
        @param:ApplicationContext private val context: Context,
        private val permissionChecker: PermissionChecker,
        private val locationDao: LocationDao) {

    suspend fun registerAll() = locationDao.getPlacesWithGeofences().forEach { update(it) }

    suspend fun update(taskId: Long) = update(locationDao.getPlaceForTask(taskId))

    suspend fun update(place: String) = update(locationDao.getPlace(place))

    @SuppressLint("MissingPermission")
    suspend fun update(place: Place?) {
        if (place == null || !permissionChecker.canAccessLocation()) {
            return
        }
        val client = LocationServices.getGeofencingClient(context)
        val geofence = locationDao.getGeofencesByPlace(place.uid!!)
        if (geofence != null) {
            Timber.d("Adding geofence for %s", geofence)
            client.addGeofences(
                    GeofencingRequest.Builder().addGeofence(toGoogleGeofence(geofence)).build(),
                    PendingIntent.getBroadcast(
                            context,
                            0,
                            Intent(context, GeofenceTransitionsIntentService.Broadcast::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            Timber.d("Removing geofence for %s", place)
            client.removeGeofences(listOf(place.id.toString()))
        }
    }

    private fun toGoogleGeofence(geofence: MergedGeofence): Geofence {
        var transitionTypes = 0
        if (geofence.arrival) {
            transitionTypes = transitionTypes or GeofencingRequest.INITIAL_TRIGGER_ENTER
        }
        if (geofence.departure) {
            transitionTypes = transitionTypes or GeofencingRequest.INITIAL_TRIGGER_EXIT
        }
        return Geofence.Builder()
                .setCircularRegion(geofence.latitude, geofence.longitude, geofence.radius.toFloat())
                .setRequestId(geofence.uid)
                .setTransitionTypes(transitionTypes)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
    }
}