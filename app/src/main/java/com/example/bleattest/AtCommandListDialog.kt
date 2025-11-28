package com.example.bleattest

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AtCommandListDialog : DialogFragment() {

    interface OnAtCommandSelectedListener {
        fun onAtCommandSelected(command: String)
    }

    private var listener: OnAtCommandSelectedListener? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnCancel: Button

    // AT 명령어 목록
    private val atCommands = listOf(
        "AT+NAME?",
        "AT+EXIT",
        "AT+MAC?",
        "AT+ROLE=?",
        "AT+ROLE?",
        "AT+ROLE=1",
        "AT+ADV_DATA?"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_at_command_list, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewAtCommands)
        btnCancel = view.findViewById(R.id.btnCancel)

        setupRecyclerView()
        setupButtons()

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = AtCommandAdapter(atCommands) { command ->
            listener?.onAtCommandSelected(command)
            dismiss()
        }
    }

    private fun setupButtons() {
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    fun setOnAtCommandSelectedListener(listener: OnAtCommandSelectedListener) {
        this.listener = listener
    }

    companion object {
        const val TAG = "AtCommandListDialog"

        fun newInstance(): AtCommandListDialog {
            return AtCommandListDialog()
        }
    }
}

// RecyclerView Adapter for AT Commands
class AtCommandAdapter(
    private val commands: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<AtCommandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAtCommand: android.widget.TextView = view.findViewById(R.id.tvAtCommand)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_at_command, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val command = commands[position]
        holder.tvAtCommand.text = command
        holder.itemView.setOnClickListener {
            onItemClick(command)
        }
    }

    override fun getItemCount(): Int = commands.size
}
