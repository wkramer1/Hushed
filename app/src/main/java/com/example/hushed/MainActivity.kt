package com.example.hushed

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.hushed.models.Messages
import kotlinx.android.synthetic.main.activity_main.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.scheduleAtFixedRate

class MainActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
        .collection("db")
    private val dummyMessages = listOf( Messages(
        sender = "Frient Unit 1",
        message = "Hey there!"
    ), Messages(
        sender = "Parental Unit 1",
        message = "Please call me back"
    ), Messages(
        sender = "Friend Unit 2",
        message = "Just wanted to let you know..."
    ), Messages(
        sender = "Sibling Unit 1",
        message = "Please don't tell Parental Unit 1 about this"
    ), Messages(
        sender = "Friend Unit 3",
        message = "Bruh!"
    ), Messages(
        sender = "Group Member 1",
        message = "Need the report to be finished soon"
    ), Messages(
        sender = "Parental Unit 2",
        message = "See you this weekend"
    ), Messages(
        sender = "Gandalf the Grey",
        message = "You Shall Not PASS!"
    ))

    private var dummyData = dummyMessages.map {it.sender to (arrayListOf(hashMapOf("received" to it.message)))}.toMap()
    // A timer lets us schedule repeated actions
    private var timer: Timer = Timer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DataSource.setDeviceID(checkAddress().toString())

        // CONNECT BUTTON **************************************************************************
        button_connect.setOnClickListener {
            Log.i("Button","Click: button_connect")


            db.document(DataSource.getDeviceID()).get()
                .addOnSuccessListener { doc ->
                    // Get current list of conversations
                    // initially, this is empty.
                    var conversationList = DataSource.getConversationList()
                    var conversationMap = DataSource.getConversations()
                    var ownId = DataSource.getDeviceID()

                    for((key, value) in doc.data.orEmpty()) {
                        var allMessages = doc.get(key) as ArrayList<HashMap<*, *>>
                        var convo = ArrayList<Messages>()
                        var partnerId = key

                        Log.i("messages", "Got " + allMessages.size + " messages for convo with " + key)
                        // Load all messages in conversation into memory
                        for (messageData in allMessages) {
                            convo.add(extractMessage(messageData, partnerId, ownId))
                        }

                        // Add modified version of last message from conversation to list
                        // where we set the 'sender' to be the partner,
                        // regardless of who actually sent that message
                        // so when it is displayed in the list,
                        // it shows the user who the conversation is with
                        // todo: translate ID to name of partner, and display that instead
                        var lastMsg = convo[convo.size-1]
                        conversationList.add(Messages(lastMsg.message, partnerId))

                        // Store entire conversation into dataset
                        conversationMap[partnerId] = convo

                    }

                    // Wait 10 seconds, and repeat some stuff every 10 seconds to get new live data
                    // for testing, we can do every second to see faster responses.
//                    var task = timer.scheduleAtFixedRate(1*1000L, 1 * 1000L) {
                    // Todo: Extract lambda into its own method
                    var task = timer.scheduleAtFixedRate(10*1000L, 10 * 1000L) {
                        Log.i("database", "refreshing database")
                        db.document(DataSource.getDeviceID()).get()
                            .addOnSuccessListener { doc ->
                                Log.i("database", "Got new data!");
                                var conversationChanged = false
                                var conversationList = DataSource.getConversationList()
                                var conversationMap = DataSource.getConversations()

                                for((key, value) in doc.data.orEmpty()) {
                                    var allMessages = doc.get(key) as ArrayList<HashMap<*, *>>
                                    var partnerId = key
                                    // check if we are missing a conversation:

                                    if (conversationMap.containsKey(key)) {

                                        // we already have this conversation, check if we're missing new messages
                                        var convo = conversationMap[key]!!
                                        if (allMessages.size > convo.size) {
                                            conversationChanged = key.equals(DataSource.getViewingConversation());
                                            Log.i("database", "new messages for " + partnerId + "!");
                                            // New messages coming in !
                                            // for (i in convo.size..allMessages.size) {

                                            // Add new messages into the conversation
                                            var i = convo.size
                                            while (i < allMessages.size) {
                                                var messageData = allMessages[i]
                                                convo.add(extractMessage(messageData, partnerId, ownId))
                                                i++
                                            }

                                            var lastMsg = convo[convo.size-1]
                                            var msg = Messages("---", "NOT_FOUND")
                                            // Find the conversation object for the updated conversation
                                            for (i in conversationList.indices) {
                                                if (conversationList[i].sender == partnerId) {
                                                    msg = conversationList[i]
                                                }
                                            }
                                            // and remove it...
                                            conversationList.remove(msg)
                                            // and add it back into the beginning of the list
                                            // to bump it to the 'top' of the list.
                                            conversationList.add(0, msg)
                                            // update message object with last message
                                            msg.message = lastMsg.message
                                        }


                                    } else {
                                        Log.i("database", "new conversation with " + partnerId + "!");
                                        var convo = ArrayList<Messages>()
                                        // we don't yet have this conversation, so add the entire conversation to local data.
                                        for (messageData in allMessages) {
                                            convo.add(extractMessage(messageData, partnerId, ownId))
                                        }

                                        // Add conversation to list so it can be seletected
                                        var lastMsg = convo[convo.size-1]
                                        conversationList.add(0, Messages(lastMsg.message, partnerId))

                                        // Store entire conversation into dataset
                                        conversationMap[partnerId] = convo
                                    }
                                }

                                // If the conversation that was being viewed has changed,
                                // call whatever was waiting for that message
                                if (conversationChanged) {
                                    DataSource.conversationUpdated()
                                }
                            }
                    }

                    // Start next activity
                    val intent = Intent(this, MessageActivity::class.java)
                    startActivity(intent)

                }
                .addOnFailureListener { e -> Log.w("Firebase", "Error retrieving document", e) }
        }

        // SETTINGS BUTTON **************************************************************************
        button_settings.setOnClickListener {
            println("SETTINGS BUTTON")
            Log.i("Button","Click: button_settings")
        }

        // ABOUT BUTTON **************************************************************************
        button_about.setOnClickListener {
            Log.i("Button","Click: button_about")
        }

        // Dummy Send BUTTON **************************************************************************
        button_dummy.setOnClickListener {

            for((key, value) in dummyData) {
                db.document(DataSource.getDeviceID()).set(hashMapOf(key to value),
                    SetOptions.merge())
                    .addOnSuccessListener { Log.d("Firebase", "DocumentSnapshot successfully written!") }
                    .addOnFailureListener { e -> Log.w("Firebase", "Error writing document", e) }
            }

            Log.i("Button","Click: button_dummy")
        }
    }

    // Turn data from the database into a Messages object that can be displayed in a conversation
    private fun extractMessage(messageData: HashMap<*,*>, partnerId: String, ownId: String): Messages {
        var str = "[NO MESSAGE]"
        var sender = "[NO SENDER]"
        if (messageData.containsKey("received") && messageData["received"] is String) {
            str = messageData["received"] as String
            sender = partnerId
        }
        if (messageData.containsKey("sent") && messageData["sent"] is String) {
            str = messageData["sent"] as String
            sender = ownId
        }

        var msg = Messages(
            sender = sender,
            message = str
        )

        return msg
    }

    private fun checkAddress(): String? {
        val prefInfo = getPreferences(Context.MODE_PRIVATE)

        if(prefInfo.getString("UUID", null) != null) {
            return prefInfo.getString("UUID", null)
        }
        else {
            var myID = UUID.randomUUID().toString()
            prefInfo?.edit()?.putString("UUID", myID)?.apply()
            return myID
        }
    }
}
