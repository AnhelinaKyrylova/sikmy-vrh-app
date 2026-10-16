package com.example.cvic2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Vlastny komponent, ktory kresli a animuje let loptičky po trajektorii
class TrajectoryAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Body trajektorie (čas, x, y) a index posledneho bodu, ktory loptička už prešla
    private var trajectoryPoints: List<TrajectoryPointParcel> = emptyList()
    private var currentPointIndex = 0
    private var animator: ValueAnimator? = null

    // Callback pre aktivitu: zavola sa vždy, keď loptička prejde na ďalši bod
    var onPointChanged: ((TrajectoryPointParcel) -> Unit)? = null

    // true = rovnaka mierka na osi X aj Y (realistic but flat)
    var keepAspectRatio = false

    // Koľkokrat môže byť os Y natiahnuta oproti osi X
    var maxStretch = 5f

    // Červena loptička
    private val ballPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Jemna žiara okolo loptičky
    private val ballGlowPaint = Paint().apply {
        color = Color.argb(60, 255, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Čiara zeme
    private val groundPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 4f
    }

    // Trava pod čiarou zeme
    private val grassPaint = Paint().apply {
        color = Color.parseColor("#C8E6C9")
        style = Paint.Style.FILL
    }

    // Čiarkovana vodiaca čiara celej trajektorie
    private val pathPaint = Paint().apply {
        color = Color.parseColor("#CAC4D0")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(16f, 16f), 0f)
        isAntiAlias = true
    }

    // Stopa, ktoru loptička zanechava za sebou
    private val trailPaint = Paint().apply {
        color = Color.parseColor("#6750A4")
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    // Tieň loptičky na zemi
    private val shadowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Čiary mriežky
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E6E0E9")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Čisla pri osiach
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#49454F")
        textSize = 30f
        isAntiAlias = true
    }

    // Čiarkovana čiara v najvyššom bode
    private val apexLinePaint = Paint().apply {
        color = Color.parseColor("#D32F2F")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }

    // Text s hodnotou maximalnej vyšky
    private val apexLabelPaint = Paint().apply {
        color = Color.parseColor("#D32F2F")
        textSize = 32f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val ballRadius = 15f // Polomer loptičky v pixeloch

    // Samostatna mierka pre os X a Y (pixely na meter)
    private var scaleX = 20f
    private var scaleY = 20f

    // Počiatok suradnic (ľavy dolny roh grafu) v pixeloch
    private var originX = 0f
    private var originY = 0f
    private val topMargin = 50f // voľne miesto nad grafom
    private val rightMargin = 30f // voľne miesto vpravo od grafu

    // Rozsah osi a krok mriežky (v metroch)
    private var axisMaxX = 1f
    private var axisMaxY = 1f
    private var stepX = 1f
    private var stepY = 1f

    // Aktualna plynula poloha loptičky (medzi dvoma bodmi trajektorie) v metroch
    private var ballX = 0f
    private var ballY = 0f

    private val trajectoryPath = Path() // cela trajektoria
    private val trailPath = Path() // stopa za loptičkou

    // Nastavi trajektoriu, pripravi vykreslenie a spusti animaciu
    fun setTrajectory(points: List<TrajectoryPointParcel>) {
        this.trajectoryPoints = points
        this.currentPointIndex = 0
        prepareTrajectoryPath()
        stopAnimation()
        if (points.isNotEmpty()) {
            startAnimation()
        }
        if (points.isNotEmpty()) {
            onPointChanged?.invoke(points.first()) // uvodne hodnoty
        }
        invalidate()
    }

    // Spusti animaciu odznova
    fun replay() {
        stopAnimation()
        currentPointIndex = 0
        startAnimation()
    }

    // Vyberie pekny krok osi (1, 2, 5, 10, 20, 50, ...)
    private fun niceStep(range: Float, targetCount: Int): Float {
        val rawStep = range / targetCount
        val magnitude = 10.0.pow(floor(log10(rawStep.toDouble()))).toFloat()
        val normalized = rawStep / magnitude
        val multiplier = when {
            normalized <= 1f -> 1f
            normalized <= 2f -> 2f
            normalized <= 5f -> 5f
            else -> 10f
        }
        return multiplier * magnitude
    }

    // Format čisla bez zbytočneho .0 (napr. 20 namiesto 20.0)
    private fun formatNumber(value: Float): String {
        return if (value == floor(value)) value.toInt().toString() else String.format("%.1f", value)
    }

    // Prevod metrov na pixely
    private fun screenX(x: Double): Float = originX + (x * scaleX).toFloat()
    private fun screenY(y: Double): Float = originY - (y * scaleY).toFloat()

    // Vypočita rozsah osi, krok mriežky a mierku podľa veľkosti obrazovky a dat
    private fun updateScale() {
        if (trajectoryPoints.isEmpty() || width == 0 || height == 0) return
        val maxX = trajectoryPoints.maxOf { it.x }.coerceAtLeast(1.0).toFloat()
        val maxY = trajectoryPoints.maxOf { it.y }.coerceAtLeast(1.0).toFloat()

        // Os X: približne 5 deleni, os Y: približne 4 delenia (s 15 % rezervou nad maximom)
        stepX = niceStep(maxX, 5)
        axisMaxX = ceil(maxX / stepX) * stepX
        stepY = niceStep(maxY * 1.15f, 4)
        axisMaxY = ceil(maxY * 1.15f / stepY) * stepY

        scaleX = (width - originX - rightMargin) / axisMaxX
        scaleY = (originY - topMargin) / axisMaxY

        if (keepAspectRatio) {
            // Rovnaka mierka na oboch osiach
            val common = minOf(scaleX, scaleY)
            scaleX = common
            scaleY = common
        } else {
            // Os Y nenaťahujeme priliš, inak by bola krivka priliš strma
            scaleY = minOf(scaleY, scaleX * maxStretch)
        }
    }

    // Pripravi čiaru celej trajektorie v pixeloch
    private fun prepareTrajectoryPath() {
        updateScale()
        trajectoryPath.reset()
        if (trajectoryPoints.isNotEmpty()) {
            val firstPoint = trajectoryPoints.first()
            ballX = firstPoint.x.toFloat()
            ballY = firstPoint.y.toFloat()

            trajectoryPath.moveTo(screenX(firstPoint.x), screenY(firstPoint.y))

            trajectoryPoints.forEach { point ->
                trajectoryPath.lineTo(screenX(point.x), screenY(point.y.coerceAtLeast(0.0)))
            }
        }
    }

    // Zavola sa pri zmene veľkosti komponentu: nastavi počiatok suradnic a prepočita kreslenie
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        originX = 90f // miesto vľavo pre čisla na osi Y
        originY = h - 70f // miesto dole pre zem a čisla na osi X
        prepareTrajectoryPath()
    }

    // Vykreslenie celeho komponentu
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trajectoryPoints.size < 2) return

        val gridTop = originY - axisMaxY * scaleY
        val gridRight = originX + axisMaxX * scaleX

        // Vodorovne čiary mriežky a čisla na osi Y
        labelPaint.textAlign = Paint.Align.RIGHT
        var value = 0f
        while (value <= axisMaxY + 0.001f) {
            val y = originY - value * scaleY
            canvas.drawLine(originX, y, gridRight, y, gridPaint)
            canvas.drawText(formatNumber(value), originX - 12f, y + 10f, labelPaint)
            value += stepY
        }

        // Zvisle čiary mriežky a čisla na osi X
        labelPaint.textAlign = Paint.Align.CENTER
        value = 0f
        while (value <= axisMaxX + 0.001f) {
            val x = originX + value * scaleX
            canvas.drawLine(x, gridTop, x, originY, gridPaint)
            canvas.drawText(formatNumber(value), x, originY + 45f, labelPaint)
            value += stepX
        }

        // Zem
        canvas.drawRect(originX, originY, gridRight, originY + 10f, grassPaint)
        canvas.drawLine(originX, originY, gridRight, originY, groundPaint)

        // Čiarkovana vodiaca čiara celej trajektorie
        canvas.drawPath(trajectoryPath, pathPaint)

        // Najvyšši bod: zobrazi sa až potom, čo ho loptička prejde
        val apexPoint = trajectoryPoints.maxByOrNull { it.y }
        if (apexPoint != null && ballX >= apexPoint.x) {
            val apexX = screenX(apexPoint.x)
            val apexY = screenY(apexPoint.y)
            canvas.drawLine(apexX, apexY, apexX, originY, apexLinePaint)
            canvas.drawText(String.format("%.2f m", apexPoint.y), apexX, apexY - 20f, apexLabelPaint)
        }

        if (currentPointIndex < trajectoryPoints.size) {
            val ballCenterX = screenX(ballX.toDouble())
            val ballCenterY = screenY(ballY.toDouble()) // Invert Y for canvas

            // Stopa od štartu až po aktualnu polohu loptičky
            trailPath.reset()
            trailPath.moveTo(
                screenX(trajectoryPoints.first().x),
                screenY(trajectoryPoints.first().y)
            )
            for (i in 1..currentPointIndex) {
                trailPath.lineTo(
                    screenX(trajectoryPoints[i].x),
                    screenY(trajectoryPoints[i].y.coerceAtLeast(0.0))
                )
            }
            trailPath.lineTo(ballCenterX, ballCenterY)
            canvas.drawPath(trailPath, trailPaint)

            // Tieň na zemi: čim je loptička vyššie, tym je tieň menši
            val shadowSize = 22f * (1f - 0.5f * (ballY / axisMaxY))
            canvas.drawOval(
                ballCenterX - shadowSize,
                originY - 6f,
                ballCenterX + shadowSize,
                originY + 6f,
                shadowPaint
            )

            // Loptička v aktualnej polohe (so žiarou)
            canvas.drawCircle(ballCenterX, ballCenterY, ballRadius * 1.8f, ballGlowPaint)
            canvas.drawCircle(ballCenterX, ballCenterY, ballRadius, ballPaint)
        }
    }

    // Spusti animaciu letu
    private fun startAnimation() {
        if (trajectoryPoints.isEmpty() || trajectoryPoints.size <= 1) return

        // Trvanie animacie podľa skutočneho času letu
        val totalTime = trajectoryPoints.last().time // v sekundach (Double)
        val animationDuration = (totalTime * 1000).toLong() //  v milisekundach

        // Animujeme podľa času (nie podľa indexu bodu), aby sa loptička pohybovala plynulo
        animator = ValueAnimator.ofFloat(0f, totalTime.toFloat()).apply {
            duration = animationDuration.coerceIn(2000L, 6000L) // Min 2s, max 6s
            interpolator = LinearInterpolator() //  konštantna rychlosť
            addUpdateListener { animation ->
                // Na konci animacie použijeme presny čas dopadu, aby sa hodnoty zhodovali so zhrnutim
                val currentTime = if (animation.animatedFraction >= 1f) totalTime
                else (animation.animatedValue as Float).toDouble()
                // Najdeme posledny bod, ktory loptička už prešla
                var index = trajectoryPoints.indexOfLast { it.time <= currentTime }
                if (index < 0) index = 0

                // Medzi tymto a nasledujucim bodom polohu linearne interpolujeme
                if (index < trajectoryPoints.size - 1) {
                    val a = trajectoryPoints[index]
                    val b = trajectoryPoints[index + 1]
                    val dt = b.time - a.time
                    val fraction = if (dt > 0.0) ((currentTime - a.time) / dt).coerceIn(0.0, 1.0) else 0.0
                    ballX = (a.x + (b.x - a.x) * fraction).toFloat()
                    ballY = (a.y + (b.y - a.y) * fraction).toFloat()
                } else {
                    ballX = trajectoryPoints[index].x.toFloat()
                    ballY = trajectoryPoints[index].y.toFloat()
                }
                ballY = ballY.coerceAtLeast(0f) // ball never goes under the ground

                // Žive hodnoty aktualizujeme len pri zmene bodu
                if (index != currentPointIndex) {
                    currentPointIndex = index
                    onPointChanged?.invoke(trajectoryPoints[currentPointIndex])
                }
                invalidate() // vyžiada prekreslenie
            }
        }
        animator?.start()
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }
    // Pri odpojeni komponentu od okna animaciu zastavime
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
