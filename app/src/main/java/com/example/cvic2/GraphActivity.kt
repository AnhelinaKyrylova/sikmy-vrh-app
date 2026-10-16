package com.example.cvic2

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class GraphActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_graph)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets }

        lineChart = findViewById(R.id.lineChart)
        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }

        // Načitanie extra dat z Intentu
        val trajectoryPoints: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (trajectoryPoints != null && trajectoryPoints.isNotEmpty()) {
            setupLineChart(trajectoryPoints)
        } else {
            Toast.makeText(this, "No trajectory data to display.", Toast.LENGTH_SHORT).show()
            finish() // Zavrie activity, ak chybaju data
        }

        supportActionBar?.title = "Height vs. Time Graph"
    }

    private fun setupLineChart(points: List<TrajectoryPointParcel>) {
        val entries = ArrayList<Entry>()
        for (point in points) {
            // Entry berie (xValue, yValue)
            entries.add(Entry(point.time.toFloat(), point.y.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Height (y versus time)")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawValues(false) // Nekresliť hodnoty na jednotlivych bodoch
        dataSet.setDrawCircles(false) // Nekresliť kruhy na každom bode
        dataSet.lineWidth = 2f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // hladšia čiara

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // Prispôsobenie osi X
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 0.5f // let trva len par sekund, 1f bolo priliš hrube
        xAxis.labelCount = 6 // Požadovany počet označeni
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.1f", value) // Format "1.0s"
            }
        }
        xAxis.axisMinimum = 0f // Začiatok osi X od 0

        // Prispôsobenie osi Y (Vľavo)
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.1f", value) // Format "1.0m"
            }
        }
        yAxisLeft.axisMinimum = 0f // Začiatok osi Y od 0

        // Prispôsobenie osi Y (Vpravo) - Vypnuta
        lineChart.axisRight.isEnabled = false

        // Popis grafu
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false

        // Povolenie gest dotyku
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)

        // Animacia vykresľovania zľava doprava (animateX už graf prekresli)
        lineChart.animateX(2000, Easing.Linear)
    }
}