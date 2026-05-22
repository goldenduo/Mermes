package com.mermes

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mermes.common.log.MermesLog as Log
import com.mermes.manager.MermesAgentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupCardListeners()
        Log.i("MermesMain", "Mermes Agent control dashboard successfully initialized and active.")
    }

    private fun setupCardListeners() {
        findViewById<CardView>(R.id.cardChat).setOnClickListener { openChatConsole() }
        findViewById<CardView>(R.id.cardMemory).setOnClickListener { openMemoryManager() }
        findViewById<CardView>(R.id.cardSoul).setOnClickListener { openSoulSettings() }
        findViewById<CardView>(R.id.cardHistory).setOnClickListener { openHistoryAudit() }
        findViewById<CardView>(R.id.cardKanban).setOnClickListener { openKanbanControl() }
        findViewById<CardView>(R.id.cardSkills).setOnClickListener { openSkillsExplorer() }
    }

    /**
     * 1. 智能对话与 Tool 进度面板 (SSE simulation based on core)
     */
    private fun openChatConsole() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_chat, null)
        bottomSheet.setContentView(view)

        val chatLog = view.findViewById<TextView>(R.id.chatLog)
        val editInput = view.findViewById<EditText>(R.id.editInput)
        val btnSend = view.findViewById<Button>(R.id.btnSend)

        btnSend.setOnClickListener {
            val prompt = editInput.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener

            editInput.setText("")
            val userRole = getString(R.string.chat_role_user)
            chatLog.append("\n$userRole: $prompt\n")

            // Simulate stream chat with active Tool calling micro progress callback
            lifecycleScope.launch {
                val mermesRole = getString(R.string.chat_role_mermes)
                chatLog.append("\n$mermesRole: ${getString(R.string.chat_connecting)}\n")
                delay(800)
                
                // Simulate SSE Tool callback events (event: hermes.tool.progress)
                val toolRunningStr = getString(R.string.chat_tool_running, getString(R.string.chat_tool_inspecting_repo))
                chatLog.append("   [🔧 hermes.tool.progress: $toolRunningStr]\n")
                delay(1200)
                val toolRunningStr2 = getString(R.string.chat_tool_running, getString(R.string.chat_tool_inspecting_python))
                chatLog.append("   [🔧 hermes.tool.progress: $toolRunningStr2]\n")
                delay(1200)

                chatLog.append("💡 Mermes: ${getString(R.string.chat_env_healthy)}\n")
            }
        }

        bottomSheet.show()
    }

    /**
     * 2. 长期记忆卡片编辑器 (MEMORY.md)
     */
    private fun openMemoryManager() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_memory, null)
        bottomSheet.setContentView(view)

        val memoryContainer = view.findViewById<LinearLayout>(R.id.memoryContainer)
        val btnAddMemory = view.findViewById<Button>(R.id.btnAddMemory)

        // Load memories asynchronously
        lifecycleScope.launch {
            val memories = MermesAgentManager.getMemories(this@MainActivity).toMutableList()
            withContext(Dispatchers.Main) {
                memoryContainer.removeAllViews()
                if (memories.isEmpty()) {
                    val emptyText = TextView(this@MainActivity).apply {
                        text = getString(R.string.memory_empty)
                        setTextColor(0xFF8F93A3.toInt())
                        textSize = 14f
                    }
                    memoryContainer.addView(emptyText)
                } else {
                    for (index in memories.indices) {
                        val card = TextView(this@MainActivity).apply {
                            text = getString(R.string.memory_item_format, index + 1, memories[index])
                            setTextColor(0xFFFFFFFF.toInt())
                            setBackgroundColor(0xFF1E202B.toInt())
                            setPadding(24, 24, 24, 24)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                              ).apply {
                                setMargins(0, 0, 0, 16)
                            }
                        }
                        memoryContainer.addView(card)
                    }
                }
            }

            btnAddMemory.setOnClickListener {
                val inputEdit = EditText(this@MainActivity).apply {
                    hint = getString(R.string.memory_input_hint)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.memory_add_title))
                    .setView(inputEdit)
                    .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                        val newMem = inputEdit.text.toString().trim()
                        if (newMem.isNotEmpty()) {
                            memories.add(newMem)
                            lifecycleScope.launch {
                                val success = MermesAgentManager.saveMemories(this@MainActivity, memories)
                                if (success) {
                                    Toast.makeText(this@MainActivity, getString(R.string.memory_save_success), Toast.LENGTH_SHORT).show()
                                    openMemoryManager() // reload
                                }
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        }

        bottomSheet.show()
    }

    /**
     * 3. 灵魂性格设定 (SOUL.md)
     */
    private fun openSoulSettings() {
        lifecycleScope.launch {
            val currentSoul = MermesAgentManager.getSoulPrompt(this@MainActivity)
            withContext(Dispatchers.Main) {
                val inputEdit = EditText(this@MainActivity).apply {
                    setText(if (currentSoul.isEmpty()) "You are Hermes, a helpful AI assistant." else currentSoul)
                    minLines = 4
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.soul_edit_title))
                    .setView(inputEdit)
                    .setPositiveButton(getString(R.string.btn_apply)) { _, _ ->
                        lifecycleScope.launch {
                            val success = MermesAgentManager.saveSoulPrompt(this@MainActivity, inputEdit.text.toString())
                            if (success) {
                                Toast.makeText(this@MainActivity, getString(R.string.soul_save_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.btn_restore_default)) { _, _ ->
                        lifecycleScope.launch {
                            MermesAgentManager.saveSoulPrompt(this@MainActivity, "You are Hermes, a helpful AI assistant.")
                            Toast.makeText(this@MainActivity, getString(R.string.soul_restore_default_success), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        }
    }

    /**
     * 4. 历史会话 SQLite 全文检索
     */
    private fun openHistoryAudit() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_history, null)
        bottomSheet.setContentView(view)

        val historyContainer = view.findViewById<LinearLayout>(R.id.historyContainer)
        val editSearch = view.findViewById<EditText>(R.id.editSearch)
        val btnSearch = view.findViewById<Button>(R.id.btnSearch)

        val loadSessions = { query: String? ->
            lifecycleScope.launch {
                val list = if (query.isNullOrEmpty()) {
                    MermesAgentManager.getSessions(this@MainActivity)
                } else {
                    MermesAgentManager.searchSessions(this@MainActivity, query).map {
                        MermesAgentManager.Session(it.session_id, "search", it.started_at, it.message_count, null, it.title)
                    }
                }
                withContext(Dispatchers.Main) {
                    historyContainer.removeAllViews()
                    if (list.isEmpty()) {
                        val text = TextView(this@MainActivity).apply {
                            text = getString(R.string.history_empty)
                            setTextColor(0xFF8F93A3.toInt())
                        }
                        historyContainer.addView(text)
                    } else {
                        for (s in list) {
                            val tv = TextView(this@MainActivity).apply {
                                text = getString(R.string.history_item_format, s.title ?: s.id, s.started_at.toString(), s.message_count)
                                setTextColor(0xFFFFFFFF.toInt())
                                setPadding(0, 12, 0, 24)
                            }
                            historyContainer.addView(tv)
                        }
                    }
                }
            }
        }

        btnSearch.setOnClickListener {
            loadSessions(editSearch.text.toString().trim())
        }

        loadSessions(null)
        bottomSheet.show()
    }

    /**
     * 5. 任务看板列表与控制
     */
    private fun openKanbanControl() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_kanban, null)
        bottomSheet.setContentView(view)

        val textLog = view.findViewById<TextView>(R.id.textLog)
        val btnDispatch = view.findViewById<Button>(R.id.btnDispatch)

        lifecycleScope.launch {
            val kanbans = MermesAgentManager.getKanbans(this@MainActivity)
            withContext(Dispatchers.Main) {
                textLog.text = if (kanbans.isEmpty()) {
                    getString(R.string.kanban_empty_logs)
                } else {
                    getString(R.string.kanban_data_logs, kanbans.toString())
                }
            }
        }

        btnDispatch.setOnClickListener {
            lifecycleScope.launch {
                val res = MermesAgentManager.dispatchKanban(this@MainActivity)
                withContext(Dispatchers.Main) {
                    textLog.text = getString(R.string.kanban_dispatch_logs, res)
                }
            }
        }

        bottomSheet.show()
    }

    /**
     * 6. 技能插件在线装卸
     */
    private fun openSkillsExplorer() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_skills, null)
        bottomSheet.setContentView(view)

        val skillsContainer = view.findViewById<LinearLayout>(R.id.skillsContainer)

        lifecycleScope.launch {
            val skills = MermesAgentManager.scanSkills(this@MainActivity)
            withContext(Dispatchers.Main) {
                skillsContainer.removeAllViews()
                if (skills.isEmpty()) {
                    val defaultSkills = listOf("python-interpreter", "web-search")
                    for (s in defaultSkills) {
                        val desc = when (s) {
                            "python-interpreter" -> getString(R.string.skill_python_desc)
                            "web-search" -> getString(R.string.skill_web_desc)
                            else -> ""
                        }
                        val tv = TextView(this@MainActivity).apply {
                            text = getString(R.string.skill_item_format, s, desc)
                            setTextColor(0xFFFFFFFF.toInt())
                            setPadding(0, 12, 0, 24)
                        }
                        skillsContainer.addView(tv)
                    }
                } else {
                    for (s in skills) {
                        val tv = TextView(this@MainActivity).apply {
                            text = getString(R.string.skill_item_format, s.name, s.description)
                            setTextColor(0xFFFFFFFF.toInt())
                            setPadding(0, 12, 0, 24)
                        }
                        skillsContainer.addView(tv)
                    }
                }
            }
        }

        bottomSheet.show()
    }
}
