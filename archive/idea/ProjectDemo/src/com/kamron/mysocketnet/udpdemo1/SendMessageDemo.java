package com.kamron.mysocketnet.udpdemo1;

import java.io.IOException;
import java.net.*;

public class SendMessageDemo {
    public static void main(String[] args) throws IOException {
        //发送数据

        //1.创建DatagramSocket对象（快递公司）
        //绑定端口，以后我们就是通过这个端口往外发送·
        //空参：所有可用的端口中随机一个进行使用
        //有参：指定端口进行绑定
        DatagramSocket ds = new DatagramSocket();

        //打包数据
        String str = "Kamron";
        //打包只接受字节数组
        byte[] bytes = str.getBytes();
        InetAddress address = InetAddress.getByName("127.0.0.1");
        int port = 10086;

        DatagramPacket dp = new DatagramPacket(bytes, bytes.length, address, port);

        //发送数据
        ds.send(dp);

        //释放资源
        ds.close();
    }
}
