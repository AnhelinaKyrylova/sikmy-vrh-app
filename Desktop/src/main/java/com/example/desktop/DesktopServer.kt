package com.example.desktop

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

const val PORT = 8888
const val GRAVITY = 9.81
const val TIME_STEP = 0.05
const val ERROR_MESSAGE = "Vynimka IOException. Koniec."

fun main() {
    var serverSocket: ServerSocket? = null
    try {
        serverSocket = ServerSocket(PORT)
    } catch (e: IOException) {
        System.err.println(ERROR_MESSAGE)
        exitProcess(1)
    }
    println("Server bezi na porte $PORT")

    while (true) {
        var clientSocket: Socket? = null
        try {
            clientSocket = serverSocket.accept()
        } catch (e: IOException) {
            System.err.println(ERROR_MESSAGE)
            exitProcess(1)
        }
        println("Klient pripojeny: ${clientSocket.inetAddress.hostAddress}")

        try {
            val dis = DataInputStream(clientSocket.getInputStream())
            val dos = DataOutputStream(clientSocket.getOutputStream())

            val rychlost = dis.readDouble()
            val uhol = dis.readDouble()
            println("Prijate: rychlost=$rychlost, uhol=$uhol")

            // Neplatne vstupy -> posleme 0 bodov
            if (rychlost <= 0 || uhol <= 0 || uhol >= 90) {
                println("Neplatne vstupy, posielam 0 bodov")
                dos.writeInt(0)
                dos.flush()
                continue
            }

            val rad = Math.toRadians(uhol)
            val vx = rychlost * cos(rad)
            val vy = rychlost * sin(rad)

            // Presny cas dopadu: y = 0  ->  t = 2*vy/g
            val tLanding = 2 * vy / GRAVITY

            val points = mutableListOf<Triple<Double, Double, Double>>()
            var t = 0.0
            while (t < tLanding) {
                val x = vx * t
                val y = vy * t - 0.5 * GRAVITY * t * t
                points.add(Triple(t, x, y))
                t += TIME_STEP
            }
            // Posledny bod = presny dopad
            points.add(Triple(tLanding, vx * tLanding, 0.0))

            dos.writeInt(points.size)
            for ((time, x, y) in points) {
                dos.writeDouble(time)
                dos.writeDouble(x)
                dos.writeDouble(y)
            }
            dos.flush()
            println("Odoslanych bodov: ${points.size}")
        } catch (e: IOException) {
            System.err.println(ERROR_MESSAGE)
        } finally {
            clientSocket.close()
        }
    }
}