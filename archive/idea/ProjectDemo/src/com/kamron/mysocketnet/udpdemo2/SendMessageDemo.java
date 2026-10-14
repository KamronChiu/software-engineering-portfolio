package com.kamron.mysocketnet.udpdemo2;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class SendMessageDemo {
/*    UDP发送数据：数据来源于键盘录入，直到输入的数据是886，发送数据结束
    UDP接收数据：因为接收端不知道发送端什么时候结束吗，所以死循环接收*/
    public static void main(String[] args) throws IOException {
        DatagramSocket ds = new DatagramSocket();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("请输入你要说的话：");
            String str = sc.nextLine();
            if (str.equals("886")) {
                break;
            }
            byte[] bytes = str.getBytes();
            InetAddress address = InetAddress.getByName("127.0.0.1");
            int port = 8080;
            DatagramPacket dp = new DatagramPacket(bytes, bytes.length, address, port);
            ds.send(dp);
        }
        ds.close();
    }
}
