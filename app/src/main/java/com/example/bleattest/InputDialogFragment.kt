package com.example.bleattest

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.bleattest.models.ScanParams

class InputDialogFragment : DialogFragment() {

    enum class CommandType {
        ENABLE_MASTER,
        SCAN,
        CONNECT,
        SEND_DATA
    }

    private var commandType: CommandType = CommandType.ENABLE_MASTER
    private var listener: OnInputListener? = null

    interface OnInputListener {
        fun onEnableMaster(enable: Boolean)
        fun onStartScan(params: ScanParams)
        fun onConnect(macAddress: String)
        fun onSendData(handle: Int, hexData: String)
    }

    companion object {
        private const val ARG_COMMAND_TYPE = "command_type"

        fun newInstance(type: CommandType): InputDialogFragment {
            val fragment = InputDialogFragment()
            val args = Bundle()
            args.putString(ARG_COMMAND_TYPE, type.name)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? OnInputListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        commandType = arguments?.getString(ARG_COMMAND_TYPE)?.let {
            CommandType.valueOf(it)
        } ?: CommandType.ENABLE_MASTER

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_input, null)

        setupDialogView(dialogView)

        return AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
    }

    private fun setupDialogView(view: View) {
        val btnExecute = view.findViewById<Button>(R.id.btnExecute)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        // SharedPreferences for saving previous values
        val sharedPrefs = requireContext().getSharedPreferences("input_prefs", Context.MODE_PRIVATE)

        when (commandType) {
            CommandType.ENABLE_MASTER -> setupEnableMasterDialog(view, btnExecute, sharedPrefs)
            CommandType.SCAN -> setupScanDialog(view, btnExecute, sharedPrefs)
            CommandType.CONNECT -> setupConnectDialog(view, btnExecute, sharedPrefs)
            CommandType.SEND_DATA -> setupSendDataDialog(view, btnExecute, sharedPrefs)
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setupEnableMasterDialog(view: View, btnExecute: Button, sharedPrefs: android.content.SharedPreferences) {
        val rgEnableMaster = view.findViewById<RadioGroup>(R.id.rgEnableMaster)
        val rbEnable = view.findViewById<RadioButton>(R.id.rbEnable)
        val rbDisable = view.findViewById<RadioButton>(R.id.rbDisable)

        // 이전 값 로드
        val lastEnabled = sharedPrefs.getBoolean("enable_master", true)
        if (lastEnabled) rbEnable.isChecked = true else rbDisable.isChecked = true

        // Hide other fields
        view.findViewById<View>(R.id.layoutScanParams).visibility = View.GONE
        view.findViewById<View>(R.id.layoutConnect).visibility = View.GONE
        view.findViewById<View>(R.id.layoutSendData).visibility = View.GONE

        btnExecute.setOnClickListener {
            val enable = rgEnableMaster.checkedRadioButtonId == R.id.rbEnable
            sharedPrefs.edit().putBoolean("enable_master", enable).apply()
            listener?.onEnableMaster(enable)
            dismiss()
        }
    }

    private fun setupScanDialog(view: View, btnExecute: Button, sharedPrefs: android.content.SharedPreferences) {
        val etMacAddress = view.findViewById<EditText>(R.id.etMacAddress)
        val etBroadcastName = view.findViewById<EditText>(R.id.etBroadcastName)
        val etMinRssi = view.findViewById<EditText>(R.id.etMinRssi)
        val etManufacturerId = view.findViewById<EditText>(R.id.etManufacturerId)
        val etData = view.findViewById<EditText>(R.id.etData)

        // 이전 값 로드
        etMacAddress.setText(sharedPrefs.getString("scan_mac", ""))
        etBroadcastName.setText(sharedPrefs.getString("scan_name", ""))
        etMinRssi.setText(sharedPrefs.getString("scan_rssi", "-80"))
        etManufacturerId.setText(sharedPrefs.getString("scan_mfg_id", ""))
        etData.setText(sharedPrefs.getString("scan_data", ""))

        // Hide other fields
        view.findViewById<View>(R.id.rgEnableMaster).visibility = View.GONE
        view.findViewById<View>(R.id.layoutConnect).visibility = View.GONE
        view.findViewById<View>(R.id.layoutSendData).visibility = View.GONE

        btnExecute.text = "Start Scan"

        btnExecute.setOnClickListener {
            val macAddress = etMacAddress.text.toString().trim()
            val broadcastName = etBroadcastName.text.toString().trim()
            val minRssiStr = etMinRssi.text.toString().trim()
            val manufacturerId = etManufacturerId.text.toString().trim()
            val data = etData.text.toString().trim()

            // Validate MAC address format if provided
            if (macAddress.isNotEmpty() && !macAddress.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
                Toast.makeText(requireContext(), "Invalid MAC address format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val minRssi = minRssiStr.toIntOrNull() ?: -100

            // 값 저장
            sharedPrefs.edit().apply {
                putString("scan_mac", macAddress)
                putString("scan_name", broadcastName)
                putString("scan_rssi", minRssiStr)
                putString("scan_mfg_id", manufacturerId)
                putString("scan_data", data)
            }.apply()

            val params = ScanParams(
                scanTime = 20,  // Default scan time
                minRssi = minRssi,
                nameFilter = data  // Use data field as HEX name filter
            )

            listener?.onStartScan(params)
            dismiss()
        }
    }

    private fun setupConnectDialog(view: View, btnExecute: Button, sharedPrefs: android.content.SharedPreferences) {
        val etConnectMac = view.findViewById<EditText>(R.id.etConnectMac)

        // 이전 값 로드
        etConnectMac.setText(sharedPrefs.getString("connect_mac", ""))

        // Hide other fields
        view.findViewById<View>(R.id.rgEnableMaster).visibility = View.GONE
        view.findViewById<View>(R.id.layoutScanParams).visibility = View.GONE
        view.findViewById<View>(R.id.layoutSendData).visibility = View.GONE

        btnExecute.text = "Connect"

        btnExecute.setOnClickListener {
            val macAddress = etConnectMac.text.toString().trim()

            // Validate MAC address
            if (!macAddress.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
                Toast.makeText(requireContext(), "Invalid MAC address format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sharedPrefs.edit().putString("connect_mac", macAddress).apply()
            listener?.onConnect(macAddress)
            dismiss()
        }
    }

    private fun setupSendDataDialog(view: View, btnExecute: Button, sharedPrefs: android.content.SharedPreferences) {
        val etHandle = view.findViewById<EditText>(R.id.etHandle)
        val etHexData = view.findViewById<EditText>(R.id.etHexData)

        // 이전 값 로드
        etHandle.setText(sharedPrefs.getString("send_handle", "1"))
        etHexData.setText(sharedPrefs.getString("send_data", ""))

        // Hide other fields
        view.findViewById<View>(R.id.rgEnableMaster).visibility = View.GONE
        view.findViewById<View>(R.id.layoutScanParams).visibility = View.GONE
        view.findViewById<View>(R.id.layoutConnect).visibility = View.GONE

        btnExecute.text = "Send"

        btnExecute.setOnClickListener {
            val handleStr = etHandle.text.toString().trim()
            val hexData = etHexData.text.toString().trim()

            val handle = handleStr.toIntOrNull()
            if (handle == null || handle < 0) {
                Toast.makeText(requireContext(), "Invalid handle", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate hex data
            if (!hexData.matches(Regex("^[0-9A-Fa-f]+$"))) {
                Toast.makeText(requireContext(), "Invalid hex data format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sharedPrefs.edit().apply {
                putString("send_handle", handleStr)
                putString("send_data", hexData)
            }.apply()

            listener?.onSendData(handle, hexData)
            dismiss()
        }
    }
}
