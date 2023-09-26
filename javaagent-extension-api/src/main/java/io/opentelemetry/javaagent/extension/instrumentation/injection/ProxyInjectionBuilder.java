package io.opentelemetry.javaagent.extension.instrumentation.injection;

public interface ProxyInjectionBuilder {

  void inject(InjectionMode mode);
}
