/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.classloading.SharedRequestPathFactory;
import io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.classloading.WebAppRequestPathFactory;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.http.server.RequestPath;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter;
import org.springframework.web.util.ServletRequestPathUtils;

class RequestPathClassLoaderTest {
  @Test
  void splitClassLoaderWithoutFilter() throws Exception {
    try (URLClassLoader classLoader = newWebAppClassLoader(true);
        GenericWebApplicationContext context = new GenericWebApplicationContext()) {
      initializeSharedFactory(classLoader);
      context.setClassLoader(classLoader);
      context.refresh();

      assertThat(context.isActive()).isTrue();
    }
  }

  @Test
  void splitClassLoaderWithFilter() throws Exception {
    try (URLClassLoader classLoader = newWebAppClassLoader(true);
        GenericWebApplicationContext context = new GenericWebApplicationContext()) {
      initializeSharedFactory(classLoader);
      context.setClassLoader(classLoader);
      registerFilter(context, classLoader);
      context.refresh();

      assertThat(context.isActive()).isTrue();
      Object filter = context.getBean("otelAutoDispatcherFilter");
      assertThat(filter.getClass().getClassLoader()).isSameAs(classLoader);
      assertThat(parseAndCacheHandle(filter)).isNull();
    }
  }

  @Test
  void duplicateRequestPathWithoutSharedSignatureWithFilter() throws Exception {
    try (URLClassLoader classLoader = newWebAppClassLoader(true);
        GenericWebApplicationContext context = new GenericWebApplicationContext()) {
      context.setClassLoader(classLoader);
      registerFilter(context, classLoader);
      context.refresh();

      assertThat(classLoader.loadClass(RequestPath.class.getName())).isNotSameAs(RequestPath.class);
      assertThat(context.getBean("otelAutoDispatcherFilter").getClass().getClassLoader())
          .isSameAs(classLoader);
      assertThat(parseAndCacheHandle(context.getBean("otelAutoDispatcherFilter"))).isNotNull();
    }
  }

  @Test
  void sharedRequestPathWithFilter() throws Exception {
    try (URLClassLoader classLoader = newWebAppClassLoader(false);
        GenericWebApplicationContext context = new GenericWebApplicationContext()) {
      initializeSharedFactory(classLoader);
      context.setClassLoader(classLoader);
      registerFilter(context, classLoader);
      context.refresh();

      assertThat(context.getBean("otelAutoDispatcherFilter").getClass().getClassLoader())
          .isSameAs(classLoader);
      assertThat(parseAndCacheHandle(context.getBean("otelAutoDispatcherFilter"))).isNotNull();
    }
  }

  private static MethodHandle parseAndCacheHandle(Object filter) throws Exception {
    Field field = filter.getClass().getDeclaredField("parseAndCacheMh");
    field.setAccessible(true);
    return (MethodHandle) field.get(null);
  }

  private static void initializeSharedFactory(ClassLoader classLoader) throws Exception {
    // Overriding the shared method constrains the child's RequestPath to the parent's type.
    assertThat(SharedRequestPathFactory.class.getMethod("getRequestPath").getReturnType())
        .isSameAs(RequestPath.class);
    Class.forName(WebAppRequestPathFactory.class.getName(), true, classLoader)
        .getConstructor()
        .newInstance();
  }

  private static void registerFilter(GenericWebApplicationContext context, ClassLoader classLoader)
      throws Exception {
    GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
    beanDefinition.setBeanClass(
        Class.forName(OpenTelemetryHandlerMappingFilter.class.getName(), false, classLoader));
    context.registerBeanDefinition("otelAutoDispatcherFilter", beanDefinition);
  }

  private static URLClassLoader newWebAppClassLoader(boolean duplicateRequestPath) {
    URL[] urls = {
      WebAppRequestPathFactory.class.getProtectionDomain().getCodeSource().getLocation(),
      OpenTelemetryHandlerMappingFilter.class.getProtectionDomain().getCodeSource().getLocation(),
      ServletRequestPathUtils.class.getProtectionDomain().getCodeSource().getLocation()
    };
    return new URLClassLoader(urls, RequestPathClassLoaderTest.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
          if (name.equals(WebAppRequestPathFactory.class.getName())
              || name.startsWith(OpenTelemetryHandlerMappingFilter.class.getName())
              || name.startsWith(ServletRequestPathUtils.class.getName())
              || (duplicateRequestPath && name.equals(RequestPath.class.getName()))) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
              loadedClass = findClass(name);
            }
            if (resolve) {
              resolveClass(loadedClass);
            }
            return loadedClass;
          }
          return super.loadClass(name, resolve);
        }
      }
    };
  }
}
