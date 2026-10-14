package inheritance;

public class SimpleAnimal extends Object{
    public String name;
    protected double weight;
    boolean adopted;
    private char gender;

   private static int noOfObjects;
   public SimpleAnimal() {noOfObjects++;}  //default只可以在同一个包被调用

    public SimpleAnimal(String name, double weight, boolean adopted, char gender) {
        this.name = name;
        this.weight = weight;
        this.adopted = adopted;
        this.gender = gender;
        noOfObjects++;
    }
    public static int getNoOfObjects() {
       return noOfObjects;
   }

    public char getGender() {
        return gender;
    }

    public void setGender(char gender) {
        this.gender = gender;
    }

    public boolean isAdopted() {
        return adopted;
    }

    public void setAdopted(boolean adopted) {
        this.adopted = adopted;
    }
    @Override
    public String toString() {
       return "This is an Animal";
    }
    public void eat(String food) {
        System.out.println(name+"is eating " + food);
    }
}
