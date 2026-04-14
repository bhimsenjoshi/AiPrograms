package com.hmie.btreport

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmie.btreport.adapter.TripAdapter
import com.hmie.btreport.databinding.ActivityMainBinding
import com.hmie.btreport.db.AppDatabase
import com.hmie.btreport.model.Trip
import kotlinx.coroutines.launch

class MainViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    val trips = db.tripDao().getAllTrips()

    fun deleteTrip(trip: Trip) = viewModelScope.launch {
        db.tripDao().deleteTrip(trip)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val adapter = TripAdapter(
            onEdit = { trip ->
                startActivity(Intent(this, TripFormActivity::class.java).apply {
                    putExtra(TripFormActivity.EXTRA_TRIP_ID, trip.id)
                })
            },
            onOpen = { trip ->
                startActivity(Intent(this, ExpenseListActivity::class.java).apply {
                    putExtra(ExpenseListActivity.EXTRA_TRIP_ID, trip.id)
                    putExtra(ExpenseListActivity.EXTRA_TRIP_NAME, trip.purpose.ifBlank { "Business Trip" })
                })
            },
            onDelete = { trip ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Trip")
                    .setMessage("Delete '${trip.purpose.ifBlank { "this trip" }}'? All expenses will be removed.")
                    .setPositiveButton("Delete") { _, _ -> vm.deleteTrip(trip) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        b.rvTrips.adapter = adapter
        vm.trips.observe(this) { list ->
            adapter.submitList(list)
            b.tvEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        b.fab.setOnClickListener {
            startActivity(Intent(this, TripFormActivity::class.java))
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.navSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.btnSupport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About HMIE Expense")
                .setMessage(
                    "Version: 1.1\n\n" +
                    "Developed by: Bhimsen Joshi\n" +
                    "Contact: bhimsen.joshi@gmail.com\n\n" +
                    "Release Date: 14 April 2026\n\n" +
                    "© 2026 HMIE. All rights reserved."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
