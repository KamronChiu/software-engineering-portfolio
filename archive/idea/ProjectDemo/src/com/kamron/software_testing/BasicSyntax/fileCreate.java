package com.kamron.software_testing.BasicSyntax;

import java.io.File;
import java.io.IOException;

public class fileCreate {
    public static void main(String[] args) throws IOException {
        //在当前文件下的aaa文件夹创建一个a.txt文件

        //创建a.txt的父级路径
        File file = new File("addFileDemo");
        //创建父级路径。如果aaa存在那么创建失败，如果不存在创建成功
        file.mkdir();

        //拼接父级路径和子级路径
        File src = new File(file, "a.txt");
        boolean b = src.createNewFile();
        if (b){
            System.out.println("success");
        }else {
            System.out.println("fail");
        }

    }
}
