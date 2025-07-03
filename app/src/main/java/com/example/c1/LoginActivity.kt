package com.example.c1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.os.Handler
import android.os.Looper
import android.util.Log

class LoginActivity : AppCompatActivity() {
    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var loginLayout: LinearLayout
    private lateinit var registerLayout: LinearLayout
    // 登录
    private lateinit var etLoginUsername: EditText
    private lateinit var etLoginPassword: EditText
    private lateinit var btnLogin: Button
    // 注册
    private lateinit var etRegisterUsername: EditText
    private lateinit var etRegisterPassword: EditText
    private lateinit var etRegisterPhone: EditText
    private lateinit var etRegisterEmail: EditText
    private lateinit var spRegisterRole: Spinner
    private lateinit var ivAvatar: ImageView
    private lateinit var btnSelectAvatar: Button
    private lateinit var btnRegister: Button
    private var avatarFile: File? = null
    private var avatarUrl: String? = null
    private lateinit var oss: OSSClient
    private val REQUEST_CODE_PICK_IMAGE = 201
    private val REQUEST_CODE_TAKE_PHOTO = 202
    private var avatarUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(generateContentView())
        initOSS()
        setupTabs()
        setupLogin()
        setupRegister()
    }

    private fun generateContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 32
            }
        }
        root.addView(logo)
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        tabLogin = TextView(this).apply {
            text = "登录"
            textSize = 20f
            setPadding(40, 20, 40, 20)
            setTextColor(Color.parseColor("#1976D2"))
            setBackgroundResource(R.drawable.tab_left_selector)
            isSelected = true
        }
        tabRegister = TextView(this).apply {
            text = "注册"
            textSize = 20f
            setPadding(40, 20, 40, 20)
            setTextColor(Color.parseColor("#1976D2"))
            setBackgroundResource(R.drawable.tab_right_selector)
            isSelected = false
        }
        tabLayout.addView(tabLogin)
        tabLayout.addView(tabRegister)
        root.addView(tabLayout)
        // 登录布局
        loginLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            setPadding(0, 32, 0, 0)
        }
        etLoginUsername = EditText(this).apply {
            hint = "用户名"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        etLoginPassword = EditText(this).apply {
            hint = "密码"
            inputType = 129
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        btnLogin = Button(this).apply {
            text = "登录"
            setBackgroundResource(R.drawable.button_primary)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply {
                topMargin = 32
            }
        }
        loginLayout.addView(etLoginUsername)
        loginLayout.addView(etLoginPassword)
        loginLayout.addView(btnLogin)
        // 注册布局
        registerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 32, 0, 0)
        }
        etRegisterUsername = EditText(this).apply {
            hint = "用户名"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        etRegisterPassword = EditText(this).apply {
            hint = "密码"
            inputType = 129
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        etRegisterPhone = EditText(this).apply {
            hint = "手机号"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        etRegisterEmail = EditText(this).apply {
            hint = "邮箱"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        spRegisterRole = Spinner(this)
        val roles = arrayOf("学生", "教师", "管理员")
        spRegisterRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        ivAvatar = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 24
                bottomMargin = 24
            }
        }
        btnSelectAvatar = Button(this).apply {
            text = "选择头像"
            setBackgroundResource(R.drawable.button_primary)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 16, 0, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        btnRegister = Button(this).apply {
            text = "注册"
            setBackgroundResource(R.drawable.button_primary)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply {
                topMargin = 32
            }
        }
        registerLayout.addView(etRegisterUsername)
        registerLayout.addView(etRegisterPassword)
        registerLayout.addView(etRegisterPhone)
        registerLayout.addView(etRegisterEmail)
        registerLayout.addView(spRegisterRole)
        registerLayout.addView(ivAvatar)
        registerLayout.addView(btnSelectAvatar)
        registerLayout.addView(btnRegister)
        root.addView(loginLayout)
        root.addView(registerLayout)
        return root
    }

    private fun setupTabs() {
        tabLogin.setOnClickListener {
            tabLogin.isSelected = true
            tabRegister.isSelected = false
            loginLayout.visibility = View.VISIBLE
            registerLayout.visibility = View.GONE
        }
        tabRegister.setOnClickListener {
            tabLogin.isSelected = false
            tabRegister.isSelected = true
            loginLayout.visibility = View.GONE
            registerLayout.visibility = View.VISIBLE
        }
    }

    private fun setupLogin() {
        btnLogin.setOnClickListener {
            val username = etLoginUsername.text.toString().trim()
            val password = etLoginPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(username, password)
        }
    }

    private fun setupRegister() {
        btnSelectAvatar.setOnClickListener {
            showAvatarSourceDialog()
        }
        btnRegister.setOnClickListener {
            val username = etRegisterUsername.text.toString().trim()
            val password = etRegisterPassword.text.toString().trim()
            val phone = etRegisterPhone.text.toString().trim()
            val email = etRegisterEmail.text.toString().trim()
            val role = spRegisterRole.selectedItem.toString()
            if (username.isEmpty() || password.isEmpty() || phone.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (avatarFile != null && avatarUrl == null) {
                Toast.makeText(this, "头像正在上传，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            register(username, password, phone, email, role, avatarUrl)
        }
    }

    private fun showAvatarSourceDialog() {
        val options = arrayOf("拍照", "相册选择")
        android.app.AlertDialog.Builder(this)
            .setTitle("选择头像来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takeAvatarPhoto()
                    1 -> pickAvatarFromGallery()
                }
            }
            .show()
    }

    private fun takeAvatarPhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createAvatarFile()
        avatarFile = photoFile
        avatarUri = FileProvider.getUriForFile(this, "com.example.c1.fileprovider", photoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, avatarUri)
        startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO)
    }

    private fun pickAvatarFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    private fun createAvatarFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(null)!!
        return File.createTempFile("AVATAR_${timeStamp}_", ".jpg", storageDir)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_PICK_IMAGE -> {
                    val uri = data?.data
                    uri?.let {
                        val file = copyUriToFile(this, it)
                        handleAvatarFileForUpload(file)
                    }
                }
                REQUEST_CODE_TAKE_PHOTO -> {
                    avatarUri?.let {
                        val file = copyUriToFile(this, it)
                        handleAvatarFileForUpload(file)
                    }
                }
            }
        }
    }

    // 兼容所有Android版本：将Uri内容复制到App私有目录
    private fun copyUriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.getExternalFilesDir(null), "avatar_${System.currentTimeMillis()}.jpg")
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

    private fun initOSS() {
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
        oss = OSSClient(applicationContext, endpoint, credentialProvider, conf)
    }

    private fun uploadAvatarToOSS(localFile: File, retryCount: Int = 1) {
        if (!localFile.exists()) {
            runOnUiThread { Toast.makeText(this, "❌ 本地图片文件不存在，无法上传", Toast.LENGTH_SHORT).show() }
            Log.e("OSS_DEBUG", "上传前文件不存在: " + localFile.absolutePath)
            return
        }
        Log.d("OSS_DEBUG", "file exists: true, path: " + localFile.absolutePath)
        val bucketName = OssConfig.BUCKET_NAME
        val objectKey = "avatar/${System.currentTimeMillis()}.jpg"
        val put = PutObjectRequest(bucketName, objectKey, localFile.absolutePath)
        // 禁用上传按钮，防止重复上传
        btnRegister.isEnabled = false
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    oss.putObject(put)
                    Log.d("OSS_DEBUG", "上传后文件存在: " + localFile.exists() + ", path: " + localFile.absolutePath)
                    val url = "${OssConfig.OSS_URL_PREFIX}$objectKey"
                    runOnUiThread {
                        btnRegister.isEnabled = true
                        avatarUrl = url
                        Toast.makeText(this, "头像上传成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("OSS_DEBUG", "上传失败: ${e.message}")
                    if (retryCount > 0) {
                        Log.d("OSS_DEBUG", "自动重试上传...")
                        uploadAvatarToOSS(localFile, retryCount - 1)
                    } else {
                        runOnUiThread {
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "头像上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }, 200)
    }

    // 拍照/选图后调用
    private fun handleAvatarFileForUpload(file: File?) {
        if (file != null && file.exists()) {
            // 压缩图片
            val compressed = compressImageFile(file)
            avatarFile = compressed
            // 延迟+防抖+重试上传
            uploadAvatarToOSS(compressed, retryCount = 1)
        } else {
            runOnUiThread { Toast.makeText(this, "❌ 图片处理失败，文件不存在", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun login(username: String, password: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "auth/login")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@LoginActivity, "登录失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                val obj = JSONObject(res)
                if (obj.optInt("code") == 0) {
                    val token = obj.optString("token")
                    saveToken(token)
                    // 保存用户信息
                    val user = obj.optJSONObject("user")
                    if (user != null) {
                        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("username", user.optString("username"))
                            .putString("phone", user.optString("phone"))
                            .putString("role", user.optString("role"))
                            .putString("avatarUrl", user.optString("avatarUrl"))
                            .apply()
                    }
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "登录失败"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun register(
        username: String,
        password: String,
        phone: String,
        email: String,
        role: String,
        avatarUrl: String?
    ) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("phone", phone)
            put("email", email)
            put("role", role)
            if (!avatarUrl.isNullOrBlank()) put("avatarUrl", avatarUrl)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "auth/register")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@LoginActivity, "注册失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                val obj = JSONObject(res)
                if (obj.optInt("code") == 0) {
                    val token = obj.optString("token")
                    saveToken(token)
                    // 保存用户信息
                    val user = obj.optJSONObject("user")
                    if (user != null) {
                        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("username", user.optString("username"))
                            .putString("phone", user.optString("phone"))
                            .putString("role", user.optString("role"))
                            .putString("avatarUrl", user.optString("avatarUrl"))
                            .apply()
                    }
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "注册成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "注册失败"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
        prefs.edit().putString("token", token).apply()
    }
} 