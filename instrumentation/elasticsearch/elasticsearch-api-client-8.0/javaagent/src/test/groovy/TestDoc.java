public class TestDoc {
  private String keyA;
  private int keyB;

  public TestDoc(String keyA, int keyB) {
    this.keyA = keyA;
    this.keyB = keyB;
  }

  public String getKeyA() {
    return keyA;
  }

  public TestDoc setKeyA(String keyA) {
    this.keyA = keyA;
    return this;
  }

  public int getKeyB() {
    return keyB;
  }

  public TestDoc setKeyB(int keyB) {
    this.keyB = keyB;
    return this;
  }
}
