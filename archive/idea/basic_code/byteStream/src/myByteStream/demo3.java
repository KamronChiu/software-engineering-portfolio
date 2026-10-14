package myByteStream;

import java.io.FileWriter;
import java.io.IOException;

public class demo3 {
    public static void main(String[] args) throws IOException {
        //字符输出流
        //Filewriter会清空文件内容
        FileWriter fw = new FileWriter("byteStream/a.txt");
        //8192的时候文件里没有内容，8193就出现了全是a
        //缓存区满了，自动保存到文件（只是把缓冲区的内容保存到文件，第8192-8195位不被保存
        //文件只有8192字节
        for (int i = 0; i < 8195; i++) {
            fw.write(99);
        }
        fw.write("hello");
        //有close才会全部保存文件中
        fw.close();
        System.out.println("----------------------------------------------------");
    }
}
