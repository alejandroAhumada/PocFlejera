package com.cencosud.smartscan.model.serverResponse;

import com.cencosud.smartscan.model.product.ProductServer

data class ProductResponse (
        val internalCode: String?,
        val message: String?,
        val payload: ProductServer?
)