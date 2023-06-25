package com.example.petscm.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import com.example.petscm.R
import com.example.petscm.SplashScreenActivity
import com.example.petscm.databinding.ActivityHomeBinding
import com.example.petscm.utils.Constants
import com.example.petscm.utils.UserUtils
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var imageAvatar: CircleImageView
    private lateinit var getResult: ActivityResultLauncher<Intent>
    private lateinit var uri: Uri
    private lateinit var waitingDiloag: AlertDialog
    private lateinit var storageReference: StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarHome.toolbar)

        drawerLayout = binding.drawerLayout
        navView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_home)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        init()

        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                uri = it.data?.data!!
                imageAvatar.setImageURI(uri)

                showUploadDialog()
            }
        }
    }

    private fun init() {
        storageReference = FirebaseStorage.getInstance().reference

        waitingDiloag = AlertDialog.Builder(this@HomeActivity)
            .setMessage("Waiting...")
            .setCancelable(false).create()


        navView.setNavigationItemSelectedListener { it ->
            if (it.itemId == R.id.nav_sing_out)  {
                val builder = AlertDialog.Builder(this@HomeActivity)
                builder.setTitle("Sing out")
                builder.setMessage("Do you really want to sing out?")
                    .setNegativeButton("CANCEL") { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton("SING OUT") { _, _, ->
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this@HomeActivity, SplashScreenActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }.setCancelable(false)

                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(resources.getColor(com.firebase.ui.auth.R.color.colorAccent))
                }
                dialog.show()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val headerView = navView.getHeaderView(0)
        val textName = headerView.findViewById(R.id.text_view_name) as TextView
        val textViewStar = headerView.findViewById(R.id.text_view_rating) as TextView
        val textViewPhone = headerView.findViewById(R.id.text_view_phone) as TextView
        imageAvatar = headerView.findViewById(R.id.profile_image)

        if (Constants.currentUser != null && Constants.currentUser?.avatar != null &&
            !TextUtils.isEmpty(Constants.currentUser?.avatar)) {

            Picasso.get()
                .load(Constants.currentUser?.avatar)
                .into(imageAvatar)
        } else {

        }

        imageAvatar.setOnClickListener {
            getImage()
        }

        textName.text = Constants.buildWelcomeMessage()
        textViewPhone.text = Constants.currentUser?.phoneNumber
        textViewStar.text = java.lang.StringBuilder().append(Constants.currentUser?.rating)
    }

    private fun showUploadDialog() {
        val builder = AlertDialog.Builder(this@HomeActivity)
        builder.setTitle("Change Avatar")
        builder.setMessage("Do you really want to change the avatar")
            .setNegativeButton("CANCEL"){dialog, _ -> dialog.dismiss()}
            .setPositiveButton("CHANGE") { dialog, _, ->
                if (uri != null) {
                    waitingDiloag.show()

                    val filePath = storageReference.child("avatar_images").child(uri.lastPathSegment!!)
                    filePath.putFile(uri).addOnSuccessListener { task ->
                        val result: Task<Uri> = task.metadata?.reference?.downloadUrl!!
                        result.addOnSuccessListener {
                            uri = it
                            val updateData = mutableMapOf<String, Any>()
                            updateData.put("avatar",uri.toString())
                            UserUtils.updateuser(drawerLayout, updateData)
                        }

                    }.addOnProgressListener { taskSnapshot ->
                        val progress = (100.0*taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        waitingDiloag.setMessage(java.lang.StringBuilder("Uploading: ").append(progress).append("%"))
                    }.addOnCompleteListener {
                        waitingDiloag.dismiss()
                    }
                }
            }.setCancelable(false)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(resources.getColor(android.R.color.holo_red_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(resources.getColor(com.firebase.ui.auth.R.color.colorAccent))
        }
        dialog.show()
    }

    private fun getImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        getResult.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_home)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}