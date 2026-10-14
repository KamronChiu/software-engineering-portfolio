package Demo3;

public class ArryOfObject {
    public static void main(String[] args) {
        int i;
        i = 77;
        int[] arr;
        arr = new int[10];
        Animal[] listA = new Animal[10];
        listA[1] = new Animal("Wang",5.8);
        listA[1].c = new Circle();
        System.out.println(i);
        System.out.println(arr[1]);
        System.out.println(listA[1]);
        System.out.println(listA[1].c.radius);
    }
}
