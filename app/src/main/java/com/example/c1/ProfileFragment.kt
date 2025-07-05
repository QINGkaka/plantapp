package com.example.c1

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {
    private lateinit var ivAvatar: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvRole: TextView
    private lateinit var btnLogout: Button

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

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("退出") { _, _ ->
                    val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
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

        if (!avatarUrl.isNullOrBlank()) {
            Glide.with(this).load(avatarUrl).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
        }
        tvUsername.text = "用户名：$username"
        tvPhone.text = "手机号：$phone"
        tvRole.text = "角色：$role"
    }

    private fun fetchUserInfoFromAPI() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        
        if (token.isNullOrBlank()) {
            Log.e("ProfileFragment", "Token为空，无法获取用户信息")
            return
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "his-user-service/account/info/me")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "获取用户信息失败: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "获取用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

                            // 更新本地存储
                            val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("username", username)
                                .putString("phone", phone)
                                .putString("role", role)
                                .putString("avatarUrl", avatarUrl)
                                .putString("email", email)
                                .apply()

                            // 更新UI
                            requireActivity().runOnUiThread {
                                tvUsername.text = "用户名：$username"
                                tvPhone.text = "手机号：$phone"
                                tvRole.text = "角色：$role"
                                
                                if (!avatarUrl.isNullOrBlank()) {
                                    Glide.with(this@ProfileFragment).load(avatarUrl).circleCrop().into(ivAvatar)
                                } else {
                                    ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
                                }
                                
                                Toast.makeText(context, "用户信息已更新", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("ProfileFragment", "API返回的用户信息为空")
                        }
                    } else {
                        Log.e("ProfileFragment", "API返回错误: ${obj.optString("message")}")
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, "获取用户信息失败: ${obj.optString("message")}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "解析API响应失败: ${e.message}")
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "解析用户信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
} 