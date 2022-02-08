package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.create
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var myFusedLocationProviderClient: FusedLocationProviderClient

    private var myProgressDialog: Dialog? = null

    private var binding: ActivityMainBinding? = null

    private lateinit var mySharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        myFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        mySharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, MODE_PRIVATE)

        setupUI()
        if (!isLocationEnabled()){
            Toast.makeText(this,"Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this).withPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,
                                "You have denied location permission. Please enable them as it is mandatory for the app to work.",
                                Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }

                }).onSameThread()
                .check()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double,longitude: Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )
            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mySharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response Result","$weatherList")
                    }else{
                        val responseCode = response.code()
                        when(responseCode){
                            400 -> {
                                Log.e("Error 400","Bad connection")
                            }
                            404 -> {
                                Log.e("Error 404","Not Found")
                            }else -> {
                            Log.e("Error 400","Generic error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Error",t.message.toString())
                }

            })
        }else{
            Toast.makeText(this,
                "No internet connection available.",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = create()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        Looper.myLooper()?.let {
            myFusedLocationProviderClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                it
            )
        }
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude,longitude)

            myFusedLocationProviderClient.removeLocationUpdates(this)
        }
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        myProgressDialog = Dialog(this)
        myProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        myProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_refresh){
            requestLocationData()
            true
        }else{
            super.onOptionsItemSelected(item)
        }

    }

    private fun hideProgressDialog(){
        if (myProgressDialog != null){
            myProgressDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(){
        val weatherResponseJsonString = mySharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()){

            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description
                binding?.tvTemp?.text =
                    String.format(getString(R.string.get_temp),
                        weatherList.main.temp,
                        getUnit(application.resources.configuration.locales.toString()))

                binding?.tvMin?.text = String.format(getString(R.string.min),weatherList.main.temp_min)
                binding?.tvMax?.text = String.format(getString(R.string.max),weatherList.main.temp_max)
                binding?.tvSpeed?.text = weatherList.wind.speed.toString()

                binding?.tvHumidity?.text = String.format(getString(R.string.humidity),weatherList.main.humidity)

                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country


                when (weatherList.weather[i].icon) {
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "50d" -> binding?.ivMain?.setImageResource(R.drawable.mist)
                }

            }
        }


    }

    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long):String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}