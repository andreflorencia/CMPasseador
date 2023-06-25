package com.example.petscm

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.petscm.models.DriverInfoModel
import com.example.petscm.ui.HomeActivity
import com.example.petscm.utils.Constants
import com.example.petscm.utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {
    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var getResult: ActivityResultLauncher<Intent>
    private lateinit var progressBar: ProgressBar

    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        init()

        progressBar = findViewById(R.id.progress_bar)
        getResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {

                } else {

                }
            }
    }

    override fun onStart() {
        super.onStart()
        displaySplashScreen()
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null) {
            firebaseAuth.removeAuthStateListener(listener)
        }
        super.onStop()
    }

    private fun displaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe {
            firebaseAuth.addAuthStateListener(listener)
        }
    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Constants.RIDER_INFO_REFERENCE)

        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {

                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            UserUtils.updateToken(this@SplashScreenActivity, token)
                            Log.d("TOKEN", token)
                        } else {
                            Toast.makeText(
                                this@SplashScreenActivity,
                                "Failed to get FCM token ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                checkUserFromFirebase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.sign_in_layout)
            .setPhoneButtonId(R.id.button_phone_sign_in)
            .setGoogleButtonId(R.id.button_google_sign_in)
            .build()

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setTheme(R.style.LoginTheme)
            .setAuthMethodPickerLayout(authMethodPickerLayout)
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .build()

        getResult.launch(signInIntent)
    }

    private fun checkUserFromFirebase() {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model!!)
                    } else {
                        showRegisterUserLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.toString(), Toast.LENGTH_SHORT)
                        .show()
                }

            })
    }

    private fun goToHomeActivity(model: DriverInfoModel) {
        Constants.currentUser = model
        startActivity(Intent(this@SplashScreenActivity, HomeActivity::class.java))
        finish()
    }

    private fun showRegisterUserLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.register_layout, null, false)

        val edit_text_name =
            itemView.findViewById<View>(R.id.edit_text_first_name) as TextInputEditText
        val edit_text_last_name =
            itemView.findViewById<View>(R.id.edit_text_last_name) as TextInputEditText
        val edit_text_phone_number =
            itemView.findViewById<View>(R.id.edit_text_phone_number) as TextInputEditText

        val btnContinue = itemView.findViewById<Button>(R.id.button_register)

        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null
            && !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        ) {
            edit_text_phone_number.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        }

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btnContinue.setOnClickListener {
            if (TextUtils.isDigitsOnly(edit_text_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter a First Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener

            } else if (TextUtils.isDigitsOnly(edit_text_last_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity,
                    "Please enter a Last Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener

            } else if (TextUtils.isEmpty(edit_text_phone_number.text.toString())) {
                return@setOnClickListener
            } else {
                val model = DriverInfoModel(
                    edit_text_name.text.toString(),
                    edit_text_last_name.text.toString(),
                    edit_text_phone_number.text.toString(),
                    0.0,
                    ""
                )

                driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        progressBar.visibility = View.GONE
                    }.addOnSuccessListener {
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "Register Successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()

                        goToHomeActivity(model)
                        progressBar.visibility = View.GONE

                    }
            }
        }
    }

}