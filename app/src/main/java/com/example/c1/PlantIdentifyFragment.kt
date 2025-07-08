package com.example.c1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import android.provider.MediaStore
import android.net.Uri
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.*
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.alibaba.sdk.android.oss.model.PutObjectResult
import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

class PlantIdentifyFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private var imageFile: File? = null
    private var imageUri: Uri? = null
    private lateinit var oss: OSSClient
    private val REQUEST_CODE_PICK_IMAGE = 102
    private lateinit var uploadProgressBar: android.widget.ProgressBar
    private lateinit var identifyProgressBar: android.widget.ProgressBar
    private lateinit var progressText: TextView
    private var isUploading = false
    private var isIdentifying = false

    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val PERMISSION_REQUEST_CODE = 100

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageFile?.let {
                try {
                    imageView.setImageURI(Uri.fromFile(it))
                    resultText.text = "🔄 正在识别中，请稍候..."
                    uploadImageToOSS(it)
                } catch (e: Exception) {
                    resultText.text = "❌ 图片显示失败: ${e.message}"
                }
            }
        } else {
            resultText.text = "❌ 拍照失败，请重试"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plant_identify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            imageView = view.findViewById(R.id.imageView)
            resultText = view.findViewById(R.id.resultText)
            val btnPick = view.findViewById<Button>(R.id.btnPick)
            uploadProgressBar = view.findViewById(R.id.uploadProgressBar)
            identifyProgressBar = view.findViewById(R.id.identifyProgressBar)
            progressText = view.findViewById(R.id.progressText)
            
            uploadProgressBar.progress = 0
            uploadProgressBar.visibility = View.GONE
            identifyProgressBar.progress = 0
            identifyProgressBar.visibility = View.GONE
            progressText.visibility = View.GONE

            // 初始化OSS
            initOSS()
            btnPick.setOnClickListener {
                if (checkPermissions()) {
                    showImageSourceDialog()
                } else {
                    requestPermissions()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "界面初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(), PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(context, "需要相机和存储权限才能使用此功能", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    resultText.text = "❌ 创建图片文件失败: ${ex.message}"
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
                            resultText.text = "❌ 无法创建图片URI"
                        }
                    } catch (e: Exception) {
                        resultText.text = "❌ FileProvider错误: ${e.message}"
                    }
                } ?: run {
                    resultText.text = "❌ 无法创建图片文件"
                }
            } else {
                resultText.text = "❌ 设备不支持拍照"
            }
        } catch (e: Exception) {
            resultText.text = "❌ 启动相机失败: ${e.message}"
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            imageFile = this
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("拍照上传", "相册选择")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_PICK_IMAGE -> {
                    val uri = data?.data
                    uri?.let {
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
            val file = File(context.getExternalFilesDir(null), "identify_${System.currentTimeMillis()}.jpg")
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

    private fun getRealPathFromUri(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = requireContext().contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = it.getString(columnIndex)
            }
        }
        return path
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

    private fun uploadImageToOSS(localFile: File, retryCount: Int = 1) {
        if (!localFile.exists()) {
            if (isAdded && activity != null) {
                activity?.runOnUiThread { resultText.text = "❌ 本地图片文件不存在，无法上传" }
            }
            Log.e("OSS_DEBUG", "上传前文件不存在: " + localFile.absolutePath)
            return
        }
        Log.d("OSS_DEBUG", "file exists: true, path: " + localFile.absolutePath)
        val bucketName = OssConfig.BUCKET_NAME
        val objectKey = "identify_images/${System.currentTimeMillis()}.jpg"
        val put = PutObjectRequest(bucketName, objectKey, localFile.absolutePath)
        // 禁用上传按钮，防止重复上传
        isUploading = true
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                view?.findViewById<Button>(R.id.btnPick)?.isEnabled = false
                uploadProgressBar.progress = 0
                uploadProgressBar.visibility = View.VISIBLE
                progressText.visibility = View.VISIBLE
                progressText.text = "📤 正在上传图片..."
                resultText.text = "🔄 准备上传图片..."
            }
        }
        put.progressCallback = com.alibaba.sdk.android.oss.callback.OSSProgressCallback<PutObjectRequest> { _, currentSize, totalSize ->
            val percent = if (totalSize > 0) (currentSize * 100 / totalSize).toInt() else 0
            activity?.runOnUiThread {
                uploadProgressBar.progress = percent
                progressText.text = "📤 正在上传图片... $percent%"
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
                            isUploading = false
                            view?.findViewById<Button>(R.id.btnPick)?.isEnabled = true
                            uploadProgressBar.visibility = View.GONE
                            progressText.visibility = View.GONE
                            resultText.text = "🔄 图片上传完成，正在识别..."
                            uploadImageUrlToServer(url)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OSS_DEBUG", "上传失败: ${e.message}")
                    if (retryCount > 0) {
                        Log.d("OSS_DEBUG", "自动重试上传...")
                        uploadImageToOSS(localFile, retryCount - 1)
                    } else {
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                isUploading = false
                                view?.findViewById<Button>(R.id.btnPick)?.isEnabled = true
                                uploadProgressBar.visibility = View.GONE
                                progressText.visibility = View.GONE
                                resultText.text = "❌ 图片上传失败: ${e.message}"
                            }
                        }
                    }
                }
            }.start()
        }, 200)
    }

    private fun uploadImageUrlToServer(imageUrl: String) {
        isIdentifying = true
        showIdentifyProgress()
        
        try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image_url", imageUrl)
                .build()
            val request = Request.Builder()
                .url(ApiConfig.PLANT_IDENTIFY_URL)
                .post(requestBody)
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    requireActivity().runOnUiThread { 
                        hideIdentifyProgress()
                        resultText.text = "❌ 识别失败: ${e.message}" 
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    val res = response.body?.string() ?: "无返回"
                    requireActivity().runOnUiThread {
                        hideIdentifyProgress()
                        try {
                            val parsedResult = parseIdentificationResult(res)
                            resultText.text = parsedResult
                        } catch (e: Exception) {
                            resultText.text = "❌ 解析结果失败: ${e.message}\n\n原始数据: $res"
                        }
                    }
                }
            })
        } catch (e: Exception) {
            requireActivity().runOnUiThread { resultText.text = "❌ 网络请求失败: ${e.message}" }
        }
    }

    private fun parseIdentificationResult(jsonString: String): String {
        return try {
            val jsonObject = JSONObject(jsonString)
            val duration = jsonObject.optDouble("duration", 0.0)
            val familyResults = jsonObject.optJSONArray("family_results")
            
            val resultBuilder = StringBuilder()
            resultBuilder.append("✅ 识别完成！\n\n")
            resultBuilder.append("⏱️ 识别耗时：${String.format("%.3f", duration)}秒\n\n")
            
            if (familyResults != null && familyResults.length() > 0) {
                resultBuilder.append("🌱 识别到的植物科属：\n\n")
                for (i in 0 until familyResults.length()) {
                    val family = familyResults.getJSONObject(i)
                    val chineseName = family.optString("chinese_name", "未知")
                    val latinName = family.optString("latin_name", "Unknown")
                    val probability = family.optDouble("probability", 0.0)
                    
                    // 根据置信度选择不同的图标
                    val confidenceIcon = when {
                        probability >= 0.5 -> "🥇"
                        probability >= 0.3 -> "🥈"
                        probability >= 0.1 -> "🥉"
                        else -> "📋"
                    }
                    
                    resultBuilder.append("$confidenceIcon ${i + 1}. $chineseName\n")
                    resultBuilder.append("   📝 学名：$latinName\n")
                    resultBuilder.append("   📊 置信度：${String.format("%.1f", probability * 100)}%\n")
                    
                    // 添加分隔线（除了最后一项）
                    if (i < familyResults.length() - 1) {
                        resultBuilder.append("   ──────────────────\n")
                    }
                    resultBuilder.append("\n")
                }
                
                // 添加建议
                val topResult = familyResults.getJSONObject(0)
                val topProbability = topResult.optDouble("probability", 0.0)
                if (topProbability >= 0.5) {
                    resultBuilder.append("💡 建议：置信度较高，结果可信度强")
                } else if (topProbability >= 0.3) {
                    resultBuilder.append("💡 建议：置信度中等，建议结合其他特征判断")
                } else {
                    resultBuilder.append("💡 建议：置信度较低，建议重新拍摄或尝试其他角度")
                }
            } else {
                resultBuilder.append("❌ 未识别到植物信息\n\n")
                resultBuilder.append("💡 建议：\n")
                resultBuilder.append("• 确保拍摄的是植物\n")
                resultBuilder.append("• 调整拍摄角度和光线\n")
                resultBuilder.append("• 确保图片清晰")
            }
            
            resultBuilder.toString()
        } catch (e: Exception) {
            "❌ 解析失败: ${e.message}\n\n原始数据: $jsonString"
        }
    }

    private fun showIdentifyProgress() {
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                identifyProgressBar.visibility = View.VISIBLE
                progressText.visibility = View.VISIBLE
                progressText.text = "🔍 正在识别植物..."
                resultText.text = "🔄 正在分析图片特征..."
                
                // 模拟识别进度
                var progress = 0
                val handler = Handler(Looper.getMainLooper())
                val runnable = object : Runnable {
                    override fun run() {
                        if (isIdentifying && progress < 90) {
                            progress += 5
                            identifyProgressBar.progress = progress
                            when {
                                progress < 30 -> progressText.text = "🔍 正在识别植物... $progress%"
                                progress < 60 -> progressText.text = "📊 正在分析特征... $progress%"
                                progress < 90 -> progressText.text = "🎯 正在匹配结果... $progress%"
                            }
                            handler.postDelayed(this, 200)
                        }
                    }
                }
                handler.post(runnable)
            }
        }
    }
    
    private fun hideIdentifyProgress() {
        isIdentifying = false
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                identifyProgressBar.visibility = View.GONE
                progressText.visibility = View.GONE
            }
        }
    }
    
    // 拍照/选图后调用
    private fun handleImageFileForUpload(file: File?) {
        if (file != null && file.exists()) {
            // 压缩图片
            val compressed = compressImageFile(file)
            imageFile = compressed
            // 立即显示图片
            imageView.setImageURI(Uri.fromFile(compressed))
            // 延迟+防抖+重试上传
            uploadImageToOSS(compressed, retryCount = 1)
        } else {
            if (isAdded && activity != null) {
                activity?.runOnUiThread { resultText.text = "❌ 图片处理失败，文件不存在" }
            }
        }
    }
} 