package demo2;

public class TernaryoperatorDemo1 {
    public static void main(String[] args) {
        //获取两个数的较大值
        int number1 = 10;
        int number2 = 20;
        System.out.println(number1 > number2 ? number1 : number2);

        //定义三个变量记录三个和尚的身高
        int height1 = 150;
        int height2 = 210;
        int height3 = 165;
        int temp = height1 > height2 ? height1 : height2;
        int max = temp > height3 ? temp : height3;
        System.out.println(max);
        //ctrl + alt + L 自动的格式化代码
    }

}
