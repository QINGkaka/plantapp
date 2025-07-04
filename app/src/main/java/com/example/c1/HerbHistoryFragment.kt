package com.example.c1

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HerbHistoryFragment : Fragment() {
    private lateinit var rvHerbHistory: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddHerb: FloatingActionButton

    private val herbRecords = mutableListOf<HerbRecord>()
    private var token: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_herb_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadUserGrowthRecordsByToken()
    }

    private fun initializeViews(view: View) {
        rvHerbHistory = view.findViewById(R.id.rvHerbHistory)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        fabAddHerb = view.findViewById(R.id.fabAddHerb)
    }

    private fun setupRecyclerView() {
        rvHerbHistory.layoutManager = LinearLayoutManager(context)
    }

    private fun setupClickListeners() {
        fabAddHerb.setOnClickListener {
            // 切换到采集页面
            switchToCollectionFragment()
        }
    }

    private fun loadUserGrowthRecordsByToken() {
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        this.token = token
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "herb-info-service/growth/userToken")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "获取历史记录失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                val obj = try { JSONObject(res) } catch (e: Exception) { null }
                val arr = obj?.optJSONArray("herbGrowths")
                herbRecords.clear()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        herbRecords.add(jsonToHerbRecord(item))
                    }
                }
                requireActivity().runOnUiThread { updateUI() }
            }
        })
    }

    private fun jsonToHerbRecord(obj: JSONObject): HerbRecord {
        return HerbRecord(
            id = obj.optInt("id").toString(),
            herbName = obj.optString("herbName"),
            locationCount = "-", // 可根据实际API补充
            temperature = obj.optDouble("temperature").toString(),
            humidity = obj.optDouble("wet").toString(),
            district = "-", // 可根据实际API补充
            street = "-", // 可根据实际API补充
            growthDescription = obj.optString("des"),
            longitude = obj.optDouble("longitude"),
            latitude = obj.optDouble("latitude"),
            collectionTime = Date(), // 可解析recordTime
            imagePath = obj.optString("imgUrl"),
            status = "已上传",
            batchCode = obj.optString("batchCode")
        )
    }

    private fun updateUI() {
        if (herbRecords.isEmpty()) {
            rvHerbHistory.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            rvHerbHistory.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            val adapter = HerbHistoryAdapter(herbRecords,
                { record -> showRecordDetail(record) },
                { record -> confirmDeleteRecord(record) }
            )
            rvHerbHistory.adapter = adapter
        }
    }

    private fun showRecordDetail(record: HerbRecord) {
        // 显示记录详情，这里可以弹出一个对话框或跳转到详情页面
        val detailMessage = """
            中药材详情：
            
            名称：${record.herbName}
            数量：${record.locationCount}株
            温度：${record.temperature}°C
            湿度：${record.humidity}%
            位置：${String.format(Locale.getDefault(), "%.6f, %.6f", record.longitude, record.latitude)}
            行政区：${record.district}
            街道：${record.street}
            描述：${record.growthDescription}
            采集时间：${record.collectionTime}
        """.trimIndent()
        
        Toast.makeText(context, detailMessage, Toast.LENGTH_LONG).show()
    }

    private fun confirmDeleteRecord(record: HerbRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除该生长记录吗？")
            .setPositiveButton("删除") { _, _ -> deleteGrowthRecord(record.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteGrowthRecord(growthId: String) {
        val tk = token ?: return
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "herb-info-service/growth/$growthId")
            .addHeader("Authorization", "Bearer $tk")
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                    loadUserGrowthRecordsByToken()
                }
            }
        })
    }

    private fun switchToCollectionFragment() {
        // 切换到采集页面
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HerbCollectionFragment())
            .addToBackStack(null)
            .commit()
    }

    // 添加新记录的方法，供其他Fragment调用
    fun addNewRecord(record: HerbRecord) {
        herbRecords.add(0, record) // 添加到列表开头
        updateUI()
    }
} 