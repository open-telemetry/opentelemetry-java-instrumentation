package context;

public class UserClass2 {
  public boolean isInstrumented() {
    return false;
  }

  public int incrementContextCountCountBroken() {
    // instrumentation will not apply to this class because advice incorrectly uses context api
    return -1;
  }
}
