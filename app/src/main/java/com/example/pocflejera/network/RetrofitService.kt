package com.cencosud.smartscan.model.network;

import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitService {

    // TODO move to firestore
    private val BASE_URL = "https://api.smdigital.cl:8443";

    private val api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()

    fun providerScanAndGoApi(): ScanAndGoApi {
        return api.create(ScanAndGoApi::class.java)
    }
}
