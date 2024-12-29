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
    @Advice.AssignReturned.ToReturned
    public static URL onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return URL resource) {
      if (resource == null) {
        URL helper = HelperResources.loadOne(classLoader, name);
        if (helper != null) {
          return helper;
        }
      }
      return resource;
    }
  }

  @SuppressWarnings("unused")
  public static class GetResourcesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static Enumeration<URL> onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return Enumeration<URL> resources) {
      List<URL> helpers = HelperResources.loadAll(classLoader, name);
      if (helpers.isEmpty()) {
        return resources;
      }

      if (!resources.hasMoreElements()) {
        return Collections.enumeration(helpers);
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
      return Collections.enumeration(result);
    }
  }

  @SuppressWarnings("unused")
  public static class GetResourceAsStreamAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static InputStream onExit(
        @Advice.This ClassLoader classLoader,
        @Advice.Argument(0) String name,
        @Advice.Return InputStream inputStream) {
      if (inputStream == null) {
        URL helper = HelperResources.loadOne(classLoader, name);
        if (helper != null) {
          try {
            return helper.openStream();
          } catch (IOException ignored) {
            // ClassLoader.getResourceAsStream also ignores io exceptions from opening the stream
          }
        }
      }
      return inputStream;
    }
  }
}
