package com.example.chatapp.listener

import android.util.Log
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject

class MyWebSocketListener : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        super.onOpen(webSocket, response)
        // Handle WebSocket opened event
        println("WebSocket opened")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val body = JSONObject(text)
            val bodyParsed = body.getString("body")

        } catch (e: JSONException) {
            Log.e("WebSocket", "Error parsing message: ${e.message}")
        }
    }
}