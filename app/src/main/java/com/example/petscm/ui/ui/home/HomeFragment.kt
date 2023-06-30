package com.example.petscm.ui.ui.home

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.Manifest

import com.example.petscm.R
import com.example.petscm.databinding.FragmentHomeBinding
import com.example.petscm.interfaces.FirebaseDriverInfoListener
import com.example.petscm.interfaces.FirebaseFailedListener
import com.example.petscm.models.DriverGeoModel
import com.example.petscm.models.DriverInfoModel
import com.example.petscm.models.GeoQueryModel
import com.example.petscm.utils.Constants
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener, FirebaseFailedListener {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var mMap: GoogleMap

    private lateinit var mapFragment: SupportMapFragment

    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Online system
    private lateinit var onlineRef: DatabaseReference
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var driversLocationRef: DatabaseReference
    private lateinit var geoFire: GeoFire

    private var distance = 1.0
    private var LIMIT_RANGE = 10.0
    private var previousLocation: Location? = null
    private var currentLocation: Location? = null

    private var firstTime = true

    private lateinit var firebaseDriverInfoListener: FirebaseDriverInfoListener
    private lateinit var firebaseFaieldListener: FirebaseFailedListener

    var cityName = ""

    private val onlineValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            currentUserRef.onDisconnect().removeValue()
        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }


    @SuppressLint("MissingPermission")
    private fun init() {
        firebaseDriverInfoListener = this

        onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")
        driversLocationRef =
            FirebaseDatabase.getInstance().getReference(Constants.RIDERS_LOCATION_REFERENCE)
        currentUserRef =
            FirebaseDatabase.getInstance().getReference(Constants.RIDERS_LOCATION_REFERENCE).child(
                FirebaseAuth.getInstance().currentUser!!.uid
            )

        geoFire = GeoFire(driversLocationRef)

        registerOnlineSystem()

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                val newPos = LatLng(
                    locationResult.lastLocation?.latitude!!,
                    locationResult.lastLocation?.longitude!!
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 10f))

                //if user has changed location, calculate and load drivers location again
                if (firstTime) {
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }

                if (previousLocation?.distanceTo(currentLocation!!)!! / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers()
                }
            }
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )

        loadAvailableDrivers()
    }


    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener {
                Snackbar.make(requireView(), it.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener { location ->

                //load all drivers in this city

                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                val addressList: List<Address>?

                try {
                    location?.latitude.let {
                        addressList = geoCoder.getFromLocation(
                            it!!,
                            location.longitude,
                            1

                        )
                    }
                    val cityName = addressList?.get(0)!!.locality

                    //Query
                    val driverLocationRef = FirebaseDatabase.getInstance()
                        .getReference(Constants.DRIVER_LOCATION_REFERENCE)
                        .child(cityName)
                    val gf = GeoFire(driverLocationRef)
                    val geoQuery = gf.queryAtLocation(
                        GeoLocation(location.latitude, location.longitude),
                        distance
                    )
                    geoQuery.removeAllListeners()

                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            Constants.driversFound?.add(DriverGeoModel(key!!, location!!))
                        }

                        override fun onKeyExited(key: String?) {

                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {

                        }

                        override fun onGeoQueryReady() {
                            if (distance <= LIMIT_RANGE) {
                                distance++
                                loadAvailableDrivers()
                            } else {
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_LONG)
                                .show()
                        }

                    })

                    driversLocationRef.addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation =
                                GeoLocation(geoQueryModel!!.l!![0], geoQueryModel.l!![1])
                            val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.longitude = geoLocation.longitude
                            val newDistance = location.distanceTo(newDriverLocation) / 1000 // in km
                            if (newDistance <= LIMIT_RANGE) {
                                findDriverByKey(driverGeoModel)
                            } else {

                            }
                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {

                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {

                        }

                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {

                        }

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(requireView(), error.message!!, Snackbar.LENGTH_LONG)
                                .show()
                        }

                    })

                } catch (e: IOException) {
                    Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
                }
            }

    }

    @SuppressLint("CheckResult")
    private fun addDriverMarker() {
        if (Constants.driversFound?.size!! > 0) {
            io.reactivex.rxjava3.core.Observable.fromIterable(Constants.driversFound)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe() { driveGeoModel: DriverGeoModel? ->
                    findDriverByKey(driveGeoModel)
                }
        } else {

        }
    }

    private fun findDriverByKey(driveGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Constants.DRIVER_INFO_REFERNCE)
            .child(driveGeoModel?.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driveGeoModel.driverInfoModel =
                            snapshot.getValue(DriverInfoModel::class.java)
                        firebaseDriverInfoListener.onDriverInfoLoadSuccess(driveGeoModel)
                    } else {
                        firebaseFaieldListener.onFirebaseFailed("Key not found" + driveGeoModel.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //Request Permissions
        Dexter.withContext(context)
            .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(permissions: PermissionGrantedResponse?) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener() {
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener {
                                Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                            }.addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        10f
                                    )
                                )
                            }
                        true
                    }


                    val view = mapFragment.view?.findViewById<View>("1".toInt())?.parent as View
                    val locationButton = view.findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250
                }

                override fun onPermissionDenied(permissions: PermissionDeniedResponse?) {

                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

            }).check()

        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Log.d("Google Map", "error")
            }
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
        }
        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        //if we already have the marker with this key, don't set it again
        if (!Constants.markerList.containsKey(driverGeoModel?.key)) {
            Constants.markerList[driverGeoModel!!.key!!] = mMap.addMarker(MarkerOptions()
                .position(LatLng(driverGeoModel.geoLocation!!.latitude, driverGeoModel.geoLocation!!.longitude))
                .flat(true)
                .title(Constants.buildName(driverGeoModel.driverInfoModel!!.firstName,driverGeoModel.driverInfoModel!!.lastName))
                .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.baseline_person_24))
            )!!

        } else {
            Toast.makeText(requireContext(),"Error",Toast.LENGTH_LONG).show()
        }

        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Constants.DRIVER_LOCATION_REFERENCE)
                .child(cityName)
                .child(driverGeoModel?.key!!)

            driverLocation.addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Constants.markerList.get(driverGeoModel.key!!) != null) {
                            val marker = Constants.markerList.get(driverGeoModel.key!!)
                            marker?.remove()
                            Constants.markerList.remove(driverGeoModel.key)
                            driverLocation.removeEventListener(this)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message,Snackbar.LENGTH_SHORT).show()
                }

            })
        }
    }

    override fun onFirebaseFailed(message: String) {
        Toast.makeText(requireContext(),message, Toast.LENGTH_LONG).show()
    }
}