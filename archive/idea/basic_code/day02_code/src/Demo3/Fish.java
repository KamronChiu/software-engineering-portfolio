package Demo3;

import inheritance.SimpleAnimal;

public class Fish extends SimpleAnimal {
    Fish(){super();}
    Fish(String name, double weight, boolean adopted, char gender) {
//        this.name = name;
//        this.weight = weight;
//        this.adopted = adopted;
//        this.gender = gender;
        super(name, weight, adopted, gender);//有私有属性的要用这种调用方法
    }

    public static void main(String[] args) {
        SimpleAnimal sa2 = new SimpleAnimal("Kate",8.8,false,'F');
        Fish f = new Fish("Kate",8.8,false,'F');
        System.out.println(f.name);
        System.out.println(f.weight);
//        System.out.println(f.adopted); //报错
        System.out.println(f.isAdopted());
        System.out.println(f.getNoOfObjects());
    }
}
