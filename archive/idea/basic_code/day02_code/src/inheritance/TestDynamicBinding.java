package inheritance;

public class TestDynamicBinding {//动态绑定
    public static void main(String[] args) {
        SimpleAnimal saC = new Cat();
        //SimpleAnimal: declared type 声明类型
        //Cat: actual type 实际类型
        saC.eat("CAT");
//        saC.work();//不能调用Cat的方法
        SimpleAnimal saB = new Bird();
        saB.eat("BIRD");//Bird没有改写SimpleAnimal的方法，所以调用的是声明对象（SimpleAnimal）的方法
        //调用的都是声明对象的方法，如果声明对象的方法被创建对象所重写，就用重写后的方法
        Object oC = new Cat();
//        oC.eat();即使Cat有eat的方法，但Object对象中不存在这个方法也不能被调用
    }
}
