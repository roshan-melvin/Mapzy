package com.swapmap.zwap.demo.navigation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.config.AppConfig
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.db.DriverTask
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

private const val TAG = "DriverTasksMgr"
private const val TASK_PROX_METERS = 500.0
private const val CAT_OTHER = "OTHER"
private const val CAT_ASK_AI = "ASK_AI"
private const val CACHE_LIFETIME_MS = 120_000L

class DriverTasksManager(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val lifecycleOwner: LifecycleOwner
) {
    private val httpClient = OkHttpClient()
    private var currentTasks: List<DriverTask> = emptyList()
    private var taskAdapter: TaskAdapter? = null
    private var bottomSheet: BottomSheetDialog? = null
    private var routePoints: List<Pair<Double, Double>> = emptyList()
    private var currentUserLat: Double = 0.0
    private var currentUserLon: Double = 0.0
    private var btnSubmit: MaterialButton? = null
    private var pbSubmit: ProgressBar? = null
    private val activeGeminiCalls = AtomicInteger(0)

    private val overpassMirrors = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.osm.ch/api/interpreter"
    )
    private var currentMirrorIndex = 0
    private val resultCache = mutableMapOf<String, Pair<PlaceResult?, Long>>()

    suspend fun nearestActiveTaskPlace(userLat: Double, userLon: Double): Pair<DriverTask, Double>? {
        val tasks = db.driverTaskDao().getTasksWithPlaces()
        var bestTask: DriverTask? = null
        var bestDist = Double.MAX_VALUE
        tasks.forEach { t ->
            val d = haversineKm(userLat, userLon, t.nearestPlaceLat!!, t.nearestPlaceLon!!)
            if (d * 1000 <= TASK_PROX_METERS && d < bestDist) {
                bestDist = d; bestTask = t
            }
        }
        return bestTask?.let { Pair(it, bestDist) }
    }

    fun show(currentLat: Double, currentLon: Double, routePts: List<Pair<Double, Double>> = emptyList()) {
        if (bottomSheet?.isShowing == true) return
        routePoints = routePts
        currentUserLat = currentLat
        currentUserLon = currentLon
        
        val view = LayoutInflater.from(context)
            .inflate(R.layout.layout_driver_tasks_overlay, null)
        bottomSheet = BottomSheetDialog(context, R.style.BottomSheetDialogTheme).also { d ->
            d.setContentView(view)
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            d.behavior.peekHeight = dpToPx(380)
            d.behavior.skipCollapsed = false
            d.show()
        }
        setupUI(view, currentLat, currentLon)

        db.driverTaskDao().getAllLive().observe(lifecycleOwner) { tasks ->
            currentTasks = tasks
            taskAdapter?.submitList(tasks.toMutableList())
            view.findViewById<TextView>(R.id.tv_task_empty)?.visibility =
                if (tasks.isEmpty()) View.VISIBLE else View.GONE
            updateSubmitButton()
        }
    }

    fun dismiss() {
        bottomSheet?.dismiss()
        bottomSheet = null
        btnSubmit = null
        pbSubmit = null
    }

    private fun setupUI(root: View, lat: Double, lon: Double) {
        val rv = root.findViewById<RecyclerView>(R.id.rv_driver_tasks)
        val etInput = root.findViewById<EditText>(R.id.et_task_input)
        val btnAdd = root.findViewById<MaterialButton>(R.id.btn_task_add_inline)
        btnSubmit = root.findViewById(R.id.btn_task_submit)
        pbSubmit = root.findViewById(R.id.pb_task_submit)

        rv.layoutManager = LinearLayoutManager(context)
        rv.isNestedScrollingEnabled = true

        taskAdapter = TaskAdapter(
            onDelete = { task -> scope.launch { db.driverTaskDao().deleteById(task.id) } },
            onCheck  = { task, done -> scope.launch { db.driverTaskDao().setCompleted(task.id, done) } },
            onCategorySelected = { task, cat ->
                scope.launch {
                    db.driverTaskDao().clearCategory(task.id)
                    db.driverTaskDao().updateCategory(task.id, cat.name)
                    fetchNearestPlace(
                        task.copy(category = cat.name, nearestPlaceName = null,
                            nearestPlaceDistance = null, nearestPlaceLat = null,
                            nearestPlaceLon = null, geminiProcessing = false),
                        lat, lon
                    )
                }
            },
            onAskAI = { task ->
                scope.launch {
                    db.driverTaskDao().clearCategory(task.id)
                    db.driverTaskDao().updateCategory(task.id, CAT_ASK_AI)
                }
            },
            onOther = { task ->
                scope.launch { db.driverTaskDao().updateCategory(task.id, CAT_OTHER) }
            }
        )
        rv.adapter = taskAdapter

        val addAction = {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etInput.setText("")
                scope.launch { db.driverTaskDao().insert(DriverTask(text = text, geminiProcessing = false)) }
            }
        }
        btnAdd.setOnClickListener { addAction() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addAction(); true } else false
        }

        btnSubmit?.setOnClickListener {
            if (activeGeminiCalls.get() > 0) return@setOnClickListener // Already processing
            
            // Only process tasks with ASK_AI category
            val aiTasks = currentTasks.filter { 
                it.category == CAT_ASK_AI && !it.geminiProcessing 
            }
            
            if (aiTasks.isEmpty()) {
                Toast.makeText(context, "No tasks need AI analysis!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Show loading state
            activeGeminiCalls.set(aiTasks.size)
            updateSubmitButton()
            
            aiTasks.forEach { task ->
                scope.launch {
                    db.driverTaskDao().setGeminiProcessing(task.id, true)
                    callGemini(task.copy(geminiProcessing = true), lat, lon)
                }
            }
            Toast.makeText(context, "🤖 AI analyzing ${aiTasks.size} task(s)…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSubmitButton() {
        // Check if any task lacks a valid category (null or empty)
        val hasUncategorized = currentTasks.any { 
            it.category == null || it.category.isEmpty() 
        }
        
        // Check if any task needs AI processing
        val aiTasks = currentTasks.filter { 
            it.category == CAT_ASK_AI && !it.geminiProcessing 
        }
        
        val isProcessing = activeGeminiCalls.get() > 0
        
        btnSubmit?.apply {
            when {
                currentTasks.isEmpty() -> {
                    visibility = View.GONE
                }
                isProcessing -> {
                    visibility = View.VISIBLE
                    text = "Processing..."
                    isEnabled = false
                    alpha = 0.6f
                }
                hasUncategorized -> {
                    visibility = View.VISIBLE
                    text = "Select Categories First"
                    isEnabled = false
                    alpha = 0.4f
                }
                aiTasks.isEmpty() -> {
                    visibility = View.VISIBLE
                    text = "All Done"
                    isEnabled = false
                    alpha = 0.4f
                }
                else -> {
                    visibility = View.VISIBLE
                    text = "Submit (${aiTasks.size} AI)"
                    isEnabled = true
                    alpha = 1.0f
                }
            }
        }
        
        pbSubmit?.visibility = if (isProcessing) View.VISIBLE else View.GONE
    }


    private fun onGeminiComplete() {
        val remaining = activeGeminiCalls.decrementAndGet()
        if (remaining <= 0) {
            scope.launch(Dispatchers.Main) {
                updateSubmitButton()
            }
        }
    }

    private fun callGemini(task: DriverTask, userLat: Double, userLon: Double) {
        val apiKey = AppConfig.get("GEMINI_API_KEY", "")
        if (apiKey.isEmpty()) {
            Log.w(TAG, "GEMINI_API_KEY not set")
            scope.launch { 
                db.driverTaskDao().setGeminiProcessing(task.id, false) 
                onGeminiComplete()
            }
            return
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val prompt = "Given the driver task: \"${task.text}\", " +
            "identify the most relevant place category from this list: " +
            "${TaskCategory.GEMINI_LIST_TEXT}. " +
            "Return ONLY the category name from the list, nothing else."
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }.toString().toRequestBody("application/json".toMediaType())

        httpClient.newCall(Request.Builder().url(url).post(body).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Gemini failed: ${e.message}")
                    scope.launch { 
                        db.driverTaskDao().setGeminiProcessing(task.id, false) 
                        onGeminiComplete()
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Gemini raw response: $responseBody")
                        
                        val json = JSONObject(responseBody)
                        
                        // Check if there's an error in the response
                        if (json.has("error")) {
                            val error = json.getJSONObject("error")
                            Log.e(TAG, "Gemini API error: ${error.optString("message", "Unknown error")}")
                            scope.launch {
                                db.driverTaskDao().setGeminiProcessing(task.id, false)
                                onGeminiComplete()
                            }
                            return
                        }
                        
                        val text = json
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0)
                            .getString("text").trim()
                        val cat = TaskCategory.fromGeminiResponse(text)
                        Log.d(TAG, "✓ Gemini '${task.text}': $text => ${cat?.label ?: "unknown"}")
                        scope.launch {
                            if (cat != null) {
                                // Update category AND clear geminiProcessing flag
                                db.driverTaskDao().updateCategory(task.id, cat.name)
                                db.driverTaskDao().setGeminiProcessing(task.id, false)
                                // Now search for place
                                fetchNearestPlace(
                                    task.copy(
                                        category = cat.name, 
                                        geminiProcessing = false,
                                        nearestPlaceName = null,
                                        nearestPlaceDistance = null,
                                        nearestPlaceLat = null,
                                        nearestPlaceLon = null
                                    ),
                                    userLat, userLon
                                )
                            } else {
                                db.driverTaskDao().setGeminiProcessing(task.id, false)
                            }
                            onGeminiComplete()
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Gemini parse error: ${ex.message}", ex)
                        scope.launch { 
                            db.driverTaskDao().setGeminiProcessing(task.id, false) 
                            onGeminiComplete()
                        }
                    }
                }
            })
    }

    private suspend fun fetchNearestPlace(task: DriverTask, userLat: Double, userLon: Double) {
        if (task.category == null || task.category == CAT_OTHER || task.category == CAT_ASK_AI) return
        val cat = TaskCategory.fromName(task.category) ?: return

        val cacheKey = makeCacheKey(cat.name, userLat, userLon)
        val cached = resultCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_LIFETIME_MS) {
            Log.d(TAG, "✓ Cached result for ${cat.label}")
            if (cached.first != null) {
                val r = cached.first!!
                db.driverTaskDao().updatePlace(task.id, r.name, r.distFromUser, r.lat, r.lon)
            } else {
                db.driverTaskDao().markNoPlaceFound(task.id)
            }
            return
        }

        var result: PlaceResult? = null
        if (routePoints.size >= 2) {
            Log.d(TAG, "Stage 1: Route bbox → ${cat.label}")
            result = searchInRouteBbox(cat, userLat, userLon)
            if (result != null) {
                Log.d(TAG, "✓ Route: ${result.name} @ ${"%.1f".format(result.distFromUser)}km")
            } else {
                Log.w(TAG, "✗ Route empty → radius fallback")
            }
        }

        if (result == null) {
            Log.d(TAG, "Stage 2: Radius 15km → ${cat.label}")
            result = searchInRadius(cat, userLat, userLon, 15000)
            if (result != null) {
                Log.d(TAG, "✓ Radius: ${result.name} @ ${"%.1f".format(result.distFromUser)}km")
            }
        }

        resultCache[cacheKey] = Pair(result, System.currentTimeMillis())

        if (result != null) {
            db.driverTaskDao().updatePlace(task.id, result.name, result.distFromUser, result.lat, result.lon)
        } else {
            db.driverTaskDao().markNoPlaceFound(task.id)
            Log.d(TAG, "✗ No ${cat.label} found")
        }
    }

    private fun makeCacheKey(category: String, lat: Double, lon: Double): String {
        val latR = String.format("%.2f", lat)
        val lonR = String.format("%.2f", lon)
        return "${category}_${latR}_${lonR}"
    }

    private data class PlaceResult(val name: String, val lat: Double, val lon: Double, val distFromUser: Double)

    private suspend fun searchInRouteBbox(cat: TaskCategory, userLat: Double, userLon: Double): PlaceResult? {
        return withContext(Dispatchers.IO) {
            try {
                val lats = routePoints.map { it.first }
                val lons = routePoints.map { it.second }
                val south = (lats.minOrNull() ?: userLat) - 0.01
                val north = (lats.maxOrNull() ?: userLat) + 0.01
                val west  = (lons.minOrNull() ?: userLon) - 0.01
                val east  = (lons.maxOrNull() ?: userLon) + 0.01
                
                val query = "[out:json][timeout:20][bbox:$south,$west,$north,$east];\n${cat.overpassFilter};\nout 50;"
                
                for (attempt in 0..2) {
                    val mirror = overpassMirrors[currentMirrorIndex % overpassMirrors.size]
                    try {
                        val body = query.toRequestBody("text/plain".toMediaType())
                        val req = Request.Builder().url(mirror).post(body).build()
                        val respBody = httpClient.newCall(req).execute().body?.string() ?: ""
                        
                        if (respBody.trim().startsWith("<?xml") || respBody.trim().startsWith("<")) {
                            Log.w(TAG, "Mirror $currentMirrorIndex rate limit, rotating...")
                            currentMirrorIndex = (currentMirrorIndex + 1) % overpassMirrors.size
                            if (attempt < 2) {
                                delay(1500)
                                continue
                            }
                            return@withContext null
                        }
                        
                        val json = JSONObject(respBody)
                        val elements = json.optJSONArray("elements") ?: JSONArray()
                        if (elements.length() == 0) return@withContext null
                        
                        var best: PlaceResult? = null
                        var bestRouteDist = Double.MAX_VALUE
                        
                        for (i in 0 until elements.length()) {
                            val el = elements.getJSONObject(i)
                            val eLat = el.optDouble("lat", Double.NaN)
                            val eLon = el.optDouble("lon", Double.NaN)
                            if (eLat.isNaN() || eLon.isNaN()) continue
                            
                            var minToRoute = Double.MAX_VALUE
                            for (pt in routePoints) {
                                val d = haversineKm(pt.first, pt.second, eLat, eLon)
                                if (d < minToRoute) minToRoute = d
                            }
                            
                            if (minToRoute < bestRouteDist) {
                                bestRouteDist = minToRoute
                                val distFromUser = haversineKm(userLat, userLon, eLat, eLon)
                                var name = el.optJSONObject("tags")?.optString("name", "") ?: ""
                                if (name.isEmpty()) name = cat.label
                                best = PlaceResult(name, eLat, eLon, distFromUser)
                            }
                        }
                        return@withContext best
                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt $attempt error: ${e.message}")
                        if (attempt < 2) delay(1500)
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "Route bbox error: ${e.message}")
                null
            }
        }
    }

    private suspend fun searchInRadius(cat: TaskCategory, userLat: Double, userLon: Double, radiusM: Int): PlaceResult? {
        return withContext(Dispatchers.IO) {
            try {
                val query = "[out:json][timeout:15];\n${cat.overpassFilter}(around:$radiusM,$userLat,$userLon);\nout 30;"
                
                for (attempt in 0..2) {
                    val mirror = overpassMirrors[currentMirrorIndex % overpassMirrors.size]
                    try {
                        val body = query.toRequestBody("text/plain".toMediaType())
                        val req = Request.Builder().url(mirror).post(body).build()
                        val respBody = httpClient.newCall(req).execute().body?.string() ?: ""
                        
                        if (respBody.trim().startsWith("<?xml") || respBody.trim().startsWith("<")) {
                            Log.w(TAG, "Radius: mirror $currentMirrorIndex rate limit")
                            currentMirrorIndex = (currentMirrorIndex + 1) % overpassMirrors.size
                            if (attempt < 2) {
                                delay(1500)
                                continue
                            }
                            return@withContext null
                        }
                        
                        val json = JSONObject(respBody)
                        val elements = json.optJSONArray("elements") ?: JSONArray()
                        if (elements.length() == 0) return@withContext null
                        
                        var best: PlaceResult? = null
                        var bestDist = Double.MAX_VALUE
                        
                        for (i in 0 until elements.length()) {
                            val el = elements.getJSONObject(i)
                            val eLat = el.optDouble("lat", Double.NaN)
                            val eLon = el.optDouble("lon", Double.NaN)
                            if (eLat.isNaN() || eLon.isNaN()) continue
                            
                            val d = haversineKm(userLat, userLon, eLat, eLon)
                            if (d < bestDist) {
                                bestDist = d
                                var name = el.optJSONObject("tags")?.optString("name", "") ?: ""
                                if (name.isEmpty()) name = cat.label
                                best = PlaceResult(name, eLat, eLon, d)
                            }
                        }
                        return@withContext best
                    } catch (e: Exception) {
                        Log.w(TAG, "Radius attempt $attempt: ${e.message}")
                        if (attempt < 2) delay(1500)
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "Radius error: ${e.message}")
                null
            }
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    inner class TaskAdapter(
        private val onDelete: (DriverTask) -> Unit,
        private val onCheck: (DriverTask, Boolean) -> Unit,
        private val onCategorySelected: (DriverTask, TaskCategory) -> Unit,
        private val onAskAI: (DriverTask) -> Unit,
        private val onOther: (DriverTask) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.VH>() {

        private val items = mutableListOf<DriverTask>()
        private val editingTaskIds = mutableSetOf<Long>()

        fun submitList(list: MutableList<DriverTask>) {
            val currentIds = list.map { it.id }.toSet()
            editingTaskIds.retainAll(currentIds)
            items.clear(); items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_driver_task, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val task = items[pos]
            val cat = TaskCategory.fromName(task.category)
            val isEditing = editingTaskIds.contains(task.id)

            h.cbDone.setOnCheckedChangeListener(null)
            h.cbDone.isChecked = task.isCompleted
            h.cbDone.setOnCheckedChangeListener { _, checked -> onCheck(task, checked) }

            h.tvText.text = task.text
            h.tvText.paintFlags = if (task.isCompleted)
                h.tvText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else
                h.tvText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()

            h.tvText.setOnClickListener {
                if (!task.geminiProcessing) {
                    if (isEditing) editingTaskIds.remove(task.id)
                    else editingTaskIds.add(task.id)
                    notifyItemChanged(pos)
                }
            }

            when {
                task.geminiProcessing -> {
                    h.tvSubtitle.visibility = View.VISIBLE
                    h.tvSubtitle.text = "⏳ AI analyzing task..."
                    h.tvSubtitle.setTextColor(Color.parseColor("#94A3B8"))
                    h.pbGemini.visibility = View.VISIBLE
                    h.rvCategoryChips.visibility = View.GONE
                }
                isEditing -> {
                    h.pbGemini.visibility = View.GONE
                    if (cat != null || task.category == CAT_ASK_AI) {
                        h.tvSubtitle.visibility = View.VISIBLE
                        h.tvSubtitle.text = "✏ Tap a new category to reassign"
                        h.tvSubtitle.setTextColor(Color.parseColor("#FFB300"))
                    } else {
                        h.tvSubtitle.visibility = View.GONE
                    }
                    showChipRow(h, task, pos)
                }
                task.category == CAT_ASK_AI -> {
                    h.pbGemini.visibility = View.GONE
                    h.rvCategoryChips.visibility = View.GONE
                    h.tvSubtitle.visibility = View.VISIBLE
                    h.tvSubtitle.text = "🤖 Awaiting AI analysis · tap Submit to process"
                    h.tvSubtitle.setTextColor(Color.parseColor("#5C6BC0"))
                }
                task.category == CAT_OTHER -> {
                    h.pbGemini.visibility = View.GONE
                    h.rvCategoryChips.visibility = View.GONE
                    h.tvSubtitle.visibility = View.VISIBLE
                    h.tvSubtitle.text = "✕ Uncategorized — tap name to reassign"
                    h.tvSubtitle.setTextColor(Color.parseColor("#607D8B"))
                }
                task.category != null && task.nearestPlaceName == null -> {
                    h.pbGemini.visibility = View.GONE
                    h.rvCategoryChips.visibility = View.GONE
                    h.tvSubtitle.text = "📡 ${cat?.emoji ?: ""} ${cat?.label ?: ""} · searching..."
                    h.tvSubtitle.setTextColor(Color.parseColor("#94A3B8"))
                    h.tvSubtitle.visibility = View.VISIBLE
                }
                task.nearestPlaceName != null && task.nearestPlaceName.isEmpty() -> {
                    h.pbGemini.visibility = View.GONE
                    h.rvCategoryChips.visibility = View.GONE
                    h.tvSubtitle.visibility = View.VISIBLE
                    h.tvSubtitle.text = "❌ No ${cat?.label ?: "place"} found nearby · tap name to reassign"
                    h.tvSubtitle.setTextColor(Color.parseColor("#EF5350"))
                }
                task.nearestPlaceName != null && task.nearestPlaceName.isNotEmpty() && cat != null -> {
                    h.pbGemini.visibility = View.GONE
                    h.rvCategoryChips.visibility = View.GONE
                    val dist = task.nearestPlaceDistance?.let { "%.1f km away".format(it) } ?: ""
                    h.tvSubtitle.text = "${cat.emoji} ${task.nearestPlaceName}  ·  $dist  (tap to change)"
                    h.tvSubtitle.setTextColor(Color.parseColor("#81C784"))
                    h.tvSubtitle.visibility = View.VISIBLE
                }
                else -> {
                    h.pbGemini.visibility = View.GONE
                    h.tvSubtitle.visibility = View.GONE
                    showChipRow(h, task, pos)
                }
            }

            h.btnDelete.setOnClickListener { onDelete(task) }
        }

        private fun showChipRow(h: VH, task: DriverTask, pos: Int) {
            h.rvCategoryChips.visibility = View.VISIBLE
            h.rvCategoryChips.layoutManager = LinearLayoutManager(
                h.rvCategoryChips.context, LinearLayoutManager.HORIZONTAL, false
            )
            h.rvCategoryChips.adapter = CategoryChipAdapter(
                onCategoryPicked = { selectedCat ->
                    editingTaskIds.remove(task.id)
                    onCategorySelected(task, selectedCat)
                },
                onAskAI = { editingTaskIds.remove(task.id); onAskAI(task) },
                onOther = { editingTaskIds.remove(task.id); onOther(task) }
            )
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val cbDone: CheckBox = v.findViewById(R.id.cb_task_done)
            val tvText: TextView = v.findViewById(R.id.tv_task_text)
            val tvSubtitle: TextView = v.findViewById(R.id.tv_task_subtitle)
            val pbGemini: ProgressBar = v.findViewById(R.id.pb_task_gemini)
            val btnDelete: ImageButton = v.findViewById(R.id.btn_task_delete)
            val rvCategoryChips: RecyclerView = v.findViewById(R.id.rv_task_category_chips)
        }
    }

    private sealed class ChipItem {
        object AskAI : ChipItem()
        data class Category(val cat: TaskCategory) : ChipItem()
        object Other : ChipItem()
    }

    inner class CategoryChipAdapter(
        private val onCategoryPicked: (TaskCategory) -> Unit,
        private val onAskAI: () -> Unit,
        private val onOther: () -> Unit
    ) : RecyclerView.Adapter<CategoryChipAdapter.CVH>() {

        private val chips: List<ChipItem> = buildList {
            add(ChipItem.AskAI)
            TaskCategory.values().forEach { add(ChipItem.Category(it)) }
            add(ChipItem.Other)
        }

        override fun getItemCount() = chips.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_category_chip, parent, false)
            return CVH(v)
        }

        override fun onBindViewHolder(h: CVH, pos: Int) {
            when (val item = chips[pos]) {
                is ChipItem.AskAI -> {
                    h.tvEmoji.text = "🤖"; h.tvLabel.text = "Ask AI"
                    (h.itemView.background as? GradientDrawable)?.setColor(Color.parseColor("#5C6BC0"))
                    h.itemView.alpha = 1.0f
                    h.itemView.setOnClickListener { onAskAI() }
                }
                is ChipItem.Category -> {
                    h.tvEmoji.text = item.cat.emoji; h.tvLabel.text = item.cat.label
                    h.itemView.alpha = 1.0f
                    (h.itemView.background as? GradientDrawable)?.setColor(item.cat.color)
                    h.itemView.setOnClickListener { onCategoryPicked(item.cat) }
                }
                is ChipItem.Other -> {
                    h.tvEmoji.text = "✕"; h.tvLabel.text = "Other"
                    h.itemView.alpha = 1.0f
                    (h.itemView.background as? GradientDrawable)?.setColor(Color.parseColor("#546E7A"))
                    h.itemView.setOnClickListener { onOther() }
                }
            }
        }

        inner class CVH(v: View) : RecyclerView.ViewHolder(v) {
            val tvEmoji: TextView = v.findViewById(R.id.tv_chip_emoji)
            val tvLabel: TextView = v.findViewById(R.id.tv_chip_label)
        }
    }
}
