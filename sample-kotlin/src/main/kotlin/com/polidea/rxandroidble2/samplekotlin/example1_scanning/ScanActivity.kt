package com.polidea.rxandroidble2.samplekotlin.example1_scanning

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.samplekotlin.DeviceActivity
import com.polidea.rxandroidble2.samplekotlin.R
import com.polidea.rxandroidble2.samplekotlin.SampleApplication
import com.polidea.rxandroidble2.samplekotlin.example1a_background_scanning.BackgroundScanActivity
import com.polidea.rxandroidble2.samplekotlin.example7_long_write.LongWriteExampleActivity
import com.polidea.rxandroidble2.samplekotlin.util.isScanPermissionGranted
import com.polidea.rxandroidble2.samplekotlin.util.requestScanPermission
import com.polidea.rxandroidble2.samplekotlin.util.showError
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_example1.*
import java.util.*

class ScanActivity : AppCompatActivity() {

    private val rxBleClient = SampleApplication.rxBleClient

    private var scanDisposable: Disposable? = null

    private val resultsAdapter = ScanResultsAdapter { startActivity(DeviceActivity.newInstance(this, it.bleDevice.macAddress)) }

    private var hasClickedScan = false

    private val isScanning: Boolean
        get() = scanDisposable != null

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val ACCESS_LOCATION_REQUEST = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example1)
        configureResultList()

        background_scan_btn.setOnClickListener { startActivity(BackgroundScanActivity.newInstance(this)) }
        scan_toggle_btn.setOnClickListener {
            // onScanToggleClick()
            // 未打开蓝牙
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                // 检查权限
                checkPermissions()
            }
        }

        long_write_btn?.setOnClickListener {
            startActivity(Intent(this, LongWriteExampleActivity::class.java))
        }
    }

    private fun configureResultList() {
        with(scan_results) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = resultsAdapter
        }
    }

    private fun onScanToggleClick() {
        if (isScanning) {
            scanDisposable?.dispose()
        } else {
            if (rxBleClient.isScanRuntimePermissionGranted) {
                scanBleDevices()
            } else {
                hasClickedScan = true
                requestScanPermission(rxBleClient)
            }
        }
        updateButtonUIState()
    }

    private fun scanBleDevices() {
        // 设置
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        // 这是过滤器
        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress("64:B7:08:D0:33:FE")
            // .setDeviceName("BOSS")
            // add custom filters if needed
            .build()

        rxBleClient.scanBleDevices(scanSettings, scanFilter)
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { dispose() }
            .subscribe({ resultsAdapter.addScanResult(it) }, { onScanFailure(it) })
            .let { scanDisposable = it }
    }

    // onError() or onComplete()会触发doFinally
    private fun dispose() {
        scanDisposable = null
        // 这里没必要清空扫描的结果
        // resultsAdapter.clearScanResults()
        updateButtonUIState()
    }

    // 如果还未打开蓝牙，会在这里报错
    /**
     *  The library does not handle managing the state of the BluetoothAdapter.
    Direct managing of the state is not recommended as it violates the application user's right to manage the state of their phone. See Javadoc of BluetoothAdapter.enable() method.
    It is the user's responsibility to inform why the application needs Bluetooth to be turned on and for ask the application's user consent.
    It is possible to show a native activity for turning the Bluetooth on by calling:

    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    int REQUEST_ENABLE_BT = 1;
    context.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
     */
    private fun onScanFailure(throwable: Throwable) {
        if (throwable is BleScanException) showError(throwable)
        else Log.w("ScanActivity", "Scan failed", throwable)
    }

    private fun updateButtonUIState() =
        scan_toggle_btn.setText(if (isScanning) R.string.button_stop_scan else R.string.button_start_scan)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (isScanPermissionGranted(requestCode, grantResults) && hasClickedScan) {
            hasClickedScan = false
            scanBleDevices()
        }
    }

    public override fun onPause() {
        super.onPause()
        // Stop scanning in onPause callback.
        if (isScanning) scanDisposable?.dispose()
    }


    /**
     * 检查蓝牙是否可用
     */
    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter: BluetoothAdapter = getBluetoothManager().adapter ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun getBluetoothManager(): BluetoothManager {
        return Objects.requireNonNull(getSystemService(BLUETOOTH_SERVICE) as BluetoothManager, "cannot get BluetoothManager")
    }

    /**
     * Android改动得有点乱，根据版本来处理权限
     */
    private fun getRequiredPermissions(): Array<String> {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions: Array<String> = getMissingPermissions(getRequiredPermissions())
            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
            } else {
                onScanToggleClick()
            }
        }
    }
}
