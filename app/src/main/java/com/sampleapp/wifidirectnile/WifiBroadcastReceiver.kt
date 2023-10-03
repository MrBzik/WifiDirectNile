package com.sampleapp.wifidirectnile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import com.sampleapp.wifidirectnile.Utils.Logger

class WifiBroadcastReceiver(
    private val onPeersChanged: () -> Unit,
    private val onDeviceChanged : (WifiP2pDevice) -> Unit,
    private val onConnectionInfo : () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {


        when(intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {


                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                Logger.log("PTP ENABLED: ${state == WifiP2pManager.WIFI_P2P_STATE_ENABLED}")
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {

                Logger.log("ANYTHING??")

                onPeersChanged()

            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                val networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?

                networkInfo?.let {

                    if(it.isConnected)
                        onConnectionInfo()
                    else
                        Logger.log("NOT CONNECTED")

                }



            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {


                val device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice?

                device?.let {
                    onDeviceChanged(it)
                }


//                (activity.supportFragmentManager.findFragmentById(R.id.frag_list) as DeviceListFragment)
//                    .apply {
//                        updateThisDevice(
//                            intent.getParcelableExtra(
//                                WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice
//                        )
//                    }
            }
        }
    }
}