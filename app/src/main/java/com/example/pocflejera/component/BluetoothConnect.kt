package com.example.pocflejera.component
/*
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Handler
import com.cencosud.smartscan.R
import com.cencosud.smartscan.model.product.Product
import com.cencosud.smartscan.model.qr.QrCreate
import com.cencosud.smartscan.model.sectorPicking.SectorPickingServer
import com.cencosud.smartscan.util.Delegate
import com.cencosud.smartscan.util.UtilNumber.Companion.currency
import com.cencosud.smartscan.util.UtilString.Companion.getIdFleje
import com.cencosud.smartscan.util.UtilString.Companion.getNativeEanThirteen
import com.cencosud.smartscan.util.UtilView.Companion.getValueSharedPreferences
import com.cencosud.smartscan.util.UtilView.Companion.setValueSharedPreferences
import com.cencosud.smartscan.view.dialog.dialogList.DialogList
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.PrinterLanguage
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.ArrayList

class BluetoothConnect constructor(private val context: Activity, private val delegateStatus: Delegate<String>) {

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mmDevice: BluetoothDevice
    private lateinit var mmOutputStream: OutputStream
    private lateinit var mmInputStream: InputStream
    private lateinit var mmSocket: BluetoothSocket
    private lateinit var workerThread: Thread
    private lateinit var readBuffer: ByteArray
    private lateinit var connection: Connection

    private var readBufferPosition = 0
    private var stopWorker = false
    private val println = "\n"
    private val marginLeft = 190

    private lateinit var product: Product
    private lateinit var qrCreate: QrCreate
    private lateinit var sectorPickingServer: SectorPickingServer
    private lateinit var delegateError: Delegate<String>
    private lateinit var delegateProgressBar: Delegate<Boolean>

    private lateinit var dialogList: DialogList

    fun findBT(isQR: Boolean) {
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (mBluetoothAdapter == null) {
                delegateStatus.execute("No bluetooth adapter available")
            }
            if (!mBluetoothAdapter.isEnabled()) {
                val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                context.startActivityForResult(enableBluetooth, 0)
                delegateStatus.execute("finished printing")
            }
            val pairedDevices = mBluetoothAdapter.getBondedDevices()
            val bluetoothDeviceName = getValueSharedPreferences(context, "BluetoothDevice")
            if (pairedDevices.size > 0) {
                if (bluetoothDeviceName.isBlank()) {
                    showDialog(pairedDevices, isQR)
                } else {
                    val bluetoothDevice = pairedDevices.find { it.address.equals(bluetoothDeviceName, true) }
                    if (bluetoothDevice != null) {
                        mmDevice = bluetoothDevice
                        if (!isQR && !product.nativeEan.isNullOrEmpty()) {
                            sendFile(product, delegateError, context, sectorPickingServer)
                        } else {
                            sendFileQR(qrCreate, delegateError, sectorPickingServer, delegateProgressBar)
                        }
                    } else {
                        setValueSharedPreferences(context, "BluetoothDevice", "")
                        showDialog(pairedDevices, isQR)
                    }
                }
            }

            delegateStatus.execute("Bluetooth device found.")
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun showDialog(pairedDevices: MutableSet<BluetoothDevice>, isQR: Boolean) {
        var list: ArrayList<BluetoothDevice> = ArrayList()
        for (device in pairedDevices) {
            list.add(device)
        }
        dialogList = DialogList()
        dialogList.setListDevices(list)
        dialogList.setDelegateCancel(delegateCancel())
        dialogList.setDelegateDevice(delegateDevice(isQR))
        dialogList.show(context.fragmentManager, context.getString(R.string.dialog_confirm))
    }

    private fun delegateCancel(): Delegate<String> {
        val delegateCancel: Delegate<String> = object : Delegate<String> {
            override fun execute(objectValue: String) {
                if (objectValue.equals("cancel")) {
                    delegateError.execute(objectValue)
                }
            }

        }
        return delegateCancel
    }

    private fun delegateDevice(isQR: Boolean): Delegate<BluetoothDevice> {
        val delegateDevice: Delegate<BluetoothDevice> = object : Delegate<BluetoothDevice> {
            override fun execute(device: BluetoothDevice) {
                setValueSharedPreferences(context, "BluetoothDevice", device.address)
                mmDevice = device
                dialogList.dismiss()
                if (isQR) {
                    delegateProgressBar.execute(true)
                    sendFileQR(qrCreate, delegateError, sectorPickingServer, delegateProgressBar)
                } else {
                    sendFile(product, delegateError, context, sectorPickingServer)
                }
            }

        }
        return delegateDevice
    }

    fun openBT() {
        try {

            val uuid: UUID = UUID.fromString(mmDevice.uuids[0].uuid.toString())
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
            mmSocket.connect()
            mmOutputStream = mmSocket.outputStream
            mmInputStream = mmSocket.inputStream
            beginListenForData()
            delegateStatus.execute("Bluetooth Opened: ${mmDevice.name}")
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun beginListenForData() {
        try {
            val handler = Handler()
            val delimiter: Byte = 10
            stopWorker = false
            readBufferPosition = 0
            readBuffer = ByteArray(1024)
            workerThread = Thread {
                while (!Thread.currentThread().isInterrupted && !stopWorker) {
                    try {
                        readInputStream(delimiter, handler)
                    } catch (ex: IOException) {
                        stopWorker = true
                    }
                }
            }
            workerThread.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun readInputStream(delimiter: Byte, handler: Handler) {
        val bytesAvailable = mmInputStream.available()
        if (bytesAvailable > 0) {
            val packetBytes = ByteArray(bytesAvailable)
            mmInputStream.read(packetBytes)
            for (i in 0 until bytesAvailable) {
                val byte = packetBytes[i]
                if (byte == delimiter) {
                    val data = String(getEncodedBytes(), StandardCharsets.UTF_8)
                    readBufferPosition = 0

                    handler.post(Runnable {
                        delegateStatus.execute(data)
                    })
                } else {
                    readBuffer[readBufferPosition++] = byte
                }
            }
        }
    }

    private fun getEncodedBytes(): ByteArray {
        val encodedBytes = ByteArray(readBufferPosition)
        System.arraycopy(
                readBuffer, 0,
                encodedBytes, 0,
                encodedBytes.size
        )
        return encodedBytes
    }

    fun sendFile(product: Product, delegateError: Delegate<String>, context: Activity, sectorPickingServer: SectorPickingServer) {
        this.product = product
        this.delegateError = delegateError
        this.sectorPickingServer = sectorPickingServer
        GlobalScope.launch {
            val printer = openConnection(delegateError, false)
            if (printer != null) {
                val filepath = prepareFile()
                createFile(printer, "fleje.LBL", product, null)
                printer.sendFileContents(filepath!!.absolutePath)
                closeConnection()
            }
        }
    }

    fun sendFileQR(qrCreate: QrCreate, delegateError: Delegate<String>, sectorPickingServer: SectorPickingServer, delegateProgressBar: Delegate<Boolean>) {
        this.delegateError = delegateError
        this.delegateProgressBar = delegateProgressBar
        this.qrCreate = qrCreate
        this.sectorPickingServer = sectorPickingServer
        GlobalScope.launch {
            val printer = openConnection(delegateError, true)
            if (printer != null) {
                val filepath = prepareFile()
                createFile(printer, "fleje.LBL", null, qrCreate)
                printer.sendFileContents(filepath!!.absolutePath)
                closeConnection()
            }
        }
    }

    private fun openConnection(delegateError: Delegate<String>, isQR: Boolean): ZebraPrinter? {
        val bluetoothDeviceName = getValueSharedPreferences(context, "BluetoothDevice")
        if (bluetoothDeviceName.isNotBlank()) {
            try {
                connection = BluetoothConnection(mmDevice.address)
                connection.open()
                return ZebraPrinterFactory.getInstance(connection)
            } catch (e: ConnectionException) {
                delegateError.execute(e.message!!)
            } catch (e: ZebraPrinterLanguageUnknownException) {
                delegateError.execute(e.message!!)
            } catch (e: Exception) {
                findBT(isQR)
            }
        } else {
            findBT(isQR)
        }
        return null
    }

    private fun prepareFile(): File? {
        return context.getFileStreamPath("fleje.LBL")
    }

    private fun createFile(printer: ZebraPrinter, fileName: String, product: Product?, qrCreate: QrCreate?) {
        val fileOutputStream = context.openFileOutput(fileName, Activity.MODE_PRIVATE)
        val printerLanguage = printer.printerControlLanguage
        val idStoreActive = getValueSharedPreferences(context, "idStoreActive")
        if (printerLanguage == PrinterLanguage.ZPL) {
            val configLabel: String
            if (product != null) {
                configLabel = getLabelStrip(product, idStoreActive)
            } else {
                configLabel = getLabelQR(qrCreate)
            }
            fileOutputStream.write(configLabel.toByteArray())
            fileOutputStream.flush()
            fileOutputStream.close()
        } else {
            delegateError.execute("Error createFile")
        }
    }

    private fun getLabelStrip(product: Product, idStoreActive: String): String {
        val configLabel = StringBuilder()
        return configLabel
                .append("^XA")
                .append("^CI28")
                .append("^POI")
                .append(println)
                .append("^A0N,30,30")
                .append("^FO${marginLeft},5")
                .append("^FH")
                .append("^FB480,3,,")
                .append("^FD${product.name?.toUpperCase()}^FS")
                .append(println)
                .append("^A0N, 50")
                .append("^FO${marginLeft},110^FD\$^FS")
                .append(println)
                .append("^A0N,100")
                .append("^FO220,90^FD${product.storePrice?.currency()}^FS")
                .append(println)
                .append("^A0N, 20")
                .append("^FO${marginLeft},190^FDPrecio X ${product.storeUm} ")
                .append("\$${product.storePum?.currency()}^FS")
                .append(println)
                .append("^A0N, 20")
                .append("^FO${marginLeft},215^FD${getIdFleje(idStoreActive)}^FS")
                .append(println)
                .append("^A0N, 40")
                .append("^FO${marginLeft},240^FD${product.sku}^FS")
                .append(println)
                .append("^A0N, 20")
                .append("^FO452,170")
                .append("^FB200,1,0,R")
                .append("^FD${getPositions(product.storeAisle, product.storePosition, product.storePlace, product.storeTray)}^FS")
                .append(println)
                .append("^BY2,2,50^FT460,240^BE,,Y,N")
                .append("^FD${getNativeEanThirteen(product.nativeEan!!)}^FS")
                .append(println)
                .append("^XZ").toString()
    }

    private fun getLabelQR(qrCreate: QrCreate?): String {
        val configLabel = StringBuilder()
        return configLabel
                .append("^XA")
                .append("^CI28")
                .append("^POI")
                .append(println)
                .append("^FT${460}, 250")
                .append("^BQN,2,10")
                .append("^FDMM,A${qrCreate?.Id}^FS")
                .append(println)
                .append("^A0N, 10")
                .append("^FO${460},230")
                .append("^FB210,1,0,C")
                .append("^FD${qrCreate?.Id}^FS")
                .append(println)
                .append("^A0N, 20")
                .append("^FO${460},245")
                .append("^FB210,2,0,C")
                .append("^FD${sectorPickingServer.sectorPicking}^FS")
                .append(println)
                .append("^A0N, 20")
                .append("^FO${460},270")
                .append("^FB210,1,0,C")
                .append("^FD${getPositions(qrCreate?.aisle, qrCreate?.positionQr, qrCreate?.place, qrCreate?.tray)}^FS")
                .append("^XZ")
                .toString()
    }

    private fun getPositions(aisle: String?, position: String?, place: String?, tray: String?): String {
        val separator = "-"
        val configPositions = StringBuilder()
        if (!aisle.isNullOrEmpty()) {
            configPositions.append("${sectorPickingServer.aisleLabel?.get(0)}${aisle}")
        }
        if (!position.isNullOrEmpty()) {
            if (configPositions.isNotEmpty()) {
                configPositions.append(separator)
            }
            configPositions.append("${sectorPickingServer.positionLabel?.get(0)}${position}")
        }
        if (!place.isNullOrEmpty()) {
            if (configPositions.isNotEmpty()) {
                configPositions.append(separator)
            }
            configPositions.append("${sectorPickingServer.placeLabel?.get(0)}${place}")
        }
        if (!tray.isNullOrEmpty()) {
            if (configPositions.isNotEmpty()) {
                configPositions.append(separator)
            }
            configPositions.append("${sectorPickingServer.trayLabel?.get(0)}${tray}")
        }
        return configPositions.toString()
    }

    private fun closeConnection() {
        if(connection.isConnected){
            connection.close()
        }
        delegateStatus.execute("finished printing")
    }
}
*/
