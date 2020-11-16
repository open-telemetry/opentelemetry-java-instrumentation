package io.opentelemetry.javaagent.bootstrap;

public interface TransformationListener {
  void onDiscovery(String typeName, ClassLoader classLoader);

  void onTransformation(String actualName, boolean matchedGlobalIgnoreMatcher);

  void onError(String typeName, ClassLoader classLoader, Throwable throwable);
}
