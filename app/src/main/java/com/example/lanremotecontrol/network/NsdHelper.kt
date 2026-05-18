package com.example.lanremotecontrol.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class NsdHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_lanremote._tcp."
    private val SERVICE_NAME = "LanRemoteHost"

    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerService(port: Int) {
        tearDown()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("NSD", "Service Registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSD", "Registration Failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("NSD", "Service Unregistered")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSD", "Unregistration Failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    var onServiceFound: ((NsdServiceInfo) -> Unit)? = null
    var onServiceLost: ((NsdServiceInfo) -> Unit)? = null

    fun discoverServices() {
        stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Discovery Started")
            }

            // Suppress deprecation warning for resolveService, as the new API is complex for this use case
            @Suppress("DEPRECATION")
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NSD", "Service Found: ${service.serviceName}")
                if (service.serviceType == SERVICE_TYPE) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NSD", "Resolve Failed: $errorCode")
                        }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            // FIX: Use hostAddresses instead of the deprecated host property
                            val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                serviceInfo.hostAddresses.firstOrNull()
                            } else {
                                serviceInfo.host
                            }
                            Log.d("NSD", "Service Resolved: $hostAddress : ${serviceInfo.port}")
                            onServiceFound?.invoke(serviceInfo)
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NSD", "Service Lost: ${service.serviceName}")
                onServiceLost?.invoke(service)
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.i("NSD", "Discovery Stopped")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) { }
        }
        discoveryListener = null
    }

    fun tearDown() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) { }
        }
        registrationListener = null
        stopDiscovery()
    }
}