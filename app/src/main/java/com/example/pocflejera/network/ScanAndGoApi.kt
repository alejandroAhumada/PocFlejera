package com.cencosud.smartscan.model.network

import com.cencosud.smartscan.model.serverResponse.ProductResponse
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ScanAndGoApi {
    @Headers(
            "Content-Type: application/json",
            "x-api-key: yL285dvequN1GLt1yUCdpiHy8L8lJOlO"
    )
    @GET("v1/cl/syg/products/productPicking?")
    fun getProduct(@Query("ean") ean: String, @Query("storeId") storeId: String): Single<ProductResponse>
}