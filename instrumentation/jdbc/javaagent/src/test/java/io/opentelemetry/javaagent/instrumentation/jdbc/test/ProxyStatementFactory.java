/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Statement;

public final class ProxyStatementFactory {

  public static Statement proxyStatementWithCustomClassLoader(Statement statement)
      throws Exception {
    TestClassLoader classLoader = new TestClassLoader(ProxyStatementFactory.class.getClassLoader());
    Class<?> testInterface = classLoader.loadClass(TestInterface.class.getName());
    if (testInterface.getClassLoader() != classLoader) {
      throw new IllegalStateException("wrong class loader");
    }
    InvocationHandler invocationHandler = (proxy, method, args) -> method.invoke(statement, args);
    return proxy(
        Statement.class,
        classLoader,
        new Class<?>[] {Statement.class, testInterface},
        invocationHandler);
  }

  public static Statement proxyStatement(InvocationHandler invocationHandler) {
    return proxy(Statement.class, invocationHandler);
  }

  public static PreparedStatement proxyPreparedStatement(PreparedStatement statement) {
    InvocationHandler invocationHandler = (proxy, method, args) -> method.invoke(statement, args);
    return proxyPreparedStatement(invocationHandler);
  }

  public static PreparedStatement proxyPreparedStatement(InvocationHandler invocationHandler) {
    return proxy(PreparedStatement.class, invocationHandler);
  }

  public static <T> T proxy(Class<T> clazz, InvocationHandler invocationHandler) {
    return proxy(
        clazz,
        ProxyStatementFactory.class.getClassLoader(),
        new Class<?>[] {clazz, TestInterface.class},
        invocationHandler);
  }

  public static <T> T proxy(
      Class<T> clazz,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    T proxy = clazz.cast(Proxy.newProxyInstance(classLoader, interfaces, invocationHandler));

    // adding package private interface TestInterface to jdk proxy forces defining the proxy class
    // in the same package as the package private interface
    // by default we ignore jdk proxies, having the proxy in a different package ensures it gets
    // instrumented
    if (!proxy
        .getClass()
        .getName()
        .startsWith("io.opentelemetry.javaagent.instrumentation.jdbc.test")) {
      throw new IllegalStateException("proxy is in wrong package");
    }

    return proxy;
  }

  private ProxyStatementFactory() {}
}
