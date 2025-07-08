package com.example.c1

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import android.view.ViewGroup

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
    private lateinit var etInvitationCode: EditText
    private lateinit var etSchoolName: EditText
    private lateinit var etUserName: EditText
    private lateinit var etEmailVerifyCode: EditText
    private lateinit var btnSendEmailCode: Button
    private lateinit var ivAvatar: ImageView
    private lateinit var btnSelectAvatar: Button
    private lateinit var btnRegister: Button
    private lateinit var avatarProgressLayout: LinearLayout
    private lateinit var avatarProgressText: TextView
    private lateinit var avatarProgressBar: ProgressBar
    private var avatarFile: File? = null
    private var avatarUrl: String? = null
    private var isAvatarUploading = false // 头像上传状态
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
        // 创建ScrollView作为根容器
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        //logo设置
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
            setPadding(0, 32, 0, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f // 让登录布局也占据剩余空间
            }
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
        // 注册布局 - 使用ScrollView包装
        val registerScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f // 让ScrollView占据剩余空间
            }
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_ALWAYS // 显示滚动条
        }
        
        registerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 32, 0, 32) // 增加底部padding
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
        
        // 邮箱验证码输入框和发送按钮
        val emailCodeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        etEmailVerifyCode = EditText(this).apply {
            hint = "邮箱验证码"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnSendEmailCode = Button(this).apply {
            text = "发送验证码"
            setBackgroundResource(R.drawable.button_primary)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 24, 16, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 8
            }
        }
        emailCodeLayout.addView(etEmailVerifyCode)
        emailCodeLayout.addView(btnSendEmailCode)
        
        spRegisterRole = Spinner(this)
        val roles = arrayOf("普通用户", "学生", "教师")
        spRegisterRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        
        // 学生/教师专用字段
        etInvitationCode = EditText(this).apply {
            hint = "邀请码（学生/教师必填）"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
            visibility = View.GONE
        }
        etSchoolName = EditText(this).apply {
            hint = "学校名称（学生/教师必填）"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
            visibility = View.GONE
        }
        etUserName = EditText(this).apply {
            hint = "真实姓名（学生/教师必填）"
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
            visibility = View.GONE
        }
        
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
        
        // 头像上传进度提示
        avatarProgressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
            visibility = View.GONE
        }
        
        avatarProgressText = TextView(this).apply {
            text = "头像上传中..."
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            }
        }
        
        avatarProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                height = 8
            }
            progressTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        }
        
        avatarProgressLayout.addView(avatarProgressText)
        avatarProgressLayout.addView(avatarProgressBar)
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
        registerLayout.addView(emailCodeLayout)
        registerLayout.addView(spRegisterRole)
        registerLayout.addView(etInvitationCode)
        registerLayout.addView(etSchoolName)
        registerLayout.addView(etUserName)
        registerLayout.addView(ivAvatar)
        registerLayout.addView(btnSelectAvatar)
        registerLayout.addView(avatarProgressLayout)
        registerLayout.addView(btnRegister)
        root.addView(loginLayout)
        registerScrollView.addView(registerLayout)
        root.addView(registerScrollView)
        scrollView.addView(root)
        return scrollView
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
        // 角色选择监听器
        spRegisterRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val role = parent?.getItemAtPosition(position).toString()
                when (role) {
                    "普通用户" -> {
                        etInvitationCode.visibility = View.GONE
                        etSchoolName.visibility = View.GONE
                        etUserName.visibility = View.GONE
                    }
                    "学生", "教师" -> {
                        etInvitationCode.visibility = View.VISIBLE
                        etSchoolName.visibility = View.VISIBLE
                        etUserName.visibility = View.VISIBLE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        btnSendEmailCode.setOnClickListener {
            val email = etRegisterEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "请先输入邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "请输入有效的邮箱地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendEmailVerificationCode(email)
        }
        
        btnSelectAvatar.setOnClickListener {
            if (isAvatarUploading) {
                Toast.makeText(this, "头像正在上传中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAvatarSourceDialog()
        }
        btnRegister.setOnClickListener {
            val username = etRegisterUsername.text.toString().trim()
            val password = etRegisterPassword.text.toString().trim()
            val phone = etRegisterPhone.text.toString().trim()
            val email = etRegisterEmail.text.toString().trim()
            val emailVerifyCode = etEmailVerifyCode.text.toString().trim()
            val role = spRegisterRole.selectedItem.toString()
            
            if (username.isEmpty() || password.isEmpty() || phone.isEmpty() || email.isEmpty() || emailVerifyCode.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isAvatarUploading) {
                Toast.makeText(this, "头像正在上传，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (avatarFile != null && avatarUrl == null) {
                Toast.makeText(this, "头像上传失败，请重新选择", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            when (role) {
                "普通用户" -> {
                    registerNormalUser(username, password, phone, email, role, avatarUrl, emailVerifyCode)
                }
                "学生", "教师" -> {
                    val invitationCode = etInvitationCode.text.toString().trim()
                    val schoolName = etSchoolName.text.toString().trim()
                    val userName = etUserName.text.toString().trim()
                    
                    if (invitationCode.isEmpty() || schoolName.isEmpty() || userName.isEmpty()) {
                        Toast.makeText(this, "学生/教师注册需要填写邀请码、学校名称和真实姓名", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    registerSchoolUser(username, password, phone, email, role, avatarUrl, 
                                     schoolName, userName, invitationCode, emailVerifyCode)
                }
            }
        }
    }

    private fun sendEmailVerificationCode(email: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "auth/register/verify/email/$email"))
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), ""))
            .build()
            
        btnSendEmailCode.isEnabled = false
        btnSendEmailCode.text = "发送中..."
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    Toast.makeText(this@LoginActivity, "发送验证码失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnSendEmailCode.isEnabled = true
                    btnSendEmailCode.text = "发送验证码"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("LoginActivity", "发送验证码API响应: $res")
                val obj = JSONObject(res)
                runOnUiThread {
                    if (obj.optInt("code") == 0) {
                        Toast.makeText(this@LoginActivity, "验证码已发送到邮箱", Toast.LENGTH_SHORT).show()
                        // 开始倒计时
                        startEmailCodeCountdown()
                    } else {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "发送验证码失败"), Toast.LENGTH_SHORT).show()
                        btnSendEmailCode.isEnabled = true
                        btnSendEmailCode.text = "发送验证码"
                    }
                }
            }
        })
    }
    
    private fun startEmailCodeCountdown() {
        var countdown = 60
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (countdown > 0) {
                    btnSendEmailCode.text = "${countdown}秒后重发"
                    btnSendEmailCode.isEnabled = false
                    countdown--
                    handler.postDelayed(this, 1000)
                } else {
                    btnSendEmailCode.isEnabled = true
                    btnSendEmailCode.text = "发送验证码"
                }
            }
        }
        handler.post(runnable)
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
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile = createAvatarFile()
            avatarFile = photoFile
            avatarUri = FileProvider.getUriForFile(this, "com.example.c1.fileprovider", photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, avatarUri)
            startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "拍照功能初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        do {
            outFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            quality -= 10
        } while (outFile.length() > maxSize && quality > 10)
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
                        // 立即显示选择的图片
                        displaySelectedImage(file)
                        handleAvatarFileForUpload(file)
                    }
                }
                REQUEST_CODE_TAKE_PHOTO -> {
                    avatarUri?.let {
                        val file = copyUriToFile(this, it)
                        // 立即显示拍照的图片
                        displaySelectedImage(file)
                        handleAvatarFileForUpload(file)
                    }
                }
            }
        }
    }

    // 显示选择的图片
    private fun displaySelectedImage(file: File?) {
        if (file != null && file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ivAvatar.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "图片显示失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
            runOnUiThread { 
                Toast.makeText(this, "❌ 本地图片文件不存在，无法上传", Toast.LENGTH_SHORT).show()
                hideAvatarUploadProgress()
            }
            Log.e("OSS_DEBUG", "上传前文件不存在: " + localFile.absolutePath)
            return
        }
        
        Log.d("OSS_DEBUG", "file exists: true, path: " + localFile.absolutePath)
        val bucketName = OssConfig.BUCKET_NAME
        val objectKey = "avatar/${System.currentTimeMillis()}.jpg"
        val put = PutObjectRequest(bucketName, objectKey, localFile.absolutePath)
        
        // 显示上传进度
        showAvatarUploadProgress()
        isAvatarUploading = true
        
        Handler(Looper.getMainLooper()).postDelayed({
            Thread {
                try {
                    // 模拟上传进度
                    for (i in 1..10) {
                        runOnUiThread {
                            avatarProgressBar.progress = i * 10
                            avatarProgressText.text = "头像上传中... ${i * 10}%"
                        }
                        Thread.sleep(200) // 模拟上传延迟
                    }
                    
                    oss.putObject(put)
                    Log.d("OSS_DEBUG", "上传后文件存在: " + localFile.exists() + ", path: " + localFile.absolutePath)
                    val url = "${OssConfig.OSS_URL_PREFIX}$objectKey"
                    
                    runOnUiThread {
                        hideAvatarUploadProgress()
                        isAvatarUploading = false
                        avatarUrl = url
                        // 显示上传成功的头像
                        displaySelectedImage(localFile)
                        Toast.makeText(this, "✅ 头像上传成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("OSS_DEBUG", "上传失败: ${e.message}")
                    if (retryCount > 0) {
                        Log.d("OSS_DEBUG", "自动重试上传...")
                        uploadAvatarToOSS(localFile, retryCount - 1)
                    } else {
                        runOnUiThread {
                            hideAvatarUploadProgress()
                            isAvatarUploading = false
                            Toast.makeText(this, "❌ 头像上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }, 200)
    }
    
    private fun showAvatarUploadProgress() {
        avatarProgressLayout.visibility = View.VISIBLE
        avatarProgressBar.progress = 0
        avatarProgressText.text = "头像上传中... 0%"
        btnSelectAvatar.isEnabled = false
        btnSelectAvatar.text = "上传中..."
    }
    
    private fun hideAvatarUploadProgress() {
        avatarProgressLayout.visibility = View.GONE
        btnSelectAvatar.isEnabled = true
        btnSelectAvatar.text = "选择头像"
    }

    // 拍照/选图后调用
    private fun handleAvatarFileForUpload(file: File?) {
        try {
            if (file != null && file.exists()) {
                // 压缩图片
                val compressed = compressImageFile(file)
                avatarFile = compressed
                // 延迟+防抖+重试上传
                uploadAvatarToOSS(compressed, retryCount = 1)
            } else {
                runOnUiThread { Toast.makeText(this, "❌ 图片处理失败，文件不存在", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun login(username: String, password: String) {
        btnLogin.isEnabled = false
        btnLogin.text = "登录中..."
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "auth/login"))
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    Toast.makeText(this@LoginActivity, "登录失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = "登录"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("LoginActivity", "登录API响应: $res")
                val obj = JSONObject(res)
                if (obj.optInt("code") == 0) {
                    val token = obj.optString("token")
                    saveToken(token)
                    // 保存用户信息
                    val user = obj.optJSONObject("user")
                    if (user != null) {
                        val userId = user.optInt("id")
                        val username = user.optString("username")
                        val phone = user.optString("phone")
                        val role = user.optString("role")
                        val avatarUrl = user.optString("avatarUrl")
                        val email = user.optString("email")
                        
                        Log.d("LoginActivity", "登录成功 - 用户ID: $userId, 用户名: $username, 手机号: $phone, 角色: $role, 头像: $avatarUrl, 邮箱: $email")
                        
                        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("userId", userId)
                            .putString("username", username)
                            .putString("phone", phone)
                            .putString("role", role)
                            .putString("avatarUrl", avatarUrl)
                            .putString("email", email)
                            .apply()
                    } else {
                        Log.e("LoginActivity", "登录响应中用户信息为空")
                    }
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    Log.e("LoginActivity", "登录失败: ${obj.optString("message")}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "登录失败"), Toast.LENGTH_SHORT).show()
                        btnLogin.isEnabled = true
                        btnLogin.text = "登录"
                    }
                }
            }
        })
    }

    private fun registerNormalUser(
        username: String,
        password: String,
        phone: String,
        email: String,
        role: String,
        avatarUrl: String?,
        emailVerifyCode: String
    ) {
        btnRegister.isEnabled = false
        btnRegister.text = "注册中..."
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("phone", phone)
            put("email", email)
            put("role", role)
            put("emailVerifyCode", emailVerifyCode)
            if (!avatarUrl.isNullOrBlank()) put("avatarUrl", avatarUrl)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "auth/register"))
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    Toast.makeText(this@LoginActivity, "注册失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetRegisterButton()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("LoginActivity", "普通用户注册API响应: $res")
                val obj = JSONObject(res)
                if (obj.optInt("code") == 0) {
                    val token = obj.optString("token")
                    saveToken(token)
                    // 保存用户信息
                    val user = obj.optJSONObject("user")
                    if (user != null) {
                        val userId = user.optInt("id")
                        val username = user.optString("username")
                        val phone = user.optString("phone")
                        val role = user.optString("role")
                        val avatarUrl = user.optString("avatarUrl")
                        val email = user.optString("email")
                        
                        Log.d("LoginActivity", "普通用户注册成功 - 用户ID: $userId, 用户名: $username, 手机号: $phone, 角色: $role, 头像: $avatarUrl, 邮箱: $email")
                        
                        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("userId", userId)
                            .putString("username", username)
                            .putString("phone", phone)
                            .putString("role", role)
                            .putString("avatarUrl", avatarUrl)
                            .putString("email", email)
                            .apply()
                    } else {
                        Log.e("LoginActivity", "注册响应中用户信息为空")
                    }
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "注册成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    Log.e("LoginActivity", "普通用户注册失败: ${obj.optString("message")}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "注册失败"), Toast.LENGTH_SHORT).show()
                        resetRegisterButton()
                    }
                }
            }
        })
    }

    private fun registerSchoolUser(
        username: String,
        password: String,
        phone: String,
        email: String,
        role: String,
        avatarUrl: String?,
        schoolName: String,
        userName: String,
        invitationCode: String,
        emailVerifyCode: String
    ) {
        btnRegister.isEnabled = false
        btnRegister.text = "注册中..."
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("phone", phone)
            put("email", email)
            put("role", role)
            put("avatarUrl", avatarUrl ?: "")
            put("schoolName", schoolName)
            put("userName", userName)
            put("invitationCode", invitationCode)
            put("emailVerifyCode", emailVerifyCode)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "auth/register/school"))
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    Toast.makeText(this@LoginActivity, "注册失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetRegisterButton()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("LoginActivity", "学生/教师注册API响应: $res")
                val obj = JSONObject(res)
                if (obj.optInt("code") == 0) {
                    val token = obj.optString("token")
                    saveToken(token)
                    // 保存用户信息
                    val user = obj.optJSONObject("user")
                    if (user != null) {
                        val userId = user.optInt("id")
                        val username = user.optString("username")
                        val phone = user.optString("phone")
                        val role = user.optString("role")
                        val avatarUrl = user.optString("avatarUrl")
                        val email = user.optString("email")
                        
                        Log.d("LoginActivity", "学生/教师注册成功 - 用户ID: $userId, 用户名: $username, 手机号: $phone, 角色: $role, 头像: $avatarUrl, 邮箱: $email")
                        
                        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("userId", userId)
                            .putString("username", username)
                            .putString("phone", phone)
                            .putString("role", role)
                            .putString("avatarUrl", avatarUrl)
                            .putString("email", email)
                            .apply()
                    } else {
                        Log.e("LoginActivity", "注册响应中用户信息为空")
                    }
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "注册成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    Log.e("LoginActivity", "学生/教师注册失败: ${obj.optString("message")}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, obj.optString("message", "注册失败"), Toast.LENGTH_SHORT).show()
                        resetRegisterButton()
                    }
                }
            }
        })
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences("user", Context.MODE_PRIVATE)
        prefs.edit().putString("token", token).apply()
    }
    
    private fun resetRegisterButton() {
        btnRegister.isEnabled = true
        btnRegister.text = "注册"
    }

    // 安全的URL拼接函数，避免多余斜杠
    private fun safeUrlJoin(base: String, path: String): String {
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }
} 