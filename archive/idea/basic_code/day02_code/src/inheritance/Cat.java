package inheritance;

import Demo3.Animal;

public class Cat extends SimpleAnimal {
    public Cat() {}
    public Cat(String name, double weight, boolean adopted, char gender) {
        super(name, weight, adopted, gender);
    }
    @Override
    public String toString() {
        return "This is a Cat called  "+ name;
    }
    @Override
    public void eat(String food) {
        System.out.println("A cat is eating " + food);
    }
    public void eat(){
        eat("small fish");//运行14行
        this.eat("large fish");//运行14行
        super.eat("Sharks");//调用的是父类的方法，当方法同名（重载）的时候，用this和super区分是调用哪里的方法
    }
    public void work(){
        System.out.println("A cat is chasing mice");
    }
}
