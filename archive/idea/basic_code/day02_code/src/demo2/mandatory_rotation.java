package demo2;

public class mandatory_rotation {
    //取值范围小的，和取值范围大的进行运算，小的会先提升为大的，再进行运算
    //由小到大：byte,short,int,long,float,double
    //byte  short. char. 三种类型的数据在运算的时候，都会直接先提升为int，然后再进行运算
    public static void main(String[] args) {
        byte b1 = 100;
        byte b2 = 100;
        //强制转换成byte类型
        byte result = (byte)(b1 + b2);
        System.out.println(result);  //结果就发生了错误，因为要转换的数据过大。
    }
}
