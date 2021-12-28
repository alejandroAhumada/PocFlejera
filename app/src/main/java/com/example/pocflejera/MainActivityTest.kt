package com.example.pocflejera

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.databinding.DataBindingUtil
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatButton
import android.util.Log
import android.view.View
import android.widget.Toast
import com.cencosud.smartscan.model.product.ProductServer
import com.example.pocflejera.databinding.ActivityMainBinding
import com.example.pocflejera.util.Zpl
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivityTest : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // android built in classes for bluetooth operations
    lateinit var mBluetoothAdapter: BluetoothAdapter
    lateinit var mmDevice: BluetoothDevice
    lateinit var mmSocket: BluetoothSocket

    // needed for communication to bluetooth device / network
    private lateinit var mmOutputStream: OutputStream
    private lateinit var mmInputStream: InputStream
    private lateinit var workerThread: Thread

    @Volatile
    var stopWorker = false

    var readBufferPosition = 0
    var printer: ZebraPrinter? = null
    lateinit var readBuffer: ByteArray

    var lblSelected: String = ""

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
                    binding.etHigth.setText("133")
                    binding.etWidth.setText("9")
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

                    handler.post(Runnable { binding.label.text = data })
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

    private fun sendFile() {
        var connection: Connection? = null
        if (mmDevice.address.isNotEmpty()) {
            connection = BluetoothConnection(mmDevice.address)
        }
        try {
            if (lblSelected != ""){
                if (!binding.etQuantity.text.isNullOrEmpty() && binding.etQuantity.text.toString().toInt() > 0) {
                    Toast.makeText(this, "Sending file to printer ...", Toast.LENGTH_SHORT).show()
                    if (!connection!!.isConnected) {
                        connection.open()
                    }

                    printer = ZebraPrinterFactory.getInstance(connection)
                    while (printer!!.currentStatus.isReadyToPrint) {
                        var j = 0
                        if (!connection.isConnected) {
                            connection.open()
                        }
                        while (j < binding.etQuantity.text.toString().toInt()) {
                            j++

                            binding.labelTotal.text = "${binding.etQuantity.text} / "
                            binding.labelCount.text = j.toString()
                            testSendFile(printer!!)
                        }
                        break
                    }
                }else{
                    binding.etQuantity.setHintTextColor(this.getColor(R.color.colorRed))
                }
                connection!!.close()
            }else{
                binding.etLblSelected.setBackgroundColor(this.getColor(R.color.colorRed))
                binding.etLblSelected.setTextColor(this.getColor(R.color.black))
            }

        } catch (e: ConnectionException) {
            showErrorDialogOnGuiThread(e.message)
        } catch (e: ZebraPrinterLanguageUnknownException) {
            showErrorDialogOnGuiThread(e.message)
        }
    }

    private fun testSendFile(printer: ZebraPrinter) {
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
        } catch (e1: ConnectionException) {
            showErrorDialogOnGuiThread("Error sending file to printer")
        } catch (e: IOException) {
            showErrorDialogOnGuiThread("Error creating file")
        }
    }


    private fun showErrorDialogOnGuiThread(errorMessage: String?) {
        runOnUiThread(Runnable {
            AlertDialog.Builder(this).setMessage(errorMessage).setTitle("Error")
                .setPositiveButton(
                    "OK"
                ) { dialog, id ->
                    dialog.dismiss()
                }.create().show()
        })
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

}