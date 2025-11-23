package com.example.bleattest.models

data class ScanParams(
    val macAddress: String = "",      // 필터링할 MAC (선택)
    val broadcastName: String = "",   // 필터링할 이름 (선택)
    val minRssi: Int = -100,          // 최소 RSSI (선택)
    val manufacturerId: String = "",  // 제조사 ID (선택)
    val data: String = ""             // 필터링할 데이터 (선택)
) {
    fun toAtCommand(): String {
        // AT+STARTNEWSCAN=<MAC>,<Name>,<RSSI>,<MfgID>,<Data>\r\n
        // RSSI는 양수로 전송 (예: -80 → 80)
        val rssiValue = if (minRssi < 0) -minRssi else minRssi
        return "AT+STARTNEWSCAN=$macAddress,$broadcastName,$rssiValue,$manufacturerId,$data"
    }
}
