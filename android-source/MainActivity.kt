package com.example.gyrotracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null

    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f
    private var lastTimestamp = 0L

    private var isStreaming by mutableStateOf(false)
    private var pcIp by mutableStateOf("192.168.1.110")
    private var pcPort by mutableStateOf("4242")
    private var sensitivity by mutableStateOf(3.0f)

    private val udpScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("Gyro Head Tracker", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(value = pcIp, onValueChange = { pcIp = it }, label = { Text("PC IP") })
                TextField(value = pcPort, onValueChange = { pcPort = it }, label = { Text("Port") })
                
                Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 1f..10f)
                Text("Sensitivity: ${"%.1f".format(sensitivity)}")

                Button(onClick = { 
                    isStreaming = !isStreaming
                    if (isStreaming) startTracking() else stopTracking()
                }) {
                    Text(if (isStreaming) "Stop" else "Start")
                }

                Text("Yaw: ${"%.2f".format(yaw)}")
                Text("Pitch: ${"%.2f".format(pitch)}")
                Text("Roll: ${"%.2f".format(roll)}")
            }
        }
    }

    private fun startTracking() {
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
    }

    private fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isStreaming) return

        if (lastTimestamp != 0L) {
            val dt = (event.timestamp - lastTimestamp) * 1e-9f
            // Gyro gives rad/s, integrate to get degrees
            yaw += Math.toDegrees(event.values[0].toDouble()).toFloat() * dt * sensitivity
            pitch += Math.toDegrees(event.values[1].toDouble()).toFloat() * dt * sensitivity
            roll += Math.toDegrees(event.values[2].toDouble()).toFloat() * dt * sensitivity
            
            sendUdp(yaw, pitch, roll)
        }
        lastTimestamp = event.timestamp
    }

    private fun sendUdp(y: Float, p: Float, r: Float) {
        udpScope.launch {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(pcIp)
                val portInt = pcPort.toIntOrNull() ?: 4242
                
                val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putFloat(y)
                buffer.putFloat(p)
                buffer.putFloat(r)
                buffer.putFloat(0f)
                buffer.putFloat(0f)
                buffer.putFloat(0f)
                
                val packet = DatagramPacket(buffer.array(), buffer.limit(), address, portInt)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
