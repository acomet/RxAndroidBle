package com.polidea.rxandroidble2.samplekotlin.example2_connection

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.internal.RxBleLog
import com.polidea.rxandroidble2.internal.logger.LoggerUtil
import com.polidea.rxandroidble2.samplekotlin.R
import com.polidea.rxandroidble2.samplekotlin.SampleApplication
import com.polidea.rxandroidble2.samplekotlin.util.*
import com.polidea.rxandroidble2.samplekotlin.util.isConnected
import com.polidea.rxandroidble2.samplekotlin.util.isConnectionPermissionGranted
import com.polidea.rxandroidble2.samplekotlin.util.requestConnectionPermission
import com.polidea.rxandroidble2.samplekotlin.util.showSnackbarShort
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_example2.autoconnect
import kotlinx.android.synthetic.main.activity_example2.connect_toggle
import kotlinx.android.synthetic.main.activity_example2.connection_state
import kotlinx.android.synthetic.main.activity_example2.newMtu
import kotlinx.android.synthetic.main.activity_example2.set_mtu

private const val EXTRA_MAC_ADDRESS = "extra_mac_address"

class ConnectionExampleActivity : AppCompatActivity() {

    companion object {
        fun newInstance(context: Context, macAddress: String) =
            Intent(context, ConnectionExampleActivity::class.java).apply {
                putExtra(EXTRA_MAC_ADDRESS, macAddress)
            }
    }

    private lateinit var bleDevice: RxBleDevice

    private var connectionDisposable: Disposable? = null

    private var stateDisposable: Disposable? = null

    private val mtuDisposable = CompositeDisposable()

    private var hasClickedConnect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example2)
        connect_toggle.setOnClickListener { onConnectToggleClick() }
        set_mtu.setOnClickListener { onSetMtu() }

        val macAddress = intent.getStringExtra(EXTRA_MAC_ADDRESS)
        title = getString(R.string.mac_address, macAddress)
        bleDevice = SampleApplication.rxBleClient.getBleDevice(macAddress!!)

        // How to listen for connection state changes
        // Note: it is meant for UI updates only — one should not observeConnectionStateChanges() with BLE connection logic
        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onConnectionStateChange(it) }
            .let { stateDisposable = it }
    }

    private fun onConnectToggleClick() {
        if (bleDevice.isConnected) {
            triggerDisconnect()
        } else {
            if (SampleApplication.rxBleClient.isConnectRuntimePermissionGranted) {
                connect()
            } else {
                hasClickedConnect = true
                requestConnectionPermission(SampleApplication.rxBleClient)
            }
        }
    }

    private fun connect() {
        bleDevice.establishConnection(autoconnect.isChecked)
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { dispose() }
            .subscribe(
                {
                    onConnectionReceived()
                },
                {
                    onConnectionFailure(it)
                }).let { connectionDisposable = it }
    }

    @TargetApi(21 /* Build.VERSION_CODES.LOLLIPOP */)
    private fun onSetMtu() {
        newMtu.text.toString().toIntOrNull()?.let { mtu ->
            bleDevice.establishConnection(false)
                .flatMapSingle { rxBleConnection -> rxBleConnection.requestMtu(mtu) }
                .take(1) // Disconnect automatically after discovery
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { updateUI() }
                .subscribe({ onMtuReceived(it) }, { onConnectionFailure(it) })
                .let { mtuDisposable.add(it) }
        }
    }

    private fun onConnectionFailure(throwable: Throwable) {
        logger("---1---" + throwable.message)
        RxBleLog.i("---2---" + throwable.message)
        showSnackbarShort("Connection error: $throwable")
    }

    private fun onConnectionReceived() {
        showSnackbarShort("Connection received")
    }

    private fun onConnectionStateChange(newState: RxBleConnection.RxBleConnectionState) {
        connection_state.text = newState.toString()
        updateUI()
    }

    private fun onMtuReceived(mtu: Int) = showSnackbarShort("MTU received: $mtu")

    private fun dispose() {
        connectionDisposable = null
        updateUI()
    }

    private fun triggerDisconnect() = connectionDisposable?.dispose()

    private fun updateUI() {
        connect_toggle.setText(if (bleDevice.isConnected) R.string.button_disconnect else R.string.button_connect)
        autoconnect.isEnabled = !bleDevice.isConnected
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (isConnectionPermissionGranted(requestCode, grantResults) && hasClickedConnect) {
            hasClickedConnect = false
            connect()
        }
    }

    override fun onPause() {
        super.onPause()
        triggerDisconnect()
        mtuDisposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        stateDisposable?.dispose()
    }
}
