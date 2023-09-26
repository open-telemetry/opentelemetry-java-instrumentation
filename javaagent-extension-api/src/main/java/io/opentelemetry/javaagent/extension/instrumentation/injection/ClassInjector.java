package io.opentelemetry.javaagent.extension.instrumentation.injection;

public interface ClassInjector {

  ProxyInjectionBuilder proxyBuilder(String classToProxy, String newProxyName);

}
