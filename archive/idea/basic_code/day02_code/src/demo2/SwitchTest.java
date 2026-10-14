package demo2;
import java.util.Scanner;
public class SwitchTest {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入星期");
        int week = sc.nextInt();
//        switch (week) {
//            case 1:
//                System.out.println("跑步");
//                break;
//            case 2:
//                System.out.println("游泳");
//                break;
//            case 3:
//                System.out.println("慢走");
//                break;
//            case 4:
//                System.out.println("动感单车");
//                break;
//            case 5:
//                System.out.println("拳击");
//                break;
//            case 6:
//                System.out.println("爬山");
//                break;
//            case 7:
//                System.out.println("好好吃一顿");
//                break;
//            default:
//                System.out.println("没有这个日期");
//                break;
//        }
                switch (week) {
            case 1->System.out.println("跑步");
            case 2->System.out.println("游泳");
            case 3->System.out.println("慢走");
            case 4->System.out.println("动感单车");
            case 5->System.out.println("拳击");
            case 6->System.out.println("爬山");
            case 7 ->System.out.println("好好吃一顿");
            default ->System.out.println("没有这个日期");
        }


    }


}
