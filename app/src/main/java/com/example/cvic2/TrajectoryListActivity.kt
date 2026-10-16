package com.example.cvic2

import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DecimalFormat
import android.widget.Button

class TrajectoryListActivity : AppCompatActivity() {

    private lateinit var listViewTrajectory: ListView
    private val decimalFormat = DecimalFormat("#.##") // format čisel na 2 desatinne miesta

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trajectory_list)
        // Odsadenie obsahu od systemovych lišt (stavovy riadok, navigacia)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_list)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        listViewTrajectory = findViewById(R.id.listViewTrajectory)
        // Tlačidlo Späť zatvori obrazovku
        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }

        // Načitanie bodov trajektorie z Intentu
        val trajectoryPointsParcel: ArrayList<TrajectoryPointParcel>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("trajectory_data")
        }

        if (trajectoryPointsParcel != null && trajectoryPointsParcel.isNotEmpty()) {
            // Konverzia objektov TrajectoryPointParcel na zoznam textov pre ArrayAdapter
            val dataForListView = trajectoryPointsParcel.map {
                "T: ${decimalFormat.format(it.time)}s,  X: ${decimalFormat.format(it.x)}m,  Y: ${decimalFormat.format(it.y)}m"
            }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1, // vstavany Android layout pre jednoduchy riadok zoznamu
                dataForListView
            )
            listViewTrajectory.adapter = adapter
        } else {
            // Žiadne data na zobrazenie
            Toast.makeText(this, "No trajectory data found.", Toast.LENGTH_LONG).show()
            // Voliteľne ak nie su data, obrazovku možno rovno zavrieť
            // finish()
        }
    }
}


