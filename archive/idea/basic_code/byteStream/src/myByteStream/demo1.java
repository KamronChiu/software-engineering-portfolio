package myByteStream;

import java.io.FileOutputStream;
import java.io.IOException;

public class demo1 {
    public static void main(String[] args) throws IOException {
        /*字节输出流：FileOutputStream
        * 实现要求：写出一段文字到本地文件中
        *
        * 步骤：
        *           创建对象
        *           写出数据
        *           释放资源
        * */

        //1.创建对象
        FileOutputStream fos = new FileOutputStream("byteStream/a.txt");
        //2.写出数据
        fos.write(100);
        fos.close();
    }
}
