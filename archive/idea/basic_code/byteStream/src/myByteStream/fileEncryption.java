package myByteStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class fileEncryption {
    public static void main(String[] args) throws IOException {
        /*加密原理：对原文件中每一个字节进行修改，然后将更改后的存到新的文件中
         * 解密原理：读取到文件之后，按照加密的规则反向操作，变回原始文件
         * ⊕：异或^     两边相同false，两边不同true
         *
         *       100:               1100100
         *       10:            ^   0001010
         *                        -------------------
         *                             1101110
         *                        ^   0001010
         *                        -------------------
         *                            1100100
         * 异或一次加密，异或同一个数字解密
         * */
        FileInputStream fis = new FileInputStream("file name");
        FileOutputStream fos = new FileOutputStream("file name");
        //加密处理
        int b;
        while ((b = fis.read()) != -1) {
            fos.write(b ^ 2);
        }
        fos.close();
        fis.close();

    }
}
