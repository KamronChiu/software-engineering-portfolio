package inheritance;

public class TestTypeCasting {//类型转换
    public static void main(String[] args) {
        SimpleAnimal sa = new SimpleAnimal("Ben",16,true,'M');
        SimpleAnimal saC = new Cat("Mia",3.1,true,'M');
        SimpleAnimal saB = new Bird();
        if(sa instanceof Cat) {
            ((Cat)sa).work();
        }else{
            System.out.println("It is not a cat.");
        }
        if(sa instanceof Cat) {
            ((Cat)saC).work();
        }else{
            System.out.println("It is a cat, it catching mice.");
        }
        if(sa instanceof Cat) {
            ((Cat)saB).work();
        }else{
            System.out.println("It is not a cat.");
        }
//        ((Cat)saB).work(); //编译器不报错，运行报错
    }
}
