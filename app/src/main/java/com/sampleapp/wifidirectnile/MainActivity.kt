package com.sampleapp.wifidirectnile

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sampleapp.wifidirectnile.Utils.Logger
import com.sampleapp.wifidirectnile.ui.theme.WifiDirectNileTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val intentFilter =

        IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        }


    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var manager: WifiP2pManager

    private lateinit var receiver : WifiBroadcastReceiver

    private var clientClass : ClientClass? = null
    private var serverClass : ServerClass? = null



    private val peers = MutableStateFlow(listOf<WifiP2pDevice>())
    private val isHost = MutableStateFlow(false)

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList.toList()
        Logger.log("on peers listener new list size: ${refreshedPeers.size}")
        if (refreshedPeers != peers.value) {
            peers.value = refreshedPeers
        }

        if (peers.value.isEmpty()) {
            Logger.log("NO DEVICES FOUND")
            return@PeerListListener
        }
    }

    private val wifiConnectionInfoListener = WifiP2pManager.ConnectionInfoListener {wifiP2pInfo ->
        val groupOwnerAddress = wifiP2pInfo.groupOwnerAddress
        if(wifiP2pInfo.isGroupOwner && wifiP2pInfo.isGroupOwner){
            Logger.log("You are host")
            isHost.value = true
            clientClass = null
            serverClass = ServerClass()
            serverClass?.start()
        } else if(wifiP2pInfo.groupFormed){
            Logger.log("You are client")
            isHost.value = false
            serverClass = null
            clientClass = ClientClass(groupOwnerAddress)
            clientClass?.start()
        }
    }


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        setContent {
            WifiDirectNileTheme {

                val lifecycle = LocalLifecycleOwner.current

                val devices by peers.collectAsState()

                val messageState = remember{
                    mutableStateOf("")
                }
                
                DisposableEffect(key1 = lifecycle){

                    val observer = LifecycleEventObserver { source, event ->

                        when(event){

                            Lifecycle.Event.ON_RESUME -> {

                                receiver = WifiBroadcastReceiver(
                                    onPeersChanged = {
                                        Logger.log("ON PEERS CHANGED")
                                    manager.requestPeers(channel, peerListListener)
                                }, onDeviceChanged = {device ->

                                    Logger.log("ON DEVICE CHANGED: ${device.deviceName}")

                                }, onConnectionInfo = {
                                    manager.requestConnectionInfo(channel, wifiConnectionInfoListener)
                                })
                                registerReceiver(receiver, intentFilter)


                            }

                            Lifecycle.Event.ON_PAUSE -> {

                                unregisterReceiver(receiver)

                            }

                            else ->{}
                        }
                    }

                    lifecycle.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycle.lifecycle.removeObserver(observer)
                    }

                }
                


                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {


                    Column(Modifier.fillMaxSize()) {

                        Button(onClick = { manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {

                            override fun onSuccess() {

                                Logger.log("Success on discovery")

                            }

                            override fun onFailure(reasonCode: Int) {
                                Logger.log("Fail on discovery")
                            }
                        }) }) {

                            Text(text = "Start discovery")

                        }


                        LazyColumn(Modifier.weight(1f)){


                            items(devices){device ->

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(text = device.deviceName, modifier = Modifier.clickable {

                                    val config = WifiP2pConfig().apply {
                                        deviceAddress = device.deviceAddress
                                        wps.setup = WpsInfo.PBC
                                    }

                                    manager.connect(channel, config, object : WifiP2pManager.ActionListener {

                                        override fun onSuccess() {
                                            Logger.log("SUCCESS ON CONNECTION")
                                        }

                                        override fun onFailure(reason: Int) {
                                            Logger.log("FAIL ON CONNECT")
                                        }
                                    })
                                })


                                Spacer(modifier = Modifier.height(10.dp))

                                Divider()

                            }
                        }

                        InputText(textViewState = messageState){
                            if(isHost.value){
                                serverClass?.sendMessage(messageState.value.toByteArray())
                            } else {
                                clientClass?.sendMessage(messageState.value.toByteArray())
                            }
                        }


                    }


                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InputText(
        textViewState : MutableState<String>,
        onSendMessage : () -> Unit
    ){

        Row (Modifier.fillMaxWidth()){
            TextField(
                modifier = Modifier.weight(1f),
                value = textViewState.value,
                onValueChange = {
                textViewState.value = it
            })

            IconButton(onClick = onSendMessage) {

                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")

            }
        }



    }

    class ServerClass() : Thread() {

        private lateinit var inputStream : InputStream
        private lateinit var outputStream : OutputStream
        private lateinit var socket : Socket
        private lateinit var serverSocket : ServerSocket

        fun sendMessage(bytes: ByteArray){

            outputStream.write(bytes)

        }


        override fun run() {
            super.run()

            try {
                serverSocket = ServerSocket(8888)
                socket = serverSocket.accept()
                inputStream = socket.getInputStream()
                outputStream = socket.getOutputStream()

            } catch (e: Exception){
                Logger.log("FAILED TO CONNECT TO SOCKET")
            }


            val executor = Executors.newSingleThreadExecutor()

            val handler = Handler(Looper.getMainLooper())

            executor.execute(kotlinx.coroutines.Runnable {

                val buffer = ByteArray(1024)
                var bytes : Int

                while (socket.isConnected){
                    bytes = inputStream.read(buffer)

                    if(bytes > 0){

                        val finalBytes = bytes

                        handler.post(kotlinx.coroutines.Runnable {

                            val message = String(buffer, 0, finalBytes)

                            Logger.log("message: $message")

                        })
                    }
                }
            })
        }
    }


    class ClientClass(hostAddress : InetAddress) : Thread() {

        val hostAdd = hostAddress.hostAddress

        val socket = Socket()

        private lateinit var inputStream : InputStream
        private lateinit var outputStream : OutputStream

        fun sendMessage(bytes: ByteArray){

            outputStream.write(bytes)

        }


        override fun run() {
            super.run()

            try {
                socket.connect(InetSocketAddress(hostAdd, 8888), 500)
                inputStream = socket.getInputStream()
                outputStream = socket.getOutputStream()

            } catch (e: Exception){
                Logger.log("FAILED TO CONNECT TO SOCKET")
            }


            val executor = Executors.newSingleThreadExecutor()

            val handler = Handler(Looper.getMainLooper())

            executor.execute(kotlinx.coroutines.Runnable {

                val buffer = ByteArray(1024)
                var bytes : Int

                while (socket.isConnected){
                    bytes = inputStream.read(buffer)

                    if(bytes > 0){

                        val finalBytes = bytes

                        handler.post(kotlinx.coroutines.Runnable {

                            val message = String(buffer, 0, finalBytes)

                            Logger.log("message: $message")

                        })
                    }
                }
            })
        }
    }

}

