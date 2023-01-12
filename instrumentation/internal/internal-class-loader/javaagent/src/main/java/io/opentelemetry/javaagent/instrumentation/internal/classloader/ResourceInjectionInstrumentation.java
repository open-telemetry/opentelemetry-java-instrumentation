/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.HelperResources;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments {@link ClassLoader} to have calls to get resources intercepted and check our map of
 * helper resources that is filled by instrumentation when they need helpers.
 */
public class ResourceInjectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("java.lang.ClassLoader"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("getResource"))
            .and(takesArguments(String.class))
            .and(returns(URL.class)),
        ResourceInjectionInstrumentation.class.getName() + "$GetResourceAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("getResources"))
            .and(takesArguments(String.class))
            .and(returns(Enumeration.class)),
        ResourceInjectionInstrumentation.class.getName() + "$GetResourcesAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("getResourceAsStream"))
            .and(takesArguments(String.class))
            .and(returns(InputStream.class)),
        ResourceInjectionInstrumentation.class.getName() + "$GetResourceAsStreamAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetResourceAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return(readOnly = false) URL resource) {
      if (resource != null) {
        return;
      }

      URL helper = HelperResources.loadOne(classLoader, name);
      if (helper != null) {
        resource = helper;
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetResourcesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return(readOnly = false) Enumeration<URL> resources) {
      List<URL> helpers = HelperResources.loadAll(classLoader, name);
      if (helpers.isEmpty()) {
        return;
      }

      if (!resources.hasMoreElements()) {
        resources = Collections.enumeration(helpers);
        return;
      }

      List<URL> result = Collections.list(resources);

      for (URL helperUrl : helpers) {
        boolean duplicate = false;
        for (URL loadedUrl : result) {
          if (helperUrl.sameFile(loadedUrl)) {
            duplicate = true;
            break;
          }
        }
        if (!duplicate) {
          result.add(helperUrl);
        }
      }

      resources = Collections.enumeration(result);
    }
  }

  @SuppressWarnings("unused")
  public static class GetResourceAsStreamAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return(readOnly = false) InputStream inputStream) {
      if (inputStream != null) {
        return;
      }

      URL helper = HelperResources.loadOne(classLoader, name);
      if (helper != null) {
        try {
          inputStream = helper.openStream();
        } catch (IOException ignored) {
          // ClassLoader.getResourceAsStream also ignores io exceptions from opening the stream
        }
      }
    }
  }
}
