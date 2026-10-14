package myByteStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class demo2 {
    public static void main(String[] args) throws IOException {
        /*void write(int b)     一次写一个字节数据
        *void write(byte[]  b)     一次写多个字节数组数据
        * void write(byte[ ]  b, int off, int len)      一次写一个字节数组的部分数据
        * */

        FileOutputStream fos = new FileOutputStream("byteStream/a.txt");
        byte[] bytes = {97,98,99,100};
        fos.write(bytes);
        fos.close();
    }
}
