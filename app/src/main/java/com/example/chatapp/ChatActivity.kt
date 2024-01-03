package com.example.chatapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.chatapp.Adapter.ChatRoomAdapter
import com.example.chatapp.api.ApiService
import com.example.chatapp.model.ChatDTO
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.ChatRoom
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class ChatActivity : AppCompatActivity() {

    private lateinit var textOutput: TextView
    private lateinit var textInput: EditText
    private lateinit var btnSend: Button
    private lateinit var editTextNewRoom: EditText
    private lateinit var btnCreateRoom: Button
    private lateinit var listViewChatRooms: ListView
    private lateinit var apiService: ApiService
    private var fetchedChatDTOs: List<ChatDTO>? = null
    private lateinit var chatRoomAdapter: ChatRoomAdapter
    private val listOfChatDTOs: MutableList<ChatDTO> = mutableListOf()
    private var selectedChatRoomId: Long = -1L
    private var lastSelectedChatRoomId: Long = -1L
    private val chatMessagesMap: MutableMap<Long, MutableList<String>> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)
        val viewToDetectSwipe: View = findViewById(R.id.viewToDetectSwipe)

        drawerLayout.closeDrawer(GravityCompat.START)

        viewToDetectSwipe.setOnTouchListener(object : OnSwipeTouchListener(this@ChatActivity) {
            override fun onSwipeRight() {
                super.onSwipeRight()
                drawerLayout.openDrawer(GravityCompat.START)
            }
        })

        listViewChatRooms = findViewById(R.id.listChatRooms)
        btnSend = findViewById(R.id.btnSend)
        textInput = findViewById(R.id.textInput)
        editTextNewRoom = findViewById(R.id.editTextNewRoom)
        btnCreateRoom = findViewById(R.id.btnCreateRoom)
        chatRoomAdapter = ChatRoomAdapter(this, listOfChatDTOs)
        listViewChatRooms.adapter = chatRoomAdapter

        val accessToken = getStoredAccessToken()
        val refreshToken = getStoredRefreshToken()

        initializeRetrofit(accessToken)
        initializeViews()

        Log.d("ChatActivity", "Before fetchExchanges()")
        fetchExchanges()
        setupCreateRoomClickListener()
        setupButtonClickListener()
        setupChatRoomClickListener()
    }

    private fun initializeRetrofit(accessToken: String?) {
        val httpClient = OkHttpClient.Builder()
        if (accessToken != null) {
            httpClient.addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .method(original.method(), original.body())
                val request = requestBuilder.build()
                chain.proceed(request)
            }
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080")
            .client(httpClient.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    private fun getStoredAccessToken(): String? {
        val sharedPreferences = getSharedPreferences("auth", Context.MODE_PRIVATE)
        return sharedPreferences.getString("accessToken", null)
    }

    private fun getStoredRefreshToken(): String? {
        val sharedPreferences = getSharedPreferences("auth", Context.MODE_PRIVATE)
        return sharedPreferences.getString("refreshToken", null)
    }

    private fun setupCreateRoomClickListener() {
        btnCreateRoom.setOnClickListener {
            val roomName = editTextNewRoom.text.toString().trim()
            if (roomName.isNotEmpty()) {
                createChatRoom(roomName)
            } else {

            }
        }
    }

    private fun createChatRoom(roomName: String) {
        apiService.createChat(roomName).enqueue(object : Callback<ChatRoom> {
            override fun onResponse(call: Call<ChatRoom>, response: Response<ChatRoom>) {
                if (response.isSuccessful) {
                    val createdChatRoom: ChatRoom? = response.body()
                    if (createdChatRoom != null) {
                        if (createdChatRoom.chatName != null) {
                            Log.d("ChatRoomCreation", "Chat room created: ${createdChatRoom.chatName}")
                        } else {
                            Log.e("ChatRoomCreation", "Chat name is null")
                        }
                    } else {
                        Log.e("ChatRoomCreation", "Empty response body")
                    }
                } else {
                    val errorMessage = "Failed to create chat room: ${response.code()}"
                    Log.e("ChatRoomCreation", errorMessage)
                }
            }

            override fun onFailure(call: Call<ChatRoom>, t: Throwable) {
                val errorMessage = "Network failure: ${t.message}"
                Log.e("ChatRoomCreation", errorMessage)
            }
        })
    }

    private fun fetchExchanges() {
        apiService.getExchanges().enqueue(object : Callback<List<ChatDTO>> {
            override fun onResponse(call: Call<List<ChatDTO>>, response: Response<List<ChatDTO>>) {
                if (response.isSuccessful) {
                    val exchanges = response.body()
                    exchanges?.let {
                        listOfChatDTOs.clear()
                        listOfChatDTOs.addAll(it)
                        chatRoomAdapter.notifyDataSetChanged()
                    }
                } else {
                    Log.e("ChatActivity", "Failed to fetch exchanges: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<ChatDTO>>, t: Throwable) {
                Log.e("ChatActivity", "Network failure: ${t.message}")
            }
        })
    }

    private fun setupChatRoomClickListener() {
        listViewChatRooms.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = listViewChatRooms.adapter.getItem(position)
            val id = when (selectedItem) {
                is String -> findChatDtoByChatName(selectedItem)?.chat?.id ?: -1L
                is ChatDTO -> selectedItem.chat?.id ?: -1L
                else -> -1L
            }

            selectedChatRoomId = id
            displayMessagesForSelectedChatRoom(selectedChatRoomId)
        }
    }

    private fun findChatDtoByChatName(chatName: String): ChatDTO? {
        Log.d("ChatActivity", "Searching for chat with name: $chatName")
        val foundChat = fetchedChatDTOs?.firstOrNull { it.chat?.chatName == chatName }
        if (foundChat != null) {
            Log.d("ChatActivity", "Found chat: ${foundChat.chat?.chatName}")
        } else {
            Log.d("ChatActivity", "Chat not found")
        }
        return foundChat
    }

    private fun initializeViews() {
        textOutput = findViewById(R.id.textOutput)
        textInput = findViewById(R.id.textInput) ?: throw IllegalStateException("textInput not found")
        btnSend = findViewById(R.id.btnSend)
    }

    private fun getSenderIdForSelectedChat(selectedChatRoom: ChatDTO?): Long {
        return selectedChatRoom?.chat?.owner?.id ?: -1L
    }

    private fun displayMessage(message: String) {
        val currentText = textOutput.text.toString()
        val newText = "$currentText\n$message"
        textOutput.text = newText
    }

    private fun sendMessage(chatRoomId: Long, senderId: Long, content: String) {
        val message = ChatMessage(type = "text", chatId = chatRoomId, senderId = senderId, content = content)

        apiService.sendMessage(message).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    displayMessageForSelectedChatRoom(content)
                    textInput.text.clear()
                } else {
                    Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@ChatActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayMessageForSelectedChatRoom(message: String) {
        if (selectedChatRoomId != -1L) {
            if (!chatMessagesMap.containsKey(selectedChatRoomId)) {
                chatMessagesMap[selectedChatRoomId] = mutableListOf()
            }
            chatMessagesMap[selectedChatRoomId]?.add(message)
            displayMessagesForSelectedChatRoom(selectedChatRoomId)
        }
    }

    private fun displayMessagesForSelectedChatRoom(chatRoomId: Long) {
        val messages = chatMessagesMap[chatRoomId]
        val formattedMessages = messages?.joinToString("\n")

        textOutput.text = formattedMessages ?: ""
    }

    private fun setupButtonClickListener() {
        btnSend.setOnClickListener {
            val selectedChatRoom = listOfChatDTOs.find { it.chat?.id == selectedChatRoomId }
            val senderId = getSenderIdForSelectedChat(selectedChatRoom)
            val content = textInput.text.toString().trim()

            if (selectedChatRoomId != -1L && senderId != -1L && content.isNotEmpty()) {
                sendMessage(selectedChatRoomId, senderId, content)
            } else {
                Toast.makeText(this, "Please select a chat room and enter a message", Toast.LENGTH_SHORT).show()
            }
        }
    }

}