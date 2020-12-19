package library;

/**
 * A class which will not be transformed by our instrumentation due to {@link
 * FieldBackedProviderTest#skipTransformationConditions()}.
 */
public class UntransformableKeyClass extends KeyClass {
  @Override
  public boolean isInstrumented() {
    return false;
  }
}
