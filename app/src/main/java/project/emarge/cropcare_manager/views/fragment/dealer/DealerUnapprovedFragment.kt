package project.emarge.cropcare_manager.views.fragment.dealer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.dialog_unapproved_dealer_location.*
import kotlinx.android.synthetic.main.fragment_dealer_unapproved.*
import kotlinx.android.synthetic.main.fragment_dealer_unapproved.view.*
import project.emarge.cropcare_manager.R
import project.emarge.cropcare_manager.databinding.DialogUnapprovedDealerLocationBinding

import project.emarge.cropcare_manager.model.datamodel.Dealer
import project.emarge.cropcare_manager.viewModels.dealers.DealerUnapprovedViewModel
import project.emarge.cropcare_manager.views.adaptor.dealer.DealerUnApprovedAdaptor
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


/**
 * A placeholder fragment containing a simple view.
 */
class DealerUnapprovedFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {


    override fun onMarkerClick(p0: Marker?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    lateinit var selectedDealer: Dealer


    val IMAGE_PERMISSION_REQUEST = 702
    val PICK_IMAGE_REQUEST = 701
    val CAM_PERMISSION_REQUEST = 703
    val PICK_CAM_REQUEST = 705
    val STORAGE_PERMISSION_REQUEST = 706
    val LOCATION_REQUEST = 900

    val READ_STORAGE_PERMISSION_REQUEST = 707


    private val REQUEST_CHECK_SETTINGS = 2


    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    lateinit var locationRequest: LocationRequest




    private lateinit var dealerUnApprovedAdaptor: DealerUnApprovedAdaptor
    private lateinit var dialogDealerLocation: Dialog


    private var currentLocation: LatLng? = null
    private var userChangedLocation: LatLng? = null

    var selectedImagefilePath: Uri = Uri.EMPTY
    lateinit var filePath: Uri
    lateinit var root: View
    private lateinit var locationCallbackRefrash: LocationCallback
    lateinit var locationRequestRefrash: LocationRequest
    lateinit var imageViewDealer: ImageView

    private lateinit var pageViewModel: DealerUnapprovedViewModel

    var isImageFromCamera: Boolean = false
    var currentPhotoPath: String = ""







    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = activity?.run { ViewModelProviders.of(this)[DealerUnapprovedViewModel::class.java] } ?: throw Exception("Invalid Activity")

    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        root = inflater.inflate(R.layout.fragment_dealer_unapproved, container, false)
        return root
    }

