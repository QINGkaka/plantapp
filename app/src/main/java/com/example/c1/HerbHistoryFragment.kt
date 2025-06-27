package com.example.c1

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
import java.util.*

class HerbHistoryFragment : Fragment() {
    private lateinit var rvHerbHistory: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddHerb: FloatingActionButton
    
    // 模拟数据，实际应用中应该从数据库或网络获取
    private val herbRecords = mutableListOf<HerbRecord>()

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
        loadHerbHistory()
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

    private fun loadHerbHistory() {
        // 这里应该从数据库或网络加载数据
        // 目前使用模拟数据
        loadMockData()
        updateUI()
    }

    private fun loadMockData() {
        // 模拟一些历史记录数据
        herbRecords.clear()
        
        // 添加一些示例数据
        herbRecords.add(
            HerbRecord(
                id = "1",
                herbName = "人参",
                locationCount = "5",
                temperature = "22.5",
                humidity = "75.0",
                district = "吉林省",
                street = "长白山区",
                growthDescription = "野生人参，生长在海拔1500米的山林中",
                longitude = 128.123456,
                latitude = 42.123456,
                collectionTime = Date(),
                imagePath = "/path/to/image1.jpg"
            )
        )
        
        herbRecords.add(
            HerbRecord(
                id = "2",
                herbName = "当归",
                locationCount = "3",
                temperature = "18.0",
                humidity = "65.0",
                district = "甘肃省",
                street = "岷县",
                growthDescription = "栽培当归，生长良好",
                longitude = 104.123456,
                latitude = 34.123456,
                collectionTime = Date(System.currentTimeMillis() - 86400000), // 昨天
                imagePath = "/path/to/image2.jpg"
            )
        )
    }

    private fun updateUI() {
        if (herbRecords.isEmpty()) {
            rvHerbHistory.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            rvHerbHistory.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            
            val adapter = HerbHistoryAdapter(herbRecords) { record ->
                showRecordDetail(record)
            }
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