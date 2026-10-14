package com.kamron.webCrawlerLesson;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameCopy {
    public static void main(String[] args) throws IOException {
/*        制造数据：
        百家姓：https://hanyu.baidu.com/shici/detail?pid=0b2f26d4c0ddb3ee693fdb1137ee1b0d&from=kg0
        男名：http://www.haoming8.cn/baobao/10881.html
        女名：http://www.haoming8.cn/baobao/7641.html
        */
        String familyNameNet = "https://hanyu.baidu.com/shici/detail?pid=0b2f26d4c0ddb3ee693fdb1137ee1b0d&from=kg0";
        String boyNameNet = "http://www.haoming8.cn/baobao/10881.html";
        String girlNameNet = "http://www.haoming8.cn/baobao/7641.html";

        //爬取数据，把网址上的数据拼接成一个字符串
        String familyNameNetStr = webCrawler(familyNameNet);
        String boyNameNetStr = webCrawler(boyNameNet);
        String girlNameNetStr = webCrawler(girlNameNet);

        //通过正则表达式，把其中符合要求的数据获取出来
        ArrayList<String> familyNameTempList = getData(familyNameNetStr, "(\\W{4})(，| 。)", 1);
        ArrayList<String> boyNameTempList = getData(boyNameNetStr, "([\\u4E00-\\u9FA5]{2})(、| 。)", 1);
        ArrayList<String> girlNameTempList = getData(girlNameNetStr, "(.. ){4}..", 0);
        System.out.println("---------------TempName pass-----------");

        //处理数据
        //姓
        //处理方式：把每一个姓氏拆开并添加到一个新的集合当中
        ArrayList<String> familyNameList = new ArrayList<>();
        for (String str : familyNameTempList) {
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                familyNameList.add(c + "");
            }
        }
        //男生名字
        //处理方案：去除其中的重复元素
        ArrayList<String> boyNameList = new ArrayList<>();
        for (String str : boyNameTempList) {
            if (!boyNameList.contains(str)) {
                boyNameList.add(str);
            }
        }
        //女生名字
        ArrayList<String> girlNameList = new ArrayList<>();
        for (String str : girlNameTempList) {
            String[] arr = str.split(" ");
            for (int i = 0; i < arr.length; i++) {
                girlNameList.add(arr[i]);
            }
        }
        System.out.println("---------------Name pass-----------");

        //生成数据
        ArrayList<String> List = getInfos(familyNameList, boyNameList, girlNameList,18,21);
        Collections.shuffle(List);
        System.out.println("---------------List pass-----------");

        //写出数据
        BufferedWriter bw = new BufferedWriter(new FileWriter("names.txt"));
        for (String str : List) {
            bw.write(str );
            bw.newLine();
        }
        bw.close();
    }

    public static ArrayList<String> getInfos(ArrayList<String> familyNameList, ArrayList<String> boyNameList, ArrayList<String> girlNameList, int boyCount, int girlCount) {
        //1.生成不重复的名字
        HashSet<String> boyhs = new HashSet<>();
        while (true) {
            if (boyhs.size() == boyCount) {
                break;
            }
            //随机
            Collections.shuffle(familyNameList);
            Collections.shuffle(boyNameList);
            boyhs.add(familyNameList.get(0) + boyNameList.get(0));
        }
        HashSet<String> girlhs = new HashSet<>();
        while (true) {
            if (girlhs.size() == girlCount) {
                break;
            }
            Collections.shuffle(familyNameList);
            Collections.shuffle(girlNameList);
            girlhs.add(familyNameList.get(0) + girlNameList.get(0));
        }

        ArrayList<String> list = new ArrayList<>();
        Random r = new Random();
        for (String boyName : boyhs) {
            int age = r.nextInt(10) + 18;
            list.add(boyName + " -- boy -- Age: " + age);
        }
        for (String girlName : girlhs) {
            int age = r.nextInt(8) + 18;
            list.add(girlName + " -- girl -- Age: " + age);
        }
        System.out.println("---------------getInfos() pass-----------");
        return list;
    }

    private static ArrayList<String> getData(String str, String regex, int index) {
        ArrayList<String> list = new ArrayList<>();
        //按照正则表达式的规则去获取数据
        Pattern pattern = Pattern.compile(regex);
        //按照pattern的规则到str中获取数据
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            String group = matcher.group(index);
            list.add(group);
        }
        System.out.println("---------------getData() pass-----------");
        return list;
    }

    public static String webCrawler(String net) throws IOException {
        StringBuilder sb = new StringBuilder();
        //创建一个URL对象
        URL url = new URL(net);
        URLConnection conn = url.openConnection();
        //读取数据. 可能会有中文所以不能用字节流，要转换成字符流
        InputStreamReader isr = new InputStreamReader(conn.getInputStream());
        int ch;
        while ((ch = isr.read()) != -1) {
            sb.append((char) ch);
        }
        isr.close();
        System.out.println("---------------webCrawler() pass-----------");
        return sb.toString();
    }
}
