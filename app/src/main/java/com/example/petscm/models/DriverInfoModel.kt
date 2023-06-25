package com.example.petscm.models

data class DriverInfoModel(
    var firstName: String,
    var lastName: String,
    var phoneNumber: String,
    var rating: Double,
    var avatar: String
) {
    constructor(): this("","","",0.0,"")
}