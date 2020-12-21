package library;

/**
 * A class which will not be transformed by our instrumentation due to, see
 * FieldBackedProviderTest's skipTransformationConditions() method.
 */
public class UntransformableKeyClass extends KeyClass {
  @Override
  public boolean isInstrumented() {
    return false;
  }
}
