package com.mcandle.bledemo;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import vpos.apipackage.At;

public class BleScan {
    private boolean isScanning = false;
    private static final String TAG = "BLEScan";
    private boolean isMaster;

    // ✅ 인터페이스를 BLEScan 클래스 내부에 정의
    public interface ScanResultListener {
        void onScanResult(JSONArray scanData);
    }

    // 콜백 인터페이스 정의
    public interface DataReceiveListener {
        void onDataReceived(String buff);
    }

    private DataReceiveListener dataReceiveListener;

    // 콜백 등록 메서드
    public void setDataReceiveListener(DataReceiveListener listener) {
        this.dataReceiveListener = listener;
    }

    public int enableMasterMode(boolean enable) {
        if (isMaster == enable) {
            Log.d("BLE_MANAGER", "Already in the requested mode. No changes made.");
            return 0; // ✅ 동일한 상태면 변경 없음
        } else {
            int ret = At.Lib_EnableMaster(enable);
            if (!enable) {
                isMaster = true; // false에서 true로 변경하여 이후 호출 방지
            } else {
                isMaster = enable;
            }
            Log.d("BLE_MANAGER", "Master mode updated successfully, Result: " + ret);
            return ret; // ✅ 변경 적용
        }
    }

    public String getDeviceMacAddress() {
        String[] macAddress = new String[1];
        int ret = At.Lib_GetAtMac(macAddress);
        Log.d("BLE_SCAN", "Device MAC Address: " + macAddress[0]);
        return (ret == 0) ? macAddress[0] : null;
    }

    public int startNewScan(String macAddress,
                            String broadcastName,
                            int rssi,
                            String manufacturerId,
                            String data) {
        int ret = At.Lib_AtStartNewScan(macAddress, broadcastName, -rssi, manufacturerId, data);

        Log.e("BLE_SCAN", "BLE Scan Started with result: " + ret);
        return ret;
    }

    public void startScanAsync(SharedPreferences sp, ScanResultListener listener) {
        startScan(sp, listener);
        // recvScanData는 startScan 내부에서 이미 Thread로 실행됨
        // 여기서 다시 호출하면 두 스레드가 동시에 실행되어 문제 발생
    }
    public void startScan(SharedPreferences sp, ScanResultListener listener) {

        if (isScanning) return;
        isScanning = true;

        Log.e(TAG, "Starting BLE scan...");

        int ret = At.Lib_EnableMaster(true);
        if (ret != 0) {
            Log.e(TAG, "Failed to enable master mode" + ret);
            return;
        }
        String mac = getDeviceMacAddress();
        Log.d("MainActivity", "mac: " + mac);

        ret = At.Lib_AtStartNewScan(
                sp.getString("macAddress", ""),
                sp.getString("broadcastName", ""),
                -sp.getInt("rssi", 0),
                sp.getString("manufacturerId", ""),
                sp.getString("data", "")
        );

        if (ret == 0) {
            // 메서드 호출 부분을 반환하여 Kotlin에서 코루틴으로 처리할 수 있게 함
            new Thread(() -> recvScanData(listener)).start();
        } else {
            isScanning = false;
            Log.e(TAG, "Failed to start BLE scan");
        }
    }

    public void stopScan() {
        if (!isScanning) return;
        isScanning = false;
        At.Lib_AtStopScan();
        Log.e(TAG, "BLE scan stopped");
    }

