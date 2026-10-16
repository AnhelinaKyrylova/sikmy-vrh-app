package com.example.cvic2

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var editTextServerIp: EditText
    private lateinit var editTextInitialVelocity: EditText
    private lateinit var editTextAngle: EditText
    private lateinit var buttonCalculate: Button
    private lateinit var buttonList: Button
    private lateinit var buttonGraph: Button
    private lateinit var buttonAnimation: Button
    private lateinit var textResult: TextView

    // emulator vidi počitač na 10.0.2.2
    private val DEFAULT_SERVER_IP = "10.0.2.2"
    private val SERVER_PORT = 8888

    private val trajectoryPoints = mutableListOf<TrajectoryPoint>()
    private val GRAVITY = 9.81 // m/s^2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editTextServerIp = findViewById(R.id.editTextServerIp)
        editTextInitialVelocity = findViewById(R.id.editTextInitialVelocity)
        editTextAngle = findViewById(R.id.editTextAngle)
        buttonCalculate = findViewById(R.id.buttonCalculate)
        buttonList = findViewById(R.id.buttonList)
        buttonGraph = findViewById(R.id.buttonGraph)
        buttonAnimation = findViewById(R.id.buttonAnimation)
        textResult = findViewById(R.id.textResult)

        buttonCalculate.setOnClickListener {
            calculateTrajectory()
        }

        buttonList.setOnClickListener {
            openScreen(TrajectoryListActivity::class.java)
        }

        buttonGraph.setOnClickListener {
            openScreen(GraphActivity::class.java)
        }

        buttonAnimation.setOnClickListener {
            openScreen(AnimationActivity::class.java)
        }
    }

    // Spoločna funkcia na otvorenie List / Graph / Animation
    private fun openScreen(target: Class<*>) {
        if (trajectoryPoints.isEmpty()) {
            Toast.makeText(this, "No data available. Calculate trajectory first.", Toast.LENGTH_SHORT).show()
            return
        }
        val parcelPoints = ArrayList(trajectoryPoints.map {
            TrajectoryPointParcel(it.time, it.x, it.y)
        })
        val intent = Intent(this, target).apply {
            putParcelableArrayListExtra("trajectory_data", parcelPoints)
        }
        startActivity(intent)
    }

    // Overi vstupy a skusi server; ak nie je dostupný, počita lokalne
    private fun calculateTrajectory() {
        val initialVelocityStr = editTextInitialVelocity.text.toString()
        val angleStr = editTextAngle.text.toString()

        if (initialVelocityStr.isEmpty() || angleStr.isEmpty()) {
            Toast.makeText(this, "Please enter both velocity and angle", Toast.LENGTH_SHORT).show()
            return
        }

        val initialVelocity = initialVelocityStr.toDoubleOrNull()
        val angleDegrees = angleStr.toDoubleOrNull()

        if (initialVelocity == null || angleDegrees == null ||
            initialVelocity <= 0 || angleDegrees <= 0 || angleDegrees >= 90
        ) {
            Toast.makeText(
                this,
                "Invalid input. Velocity and angle must be positive, and angle < 90",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        calculateOnServer(initialVelocity, angleDegrees)
    }

    // VYPOČET NA SERVERI
    private fun calculateOnServer(initialVelocity: Double, angleDegrees: Double) {
        // IP adresa z poľa (ak je prazdne, použije sa predvolena)
        val serverIp = editTextServerIp.text.toString().trim().ifEmpty { DEFAULT_SERVER_IP }

        buttonCalculate.isEnabled = false
        textResult.text = "Connecting to $serverIp ..."

        // Sieť nesmie bežať na hlavnom vlakne
        Thread {
            val received = mutableListOf<TrajectoryPoint>()
            var error: String? = null

            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(serverIp, SERVER_PORT), 2000)
                    socket.soTimeout = 5000

                    val dos = DataOutputStream(socket.getOutputStream())
                    val dis = DataInputStream(socket.getInputStream())

                    // Poradie musi byť rovnake ako na serveri: rychlosť, potom uhol
                    dos.writeDouble(initialVelocity)
                    dos.writeDouble(angleDegrees)
                    dos.flush()

                    val count = dis.readInt()
                    for (i in 0 until count) {
                        val t = dis.readDouble()
                        val x = dis.readDouble()
                        val y = dis.readDouble()
                        received.add(TrajectoryPoint(t, x, y))
                    }
                }
            } catch (e: IOException) {
                android.util.Log.e("Server", "Connection failed", e)
                error = e.message ?: "IOException"
            }

            runOnUiThread {
                buttonCalculate.isEnabled = true
                if (error != null || received.isEmpty()) {
                    // Server nie je dostupny -> založny lokalny vypočet
                    Toast.makeText(this, "Server not available, calculating locally", Toast.LENGTH_SHORT).show()
                    calculateLocally(initialVelocity, angleDegrees)
                } else {
                    trajectoryPoints.clear()
                    trajectoryPoints.addAll(received)
                    showSummary("Server $serverIp")
                }
            }
        }.start()
    }

    // LOKALNY VYPOČET
    private fun calculateLocally(initialVelocity: Double, angleDegrees: Double) {
        val angleRadians = Math.toRadians(angleDegrees)

        trajectoryPoints.clear()

        // Čas letu = 2 * v0 * sin(theta) / g
        val timeOfFlight = (2 * initialVelocity * sin(angleRadians)) / GRAVITY

        val timeStep = 0.05
        var currentTime = 0.0

        while (currentTime <= timeOfFlight) {
            val x = initialVelocity * cos(angleRadians) * currentTime
            val y = (initialVelocity * sin(angleRadians) * currentTime) - (0.5 * GRAVITY * currentTime.pow(n = 2))

            if (y >= 0) {
                trajectoryPoints.add(TrajectoryPoint(currentTime, x, y))
            } else if (trajectoryPoints.isNotEmpty() && trajectoryPoints.last().y > 0) {
                val lastPoint = trajectoryPoints.last()
                val timeToGround = (initialVelocity * sin(angleRadians) + sqrt((initialVelocity * sin(angleRadians)).pow(n = 2) - 2 * GRAVITY * (-lastPoint.y))) / GRAVITY
                val finalX = initialVelocity * cos(angleRadians) * timeToGround
                trajectoryPoints.add(TrajectoryPoint(timeToGround, finalX, y = 0.0))
                break
            }

            if (y < 0 && currentTime > 0) break

            currentTime += timeStep
        }

        // Posledny bod presne na zemi
        if (trajectoryPoints.isNotEmpty() && trajectoryPoints.last().y != 0.0 && timeOfFlight > 0) {
            val finalX = initialVelocity * cos(angleRadians) * timeOfFlight
            trajectoryPoints.add(TrajectoryPoint(timeOfFlight, x = finalX, y = 0.0))
        }

        showSummary("Local")
    }

    // Zhrnutie vysledkov: farebne zvyraznený je len riadok Source
    private fun showSummary(source: String) {
        if (trajectoryPoints.isEmpty()) return
        val flightTime = trajectoryPoints.last().time
        val maxHeight = trajectoryPoints.maxOf { it.y }
        val range = trajectoryPoints.last().x

        val header = " Source: $source (${trajectoryPoints.size} points) "
        val body = String.format(
            "\nFlight time: %.2f s\nMax height: %.2f m\nRange: %.2f m",
            flightTime, maxHeight, range
        )

        // Fialova = server, oranžova = lokalny vypočet
        val color = if (source.startsWith("Server")) "#6750A4" else "#FB8C00"

        val text = SpannableString(header + body)
        val end = header.length
        text.setSpan(BackgroundColorSpan(Color.parseColor(color)), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(Color.WHITE), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        textResult.text = text
    }
}