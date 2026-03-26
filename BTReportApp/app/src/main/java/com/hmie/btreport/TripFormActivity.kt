package com.hmie.btreport

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmie.btreport.databinding.ActivityTripFormBinding
import com.hmie.btreport.db.AppDatabase
import com.hmie.btreport.model.Trip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TripFormViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    var existingTrip: Trip? = null

    fun loadTrip(id: Int, onLoaded: (Trip) -> Unit) = viewModelScope.launch {
        db.tripDao().getTripById(id)?.let {
            existingTrip = it
            onLoaded(it)
        }
    }

    fun saveTrip(trip: Trip, onDone: () -> Unit) = viewModelScope.launch {
        if (trip.id == 0) db.tripDao().insertTrip(trip)
        else db.tripDao().updateTrip(trip)
        onDone()
    }
}

class TripFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }

    private lateinit var b: ActivityTripFormBinding
    private val vm: TripFormViewModel by viewModels()
    private val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTripFormBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val tripId = intent.getIntExtra(EXTRA_TRIP_ID, 0)
        if (tripId != 0) {
            supportActionBar?.title = "Edit Trip"
            vm.loadTrip(tripId) { trip -> runOnUiThread { populate(trip) } }
        } else {
            supportActionBar?.title = "New Business Trip"
        }

        b.etStartDate.setOnClickListener { pickDate { b.etStartDate.setText(it) } }
        b.etEndDate.setOnClickListener { pickDate { b.etEndDate.setText(it) } }

        b.btnSave.setOnClickListener { saveTrip() }
    }

    private fun populate(trip: Trip) {
        b.etEmployeeName.setText(trip.employeeName)
        b.etEmployeeId.setText(trip.employeeId)
        b.etDepartment.setText(trip.department)
        b.etDesignation.setText(trip.designation)
        b.etCostCenter.setText(trip.costCenter)
        b.etStartDate.setText(trip.startDate)
        b.etEndDate.setText(trip.endDate)
        b.etPurpose.setText(trip.purpose)
        b.etRoute.setText(trip.route)
    }

    private fun saveTrip() {
        val name = b.etEmployeeName.text.toString().trim()
        if (name.isBlank()) { b.etEmployeeName.error = "Required"; return }
        val startDate = b.etStartDate.text.toString().trim()
        if (startDate.isBlank()) { b.etStartDate.error = "Required"; return }

        val trip = Trip(
            id = vm.existingTrip?.id ?: 0,
            employeeName = name,
            employeeId = b.etEmployeeId.text.toString().trim(),
            department = b.etDepartment.text.toString().trim(),
            designation = b.etDesignation.text.toString().trim(),
            costCenter = b.etCostCenter.text.toString().trim(),
            startDate = startDate,
            endDate = b.etEndDate.text.toString().trim(),
            purpose = b.etPurpose.text.toString().trim(),
            route = b.etRoute.text.toString().trim()
        )
        vm.saveTrip(trip) {
            runOnUiThread {
                Toast.makeText(this, "Trip saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d)
            onPicked(sdf.format(c.time))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
