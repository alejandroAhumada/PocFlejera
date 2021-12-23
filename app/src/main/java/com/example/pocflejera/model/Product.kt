package com.cencosud.smartscan.model.product

data class Product(
        val ean: String,
        val sku: String?,
        val id: String?,
        var name: String?,
        val imageUrl: String?,
        val brandName: String?,
        val refId: String?,
        val department: Int?,
        val isFraction: Boolean?,
        val amount: Int?,
        val discount: String?,
        val quantity: Int?,
        val nativeEan: String?,
        var storeUm: String?,
        val storePrice: Int?,
        val storePum: Int?,
        val storeLastUpdate: String?,
        var storeAisle: String?,
        var storeSectorPicking: String?,
        var storePlace: String?,
        var storePosition: String?,
        var storeTray: String?
)