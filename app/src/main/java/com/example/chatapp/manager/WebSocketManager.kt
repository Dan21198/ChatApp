package com.example.chatapp.manager

import android.util.Log
import com.example.chatapp.ChatActivity
import com.example.chatapp.api.ApiService
import com.example.chatapp.listener.MessageListener
import retrofit2.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Callback
import java.util.concurrent.TimeUnit

class WebSocketManager(private val messageListener: MessageListener) {
    private var webSocket: WebSocket? = null

    fun connectWebSocket(webSocketUrl: String, token: String, queues: List<String>) {
        val request = Request.Builder()
            .url(webSocketUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val client = OkHttpClient()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                super.onOpen(webSocket, response)
                // Subscribe to each queue when WebSocket opens
                queues.forEach { queue ->
                    webSocket.send("SUBSCRIBE $queue")
                    Log.d("WebSocketManager", "Subscribed to $queue")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                // Handle received messages
                Log.d("WebSocketManager", "Received message: $text")
                messageListener.onMessageReceived(text) // Add this line
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                super.onFailure(webSocket, t, response)
                // Handle WebSocket failure
                Log.e("WebSocketManager", "WebSocket failure: ${t.message}")
            }
        })
    }

    fun closeWebSocket() {
        webSocket?.cancel()
    }

    fun onMessageReceived(message: String) {
        Log.d("WebSocketManager", "Message received: $message")
        messageListener.onMessageReceived(message)
    }
}




