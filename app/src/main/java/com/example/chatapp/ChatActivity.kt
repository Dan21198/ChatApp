package com.example.chatapp


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
import com.example.chatapp.listener.MessageListener
import com.example.chatapp.manager.ExchangeManager
import com.example.chatapp.manager.MessageManager
import com.example.chatapp.manager.RetrofitManager
import com.example.chatapp.manager.TokenManager
import com.example.chatapp.manager.WebSocketManager
import com.example.chatapp.model.ChatDTO
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.ChatRoom
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class ChatActivity : AppCompatActivity(), MessageListener {

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
    private lateinit var messageManager: MessageManager
    private lateinit var exchangeManager: ExchangeManager
    private val chatMessagesMap: MutableMap<Long, MutableList<String>> = mutableMapOf()
    private lateinit var webSocketManager: WebSocketManager

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

        initializeRetrofit()
        initializeViews()

        messageManager = MessageManager(apiService)
        exchangeManager = ExchangeManager(apiService)
        webSocketManager = WebSocketManager(this)

        /*webSocketManager = WebSocketManager()
        val webSocketUrl = "ws://10.0.2.2:8080/ws-message/websocket"
        val accessToken = TokenManager.getAccessToken() ?: ""
        webSocketManager.connectWebSocket(webSocketUrl, accessToken)

        webSocketManager.setMessageListener { message ->
            Log.d("ChatActivity", "Received message in ChatActivity: $message")
        }

        webSocketManager.fetchQueuesAndSubscribe(apiService)*/

        fetchQueuesAndInitializeWebSocket()
        fetchExchanges()
        setupCreateRoomClickListener()
        setupButtonClickListener()
        setupChatRoomClickListener()
        //fetchAndSubscribeToQueues()
        //connectWebSocket()
    }

    private fun fetchQueuesAndInitializeWebSocket() {
        apiService.getQueues().enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful) {
                    val queues = response.body()
                    queues?.let {
                        val webSocketManager = WebSocketManager(this@ChatActivity)
                        val webSocketUrl = "ws://10.0.2.2:8080/ws-message/websocket"
                        val accessToken = TokenManager.getAccessToken() ?: ""
                        webSocketManager.connectWebSocket(webSocketUrl, accessToken, it)
                    }
                } else {
                    Log.e("WebSocketManager", "Failed to fetch queues: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                Log.e("WebSocketManager", "Network error while fetching queues: ${t.message}")
            }
        })
    }

    override fun onMessageReceived(message: String) {
        Log.d("ChatActivity", "Received message: $message")
    }

    private fun initializeRetrofit() {
        TokenManager.initSharedPreferences(this)
        val accessToken = TokenManager.getAccessToken()
        apiService = RetrofitManager.createRetrofitClient(accessToken)
    }

    private fun connectWebSocket() {
        val webSocketUrl = "http://10.0.2.2:8080/ws-message"
        Log.d("ChatActivity", "Connecting to WebSocket")
        TokenManager.getAccessToken()?.let { token ->
           // webSocketManager.connectWebSocket(webSocketUrl, token)
        }
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
                            Log.d(
                                "ChatRoomCreation",
                                "Chat room created: ${createdChatRoom.chatName}"
                            )
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
        exchangeManager.fetchExchanges(
            onSuccess = { exchanges ->
                listOfChatDTOs.clear()
                listOfChatDTOs.addAll(exchanges)
                chatRoomAdapter.notifyDataSetChanged()
            },
            onFailure = { errorMessage ->
                Log.e("ChatActivity", errorMessage)
            }
        )
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
        textInput =
            findViewById(R.id.textInput) ?: throw IllegalStateException("textInput not found")
        btnSend = findViewById(R.id.btnSend)
    }

    private fun getSenderIdForSelectedChat(selectedChatRoom: ChatDTO?): Long {
        return selectedChatRoom?.chat?.owner?.id ?: -1L
    }

    private fun displayMessageForSelectedChatRoom(message: String) {
        updateChatMessages(message)
        updateChatRoomDisplay(selectedChatRoomId)
    }

    private fun updateChatMessages(message: String) {
        chatMessagesMap.getOrPut(selectedChatRoomId) { mutableListOf() }.add(message)
    }

    private fun updateChatRoomDisplay(chatRoomId: Long) {

        val messages = chatMessagesMap[chatRoomId]
        val formattedMessages = messages?.joinToString("\n")

        textOutput.text = formattedMessages ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.closeWebSocket()
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

}
