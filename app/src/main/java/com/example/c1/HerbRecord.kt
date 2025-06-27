package com.example.c1

import java.util.Date

data class HerbRecord(
    val id: String,
    val herbName: String,
    val locationCount: String,
    val temperature: String,
    val humidity: String,
    val district: String,
    val street: String,
    val growthDescription: String,
    val longitude: Double,
    val latitude: Double,
    val collectionTime: Date,
    val imagePath: String,
    val status: String = "已上传"
) 