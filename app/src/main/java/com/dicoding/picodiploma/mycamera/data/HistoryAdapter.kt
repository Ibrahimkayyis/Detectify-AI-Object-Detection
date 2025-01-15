package com.dicoding.picodiploma.mycamera.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dicoding.picodiploma.mycamera.R

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val detectionList = mutableListOf<DetectionEntity>()
    private var onItemLongClick: ((DetectionEntity) -> Unit)? = null

    fun setOnItemLongClickListener(listener: (DetectionEntity) -> Unit) {
        onItemLongClick = listener
    }

    fun setData(newData: List<DetectionEntity>) {
        detectionList.clear()
        detectionList.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val detection = detectionList[position]
        holder.bind(detection)

        // Tambahkan long click
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(detection)
            true
        }
    }

    override fun getItemCount(): Int = detectionList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val labelTextView: TextView = itemView.findViewById(R.id.labelTextView)
        private val scoreTextView: TextView = itemView.findViewById(R.id.scoreTextView)

        fun bind(detection: DetectionEntity) {
            labelTextView.text = detection.label
            scoreTextView.text = "Confidence: ${(detection.score * 100).toInt()}%"
            Glide.with(itemView.context)
                .load(detection.imageUri)
                .into(imageView)
        }
    }
}
