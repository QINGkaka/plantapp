package com.example.c1

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide

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

        // 加载用户信息
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val avatarUrl = prefs.getString("avatarUrl", null)
        val username = prefs.getString("username", "-")
        val phone = prefs.getString("phone", "-")
        val role = prefs.getString("role", "-")

        if (!avatarUrl.isNullOrBlank()) {
            Glide.with(this).load(avatarUrl).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
        }
        tvUsername.text = "用户名：$username"
        tvPhone.text = "手机号：$phone"
        tvRole.text = "角色：$role"

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
} 