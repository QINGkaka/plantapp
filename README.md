# plantapp

这是一个使用andriod原生框架开发的《中草药信息采集与识别app》
使用andriod studio运行调试：分为仿真机调试和真机调试
采用真机无线调试
首先打开手机开发者模式，打开无线调试，确保手机和电脑处于同一局域网内

![手机调试](./images/手机调试.jpg)

指令1（只需要执行一次，配对即可）：**adb pair 192.168.1.17:37713**    （图中“与设备配对”的IP和端口，并输入配对码）
Enter pairing code: **382767**
Successfully paired to 192.168.1.17:37713 [guid=adb-c9931dc7-xbHbYA]

指令2（若手机下线则需要重新执行）：**adb connect 192.168.1.17:41899** （实际IP和端口）
connected to 192.168.1.17:41899

指令3（查看设备状态）：**adb devices**
List of devices attached
192.168.1.17:41899 device
adb-c9931dc7-xbHbYA._adb-tls-connect._tcp device

连接成功后，在andriod studio中run  左侧可以选择仿真机Pixel或者真机redmi等等

![andriod studio显示](./images/5EB8A123B5A759BD0D39139914B5B578.jpg)

运行效果：

![图片1](./images/533563AD818EBC4E30A6F625A16FB4F3.jpg)![图片2](./images/FC8C725A4DB3C857CD3F9211A7518AE4.jpg)![图片3](./images/FC93E7EE432522F937DC368B9E52D09B.jpg)
运行植物识别功能时，需要运行“识别项目的app.py”

记得修改本项目代码中请求的IP地址和端口为实际IP+端口