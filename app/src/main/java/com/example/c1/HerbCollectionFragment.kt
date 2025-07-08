package com.example.c1

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.alibaba.sdk.android.oss.model.PutObjectResult
import com.example.c1.ApiConfig
import com.example.c1.OssConfig
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

class HerbCollectionFragment : Fragment() {
    private lateinit var herbImageView: ImageView
    private lateinit var tvLocation: TextView
    private lateinit var etHerbName: com.google.android.material.textfield.TextInputEditText
    private lateinit var etBatchCode: EditText
    private lateinit var etHerbOrigin: EditText
    private lateinit var etLocationCount: EditText
    private lateinit var etTemperature: EditText
    private lateinit var etHumidity: EditText
    private lateinit var etDistrict: com.google.android.material.textfield.TextInputEditText
    private lateinit var etStreet: com.google.android.material.textfield.TextInputEditText
    private lateinit var etGrowthDescription: EditText
    private lateinit var btnTakeHerbPhoto: Button
    private lateinit var btnSubmitHerb: Button
    
    private var imageFile: File? = null
    private var imageUri: Uri? = null
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private lateinit var locationManager: LocationManager
    private var currentLocationListener: LocationListener? = null
    private lateinit var oss: OSSClient
    private val REQUEST_CODE_PICK_IMAGE = 102
    private val REQUEST_CODE_TAKE_PHOTO = 103

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val permissionRequestCode = 101

    // 从后端获取的中药材列表
    private var herbList: List<String> = listOf()
    
    // 行政区和街道级联选择相关变量
    private var districtList: List<String> = listOf()
    private var streetList: List<String> = listOf()
    private var districtIdMap: Map<String, Int> = mapOf() // 名称->ID
    private var streetIdMap: Map<String, Int> = mapOf()   // 名称->ID

