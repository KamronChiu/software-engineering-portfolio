package inheritance;

public class TestPolymorphism {
    public static void main(String[] args) {
        //第一种情况：用子类的构造方法构建父类对象
        Object o = new Object();
        Object oSA = new SimpleAnimal();
        Object oC =new Cat("Mia",3.1,false,'F');
        SimpleAnimal sA = new SimpleAnimal("Elle", 5, true,'M');
//        SimpleAnimal saO = new Object(); 不可以调用父类的构造方法
        SimpleAnimal saC = new Cat("Jack", 5, true,'F');
        Cat c = new Cat("Toby", 5, true,'M');
        //第二种情况：使用子类对象作为参数传递进方法
        m(sA);//调用自定义的m方法
        m(c);//他的子类也可以调用m方法
//        m(o);但他的父类不可以用，会报错
    }
    public static void m(SimpleAnimal x) {
        x.eat("test");
    }
}
