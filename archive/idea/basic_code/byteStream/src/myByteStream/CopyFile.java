package myByteStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CopyFile {
            /*文件拷贝
            * 把/Users/friedrichkamron/Desktop/photos/China/HongKong/IMG_8541.MOV文件拷贝到当前模块下
            * */
    public static void main(String[] args) throws IOException {
        //创建对象：
        FileInputStream fis = new FileInputStream("/Users/friedrichkamron/Desktop/photos/China/HongKong/IMG_8541.MOV");
        FileOutputStream fos = new FileOutputStream("byteStream/copy.MOV");
        int b;
        //拷贝：核心思想：边读边写
        while ((b = fis.read()) != -1) {
            fos.write(b);
        }
        //释放资源：规则：先开的最后关闭
        fos.close();
        fis.close();
    }
}
