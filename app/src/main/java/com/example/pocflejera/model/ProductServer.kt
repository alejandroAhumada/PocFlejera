package com.cencosud.smartscan.model.product

import com.example.pocflejera.model.Store

data class ProductServer (
    val ean: String,
    val sku: String?,
    val id: String?,
    val name: String?,
    val imageUrl: String?,
    val brandName: String?,
    val refId: String?,
    val department: Int?,
    val isFraction: Boolean?,
    val amount: Int?,
    val discount: String?,
    val quantity: Int?,
    val nativeEan: String?,
    val stores: Map<String, Store>
) {
    fun getProduct(): Product? {

        return Product(
                ean,
                sku,
                id,
                name,
                imageUrl,
                brandName,
                refId,
                department,
                isFraction,
                amount,
                discount,
                quantity,
                nativeEan,
                stores!!.get(stores!!.keys.first())!!.um,
                stores!!.get(stores!!.keys.first())!!.price,
                stores!!.get(stores!!.keys.first())!!.pum,
                stores!!.get(stores!!.keys.first())!!.lastUpdate,
                stores!!.get(stores!!.keys.first())!!.aisle,
                stores!!.get(stores!!.keys.first())!!.sectorPicking,
                stores!!.get(stores!!.keys.first())!!.place,
                stores!!.get(stores!!.keys.first())!!.position,
                stores!!.get(stores!!.keys.first())!!.tray
        )
    }
}