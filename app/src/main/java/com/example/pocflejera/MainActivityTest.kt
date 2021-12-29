package com.example.pocflejera

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatButton
import android.view.View
import com.example.pocflejera.databinding.ActivityMainBinding
import com.example.pocflejera.util.Zpl
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivityTest : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityMainBinding

    // android built in classes for bluetooth operations
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mmDevice: BluetoothDevice
    private lateinit var mmSocket: BluetoothSocket

    // needed for communication to bluetooth device / network
    private lateinit var mmOutputStream: OutputStream
    private lateinit var mmInputStream: InputStream
    private lateinit var workerThread: Thread

    @Volatile
    var stopWorker = false

    private var readBufferPosition = 0
    private var printer: ZebraPrinter? = null
    private lateinit var readBuffer: ByteArray

    private var lblSelected: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        initializeComponent()
        findBT()
        openBT()
    }

    private fun initializeComponent() {
        binding.open.setOnClickListener(onClickOpen())
        binding.send.setOnClickListener(onClickSend())
        binding.close.setOnClickListener(onClickClose())
        binding.lblProduct.setOnClickListener(onClickLbl())
        binding.lblChristmas.setOnClickListener(onClickLbl())
        binding.lblBag.setOnClickListener(onClickLbl())
    }

    @SuppressLint("NewApi")
    private fun onClickLbl(): View.OnClickListener {
        return View.OnClickListener {
            when ((it as AppCompatButton).text) {
                "Logo Producto" -> {
                    lblSelected = Zpl.getLogoProducto()
                    binding.etHigth.setText("170")
                    binding.etWidth.setText("7")
                    binding.etLblSelected.setBackgroundColor(this.getColor(R.color.white))
                }
                "Logo Navidad" -> {
                    lblSelected = Zpl.getLogoNavidad()
                    binding.etHigth.setText("210")
                    binding.etWidth.setText("7")
                    binding.etLblSelected.setBackgroundColor(this.getColor(R.color.white))
                }
                "Logo Bolsa" -> {
                    lblSelected = Zpl.getLogoBolsa()
                    binding.etHigth.setText("168")
                    binding.etWidth.setText("40")
                    binding.etLblSelected.setBackgroundColor(this.getColor(R.color.white))
                }
            }

            binding.etLblSelected.text = "Imprimir ${it.text}"
        }
    }

    private fun onClickOpen(): View.OnClickListener {
        return View.OnClickListener {
            findBT()
            openBT()
        }
    }

    private fun onClickSend(): View.OnClickListener {
        return View.OnClickListener {
            sendFile()
        }
    }

    private fun onClickClose(): View.OnClickListener {
        return View.OnClickListener {
            closeBT()
        }
    }

    private fun findBT() {
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (mBluetoothAdapter == null) {
                binding.label.text = "No bluetooth adapter available"
            }
            if (!mBluetoothAdapter.isEnabled) {
                val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBluetooth, 0)
            }
            val pairedDevices = mBluetoothAdapter.bondedDevices
            if (pairedDevices.size > 0) {
                for (device in pairedDevices) {

                    // we got this name from the list of paired devices
                    if (device.name == "ZQ630-DEV") {
                        mmDevice = device
                        break
                    }
                }
            }
            binding.label.text = "Bluetooth device found."
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun openBT() {
        try {
            val uuid: UUID =
                UUID.fromString(mmDevice.uuids[0].uuid.toString()) // Standard SerialPortService ID
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid)
            mmSocket.connect()
            mmOutputStream = mmSocket.outputStream
            mmInputStream = mmSocket.inputStream
            beginListenForData()
            binding.label.text = "Bluetooth Opened: ${mmDevice.name}"
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

                    handler.post { binding.label.text = data }
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

    @SuppressLint("NewApi")
    private fun sendFile() {
        var connection: Connection? = null
        if (mmDevice.address.isNotEmpty()) {
            connection = BluetoothConnection(mmDevice.address)
        }
        launch {
            try {
                if (lblSelected != "") {
                    if (!binding.etQuantity.text.isNullOrEmpty() && binding.etQuantity.text.toString().toInt() > 0) {
                        sendOneToOne(connection)
                    } else {
                        binding.etQuantity.setHintTextColor(this@MainActivityTest.getColor(R.color.colorRed))
                    }
                    connection!!.close()
                } else {
                    binding.etLblSelected.setBackgroundColor(this@MainActivityTest.getColor(R.color.colorRed))
                    binding.etLblSelected.setTextColor(this@MainActivityTest.getColor(R.color.black))
                }

            } catch (e: ConnectionException) {
                showErrorDialogOnGuiThread(e.message)
                sendFile()
            } catch (e: ZebraPrinterLanguageUnknownException) {
                showErrorDialogOnGuiThread(e.message)
            }
        }

    }

    private fun sendOneToOne(connection: Connection?) {
        if (!connection!!.isConnected) {
            connection.open()
        }

        printer = ZebraPrinterFactory.getInstance(connection)
        while (printer!!.currentStatus.isReadyToPrint) {
            var index = 0
            if (!connection.isConnected) {
                connection.open()
            }
            while (index < binding.etQuantity.text.toString().toInt()) {
                index++
                runOnUiThread {
                    binding.labelTotal.text = "${binding.etQuantity.text} / "
                }
                testSendFile(printer!!, index)
            }
            break
        }
    }

    private fun testSendFile(printer: ZebraPrinter, index: Int) {
        try {
            val nameFile = "fleje.LBL"
            val filepath = getFileStreamPath(nameFile)
            Zpl.getZpl(
                nameFile,
                this,
                lblSelected,
                binding.etHigth.text.toString(),
                binding.etWidth.text.toString()
            )
            printer.sendFileContents(filepath.absolutePath)
            runOnUiThread {
                binding.labelCount.text = index.toString()
            }
        } catch (e1: ConnectionException) {
            showErrorDialogOnGuiThread("Error sending file to printer")
        } catch (e: IOException) {
            showErrorDialogOnGuiThread("Error creating file")
        }
    }


    private fun showErrorDialogOnGuiThread(errorMessage: String?) {
        runOnUiThread {
            AlertDialog.Builder(this).setMessage(errorMessage).setTitle("Error")
                .setPositiveButton(
                    "OK"
                ) { dialog, id ->
                    dialog.dismiss()
                }.create().show()
        }
    }

    private fun closeBT() {
        try {
            stopWorker = true
            mmOutputStream.close()
            mmInputStream.close()
            mmSocket.close()
            binding.label.text = "Bluetooth Closed"
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

}