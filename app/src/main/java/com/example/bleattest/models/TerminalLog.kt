package com.example.bleattest.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TerminalLog(
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val content: String,
    val formattedTime: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(timestamp))
)

enum class LogType {
    SEND,      // 송신 (파란색)
    RECEIVE,   // 수신 (초록색)
    SCAN,      // 스캔 결과 (노란색)
    ERROR,     // 에러 (빨간색)
    INFO       // 정보 (회색)
}
