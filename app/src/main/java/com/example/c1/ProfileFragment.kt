package com.example.c1

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.Callback
import org.json.JSONObject
import java.io.File
import java.io.IOException

class ProfileFragment : Fragment() {
    private lateinit var ivAvatar: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvRole: TextView
        private lateinit var btnEditProfile: Button
    private lateinit var btnChangePassword: Button
    private lateinit var btnLogout: Button
    
    private var avatarFile: File? = null
    private var avatarUrl: String? = null
    private var isUploading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        tvUsername = view.findViewById(R.id.tvUsername)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvRole = view.findViewById(R.id.tvRole)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        btnLogout = view.findViewById(R.id.btnLogout)

        // 先显示本地存储的用户信息
        loadLocalUserInfo()
        
        // 然后从API获取最新的用户信息
        fetchUserInfoFromAPI()

        // 添加调试按钮（长按用户名显示详细信息）
        tvUsername.setOnLongClickListener {
            showDebugInfo()
            true
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("退出") { _, _ ->
                    logout()
                }
                .setNegativeButton("取消", null)
                .show()
        }


    }

    private fun loadLocalUserInfo() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val avatarUrl = prefs.getString("avatarUrl", null)
        val username = prefs.getString("username", "-")
        val phone = prefs.getString("phone", "-")
        val role = prefs.getString("role", "-")

        Log.d("ProfileFragment", "本地用户信息 - 用户名: $username, 手机号: $phone, 角色: $role, 头像: $avatarUrl")

        // 如果用户信息为空（如退出登录后），不做任何提示，直接return
        if (username == "-" && phone == "-" && role == "-") {
            return
        }

        if (!avatarUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .override(150, 150) // 限制头像尺寸
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(com.bumptech.glide.Priority.HIGH) // 头像优先级高
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
        }
        tvUsername.text = "用户名：$username"
        tvPhone.text = "手机号：$phone"
        tvRole.text = "角色：$role"
    }
    //从API获取用户信息
    private fun fetchUserInfoFromAPI() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        if (token.isNullOrBlank()) {
            Log.e("ProfileFragment", "Token为空，无法获取用户信息")
            return
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "his-user-service/account/info/me"))
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "获取用户信息失败: ${e.message}")
                // 检查Fragment是否仍然附加到Activity
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null && !token.isNullOrBlank()) {
                            Toast.makeText(context, "获取用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("ProfileFragment", "API响应: $res")
                
                try {
                    val obj = JSONObject(res)
                    if (obj.optInt("code") == 0) {
                        val user = obj.optJSONObject("user")
                        if (user != null) {
                            val username = user.optString("username")
                            val phone = user.optString("phone")
                            val role = user.optString("role")
                            val avatarUrl = user.optString("avatarUrl")
                            val email = user.optString("email")

                            Log.d("ProfileFragment", "API返回用户信息 - 用户名: $username, 手机号: $phone, 角色: $role, 头像: $avatarUrl, 邮箱: $email")

                            // 检查Fragment是否仍然附加到Activity
                            if (isAdded && activity != null) {
                                                            // 检查用户信息是否发生变化
                            val prefs = context?.getSharedPreferences("user", Context.MODE_PRIVATE)
                            val oldUsername = prefs?.getString("username", "")
                            val oldPhone = prefs?.getString("phone", "")
                            val oldRole = prefs?.getString("role", "")
                            val oldAvatarUrl = prefs?.getString("avatarUrl", "")
                            val oldEmail = prefs?.getString("email", "")
                            
                            val hasChanged = oldUsername != username || 
                                           oldPhone != phone || 
                                           oldRole != role || 
                                           oldAvatarUrl != avatarUrl || 
                                           oldEmail != email
                            
                            // 更新本地存储
                            activity?.runOnUiThread {
                                if (isAdded && context != null) {
                                    try {
                                        prefs?.edit()
                                            ?.putString("username", username)
                                            ?.putString("phone", phone)
                                            ?.putString("role", role)
                                            ?.putString("avatarUrl", avatarUrl)
                                            ?.putString("email", email)
                                            ?.apply()

                                        // 更新UI
                                        tvUsername.text = "用户名：$username"
                                        tvPhone.text = "手机号：$phone"
                                        tvRole.text = "角色：$role"
                                        
                                        if (!avatarUrl.isNullOrBlank()) {
                                            Glide.with(this@ProfileFragment).load(avatarUrl).circleCrop().into(ivAvatar)
                                        } else {
                                            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                                        }
                                        // 只在信息真正发生变化时才显示提示
                                        if (hasChanged) {
                                            Toast.makeText(context, "用户信息已更新", Toast.LENGTH_SHORT).show()
                                        }
                                        } catch (e: Exception) {
                                            Log.e("ProfileFragment", "更新UI时出错: ${e.message}")
                                        }
                                    }
                                }
                            } else {
                                Log.w("ProfileFragment", "Fragment已分离，跳过UI更新")
                            }
                        } else {
                            Log.e("ProfileFragment", "API返回的用户信息为空")
                        }
                    } else {
                        Log.e("ProfileFragment", "API返回错误: ${obj.optString("message")}")
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                if (isAdded && context != null && !token.isNullOrBlank()) {
                                    Toast.makeText(context, "获取用户信息失败: ${obj.optString("message")}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "解析API响应失败: ${e.message}")
                    if (isAdded && activity != null) {
                        activity?.runOnUiThread {
                            if (isAdded && context != null && !token.isNullOrBlank()) {
                                // 只有token存在时才提示解析失败
                                Toast.makeText(context, "解析用户信息失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun logout() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        // 先清除本地数据，防止后续UI异常
        prefs.edit().clear().apply()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
        // 不再进行任何API请求和UI更新
    }
    //展示编辑信息的对话
    private fun showEditProfileDialog() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val currentPhone = prefs.getString("phone", "")
        val currentEmail = prefs.getString("email", "")
        
        if (currentPhone.isNullOrBlank() || currentEmail.isNullOrBlank()) {
            Toast.makeText(context, "无法获取当前用户信息，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // 手机号标签和输入框
        val phoneLabel = TextView(requireContext()).apply {
            text = "手机号："
            textSize = 16f
            setTextColor(requireContext().getColor(android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            }
        }
        
        val etPhone = EditText(requireContext()).apply {
            hint = "手机号"
            setText(currentPhone)
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        
        // 邮箱标签和输入框
        val emailLabel = TextView(requireContext()).apply {
            text = "邮箱："
            textSize = 16f
            setTextColor(requireContext().getColor(android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8
            }
        }
        
        val etEmail = EditText(requireContext()).apply {
            hint = "邮箱"
            setText(currentEmail)
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        
        dialogView.addView(phoneLabel)
        dialogView.addView(etPhone)
        dialogView.addView(emailLabel)
        dialogView.addView(etEmail)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("编辑个人资料")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newPhone = etPhone.text.toString().trim()
                val newEmail = etEmail.text.toString().trim()
                
                if (newPhone.isEmpty() || newEmail.isEmpty()) {
                    Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 验证手机号格式
                if (!newPhone.matches(Regex("^1[3-9]\\d{9}$"))) {
                    Toast.makeText(context, "请输入有效的手机号", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 验证邮箱格式
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    Toast.makeText(context, "请输入有效的邮箱地址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                updateUserInfo(newPhone, newEmail)
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }
    
    private fun updateUserInfo(phone: String, email: String) {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val userId = prefs.getInt("userId", 0)
        
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "登录状态异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("userId", userId)
            put("phone", phone)
            put("email", email)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "his-user-service/account/info/me"))
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "更新用户信息失败: ${e.message}")
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "更新用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("ProfileFragment", "更新用户信息API响应: $res")
                
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            try {
                                val obj = JSONObject(res)
                                if (obj.optInt("code") == 0) {
                                    val user = obj.optJSONObject("user")
                                    if (user != null) {
                                        val username = user.optString("username")
                                        val phone = user.optString("phone")
                                        val role = user.optString("role")
                                        val avatarUrl = user.optString("avatarUrl")
                                        val email = user.optString("email")
                                        
                                        // 更新本地存储
                                        prefs.edit()
                                            .putString("username", username)
                                            .putString("phone", phone)
                                            .putString("role", role)
                                            .putString("avatarUrl", avatarUrl)
                                            .putString("email", email)
                                            .apply()
                                        
                                        // 更新UI
                                        tvUsername.text = "用户名：$username"
                                        tvPhone.text = "手机号：$phone"
                                        tvRole.text = "角色：$role"
                                        
                                        if (!avatarUrl.isNullOrBlank()) {
                                            Glide.with(this@ProfileFragment).load(avatarUrl).circleCrop().into(ivAvatar)
                                        } else {
                                            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                                        }
                                        
                                        Toast.makeText(context, "个人资料更新成功", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, obj.optString("message", "更新用户信息失败"), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("ProfileFragment", "解析更新用户信息响应失败: ${e.message}")
                                Toast.makeText(context, "更新用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun showChangePasswordDialog() {
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val etOldPassword = EditText(requireContext()).apply {
            hint = "原密码"
            inputType = 129 // 密码输入类型
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        
        val etNewPassword = EditText(requireContext()).apply {
            hint = "新密码"
            inputType = 129 // 密码输入类型
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        
        val etConfirmPassword = EditText(requireContext()).apply {
            hint = "确认新密码"
            inputType = 129 // 密码输入类型
            setBackgroundResource(R.drawable.input_rounded_bg)
            setPadding(32, 24, 32, 24)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        
        dialogView.addView(etOldPassword)
        dialogView.addView(etNewPassword)
        dialogView.addView(etConfirmPassword)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("修改密码")
            .setView(dialogView)
            .setPositiveButton("确认修改") { _, _ ->
                val oldPassword = etOldPassword.text.toString().trim()
                val newPassword = etNewPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()
                
                if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newPassword != confirmPassword) {
                    Toast.makeText(context, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newPassword.length < 6) {
                    Toast.makeText(context, "新密码长度不能少于6位", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                changePassword(oldPassword, newPassword)
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }
    
    private fun changePassword(oldPassword: String, newPassword: String) {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val username = prefs.getString("username", "")
        
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "登录状态异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
        }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "his-user-service/account/password/reset"))
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "修改密码失败: ${e.message}")
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "修改密码失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("ProfileFragment", "修改密码API响应: $res")
                
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            try {
                                val obj = JSONObject(res)
                                if (obj.optInt("code") == 0) {
                                    val newToken = obj.optString("token")
                                    // 更新token
                                    prefs.edit().putString("token", newToken).apply()
                                    Toast.makeText(context, "密码修改成功，请重新登录", Toast.LENGTH_SHORT).show()
                                    
                                    // 清除本地数据并跳转到登录页面
                                    prefs.edit().clear().apply()
                                    val intent = Intent(requireContext(), LoginActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(context, obj.optString("message", "修改密码失败"), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("ProfileFragment", "解析修改密码响应失败: ${e.message}")
                                Toast.makeText(context, "修改密码失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun showDebugInfo() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val allPrefs = prefs.all
        
        val debugInfo = StringBuilder()
        debugInfo.append("=== 调试信息 ===\n")
        val token = allPrefs["token"] as? String
        debugInfo.append("Token: ${token?.take(20)}...\n")
        debugInfo.append("用户名: ${allPrefs["username"]}\n")
        debugInfo.append("手机号: ${allPrefs["phone"]}\n")
        debugInfo.append("角色: ${allPrefs["role"]}\n")
        debugInfo.append("邮箱: ${allPrefs["email"]}\n")
        debugInfo.append("头像URL: ${allPrefs["avatarUrl"]}\n")
        debugInfo.append("================\n")
        debugInfo.append("所有存储的键值对:\n")
        allPrefs.forEach { (key, value) ->
            debugInfo.append("$key: $value\n")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("调试信息")
            .setMessage(debugInfo.toString())
            .setPositiveButton("复制到日志") { _, _ ->
                Log.d("ProfileFragment", debugInfo.toString())
                Toast.makeText(context, "调试信息已复制到日志", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // 安全的URL拼接函数，避免多余斜杠
    private fun safeUrlJoin(base: String, path: String): String {
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }
} 