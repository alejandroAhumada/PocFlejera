package com.example.pocflejera

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.example.pocflejera.databinding.ActivityMainBinding
import java.io.IOException

class MainActivityTest : AppCompatActivity() {

    /*private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        initializeComponent()
    }

    private fun initializeComponent() {
        binding.open.setOnClickListener {
            try {
                findBT()
                openBT()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }

        // send data typed by the user to be printed
        binding.send.setOnClickListener {
            try {
                //sendData()
                //sendFile()
                onclickSendButton()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }

        // close bluetooth connection
        binding.close.setOnClickListener(View.OnClickListener {
            try {
                closeBT()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        })
    }*/
}