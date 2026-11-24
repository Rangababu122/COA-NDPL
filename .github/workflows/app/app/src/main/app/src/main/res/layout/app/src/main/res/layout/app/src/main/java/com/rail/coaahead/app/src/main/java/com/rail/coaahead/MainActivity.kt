package com.rail.coaahead

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

data class StationSlot(
    val stationCode: String,
    val trains: List<TrainInfo>,
    val isFirst: Boolean,
    val isLast: Boolean
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvLastUpdated: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var spinnerStation: Spinner
    private lateinit var cbHideTerminated: CheckBox
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerStations: RecyclerView

    private var isUpDirection = true
    private var selectedStartStation = "ALL"
    private var cachedData: CoaSheetData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUp = findViewById(R.id.btnUp)
        btnDown = findViewById(R.id.btnDown)
        spinnerStation = findViewById(R.id.spinnerStation)
        cbHideTerminated = findViewById(R.id.cbHideTerminated)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerStations = findViewById(R.id.recyclerStations)

        recyclerStations.layoutManager = LinearLayoutManager(this)

        btnUp.setOnClickListener {
            if (!isUpDirection) {
                isUpDirection = true
                selectedStartStation = "ALL"
                updateDirectionUi()
                populateStationSpinner()
                renderStations()
            }
        }

        btnDown.setOnClickListener {
            if (isUpDirection) {
                isUpDirection = false
                selectedStartStation = "ALL"
                updateDirectionUi()
                populateStationSpinner()
                renderStations()
            }
        }

        cbHideTerminated.setOnCheckedChangeListener { _, _ ->
            renderStations()
        }

        btnRefresh.setOnClickListener { loadData() }
        swipeRefresh.setOnRefreshListener { loadData() }

        updateDirectionUi()
        populateStationSpinner()
        loadData()
    }

    private fun updateDirectionUi() {
        if (isUpDirection) {
            btnUp.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16A34A"))
            btnUp.setTextColor(Color.WHITE)
            btnDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A303C"))
            btnDown.setTextColor(Color.parseColor("#CBD5E1"))
        } else {
            btnDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16A34A"))
            btnDown.setTextColor(Color.WHITE)
            btnUp.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A303C"))
            btnUp.setTextColor(Color.parseColor("#CBD5E1"))
        }
    }

    private fun populateStationSpinner() {
        val ordered = if (isUpDirection) CoaRepository.UP_STATIONS else CoaRepository.DN_STATIONS
        val options = listOf("ALL STATIONS") + ordered

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.textSize = 13f
                v.setTypeface(null, Typeface.BOLD)
                v.setPadding(16, 0, 16, 0)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.setBackgroundColor(Color.parseColor("#1E232B"))
                v.setPadding(24, 20, 24, 20)
                return v
            }
        }
        spinnerStation.adapter = adapter

        spinnerStation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStartStation = if (position == 0) "ALL" else ordered[position - 1]
                renderStations()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        tvLastUpdated.text = "Fetching live COA sheet..."
        lifecycleScope.launch {
            try {
                val data = CoaRepository.fetchCoaData()
                cachedData = data
                tvLastUpdated.text = "Updated: ${data.lastUpdated}"
                renderStations()
            } catch (e: Exception) {
                tvLastUpdated.text = "Error loading sheet (Check internet)"
                Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun renderStations() {
        val data = cachedData ?: return
        val fullOrderedStations = if (isUpDirection) CoaRepository.UP_STATIONS else CoaRepository.DN_STATIONS
        val rawTrains = if (isUpDirection) data.upTrains else data.dnTrains
        val hideTer = cbHideTerminated.isChecked

        val filteredTrains = if (hideTer) {
            rawTrains.filter { !it.status.contains("Ter", ignoreCase = true) }
        } else {
            rawTrains
        }

        val startIndex = if (selectedStartStation == "ALL") 0 else fullOrderedStations.indexOf(selectedStartStation).coerceAtLeast(0)
        val visibleStations = fullOrderedStations.subList(startIndex, fullOrderedStations.size)

        val trainsByStation = filteredTrains.groupBy { it.station }

        val slots = visibleStations.mapIndexed { index, stn ->
            StationSlot(
                stationCode = stn,
                trains = trainsByStation[stn] ?: emptyList(),
                isFirst = index == 0,
                isLast = index == visibleStations.lastIndex
            )
        }

        recyclerStations.adapter = StationAdapter(slots) { train ->
            showRemarksDialog(train)
        }
    }

    private fun showRemarksDialog(train: TrainInfo) {
        val message = buildString {
            append("Train: ${train.trainName}\n")
            append("Loco: ${train.loco}\n")
            append("Station: ${train.station} (${train.status} @ ${train.time})\n\n")
            append("Remarks:\n${train.remarks.ifEmpty { "No remarks entered." }}")
        }

        AlertDialog.Builder(this)
            .setTitle("${train.direction} Train Remarks")
            .setMessage(message)
            .setPositiveButton("CLOSE", null)
            .show()
    }
}

class StationAdapter(
    private val items: List<StationSlot>,
    private val onRemarksClick: (TrainInfo) -> Unit
) : RecyclerView.Adapter<StationAdapter.SlotViewHolder>() {

    class SlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val lineTop: View = view.findViewById(R.id.lineTop)
        val lineBottom: View = view.findViewById(R.id.lineBottom)
        val stationDot: View = view.findViewById(R.id.stationDot)
        val cardStation: LinearLayout = view.findViewById(R.id.cardStation)
        val tvStationCode: TextView = view.findViewById(R.id.tvStationCode)
        val tvClearTrack: TextView = view.findViewById(R.id.tvClearTrack)
        val trainsContainer: LinearLayout = view.findViewById(R.id.trainsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station_row, parent, false)
        return SlotViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = items[position]
        val ctx = holder.itemView.context

        holder.lineTop.visibility = if (slot.isFirst) View.INVISIBLE else View.VISIBLE
        holder.lineBottom.visibility = if (slot.isLast) View.INVISIBLE else View.VISIBLE
        holder.tvStationCode.text = slot.stationCode

        holder.trainsContainer.removeAllViews()

        if (slot.trains.isEmpty()) {
            holder.tvClearTrack.visibility = View.VISIBLE
            holder.trainsContainer.visibility = View.GONE
            holder.cardStation.setBackgroundColor(Color.parseColor("#161A20"))
            holder.stationDot.alpha = 0.35f
        } else {
            holder.tvClearTrack.visibility = View.GONE
            holder.trainsContainer.visibility = View.VISIBLE
            holder.cardStation.setBackgroundColor(Color.parseColor("#212733"))
            holder.stationDot.alpha = 1.0f

            for (train in slot.trains) {
                val rowLayout = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(Color.parseColor("#2B3342"))
                    setPadding(20, 16, 16, 16)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = 10
                    layoutParams = params
                }

                val infoColumn = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTrain = TextView(ctx).apply {
                    text = train.trainName
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                }

                val tvLocoStatus = TextView(ctx).apply {
                    text = "LOCO: ${train.loco}  |  ${train.status} (${train.time})"
                    setTextColor(getStatusColor(train.status))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                }

                infoColumn.addView(tvTrain)
                infoColumn.addView(tvLocoStatus)
                rowLayout.addView(infoColumn)

                if (train.remarks.isNotEmpty()) {
                    val btnRemarks = Button(ctx).apply {
                        text = "REMARKS"
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))
                        setPadding(18, 4, 18, 4)
                        setOnClickListener { onRemarksClick(train) }
                    }
                    rowLayout.addView(btnRemarks)
                }

                holder.trainsContainer.addView(rowLayout)
            }
        }
    }

    private fun getStatusColor(status: String): Int {
        return when {
            status.equals("Dep", true) -> Color.parseColor("#4ADE80")   // Green
            status.equals("Arr", true) -> Color.parseColor("#FBBF24")   // Amber
            status.equals("Stbld", true) -> Color.parseColor("#F87171") // Red
            else -> Color.parseColor("#94A3B8")                         // Grey
        }
    }
}
