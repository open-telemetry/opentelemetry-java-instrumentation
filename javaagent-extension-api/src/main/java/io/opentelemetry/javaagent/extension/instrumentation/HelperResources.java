package io.opentelemetry.javaagent.extension.instrumentation;

public interface HelperResources {

  /**
   * Registers a resource to be injected in the user's class loader.
   *
   * <p>This is a convenience method for {@code register(resourceName, resourceName)}.
   */
  default void register(String resourceName) {
    register(resourceName, resourceName);
  }

  /**
   * Registers a resource to be injected in the user's class loader.
   *
   * <p>{@code resourceName} and {@code resourceNameForContent} can be the same, but it is often
   * desirable to use a slightly different name or path for the resource file provided by the
   * instrumentation so that multiple instrumentation modules (e.g. applying to different versions
   * of a particular library) can co-exist inside the agent jar file.
   *
   * @param resourceName the name of the resource to inject in to the user's class loader
   * @param resourceNameForContent the name of the instrumentation resource which provides the
   *     content for injection
   */
  void register(String resourceName, String resourceNameForContent);
}
