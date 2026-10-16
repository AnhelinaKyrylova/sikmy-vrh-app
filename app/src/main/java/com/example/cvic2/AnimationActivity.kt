package com.example.cvic2

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AnimationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_animation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Načítanie dát z Intentu
        val points: ArrayList<TrajectoryPointParcel>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("trajectory_data", TrajectoryPointParcel::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("trajectory_data")
            }

        if (points.isNullOrEmpty()) {
            Toast.makeText(this, "No trajectory data to animate.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Spustenie animácie
        val animationView = findViewById<TrajectoryAnimationView>(R.id.trajectoryView)

        val textTime = findViewById<TextView>(R.id.textTime)
        val textX = findViewById<TextView>(R.id.textX)
        val textY = findViewById<TextView>(R.id.textY)

        // Zive hodnoty počas letu
        animationView.onPointChanged = { p ->
            textTime.text = String.format("%.2f s", p.time)
            textX.text = String.format("%.2f m", p.x)
            textY.text = String.format("%.2f m", p.y)
        }

        // Zhrnutie celého letu
        findViewById<TextView>(R.id.textFlight).text = String.format("%.2f s", points.last().time)
        findViewById<TextView>(R.id.textMaxHeight).text = String.format("%.2f m", points.maxOf { it.y })
        findViewById<TextView>(R.id.textRange).text = String.format("%.2f m", points.last().x)

        animationView.setTrajectory(points)

        findViewById<Button>(R.id.buttonReplay).setOnClickListener {
            animationView.replay()
        }

        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }
    }
}