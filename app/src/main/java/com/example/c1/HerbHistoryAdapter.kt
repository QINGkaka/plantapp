package com.example.c1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HerbHistoryAdapter(
    private val herbRecords: List<HerbRecord>,
    private val onItemClick: (HerbRecord) -> Unit,
    private val onDeleteClick: (HerbRecord) -> Unit,
    private val onTraceBatchClick: (HerbRecord) -> Unit
) : RecyclerView.Adapter<HerbHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivHerbImage: ImageView = view.findViewById(R.id.ivHerbImage)
        val tvHerbName: TextView = view.findViewById(R.id.tvHerbName)
        val tvCollectionTime: TextView = view.findViewById(R.id.tvCollectionTime)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
        val btnTraceBatch: Button = view.findViewById(R.id.btnTraceBatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_herb_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = herbRecords[position]
        
        holder.tvHerbName.text = record.herbName
        holder.tvCollectionTime.text = "采集时间：${formatDate(record.collectionTime)}"
        holder.tvLocation.text = "位置：${String.format(Locale.getDefault(), "%.4f, %.4f", record.longitude, record.latitude)}"
        holder.tvStatus.text = record.status
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(record)
        }
        holder.btnDelete.setOnClickListener {
            onDeleteClick(record)
        }
        holder.btnTraceBatch.setOnClickListener {
            onTraceBatchClick(record)
        }
    }

    override fun getItemCount() = herbRecords.size

    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
} 