    override fun onStart() {
        super.onStart()
        fusedLocationClient = activity?.let { LocationServices.getFusedLocationProviderClient(it) }!!
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                onLocationChanged(locationResult!!.lastLocation)
            }
        }
        locationCallbackRefrash = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                onLocationChangedRefrash(locationResult!!.lastLocation)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = context?.let { ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) }
            if (permission != PackageManager.PERMISSION_GRANTED) {
                makeRequest()
            } else {
                createLocationRequest()
            }
        } else {
            createLocationRequest()
        }
        getDealers()
        root.swiperefresh_dealerunapproved_items.setOnRefreshListener {
            getDealers()
        }




    }




    private fun getDealers() {

        pageViewModel.getDealers(progressBar_dealer_unapproved).observe(this, Observer<ArrayList<Dealer>> {
            it?.let { result ->
                dealerUnApprovedAdaptor = DealerUnApprovedAdaptor(result, context as Activity)
                recyclerView_unapproved_dealers.adapter = dealerUnApprovedAdaptor

                if (result.isEmpty()) {
                    textview_nounapprovedealers.visibility = View.VISIBLE
                    recyclerView_unapproved_dealers.visibility = View.GONE
                    textview_nounapprovedealers.text = "No unapproved dealers"

                } else {
                    textview_nounapprovedealers.visibility = View.GONE
                    recyclerView_unapproved_dealers.visibility = View.VISIBLE
                }

               swiperefresh_dealerunapproved_items.isRefreshing = false

                dealerUnApprovedAdaptor.setOnItemClickListener(object : DealerUnApprovedAdaptor.ClickListener {
                    override fun onClick(dealer: Dealer, aView: View) {
                        selectedDealer = dealer
                        openDialogDealerLocation(dealer)
                    }
                })
            }
        })


    }


    private fun openDialogDealerLocation(dealer: Dealer) {
        dialogDealerLocation = Dialog(context as Activity)
        dialogDealerLocation.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogDealerLocation.window!!.setBackgroundDrawableResource(android.R.color.white)
        dialogDealerLocation.setContentView(R.layout.dialog_unapproved_dealer_location)
        dialogDealerLocation.setCancelable(true)


        var textCode = dialogDealerLocation.findViewById<TextView>(R.id.textview_code)
        var textName = dialogDealerLocation.findViewById<TextView>(R.id.textview_name)
        var mapView = dialogDealerLocation.findViewById<MapView>(R.id.mapView_unapproved)
        var textBtnUpdate = dialogDealerLocation.findViewById<TextView>(R.id.text_btn_update)
        var editTextNumber = dialogDealerLocation.findViewById<EditText>(R.id.editText_delertp)
        var imageviewGalleryImageIcon = dialogDealerLocation.findViewById<ImageView>(R.id.imageView_dealer_image_gallery_icon)
        var imageviewCamImageIcon = dialogDealerLocation.findViewById<ImageView>(R.id.imageView_dealer_image_cam_icon)
        var progressBar = dialogDealerLocation.findViewById<ProgressBar>(R.id.progressBar_dialog_dealre_updaet_image)

        progressBar.visibility = View.VISIBLE

        imageViewDealer = dialogDealerLocation.findViewById<ImageView>(R.id.imageView_dealer_image)


        textCode.text = dealer.dealerCode
        editTextNumber.setText(dealer.dealerContactNumber.toString())
        textName.text = dealer.dealerName



        val requestOptions = RequestOptions()
        requestOptions.placeholder(R.drawable.noimage)
        requestOptions.error(R.drawable.noimage)

            val requestListener = object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                   progressBar.visibility = View.GONE
                    return false
                }
                override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            }




        Glide.with(context as Activity)
            .asBitmap()
            .apply(requestOptions)
            .listener(requestListener)
            .load(dealer.dealerImg)
            .into(imageViewDealer)


        var relativelayoutRefrash = dialogDealerLocation.findViewById<RelativeLayout>(R.id.relativelayout_refrash)


        imageviewGalleryImageIcon.setOnClickListener {
            val permission = ContextCompat.checkSelfPermission(context as Activity, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                makeRequestImage()
            } else {
                chooseFile()
            }

        }



        imageviewCamImageIcon.setOnClickListener {
            val permissionCam = ContextCompat.checkSelfPermission(context as Activity, Manifest.permission.CAMERA)
            val permission =
                ContextCompat.checkSelfPermission(context as Activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

            when {
                permission != PackageManager.PERMISSION_GRANTED -> makeRequestSTORAGE()
                permissionCam != PackageManager.PERMISSION_GRANTED -> makeRequestCamera()
                else -> openCamera()
            }


        }
        relativelayoutRefrash.setOnClickListener {
            progressBar_dealer_unapproved.visibility = View.VISIBLE
            createLocationRequestRefrash()
            startLocationUpdatesRefrash()
            dialogDealerLocation.dismiss()
        }
        textBtnUpdate.setOnClickListener {
            if (currentLocation == null) {
                Toast.makeText(context as Activity, "Please recheck your location", Toast.LENGTH_LONG).show()
            } else {

                var lastLocation = if(userChangedLocation==null){
                    currentLocation
                }else{
                    userChangedLocation
                }
                pageViewModel.updateDealersLocation(
                    lastLocation!!,
                    selectedDealer,
                    selectedImagefilePath,
                    editTextNumber.text.toString(),
                    isImageFromCamera
                )
                    .observe(this, Observer<Dealer> {
                        it?.let { result ->
                            if (!result.status) {
                                showMessage(
                                    result.loginNetworkError.errorTitle.toString(),
                                    result.loginNetworkError.errorMessage.toString()
                                )
                            } else {
                                Toast.makeText(context as Activity, "Dealer update successfully ", Toast.LENGTH_LONG).show()
                                userChangedLocation = null
                                getDealers()
                            }

                            dialogDealerLocation.dismiss()

                        }
                    })

            }


        }



        if (mapView != null) {
            mapView.onCreate(null)
            mapView.onResume()
            mapView.getMapAsync(this)
        }





        dialogDealerLocation.show()

    }


    private fun makeRequestCamera() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAM_PERMISSION_REQUEST
        )
    }


    private fun makeRequestImage() {
        requestPermissions(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            IMAGE_PERMISSION_REQUEST
        )
    }





    private fun chooseFile() {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(Intent.createChooser(intent, "Select image"), PICK_IMAGE_REQUEST)
    }


    private fun openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dispatchTakePictureIntent()
        } else {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(context?.packageManager!!)?.also {
                    startActivityForResult(takePictureIntent, PICK_CAM_REQUEST)
                }
            }
        }

    }


    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also {
                mediaScanIntent ->
            val f = File(currentPhotoPath)

            mediaScanIntent.data = Uri.fromFile(f)
            context?.sendBroadcast(mediaScanIntent)
        }
    }



    private fun makeRequestSTORAGE() {
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST
        )
    }

    private fun showMessage(title: String, msg: String) {
        val alertDialogBuilder = AlertDialog.Builder(context as Activity)
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(msg)
        alertDialogBuilder.setNegativeButton("OK", DialogInterface.OnClickListener { _, _ -> return@OnClickListener })
        alertDialogBuilder.show()
    }


    private fun onLocationChanged(location: Location) {
        currentLocation = LatLng(location.latitude, location.longitude)
    }


    private fun onLocationChangedRefrash(location: Location) {
        progressBar_dealer_unapproved.visibility = View.GONE
        currentLocation = LatLng(location.latitude, location.longitude)
        if (dialogDealerLocation.isShowing) {

        } else {
            stopLocationUpdatesRefrash()
            openDialogDealerLocation(selectedDealer)
        }


    }


    override fun onMapReady(p0: GoogleMap?) {
        MapsInitializer.initialize(context)
        mMap = p0!!

        mMap.uiSettings.isMyLocationButtonEnabled = false
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.uiSettings.isMapToolbarEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true



        if (currentLocation == null) {
            startLocationUpdates()
        } else {
            mMap.addMarker(MarkerOptions().position(currentLocation!!).title("").draggable(true))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 17f))
        }


        mMap.setOnCameraMoveListener {
            mMap.clear()
            userChangedLocation = mMap.cameraPosition.target

            mMap.addMarker(MarkerOptions().position(userChangedLocation!!).title("").draggable(true))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userChangedLocation, 17f))
        }

        mMap.setOnCameraIdleListener {
            if (userChangedLocation == null) {
            } else {

            }
        }

    }


    private fun makeRequest() {
       requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_REQUEST
        )
    }


    fun createLocationRequestRefrash() {

        locationRequestRefrash = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

    }


    fun createLocationRequest() {

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }


        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = activity?.let { LocationServices.getSettingsClient(it) }
        val task = client?.checkLocationSettings(builder.build())
        builder.setAlwaysShow(true)

        task?.addOnSuccessListener {
            startLocationUpdates()
        }
        task?.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                try {
                    e.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                }

            }
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_IMAGE_REQUEST -> when (resultCode) {
                Activity.RESULT_OK -> try {
                    Picasso.get().load(data!!.data).into(imageViewDealer)
                    selectedImagefilePath = data.data!!


                } catch (e: Exception) {
                    val alertDialogBuilder = AlertDialog.Builder(context as Activity)
                    alertDialogBuilder.setMessage("Image not selected properly, Please try again$e")
                    alertDialogBuilder.setPositiveButton(
                        "OK",
                        DialogInterface.OnClickListener { _, _ -> return@OnClickListener })
                    alertDialogBuilder.show()
                }
                Activity.RESULT_CANCELED -> Toast.makeText(
                    context as Activity,
                    "Image not selected properly, Please try again",
                    Toast.LENGTH_SHORT
                ).show()
                else -> {
                }
            }


            PICK_CAM_REQUEST -> when (resultCode) {
                Activity.RESULT_OK -> try {
                    isImageFromCamera = true

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Glide.with(context as Activity)
                            .asBitmap()
                            .load(currentPhotoPath)
                            .into(imageViewDealer)
                        selectedImagefilePath = Uri.parse(currentPhotoPath)
                    } else {
                        filePath = data!!.data!!
                        Glide.with(context as Activity)
                            .asBitmap()
                            .load(filePath)
                            .into(imageViewDealer)
                        selectedImagefilePath = filePath
                    }


                   galleryAddPic()

                } catch (e: Exception) {
                    val alertDialogBuilder = AlertDialog.Builder(context as Activity)
                    alertDialogBuilder.setMessage("Image not selected properly, Please try again")
                    alertDialogBuilder.setPositiveButton(
                        "OK",
                        DialogInterface.OnClickListener { _, _ -> return@OnClickListener })
                    alertDialogBuilder.show()
                }
                Activity.RESULT_CANCELED -> Toast.makeText(
                    context as Activity, "Image not selected properly, Please try again", Toast.LENGTH_SHORT
                ).show()
                else -> {
                }
            }
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    createLocationRequest()
                    createLocationRequestRefrash()
                } else {
                    Toast.makeText(context, "Oops! Permission Denied!!", Toast.LENGTH_SHORT).show()
                }
                return
            }
            IMAGE_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    chooseFile()
                } else {
                    Toast.makeText(context, "Oops! Permission Denied!!", Toast.LENGTH_SHORT).show()
                }
                return
            }

            READ_STORAGE_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getPendingDir()
                    //getImagesFromIntranalStorage()
                } else {
                    Toast.makeText(context, "Oops! Permission Denied!!", Toast.LENGTH_SHORT).show()
                }
                return
            }

            CAM_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val permission = ContextCompat.checkSelfPermission(context as Activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        makeRequestSTORAGE()
                    } else {
                        openCamera()
                    }
                } else {
                    Toast.makeText(context as Activity, "Oops! Permission Denied!!", Toast.LENGTH_SHORT).show()
                }
                return
            }

            STORAGE_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val permission = ContextCompat.checkSelfPermission(context as Activity, Manifest.permission.CAMERA)

                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        makeRequestCamera()
                    } else {
                        openCamera()
                    }
                } else {
                    Toast.makeText(context as Activity, "Oops! Permission Denied!!", Toast.LENGTH_SHORT).show()
                }

                return
            }

        }
    }


    override fun onDestroyView() {
        stopLocationUpdates()
        super.onDestroyView()
    }

    override fun onStop() {
        stopLocationUpdates()
        super.onStop()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        fusedLocationClient.removeLocationUpdates(locationCallbackRefrash)
    }

    private fun stopLocationUpdatesRefrash() {
        fusedLocationClient.removeLocationUpdates(locationCallbackRefrash)
    }




    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesRefrash() {
        fusedLocationClient.requestLocationUpdates(locationRequestRefrash, locationCallbackRefrash, null /* Looper */)
    }



    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
       // val storageDir: File = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(context?.packageManager!!)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {

                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(context as Activity, (activity?.packageName+".provider"), it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, PICK_CAM_REQUEST)
                }
            }
        }
    }



    private fun getPendingDir(){
        println("dirrrrrrr :  ")
        val storageDir: File = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!


        if(storageDir == null){
            println("dirrrrrrr : null ")
        }else{
            for(item in  storageDir.list()){
                println("dirrrrrrr :  "+item)
            }
        }
    }


    private fun getImagesFromIntranalStorage(){

        val proj = arrayOf<String>(MediaStore.Images.Media.DATA)
        val orderBy = MediaStore.Images.Media._ID

        var cursor = context?.contentResolver?.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, orderBy)
        var count = cursor?.count


        var arrPath = arrayOfNulls<String>(count!!)

        for (i in 0 until count)
        {
            cursor?.moveToPosition(i)
            val dataColumnIndex = cursor?.getColumnIndex(MediaStore.Images.Media.DATA)
            arrPath[i] = cursor?.getString(dataColumnIndex!!)

           // println("dirrrrrrr getImagesFromIntranalStorage "+arrPath[i])

            val file = File(arrPath[i])

            println("dirrrrrrr getImagesFromIntranalStorage "+file.name)



          //  Log.i("PATH", arrPath[i])
        }

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun addImagesUpKitKat(data: Uri) : String{
        var filep : String = ""
        if (data == null) {
            Toast.makeText(context as Activity, "Please select image from gallery ", Toast.LENGTH_LONG).show()
        } else {
            filep = getPath(context as Activity,data)
        }
        return filep
    }



    private fun addImages(data: Uri?) : String{
        var filep : String = ""
        if (data == null) {
            Toast.makeText(context as Activity, "Please select image from gallery 3", Toast.LENGTH_LONG).show()
        } else {
            filep = getRealPathFromURI(context as Activity,data)
        }
        return filep

    }

    fun getRealPathFromURI(context: Context, contentUri: Uri): String {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf<String>(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri, proj, null, null, null)!!
            val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()

            return if (cursor.getString(column_index) == null) {
                ""
            } else {
                cursor.getString(column_index)
            }

        } finally {
            cursor?.close()
        }
    }


    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getPath(context:Context, uri:Uri):String {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri))
        {
            if (isExternalStorageDocument(uri))
            {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split((":").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true))
                {
                    return (Environment.getExternalStorageDirectory().toString() + "/" + split[1])
                }
            }
            else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))

                return getDataColumn(context, contentUri, null, null)
            }
            else if (isMediaDocument(uri))
            {
                var docId = DocumentsContract.getDocumentId(uri)
                var split = docId.split((":").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                var type = split[0]
                var contentUri:Uri = Uri.EMPTY
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf<String>(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }

        }
        else if ("content".equals(uri.scheme, ignoreCase = true))
        {
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.lastPathSegment.toString()
            return this.getDataColumn(context, uri, null, null).toString()
        }
        else if ("file".equals(uri.scheme, ignoreCase = true))
        {
            return uri.path.toString()
        }

        return ""
    }


    fun getDataColumn(context:Context, uri:Uri?,selection:String?, selectionArgs:Array<String>?): String {

        lateinit var cursor:Cursor
        val column = "_data"
        val projection = arrayOf<String>(column)
        try
        {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)!!
            if (cursor != null && cursor.moveToFirst())
            {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        }
        finally
        {
            cursor.close()
        }
        return ""
    }

    private fun isExternalStorageDocument(uri:Uri):Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }
    /**
     * @param uri - The Uri to check.
     * @return - Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri:Uri):Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }
    /**
     * @param uri - The Uri to check.
     * @return - Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri:Uri):Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
    /**
     * @param uri - The Uri to check.
     * @return - Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri:Uri):Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }


    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"
        @JvmStatic
        fun newInstance(sectionNumber: Int): DealerUnapprovedFragment {
            return DealerUnapprovedFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}