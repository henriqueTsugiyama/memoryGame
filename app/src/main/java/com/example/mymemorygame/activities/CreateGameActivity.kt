package com.example.mymemorygame.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.Image
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemorygame.R
import com.example.mymemorygame.adapters.ImagePickerAdapter
import com.example.mymemorygame.models.BoardSize
import com.example.mymemorygame.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateGameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreatGameActivity"
        private const val PICK_PHOTO_CODE = 655
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MAX_GAME_NAME_LENGTH = 14
        private const val MIN_GAME_NAME_LENGTH = 3
    }
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar
    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImgsUris = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_game)

        //mapping views on activity_create_game components
        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)
        //initialization of intent data and back arrow
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose your pictures $numImagesRequired"

        btnSave.setOnClickListener {
            saveDataToFirebase()
        }
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
               btnSave.isEnabled = shouldEnablesaveBtn()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })

        adapter = ImagePickerAdapter(this, chosenImgsUris, boardSize, object : ImagePickerAdapter.ImageClickListener {
            override fun onPlaceHOlderClick() {
                if(isPermissionGranted(this@CreateGameActivity, READ_PHOTOS_PERMISSION)){
                    launchIntentForPhotos()
                } else {
                    requesPermission(this@CreateGameActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }
        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        if(requestCode == READ_EXTERNAL_PHOTOS_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchIntentForPhotos()
            } else {
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null){
            Log.w(TAG, "Did not get data back from the launched activity, user likely cancelled flow")
            return
        }
        val selectedUri = data.data
        val clippedData = data.clipData
        if(clippedData != null) {
            Log.i(TAG, "clipdata number of imgs ${clippedData.itemCount}: $clippedData")
            for (i in 0 until clippedData.itemCount) {
                val clipItem = clippedData.getItemAt(i)
                if (chosenImgsUris.size < numImagesRequired) {
                    chosenImgsUris.add(clipItem.uri)
                }
            }
        } else if(selectedUri != null){
            Log.i(TAG, "data $selectedUri")
            chosenImgsUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics(${chosenImgsUris.size}/ $numImagesRequired)"
        btnSave.isEnabled = shouldEnablesaveBtn()
    }

    private fun shouldEnablesaveBtn(): Boolean {
        if(chosenImgsUris.size != numImagesRequired){
            return false
        }
        if(etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true;
    }

    private fun saveDataToFirebase() {
        val customGameName = etGameName.text.toString()
        Log.i(TAG, "save data to database")
        btnSave.isEnabled = false
        //Check on firestore if game name isn't already in use
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if(document != null && document.data != null) {
                AlertDialog.Builder(this)
                        .setTitle("Name taken")
                        .setMessage("A game already exists with '$customGameName'. Please choose another one")
                        .setPositiveButton("Ok", null)
                        .show()
                btnSave.isEnabled = true
            } else {
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener { inTransitError ->
            Log.e(TAG, "Encountered error while uploading, try again in a bit.", inTransitError)
            Toast.makeText(this, "Encountered error while uploading, try again in a bit.", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }

    }
    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterErr = false
        val uploadedImgUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImgsUris.withIndex()){
            val imageByteArray = getImageByteArray(photoUri)
            val file_path = "image/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(file_path)
            photoReference.putBytes(imageByteArray)
                    .continueWithTask { photoUploadTask ->
                        Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                        photoReference.downloadUrl
                    }.addOnCompleteListener { downloadUrlTask ->
                        if(!downloadUrlTask.isSuccessful){
                            Log.e(TAG, "Exception with FirebaseStorage",downloadUrlTask.exception)
                            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                            didEncounterErr = true
                            return@addOnCompleteListener
                        }
                        if (didEncounterErr) {
                            pbUploading.visibility = View.GONE
                            return@addOnCompleteListener
                        }
                        //success case and progress of imageurls upload
                        val downloadUrl = downloadUrlTask.result.toString()
                        uploadedImgUrls.add(downloadUrl)
                        pbUploading.progress = uploadedImgUrls.size *100 / chosenImgsUris.size
                        Log.i(TAG, "finished upload $photoUri, num uploaded ${uploadedImgUrls.size}")
                        if(uploadedImgUrls.size == chosenImgsUris.size) {
                            handleAllImageUploaded(gameName, uploadedImgUrls)
                        }
                    }
        }
    }

    private fun handleAllImageUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName)
                .set(mapOf("images" to imageUrls))
                .addOnCompleteListener{gameCreationTask ->
                    pbUploading.visibility = View.GONE
                    if (!gameCreationTask.isSuccessful){
                        Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                        Toast.makeText(this, "Failed at game creation", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    Log.i(TAG, "Successfully created game $gameName")
                    AlertDialog.Builder(this)
                            .setTitle("Upload Complete! Let's play your game '$gameName'")
                            .setPositiveButton("Ok"){_,_ ->
                                val resultData = Intent()
                                resultData.putExtra(EXTRA_GAME_NAME, gameName)
                                setResult(Activity.RESULT_OK, resultData)
                                finish()
                            }.show()
                }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitMap= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else{
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitMap.width} and height ${originalBitMap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitMap, 250)
        Log.i(TAG,"Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}" )
        val byteOuputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOuputStream)
        return byteOuputStream.toByteArray()
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Choose your pictures"), PICK_PHOTO_CODE)
    }
}