    // 安全的URL拼接函数，避免多余斜杠
    private fun safeUrlJoin(base: String, path: String): String {
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageFile?.let {
                try {
                    herbImageView.setImageURI(Uri.fromFile(it))
                    Toast.makeText(context, "✅ 图片拍摄成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 图片显示失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "❌ 拍照失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_herb_collection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initializeViews(view)
            setupLocationManager()
            setupClickListeners()
            setupHerbNameSpinner()
            setupDistrictAndStreetSpinners()
            // 初始化OSS
            initOSS()
            // 检查并申请权限
            if (checkPermissions()) {
                getCurrentLocation()
            } else {
                requestPermissions()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "界面初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeViews(view: View) {
        try {
            herbImageView = view.findViewById(R.id.herbImageView)
            tvLocation = view.findViewById(R.id.tvLocation)
            etHerbName = view.findViewById(R.id.etHerbName)
            etBatchCode = view.findViewById(R.id.etBatchCode)
            etHerbOrigin = view.findViewById(R.id.etHerbOrigin)
            etLocationCount = view.findViewById(R.id.etLocationCount)
            etTemperature = view.findViewById(R.id.etTemperature)
            etHumidity = view.findViewById(R.id.etHumidity)
            etDistrict = view.findViewById(R.id.etDistrict)
            etStreet = view.findViewById(R.id.etStreet)
            etGrowthDescription = view.findViewById(R.id.etGrowthDescription)
            btnTakeHerbPhoto = view.findViewById(R.id.btnTakeHerbPhoto)
            btnSubmitHerb = view.findViewById(R.id.btnSubmitHerb)
            
            // 添加地图选择按钮
            val btnSelectOnMap = view.findViewById<Button>(R.id.btnSelectOnMap)
            btnSelectOnMap.setOnClickListener {
                showMapSelectionDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "视图初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLocationManager() {
        try {
            locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Exception) {
            Toast.makeText(context, "位置服务初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        btnTakeHerbPhoto.setOnClickListener {
            if (checkPermissions()) {
                showImageSourceDialog()
            } else {
                requestPermissions()
            }
        }

        btnSubmitHerb.setOnClickListener {
            if (validateInputs()) {
                submitHerbData()
            }
        }
    }

    private fun setupHerbNameSpinner() {
        try {
            // 从后端获取中药列表
            fetchHerbList { herbs ->
                herbList = herbs
                activity?.runOnUiThread {
                    // 设置中药名称点击事件
                    etHerbName.setOnClickListener {
                        showHerbNameSelectionDialog()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "中药名称选择器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showHerbNameSelectionDialog() {
        if (herbList.isEmpty()) {
            Toast.makeText(context, "正在加载中药数据...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = herbList.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择中药名称")
            .setItems(items) { _, which ->
                val selectedHerb = herbList[which]
                etHerbName.setText(selectedHerb)
            }
            .show()
    }
    
    private fun fetchHerbList(callback: (List<String>) -> Unit) {
        val url = safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/herbs")
        Log.d("HerbCollection", "请求中药列表URL: $url")
        
        // 获取token
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        // 如果有token，添加认证头
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        
        val request = requestBuilder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "❌ 获取中药列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                Log.d("HerbCollection", "中药列表HTTP状态码: ${response.code}")
                Log.d("HerbCollection", "中药列表body: $body")
                
                // 处理401错误
                if (response.code == 401) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 请先登录", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                try {
                    val jsonObj = org.json.JSONObject(body)
                    val jsonArr = jsonObj.getJSONArray("herbs")
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val name = obj.getString("name")
                        list.add(name)
                    }
                    // 添加"待识别"选项
                    list.add("待识别")
                    activity?.runOnUiThread { 
                        callback(list)
                    }
                } catch (e: Exception) {
                    Log.e("HerbCollection", "解析中药列表数据失败: ${e.message}\nbody=$body")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 解析中药列表数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    
    private fun setupDistrictAndStreetSpinners() {
        try {
            // 初始化街道选择框为禁用状态
            etStreet.isEnabled = false
            // 注意：这里不需要动态设置hint，布局文件中已经有正确的hint
            
            // 获取所有行政区
            fetchDistricts { districts, idMap ->
                districtList = districts
                districtIdMap = idMap
                Log.d("HerbCollection", "获取到${districts.size}个行政区")
                
                activity?.runOnUiThread {
                    Log.d("HerbCollection", "行政区数据加载完成")
                    
                    // 设置行政区点击事件
                    etDistrict.setOnClickListener {
                        // 阻止键盘弹出
                        etDistrict.clearFocus()
                        showDistrictSelectionDialog()
                    }
                    
                    // 阻止键盘弹出
                    etDistrict.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            etDistrict.clearFocus()
                            showDistrictSelectionDialog()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "行政区街道选择器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showDistrictSelectionDialog() {
        if (districtList.isEmpty()) {
            Toast.makeText(context, "正在加载行政区数据...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = districtList.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择行政区")
            .setItems(items) { _, which ->
                val selectedDistrict = districtList[which]
                val districtId = districtIdMap[selectedDistrict] ?: return@setItems
                Log.d("HerbCollection", "选择了行政区: $selectedDistrict, ID: $districtId")
                
                etDistrict.setText(selectedDistrict)
                
                // 启用街道选择
                etStreet.isEnabled = true
                // 不需要动态设置hint，布局文件中已经有正确的hint
                etStreet.text = null
                
                // 获取对应街道
                fetchStreets(districtId) { streets, streetMap ->
                    streetList = streets
                    streetIdMap = streetMap
                    Log.d("HerbCollection", "获取到${streets.size}个街道")
                    
                    activity?.runOnUiThread {
                        Log.d("HerbCollection", "街道数据加载完成")
                        
                        // 设置街道点击事件
                        etStreet.setOnClickListener {
                            // 阻止键盘弹出
                            etStreet.clearFocus()
                            showStreetSelectionDialog()
                        }
                        
                        // 阻止键盘弹出
                        etStreet.setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                etStreet.clearFocus()
                                showStreetSelectionDialog()
                            }
                        }
                    }
                }
            }
            .show()
    }
    
    private fun showStreetSelectionDialog() {
        if (streetList.isEmpty()) {
            Toast.makeText(context, "正在加载街道数据...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = streetList.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择街道")
            .setItems(items) { _, which ->
                val selectedStreet = streetList[which]
                Log.d("HerbCollection", "选择了街道: $selectedStreet")
                etStreet.setText(selectedStreet)
            }
            .show()
    }
    
    private fun fetchDistricts(callback: (List<String>, Map<String, Int>) -> Unit) {
        val url = safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/division/district")
        Log.d("HerbCollection", "请求URL: $url")
        
        // 获取token
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        // 如果有token，添加认证头
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        
        val request = requestBuilder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "❌ 获取行政区失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                Log.d("HerbCollection", "HTTP状态码: ${response.code}")
                Log.d("HerbCollection", "districts body: $body")
                
                // 处理401错误
                if (response.code == 401) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 请先登录", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                try {
                    val jsonObj = org.json.JSONObject(body)
                    val jsonArr = jsonObj.getJSONArray("districts")
                    val list = mutableListOf<String>()
                    val map = mutableMapOf<String, Int>()
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val name = obj.getString("name")
                        val id = obj.getInt("id")
                        list.add(name)
                        map[name] = id
                    }
                    activity?.runOnUiThread { 
                        callback(list, map)
                    }
                } catch (e: Exception) {
                    Log.e("HerbCollection", "解析行政区数据失败: ${e.message}\nbody=$body")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 解析行政区数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    
    private fun fetchStreets(districtId: Int, callback: (List<String>, Map<String, Int>) -> Unit) {
        // districtId实际需要districtName，需做映射
        val districtName = districtList.find { districtIdMap[it] == districtId } ?: ""
        val url = safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/division/$districtName/street")
        
        // 获取token
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        // 如果有token，添加认证头
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        
        val request = requestBuilder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "❌ 获取街道失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                Log.d("HerbCollection", "streets body: $body")
                
                // 处理401错误
                if (response.code == 401) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 请先登录", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                try {
                    val jsonObj = org.json.JSONObject(body)
                    val jsonArr = jsonObj.getJSONArray("streets")
                    val list = mutableListOf<String>()
                    val map = mutableMapOf<String, Int>()
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val name = obj.getString("streetName")
                        val id = obj.getInt("streetId")
                        list.add(name)
                        map[name] = id
                    }
                    activity?.runOnUiThread { 
                        callback(list, map)
                    }
                } catch (e: Exception) {
                    Log.e("HerbCollection", "解析街道数据失败: ${e.message}\nbody=$body")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "❌ 解析街道数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun checkPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        // 显示权限说明
        context?.let { ctx ->
            Toast.makeText(ctx, "需要位置权限来获取中药材发现位置", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(requireActivity(), permissions, permissionRequestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == permissionRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "✅ 权限获取成功，正在获取位置...", Toast.LENGTH_SHORT).show()
                }
                getCurrentLocation()
            } else {
                context?.let { ctx ->
                    Toast.makeText(ctx, "❌ 需要位置权限才能记录中药材发现位置", Toast.LENGTH_LONG).show()
                }
                tvLocation.text = "❌ 位置权限被拒绝\n请在设置中手动开启位置权限"
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            tvLocation.text = "🔄 正在获取位置信息..."
            
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                currentLocationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        tvLocation.text = "📍 当前位置：\n经度：${String.format(Locale.getDefault(), "%.6f", currentLongitude)}\n纬度：${String.format(Locale.getDefault(), "%.6f", currentLatitude)}"
                        locationManager.removeUpdates(this)
                        currentLocationListener = null
                        
                        // 安全地显示Toast
                        context?.let { ctx ->
                            Toast.makeText(ctx, "✅ 位置获取成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 尝试从GPS获取位置
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    currentLocationListener?.let { listener ->
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
                    }
                    // 设置超时，5秒后如果还没获取到位置，尝试网络定位
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
                            currentLocationListener?.let { listener ->
                                locationManager.removeUpdates(listener)
                            }
                            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                currentLocationListener?.let { listener ->
                                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
                                }
                            } else {
                                tvLocation.text = "❌ GPS定位超时，网络定位不可用\n请检查GPS和网络设置"
                            }
                        }
                    }, 5000)
                }
                // 尝试从网络获取位置
                else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    currentLocationListener?.let { listener ->
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
                    }
                }
                else {
                    tvLocation.text = "❌ GPS和网络定位都不可用\n请检查GPS和网络设置"
                }
            } else {
                tvLocation.text = "❌ 位置权限未授权\n请允许应用获取位置信息"
            }
        } catch (e: Exception) {
            tvLocation.text = "❌ 获取位置失败: ${e.message}"
        }
    }

    private fun dispatchTakePictureIntent() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(context, "创建图片文件失败: ${ex.message}", Toast.LENGTH_SHORT).show()
                    null
                }
                photoFile?.also {
                    try {
                        imageUri = FileProvider.getUriForFile(
                            requireContext(),
                            "com.example.c1.fileprovider",
                            it
                        )
                        imageUri?.let { uri ->
                            takePictureLauncher.launch(uri)
                        } ?: run {
                            Toast.makeText(context, "无法创建图片URI", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "FileProvider错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(context, "无法创建图片文件", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "设备不支持拍照", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "启动相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "HERB_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            imageFile = this
        }
    }

    private fun validateInputs(): Boolean {
        if (imageFile == null) {
            Toast.makeText(context, "请先拍摄中药材图片", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etHerbName.text.isNullOrBlank()) {
            Toast.makeText(context, "请输入中药名称", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etDistrict.text.isNullOrBlank()) {
            Toast.makeText(context, "请输入行政区", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etStreet.text.isNullOrBlank()) {
            Toast.makeText(context, "请输入街道", Toast.LENGTH_SHORT).show()
            return false
        }

        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
            Toast.makeText(context, "无法获取位置信息，请检查GPS设置", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun submitHerbData() {
        if (imageFile == null) {
            Toast.makeText(context, "请先拍摄或选择中药材图片", Toast.LENGTH_SHORT).show()
            return
        }
        btnSubmitHerb.isEnabled = false
        btnSubmitHerb.text = "🔄 图片上传中..."
        uploadImageToOSS(imageFile!!, { ossUrl ->
            btnSubmitHerb.text = "🔄 信息提交中..."
            submitGrowthRecord(ossUrl)
        }, { errorMsg ->
            btnSubmitHerb.isEnabled = true
            btnSubmitHerb.text = "✅ 提交中药材信息"
            Toast.makeText(context, "❌ 图片上传失败: $errorMsg", Toast.LENGTH_LONG).show()
        })
    }

    private fun submitGrowthRecord(imgUrl: String) {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
            btnSubmitHerb.isEnabled = true
            btnSubmitHerb.text = "✅ 提交中药材信息"
            return
        }
        val client = OkHttpClient()
        // 构造JSON对象
        val json = org.json.JSONObject()
        json.put("herbName", etHerbName.text.toString())
        json.put("batchCode", etBatchCode.text.toString())
        json.put("wet", etHumidity.text.toString())
        json.put("temperature", etTemperature.text.toString())
        json.put("longitude", currentLongitude)
        json.put("latitude", currentLatitude)
        json.put("imgUrl", imgUrl)
        if (!etGrowthDescription.text.isNullOrBlank()) {
            json.put("des", etGrowthDescription.text.toString())
        }
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaType(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/growth"))
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            btnSubmitHerb.isEnabled = true
                            btnSubmitHerb.text = "✅ 提交中药材信息"
                            Toast.makeText(context, "提交失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            btnSubmitHerb.isEnabled = true
                            btnSubmitHerb.text = "✅ 提交中药材信息"
                            val obj = try { org.json.JSONObject(res) } catch (e: Exception) { null }
                            if (obj != null && obj.optInt("code") == 0) {
                                Toast.makeText(context, "生长记录提交成功", Toast.LENGTH_LONG).show()
                                clearForm()
                            } else {
                                Toast.makeText(context, obj?.optString("message", "提交失败") ?: "提交失败", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun clearForm() {
        etHerbName.text?.clear()
        etLocationCount.text?.clear()
        etTemperature.text?.clear()
        etHumidity.text?.clear()
        etDistrict.text?.clear()
        etStreet.text?.clear()
        etGrowthDescription.text?.clear()
        herbImageView.setImageResource(R.mipmap.ic_launcher)
        imageFile = null
        imageUri = null
    }

    private fun returnToHistoryWithNewRecord(newRecord: HerbRecord) {
        // 切换到历史记录页面
        val historyFragment = HerbHistoryFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, historyFragment)
            .commit()
        
        // 延迟添加新记录，确保Fragment已经创建
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            historyFragment.addNewRecord(newRecord)
        }, 100)
    }

    private fun showMapSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_map_selection, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("📍 手动选择位置")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, _ ->
                // 获取用户输入的经纬度
                val etLongitude = dialogView.findViewById<EditText>(R.id.etLongitude)
                val etLatitude = dialogView.findViewById<EditText>(R.id.etLatitude)
                
                val longitudeStr = etLongitude.text.toString()
                val latitudeStr = etLatitude.text.toString()
                
                if (longitudeStr.isNotEmpty() && latitudeStr.isNotEmpty()) {
                    try {
                        currentLongitude = longitudeStr.toDouble()
                        currentLatitude = latitudeStr.toDouble()
                        tvLocation.text = "📍 手动设置位置：\n经度：${String.format(Locale.getDefault(), "%.6f", currentLongitude)}\n纬度：${String.format(Locale.getDefault(), "%.6f", currentLatitude)}"
                        Toast.makeText(context, "✅ 位置已更新", Toast.LENGTH_SHORT).show()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(context, "❌ 请输入有效的经纬度", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "❌ 请输入经纬度", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        // 设置当前GPS位置到对话框
        val tvCurrentLocation = dialogView.findViewById<TextView>(R.id.tvCurrentLocation)
        tvCurrentLocation.text = "经度：${String.format(Locale.getDefault(), "%.6f", currentLongitude)}\n纬度：${String.format(Locale.getDefault(), "%.6f", currentLatitude)}"
        
        // 设置当前值到输入框
        val etLongitude = dialogView.findViewById<EditText>(R.id.etLongitude)
        val etLatitude = dialogView.findViewById<EditText>(R.id.etLatitude)
        etLongitude.setText(String.format(Locale.getDefault(), "%.6f", currentLongitude))
        etLatitude.setText(String.format(Locale.getDefault(), "%.6f", currentLatitude))
        
        dialog.show()
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("拍照上传", "相册选择")
        AlertDialog.Builder(requireContext())
            .setTitle("选择图片来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> dispatchTakePictureIntent()
                    1 -> pickImageFromGallery()
                }
            }
            .show()
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_CODE_PICK_IMAGE -> {
                    val uri = data.data
                    uri?.let {
                        val file = copyUriToFile(requireContext(), it)
                        handleImageFileForUpload(file)
                    }
                }
                REQUEST_CODE_TAKE_PHOTO -> {
                    imageUri?.let {
                        val file = copyUriToFile(requireContext(), it)
                        handleImageFileForUpload(file)
                    }
                }
            }
        }
    }

    // 兼容所有Android版本：将Uri内容复制到App私有目录
    private fun copyUriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.getExternalFilesDir(null), "herb_${System.currentTimeMillis()}.jpg")
            val outputStream = file.outputStream()
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun compressImageFile(src: File, maxSize: Int = 1024 * 1024): File {
        val bitmap = BitmapFactory.decodeFile(src.absolutePath)
        val outFile = File(src.parent, "compressed_${src.name}")
        var quality = 90
        outFile.outputStream().use { stream ->
            do {
                stream.flush()
                stream.close()
                outFile.delete()
                outFile.createNewFile()
                val tempStream = outFile.outputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, tempStream)
                tempStream.flush()
                tempStream.close()
                quality -= 10
            } while (outFile.length() > maxSize && quality > 10)
        }
        return outFile
    }

    private fun uploadImageToOSS(localFile: File, onSuccess: (String) -> Unit, onError: (String) -> Unit, retryCount: Int = 1) {
        if (!localFile.exists()) {
            if (isAdded && activity != null) {
                activity?.runOnUiThread { onError("本地图片文件不存在，无法上传") }
            }
            Log.e("OSS_DEBUG", "上传前文件不存在: " + localFile.absolutePath)
            return
        }
        Log.d("OSS_DEBUG", "file exists: true, path: " + localFile.absolutePath)
        val bucketName = OssConfig.BUCKET_NAME
        val objectKey = "herb_images/${System.currentTimeMillis()}.jpg"
        val put = PutObjectRequest(bucketName, objectKey, localFile.absolutePath)
        // 禁用上传按钮，防止重复上传
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                btnSubmitHerb.isEnabled = false
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    oss.putObject(put)
                    Log.d("OSS_DEBUG", "上传后文件存在: " + localFile.exists() + ", path: " + localFile.absolutePath)
                    val url = "${OssConfig.OSS_URL_PREFIX}$objectKey"
                    if (isAdded && activity != null) {
                        activity?.runOnUiThread {
                            btnSubmitHerb.isEnabled = true
                            onSuccess(url)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OSS_DEBUG", "上传失败: ${e.message}")
                    if (retryCount > 0) {
                        Log.d("OSS_DEBUG", "自动重试上传...")
                        uploadImageToOSS(localFile, onSuccess, onError, retryCount - 1)
                    } else {
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                btnSubmitHerb.isEnabled = true
                                onError(e.message ?: "未知错误")
                            }
                        }
                    }
                }
            }.start()
        }, 200)
    }

    // 拍照/选图后调用
    private fun handleImageFileForUpload(file: File?) {
        if (file != null && file.exists()) {
            // 压缩图片
            val compressed = compressImageFile(file)
            imageFile = compressed
            // 立即显示图片
            herbImageView.setImageBitmap(BitmapFactory.decodeFile(compressed.absolutePath))
            // 显示成功提示
            Toast.makeText(context, "✅ 图片选择成功，请填写信息后点击提交", Toast.LENGTH_SHORT).show()
        } else {
            if (isAdded && activity != null) {
                activity?.runOnUiThread { Toast.makeText(context, "❌ 图片处理失败，文件不存在", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun initOSS() {
        Log.d("OSS_DEBUG", "endpoint=" + OssConfig.ENDPOINT + ", bucket=" + OssConfig.BUCKET_NAME + ", key=" + OssConfig.ACCESS_KEY_ID)
        val endpoint = OssConfig.ENDPOINT
        val accessKeyId = OssConfig.ACCESS_KEY_ID
        val accessKeySecret = OssConfig.ACCESS_KEY_SECRET
        val credentialProvider = OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret)
        val conf = ClientConfiguration().apply {
            connectionTimeout = 60 * 1000
            socketTimeout = 60 * 1000
            maxConcurrentRequest = 5
            maxErrorRetry = 2
        }
        oss = OSSClient(requireContext().applicationContext, endpoint, credentialProvider, conf)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 安全地移除位置监听器
        currentLocationListener?.let { listener ->
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                // 忽略异常
            }
        }
        currentLocationListener = null
    }
} 