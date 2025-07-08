package com.example.c1

object ApiConfig {
    // 仿真器访问本机服务用10.0.2.2
    //真机则替换为电脑的ip:port
    const val BASE_URL = "http://192.168.51.58:8090/"
    // 植物识别服务地址（请根据实际情况修改）
    const val PLANT_IDENTIFY_URL = "http://192.168.51.58:5000/api/identify"
}
