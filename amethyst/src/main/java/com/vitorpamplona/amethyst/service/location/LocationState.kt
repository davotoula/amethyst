/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.fonfon.kgeohash.GeoHash
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.service.location.LocationState.Companion.MIN_DISTANCE
import com.vitorpamplona.amethyst.service.location.LocationState.Companion.MIN_TIME
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeohashPrecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class LocationFlow(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun get(
        minTimeMs: Long = MIN_TIME,
        minDistanceM: Float = MIN_DISTANCE,
    ): Flow<Location> =
        callbackFlow {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val locationCallback =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        launch { send(location) }
                    }

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {}
                }

            Log.d("Location Service", "LocationState Start")
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                minTimeMs,
                minDistanceM,
                locationCallback,
                Looper.getMainLooper(),
            )

            awaitClose {
                locationManager.removeUpdates(locationCallback)
                Log.d("Location Service", "LocationState Stop")
            }
        }
}

class LocationState(
    context: Context,
    scope: CoroutineScope,
) {
    companion object {
        const val MIN_TIME: Long = 10000L
        const val MIN_DISTANCE: Float = 100.0f
    }

    sealed class LocationResult {
        data class Success(
            val geoHash: GeoHash,
        ) : LocationResult()

        object LackPermission : LocationResult()

        object Loading : LocationResult()
    }

    private var hasLocationPermission = MutableStateFlow<Boolean>(false)
    private var latestLocation: LocationResult = LocationResult.Loading

    fun setLocationPermission(newValue: Boolean) {
        if (newValue != hasLocationPermission.value) {
            hasLocationPermission.tryEmit(newValue)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val geohashStateFlow =
        hasLocationPermission
            .transformLatest {
                emitAll(
                    LocationFlow
                        (context)
                        .get(MIN_TIME, MIN_DISTANCE)
                        .map {
                            LocationResult.Success(it.toGeoHash(GeohashPrecision.KM_5_X_5.digits)) as LocationResult
                        }.onEach {
                            latestLocation = it
                        }.catch { e ->
                            e.printStackTrace()
                            latestLocation = LocationResult.LackPermission
                            emit(LocationResult.LackPermission)
                        },
                )
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                latestLocation,
            )
}

object CachedGeoLocations {
    val locationNames = LruCache<String, String>(20)

    fun cached(geoHashStr: String): String? = locationNames[geoHashStr]

    suspend fun geoLocate(
        geoHashStr: String,
        location: Location,
        context: Context,
    ): String? {
        locationNames[geoHashStr]?.let {
            return it
        }

        val name = ReverseGeoLocationUtil().execute(location, context)?.ifBlank { null }

        if (name != null) {
            locationNames.put(geoHashStr, name)
        }

        return name
    }
}

private class ReverseGeoLocationUtil {
    suspend fun execute(
        location: Location,
        context: Context,
    ): String? {
        return try {
            Geocoder(context)
                .getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                )?.firstOrNull()
                ?.let { address ->
                    listOfNotNull(address.locality ?: address.subAdminArea, address.countryCode)
                        .joinToString(", ")
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            return null
        }
    }
}

class ReverseGeoLocationFlow(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun get(location: Location): Flow<String?> =
        callbackFlow {
            val locationManager = Geocoder(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locationCallback =
                    (
                        Geocoder.GeocodeListener { addresses ->
                            launch {
                                send(
                                    addresses.firstOrNull()?.let {
                                        listOfNotNull(it.locality ?: it.subAdminArea, it.countryCode).joinToString(", ")
                                    },
                                )
                            }
                        }
                    )
                Log.d("GeoLocation Service", "LocationState Start")

                locationManager
                    .getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        locationCallback,
                    )
            } else {
                launch {
                    send(
                        Geocoder(context)
                            .getFromLocation(
                                location.latitude,
                                location.longitude,
                                1,
                            )?.firstOrNull()
                            ?.let { address ->
                                listOfNotNull(address.locality ?: address.subAdminArea, address.countryCode)
                                    .joinToString(", ")
                            },
                    )
                }
            }
        }
}
