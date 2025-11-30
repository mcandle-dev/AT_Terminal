package com.example.bleattest.models

/**
 * AT+OBSERVER 명령 파라미터
 *
 * 매뉴얼 규격: AT+OBSERVER=<Enable>,<ScanTime>,<Param3>,<Param4>,<RSSI>,<Param6>,<NameFilter>
 * 예제: AT+OBSERVER=1,20,,,-60,,020106
 *
 * @param scanTime 스캔 시간 (초), 기본값 20
 * @param minRssi 최소 RSSI 임계값 (dBm), 이 값 이상인 장치만 스캔됨 (예: -60)
 * @param nameFilter 브로드캐스트 네임 필터 (HEX 형식)
 *                   예: "020106" 또는 "5246" (ASCII "RF"의 HEX)
 */
data class ScanParams(
    val scanTime: Int = 20,           // 스캔 시간 (초)
    val minRssi: Int = -100,          // 최소 RSSI (dBm) - 음수 그대로 사용
    val nameFilter: String = ""       // 브로드캐스트 네임 필터 (HEX)
) {
    fun toAtCommand(): String {
        // AT+OBSERVER=<Enable>,<ScanTime>,<Param3>,<Param4>,<RSSI>,<Param6>,<NameFilter>
        // 예: AT+OBSERVER=1,20,,,-60,,020106
        return "AT+OBSERVER=1,$scanTime,,,$minRssi,,$nameFilter"
    }
}
