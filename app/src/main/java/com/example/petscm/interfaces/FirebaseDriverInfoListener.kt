package com.example.petscm.interfaces

import com.example.petscm.models.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)

}