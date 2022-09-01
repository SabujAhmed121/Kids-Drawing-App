package com.example.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.icu.text.CaseMap
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var customProgressDialog: Dialog? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()){
            result->
            if (result.resultCode == RESULT_OK && result.data != null){
                val imageBackground: ImageView = findViewById(R.id.imageSelect)
                imageBackground.setImageURI(result.data?.data)
            }
        }

//    Ata set hoyasa use ar jonno kintu use kora hoi ni
    val requestPermissionLauncher: ActivityResultLauncher
    <Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()){
        permission->
        permission.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value

            if (isGranted){
                Toast.makeText(this@MainActivity, "Permission granted now you " +
                        "can read the storage files.", Toast.LENGTH_LONG).show()
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }else{
                if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                    Toast.makeText(this@MainActivity, "Permission " +
                            "not Granted", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linerLayoutPaintColours = findViewById<LinearLayout>(R.id.liner_paint_colour)

        mImageButtonCurrentPaint = linerLayoutPaintColours[3] as ImageButton


        val brushSizeSelectButton: ImageButton = findViewById(R.id.ib_brush)
        val gallerySelectButton: ImageButton = findViewById(R.id.ib_gallery)
        val buttonUndo: ImageButton = findViewById(R.id.ib_undo)
        val buttonRedo: ImageButton = findViewById(R.id.ib_redo)
        val buttonSave: ImageButton = findViewById(R.id.ib_save)

        buttonSave.setOnClickListener {
            showCustomProgressDialog()
            if (isReadStorageAllowed()){
                lifecycleScope.launch {
                    val flDrawingView : FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }

        buttonUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        buttonRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }

        gallerySelectButton.setOnClickListener{
            requestStoragePermission()
        }

        brushSizeSelectButton.setOnClickListener {
            showBrushSizeChooserDialog()
        }
    }
    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationDialog("Alert", "Kids Drawing app need to access your external storage.")
        }else{
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }
    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")
        val smallBrush = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBrush.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBrush = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBrush.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBrush = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBrush.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }
    fun colorClicked(view: View){
        if (view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colortag = imageButton.tag.toString()
            drawingView?.setColor(colortag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed_xml)
            )

        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_normal)
        )
        mImageButtonCurrentPaint = view

        }
    }
    private fun isReadStorageAllowed(): Boolean {

        val result = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun getBitmapFromView(view: View): Bitmap{
        val returnBitmap = Bitmap.createBitmap(view.width, view.height,
            Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO){
            if (mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "KidsDrawingApp" + System.currentTimeMillis()/1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath
                    runOnUiThread {
                        cancelCustomProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity, "File saved in $result",
                                Toast.LENGTH_LONG).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity, "Unsuccessful",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch (e:Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showRationDialog(title: String,
    message:String){
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }
    private fun cancelCustomProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun showCustomProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)

        customProgressDialog?.setContentView(R.layout.dialog_progress_bar)

        customProgressDialog?.show()
    }
    private fun shareImage(result: String){
        MediaScannerConnection.scanFile(this, arrayOf(result), null){
            path, uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }
}
