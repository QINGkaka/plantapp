package com.example.c1

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HerbHistoryFragment : Fragment() {
    private lateinit var rvHerbHistory: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddHerb: FloatingActionButton
    private lateinit var loadingLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val herbRecords = mutableListOf<HerbRecord>()
    private var token: String? = null
    private var isLoading = false
    private var lastLoadTime = 0L
    private val CACHE_DURATION = 30000L // 30秒缓存
    
    // 安全的URL拼接函数，避免多余斜杠
    private fun safeUrlJoin(base: String, path: String): String {
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }
    
    companion object {
        private var globalLastLoadTime = 0L
        private var globalHerbRecords = mutableListOf<HerbRecord>()
    }

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
        
        // 恢复全局缓存的数据
        if (globalHerbRecords.isNotEmpty()) {
            herbRecords.clear()
            herbRecords.addAll(globalHerbRecords)
            lastLoadTime = globalLastLoadTime
        }
        
        // 检查是否需要重新加载数据
        if (shouldReloadData()) {
            Log.d("HerbHistoryFragment", "需要重新加载数据")
            loadUserGrowthRecordsByToken()
        } else {
            // 使用缓存数据
            Log.d("HerbHistoryFragment", "使用缓存数据，共${herbRecords.size}条记录")
            updateUI()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 页面恢复时，只有在数据为空且需要刷新时才加载
        if (herbRecords.isEmpty() && shouldReloadData()) {
            Log.d("HerbHistoryFragment", "onResume: 数据为空，需要重新加载")
            loadUserGrowthRecordsByToken()
        } else {
            Log.d("HerbHistoryFragment", "onResume: 使用现有数据，共${herbRecords.size}条记录")
        }
    }
    
    private fun shouldReloadData(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastLoadTime
        val shouldReload = herbRecords.isEmpty() || timeDiff > CACHE_DURATION
        
        Log.d("HerbHistoryFragment", "缓存检查: 数据条数=${herbRecords.size}, 时间差=${timeDiff}ms, 需要重新加载=$shouldReload")
        
        return shouldReload
    }

    private fun initializeViews(view: View) {
        rvHerbHistory = view.findViewById(R.id.rvHerbHistory)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        fabAddHerb = view.findViewById(R.id.fabAddHerb)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupRecyclerView() {
        rvHerbHistory.layoutManager = LinearLayoutManager(context)
    }

    private fun setupClickListeners() {
        fabAddHerb.setOnClickListener {
            // 切换到采集页面
            switchToCollectionFragment()
        }
        
        // 设置下拉刷新
        swipeRefreshLayout.setOnRefreshListener {
            // 强制刷新数据
            lastLoadTime = 0L
            loadUserGrowthRecordsByToken()
        }
        
        // 空状态页面的刷新按钮
        view?.findViewById<android.widget.Button>(R.id.btnRefresh)?.setOnClickListener {
            lastLoadTime = 0L
            loadUserGrowthRecordsByToken()
        }
    }

    private fun loadUserGrowthRecordsByToken() {
        if (isLoading) {
            return // 防止重复请求
        }
        
        val prefs = requireContext().getSharedPreferences("user", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        
        this.token = token
        isLoading = true
        showLoadingState()
        
        // 设置网络超时
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url(safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/growth/userToken"))
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isLoading = false
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            hideLoadingState()
                            swipeRefreshLayout.isRefreshing = false
                            Toast.makeText(context, "获取历史记录失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                Log.d("HerbHistoryFragment", "API响应: $res")
                val obj = try { JSONObject(res) } catch (e: Exception) { null }
                val arr = obj?.optJSONArray("herbGrowths")
                herbRecords.clear()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val record = jsonToHerbRecord(item)
                        Log.d("HerbHistoryFragment", "解析记录: 名称=${record.herbName}, 图片=${record.imagePath}")
                        herbRecords.add(record)
                    }
                }
                
                lastLoadTime = System.currentTimeMillis()
                globalLastLoadTime = lastLoadTime
                isLoading = false
                
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            // 保存到全局缓存
                            globalHerbRecords.clear()
                            globalHerbRecords.addAll(herbRecords)
                            
                            hideLoadingState()
                            swipeRefreshLayout.isRefreshing = false
                            updateUI()
                        }
                    }
                }
            }
        })
    }

    private fun jsonToHerbRecord(obj: JSONObject): HerbRecord {
        // 解析recordTime字符串为Date对象
        val recordTimeStr = obj.optString("recordTime")
        val collectionTime = try {
            if (recordTimeStr.isNotEmpty()) {
                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                formatter.parse(recordTimeStr) ?: Date()
            } else {
                Date()
            }
        } catch (e: Exception) {
            Date()
        }
        
        return HerbRecord(
            id = obj.optInt("id").toString(),
            herbName = obj.optString("herbName"),
            locationCount = "-", // API中没有此字段
            temperature = obj.optDouble("temperature").toString(),
            humidity = obj.optDouble("wet").toString(),
            district = "-", // API中没有此字段
            street = "-", // API中没有此字段
            growthDescription = obj.optString("des"),
            longitude = obj.optDouble("longitude"),
            latitude = obj.optDouble("latitude"),
            collectionTime = collectionTime,
            imagePath = obj.optString("imgUrl"),
            status = "已上传",
            batchCode = obj.optString("batchCode")
        )
    }

    private fun showLoadingState() {
        if (isAdded && context != null) {
            loadingLayout.visibility = View.VISIBLE
            rvHerbHistory.visibility = View.GONE
            emptyStateLayout.visibility = View.GONE
        }
    }
    
    private fun hideLoadingState() {
        if (isAdded && context != null) {
            loadingLayout.visibility = View.GONE
        }
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
        // 显示记录详情对话框
        val detailMessage = """
            中药材详情：
            
            名称：${record.herbName}
            批次编码：${record.batchCode}
            温度：${record.temperature}°C
            湿度：${record.humidity}%
            位置：${String.format(Locale.getDefault(), "%.6f, %.6f", record.longitude, record.latitude)}
            描述：${record.growthDescription}
            采集时间：${record.collectionTime}
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("🌿 中药材详情")
            .setMessage(detailMessage)
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ -> confirmDeleteRecord(record) }
            .show()
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
            .url(safeUrlJoin(ApiConfig.BASE_URL, "herb-info-service/growth/$growthId"))
            .addHeader("Authorization", "Bearer $tk")
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                        if (isAdded && context != null) {
                            Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                            loadUserGrowthRecordsByToken()
                        }
                    }
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