package com.example.mymemorygame.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.Image
import android.net.Uri
import android.nfc.Tag
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemorygame.R
import com.example.mymemorygame.adapters.ImagePickerAdapter
import com.example.mymemorygame.models.BoardSize
import com.example.mymemorygame.utils.BitmapScaler
import com.example.mymemorygame.utils.EXTRA_BOARD_SIZE
import com.example.mymemorygame.utils.isPermissionGranted
import com.example.mymemorygame.utils.requesPermission
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
    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImgsUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_game)

        //mapping views on activity_create_game components
        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
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
        Log.i(TAG, "save data to database")
        for ((index, photoUri) in chosenImgsUris.withIndex()){
            val imageByteArray = getImageByteArray(photoUri)
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
