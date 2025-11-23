package com.example.bleattest

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bleattest.models.LogType
import com.example.bleattest.models.TerminalLog

class TerminalAdapter : RecyclerView.Adapter<TerminalAdapter.LogViewHolder>() {

    private val logs = mutableListOf<TerminalLog>()
    private val maxLogCount = 1000 // 최대 로그 개수

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvDirection: TextView = view.findViewById(R.id.tvDirection)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_terminal_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]

        holder.tvTimestamp.text = "[${log.formattedTime}]"

        // 방향 표시 및 색상 설정
        when (log.type) {
            LogType.SEND -> {
                holder.tvDirection.text = ">"
                holder.tvContent.setTextColor(Color.parseColor("#2196F3")) // 파란색
                holder.tvDirection.setTextColor(Color.parseColor("#2196F3"))
            }
            LogType.RECEIVE -> {
                holder.tvDirection.text = "<"
                holder.tvContent.setTextColor(Color.parseColor("#4CAF50")) // 초록색
                holder.tvDirection.setTextColor(Color.parseColor("#4CAF50"))
            }
            LogType.SCAN -> {
                holder.tvDirection.text = "<"
                holder.tvContent.setTextColor(Color.parseColor("#FFC107")) // 노란색
                holder.tvDirection.setTextColor(Color.parseColor("#FFC107"))
            }
            LogType.ERROR -> {
                holder.tvDirection.text = "!"
                holder.tvContent.setTextColor(Color.parseColor("#F44336")) // 빨간색
                holder.tvDirection.setTextColor(Color.parseColor("#F44336"))
            }
            LogType.INFO -> {
                holder.tvDirection.text = "ℹ"
                holder.tvContent.setTextColor(Color.parseColor("#9E9E9E")) // 회색
                holder.tvDirection.setTextColor(Color.parseColor("#9E9E9E"))
            }
        }

        holder.tvContent.text = log.content
    }

    override fun getItemCount(): Int = logs.size

    /**
     * 로그 추가
     */
    fun addLog(log: TerminalLog) {
        // 최대 개수 초과 시 오래된 로그 제거
        if (logs.size >= maxLogCount) {
            logs.removeAt(0)
            notifyItemRemoved(0)
        }

        logs.add(log)
        notifyItemInserted(logs.size - 1)
    }

    /**
     * 모든 로그 삭제
     */
    fun clearLogs() {
        val size = logs.size
        logs.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * 로그 개수 반환
     */
    fun getLogCount(): Int = logs.size
}
