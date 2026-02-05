package com.swapmap.zwap.demo.community

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.repository.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.tasks.await

class ContributionFragment : Fragment(R.layout.fragment_contribution) {

    private lateinit var adapter: ContributionAdapter
    private val repository = ReportRepository()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvContributions = view.findViewById<RecyclerView>(R.id.rv_contributions)
        val spinner = view.findViewById<android.widget.ProgressBar>(R.id.loading_spinner)
        val emptyText = view.findViewById<android.widget.TextView>(R.id.tv_empty_state)

        rvContributions.layoutManager = LinearLayoutManager(context)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Fetch Data
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reports = repository.getUserReports(userId)
                    
                    withContext(Dispatchers.Main) {
                        spinner.visibility = View.GONE
                        if (reports.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                            rvContributions.visibility = View.GONE
                        } else {
                            adapter = ContributionAdapter(reports)
                            rvContributions.adapter = adapter
                            rvContributions.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        spinner.visibility = View.GONE
                    }
                }
            }
        } else {
            spinner.visibility = View.GONE
            emptyText.text = "Please log in to view contributions."
            emptyText.visibility = View.VISIBLE
        }
    }
}
