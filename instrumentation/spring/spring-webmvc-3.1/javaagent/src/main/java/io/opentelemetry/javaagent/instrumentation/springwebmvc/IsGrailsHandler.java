package io.opentelemetry.javaagent.instrumentation.springwebmvc;

public final class IsGrailsHandler {

  private static final ClassValue<Boolean> cache =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          return type.getName().startsWith("org.grails.");
        }
      };

  public static boolean isGrailsHandler(Object handler) {
    return cache.get(handler.getClass());
  }

  private IsGrailsHandler() {}
}
