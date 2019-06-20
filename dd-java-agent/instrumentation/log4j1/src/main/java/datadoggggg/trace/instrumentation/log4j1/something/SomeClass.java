package datadoggggg.trace.instrumentation.log4j1.something;

public class SomeClass {

  private static SomeClass instance = new SomeClass();

  public SomeClass() {
    System.out.println("SomeClass Constructor.......!!!!!!!");
  }

  public static void put() {
    instance.doSomething();
  }

  public void doSomething() {
    System.out.println("SomeClass Doing something............");
  }
}
