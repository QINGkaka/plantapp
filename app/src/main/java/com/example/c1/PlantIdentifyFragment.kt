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

class PlantIdentifyFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private var imageFile: File? = null
    private var imageUri: Uri? = null

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
                    uploadImage(it)
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

            btnPick.setOnClickListener {
                if (checkPermissions()) {
                    dispatchTakePictureIntent()
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

    private fun uploadImage(file: File) {
        try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", file.name, file.asRequestBody("image/*".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("http://192.168.1.14:5000/api/identify")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    requireActivity().runOnUiThread { resultText.text = "❌ 上传失败: ${e.message}" }
                }

                override fun onResponse(call: Call, response: Response) {
                    val res = response.body?.string() ?: "无返回"
                    requireActivity().runOnUiThread { 
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
} 