    public void recvScanData(ScanResultListener listener) {
        byte[] recvData =new byte[2048];
        int[] recvDataLen =new int[2];
        String lineLeft="";
        Map<String, JSONObject> deviceMap = new ConcurrentHashMap<>();  // 루프 밖에서 누적 관리


        while (isScanning) {
            int ret = At.Lib_ComRecvAT(recvData, recvDataLen, 20, 1000);
            Log.e("recvScanData", "runLib_ComRecvAT: recvDataLen"+recvDataLen[0] );
            Log.e("recvScanData", "Lib_ComRecvAT recvData: "+bytesToHex(recvData,recvDataLen[0]));
            boolean startProcessing = false;

            // String buff= lineLeft+new String(recvData);
            String buff= lineLeft+new String(recvData, 0, recvDataLen[0]);
            Log.e("recvScanData", "lineLeft: [" + lineLeft + "]");
            Log.e("recvScanData", "buff: [" + buff + "]");
            // String []data=buff.split("\r\n|\r|\n");
            String []data=buff.split("\\r\\n|\\r|\\n", -1); // ����λ�������ַ���
            Log.e("recvScanData", "lineCount: " + data.length + ", lines: " + java.util.Arrays.toString(data));
            int lineCount=data.length;
            // if(lineCount>0)//each time response data left last line ,for maybe data not recv all.
            //     lineLeft = data[lineCount-1];
            // else
            //     lineLeft="";
            // �������һ��δ�������
            lineLeft = (data.length > 0) ? data[data.length-1] : "";
            //for (String line : data)
            for (int i=0;i<lineCount-1;i++)
            {
                String line =data[i];
//                    Log.e("TAG", "debug crash position:echo22" );
                if (line.startsWith("MAC:")) {
                    startProcessing = true;
                    String[] parts = line.split(",",3);
                    if(parts.length < 3) {
                        continue;
                    }

                    String mac = parts[0].split(":",2)[1].trim();
                    String rssi = parts[1].split(":")[1].trim();
                    int irssi =0;
                    try {
                        irssi = Integer.parseInt(rssi); // ��֤ RSSI �Ƿ�Ϊ��Ч����
                    } catch (NumberFormatException e) {
                        Log.e("TAG", "Invalid RSSI value: " + rssi);
                        continue;
                    }

                    String[] payloadArr = parts[2].split(":",2);
                    if (payloadArr.length < 2) continue;;
                    String payload = payloadArr[1].trim();
                    if((payload.length()>62)||(payload.length()%2!=0)) continue;
//                        Log.e("TAG", "debug crash position:echo20" );
                    JSONObject device;
                    if (deviceMap.containsKey(mac)) {
                        device = deviceMap.get(mac);
                    } else {
                        device = new JSONObject();
                        try {
                            device.put("MAC", mac);
                        } catch (JSONException e) {
                            Log.e("TAG", "Handler runLib_ComRecvAT mac 0000: JSONException"+e );
                            //throw new RuntimeException(e);
                            continue;
                        }
                        deviceMap.put(mac, device);
                    }
//                        Log.e("TAG", "debug crash position:echo19" );
                    if (parts[2].startsWith("RSP")) {

                        try {
                            assert device != null;
                            device.put("RSP_org", payload);
                            device.put("RSP", parseAdvertisementData(hexStringToByteArray(payload)));
                        } catch (JSONException e) {
                            Log.e("TAG", "Runnable 444: JSONException"+e );
//                                throw new RuntimeException(e);
                            continue;
                        }

                    } else if (parts[2].startsWith("ADV")) {
                        //device.put("ADV", parsePayload(payload));
                        try {
                            assert device != null;
                            device.put("ADV_org", payload);
                            device.put("ADV", parseAdvertisementData(hexStringToByteArray(payload)));
                        } catch (JSONException e) {
                            Log.e("TAG", "Runnable 333: JSONException"+e );
//                                throw new RuntimeException(e);
                            continue;
                        }
                    }
                    //Log.e("TAG", "debug crash position:echo18" );
                    try {
                        assert device != null;
                        // Log.e("TAG", "debug crash position:echo18"+rssi );
                        device.put("RSSI", irssi);
                    } catch (JSONException e) {
                        Log.e("TAG", "Runnable 222: JSONException"+e.getMessage() );
//                            throw new RuntimeException(e);
                        continue;
                    }
//                        Log.e("TAG", "debug crash position:echo17" );
                    // ���ʱ����ֶ�
                    try {
//                                long curr_time=System.currentTimeMillis();
                        device.put("Timestamp", System.currentTimeMillis());
                    } catch (JSONException e) {
                        //Log.e("TAG", "Runnable 000: JSONException"+e );
//                            throw new RuntimeException(e);
                        continue;
                    }
//                        Log.e("TAG", "debug crash position:echo16" );
                } else if (startProcessing) {
                    // ����Ѿ���ʼ����MAC���ݣ���������MAC��ͷ�����ݣ�������
                    continue;
                }
            }

            // for loop 완료 후 한 번만 전송 (ADV + RSP가 합쳐진 상태)
            Log.e("deviceMap", "deviceMap" + deviceMap );

            synchronized (deviceMap) {
                Map<String, JSONObject> snapshot = new HashMap<>(deviceMap); // 복사본 생성
                JSONArray resultArray = new JSONArray(snapshot.values());
                Log.e("resultArray", "resultArray" + resultArray );
                if (listener != null) {
                    Log.e("listener", "resultArray" + resultArray );
                    listener.onScanResult(resultArray);
                }
            }
        }
    }
    private static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        if(len%2==1)
            len--;
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
    private static String bytesToHex(byte[] bytes,int len) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
    public static JSONObject parseAdvertisementData(byte[] advertisementData) throws JSONException {
//        Map<String, String> parsedData = new HashMap<>();
//        byte[] advertisementData =new byte[advertiseData.length()/2];
        JSONObject parsedData = new JSONObject();
        if (advertisementData == null || advertisementData.length == 0) return parsedData;
        Log.d("parseAdvertisementData", "advertisementData: " + bytesToHex(advertisementData));
        int offset = 0;
        try {
            while (offset < advertisementData.length) {
                int length = advertisementData[offset++] & 0xFF;
                if (length == 0) break;

                int type = advertisementData[offset] & 0xFF;
                offset++;

                byte[] data = new byte[length - 1];
                if (length - 1 > advertisementData.length - offset)//data format issue.
                {
                    return null;
                }
                System.arraycopy(advertisementData, offset, data, 0, length - 1);
                offset += length - 1;

                switch (type) {
                    case 0x01: // Flags
                        parsedData.put("Flags", bytesToHex(data));
                        break;
                    case 0x02: // Incomplete List of 16-bit Service Class UUIDs
                    case 0x03: // Complete List of 16-bit Service Class UUIDs
                        parsedData.put("Service UUIDs", bytesToHex(data));
                        break;
                    case 0x04: // Incomplete List of 32-bit Service Class UUIDs
                    case 0x05: // Complete List of 32-bit Service Class UUIDs
                        parsedData.put("Service UUIDs", bytesToHex(data));
                        break;
                    case 0x06: // Incomplete List of 128-bit Service Class UUIDs
                    case 0x07: // Complete List of 128-bit Service Class UUIDs
                        parsedData.put("Service UUIDs", uuid128LeToString(data));
                        break;
                    case 0x08: // Shortened Local Name
                    case 0x09: // Complete Local Name
                        parsedData.put("Device Name", new String(data));
                        break;
                    case 0x0A: // Complete Local Name
//                    byte [] tx_power=hexStringToByteArray(new String(data));
                        parsedData.put("TX Power Level", data[0]);
                        break;
                    case 0xFF: // Manufacturer Specific Data
                        parsedData.put("Manufacturer Data", bytesToHex(data));
                        break;

                    case 0x16: // Service Data - 16-bit UUID
                        if (data.length >= 2) {
                            String uuid16 = String.format("%04X", ((data[1] & 0xFF) << 8) | (data[0] & 0xFF));
                            byte[] serviceData = Arrays.copyOfRange(data, 2, data.length);
                            addServiceData(parsedData, uuid16, serviceData);
                        }
                        break;
                    case 0x20: // Service Data - 32-bit UUID
                        if (data.length >= 4) {
                            String uuid32 = String.format("%08X",
                                    ((data[3] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[0] & 0xFF));
                            byte[] serviceData = Arrays.copyOfRange(data, 4, data.length);
                            addServiceData(parsedData, uuid32, serviceData);
                        }
                        break;
                    case 0x21: // Service Data - 128-bit UUID
                        if (data.length >= 16) {
                            String uuid128 = bytesToHex(Arrays.copyOfRange(data, 0, 16));
                            byte[] serviceData = Arrays.copyOfRange(data, 16, data.length);
                            addServiceData(parsedData, uuid128, serviceData);
                        }

                    default:
                        parsedData.put("Unknown Data (" + type + ")", bytesToHex(data));
                        break;
                }
            }
        } catch (Exception e) {
            try {
                parsedData.put ("error", "Parse failed: " + e.getMessage());
            } catch ( JSONException ignored) {}
        }

        return parsedData;
    }

    private static void addServiceData(JSONObject parsedData, String uuid, byte[] serviceData) throws JSONException {
        // UUID별 Service Data가 있는 배열인데 하나로만 uuid 아늠
        String key = "Service Data" ;
        JSONArray serviceArray;

        if (parsedData.has(key)) {
            serviceArray = parsedData.getJSONArray(key);
        } else {
            serviceArray = new JSONArray();
            parsedData.put(key, serviceArray);
        }

        serviceArray.put(bytesToHex(serviceData));
    }

    public static String uuid128LeToString(byte[] le) {
        if (le == null || le.length != 16) {
            throw new IllegalArgumentException("UUID byte array must be 16 bytes");
        }
        // LE → BE 변환 (역순)
        byte[] be = new byte[16];
        for (int i = 0; i < 16; i++) {
            be[i] = le[15 - i];
        }
        // ByteBuffer로 읽어서 UUID 생성
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(be);
        long msb = bb.getLong();
        long lsb = bb.getLong();
        return new java.util.UUID(msb, lsb).toString();
    }


}

