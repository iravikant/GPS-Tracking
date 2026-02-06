package com.gpstracking

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gpstracking.Room.AppDatabase
import com.gpstracking.databinding.ActivitySessionHistoryBinding
import com.gpstracking.ui.adapter.SessionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionHistoryBinding
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadSessions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Tracking History"
        }
    }

    private fun setupRecyclerView() {
        adapter = SessionAdapter { session ->
            val intent = android.content.Intent(this, SessionDetailActivity::class.java).apply {
                putExtra("session_id", session.id)
            }
            startActivity(intent)
        }

        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(this@SessionHistoryActivity)
            adapter = this@SessionHistoryActivity.adapter
        }
    }

    private fun loadSessions() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                AppDatabase.get(this@SessionHistoryActivity)
                    .sessionDao()
                    .getAllSessions()
            }

            binding.progressBar.visibility = View.GONE

            if (sessions.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvSessions.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvSessions.visibility = View.VISIBLE
                adapter.submitList(sessions)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}