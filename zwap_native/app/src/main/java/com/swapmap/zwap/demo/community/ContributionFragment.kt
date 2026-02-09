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

import androidx.lifecycle.lifecycleScope
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.db.PendingReport
import com.google.firebase.Timestamp

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
            // Observe pending reports from Room DB (real-time updates)
            val dao = AppDatabase.getDatabase(requireContext()).pendingReportDao()
            
            lifecycleScope.launch {
                dao.getAllPendingReports().collect { pendingReports ->
                    // Fetch completed reports from Firestore
                    val firestoreReports = try {
                        repository.getUserReports(userId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                    
                    // Convert PendingReport to Report for adapter
                    val pendingAsReports = pendingReports.map { pending ->
                        com.swapmap.zwap.demo.model.Report(
                            id = pending.id,
                            userId = pending.userId,
                            incidentType = pending.incidentType,
                            description = pending.description,
                            latitude = pending.latitude,
                            longitude = pending.longitude,
                            imageUrl = pending.imageUri,
                            status = pending.status, // "Pending", "Uploading", "Failed"
                            createdAt = Timestamp(pending.createdAt / 1000, 0)
                        )
                    }
                    
                    // Merge: Pending items first, then Firestore items
                    val allReports = pendingAsReports + firestoreReports
                    
                    withContext(Dispatchers.Main) {
                        spinner.visibility = View.GONE
                        if (allReports.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                            rvContributions.visibility = View.GONE
                        } else {
                            adapter = ContributionAdapter(allReports)
                            rvContributions.adapter = adapter
                            rvContributions.visibility = View.VISIBLE
                            emptyText.visibility = View.GONE
                        }
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
