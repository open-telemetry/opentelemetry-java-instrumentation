/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.AmazonWebServiceRequest;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.AmazonWebServiceRequest");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("com.amazonaws.services.")
        .and(extendsClass(named("com.amazonaws.AmazonWebServiceRequest")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("setBucketName").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$BucketNameAdvice");
    transformers.put(
        named("setQueueUrl").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$QueueUrlAdvice");
    transformers.put(
        named("setQueueName").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$QueueNameAdvice");
    transformers.put(
        named("setStreamName").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$StreamNameAdvice");
    transformers.put(
        named("setTableName").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$TableNameAdvice");
    return transformers;
  }

  public static class BucketNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) String value, @Advice.This AmazonWebServiceRequest request) {
      ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
          InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class);
      RequestMeta requestMeta = contextStore.get(request);
      if (requestMeta == null) {
        requestMeta = new RequestMeta();
        contextStore.put(request, requestMeta);
      }
      requestMeta.setBucketName(value);
    }
  }

  public static class QueueUrlAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) String value, @Advice.This AmazonWebServiceRequest request) {
      ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
          InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class);
      RequestMeta requestMeta = contextStore.get(request);
      if (requestMeta == null) {
        requestMeta = new RequestMeta();
        contextStore.put(request, requestMeta);
      }
      requestMeta.setQueueUrl(value);
    }
  }

  public static class QueueNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) String value, @Advice.This AmazonWebServiceRequest request) {
      ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
          InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class);
      RequestMeta requestMeta = contextStore.get(request);
      if (requestMeta == null) {
        requestMeta = new RequestMeta();
        contextStore.put(request, requestMeta);
      }
      requestMeta.setQueueName(value);
    }
  }

  public static class StreamNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) String value, @Advice.This AmazonWebServiceRequest request) {
      ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
          InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class);
      RequestMeta requestMeta = contextStore.get(request);
      if (requestMeta == null) {
        requestMeta = new RequestMeta();
        contextStore.put(request, requestMeta);
      }
      requestMeta.setStreamName(value);
    }
  }

  public static class TableNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) String value, @Advice.This AmazonWebServiceRequest request) {
      ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
          InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class);
      RequestMeta requestMeta = contextStore.get(request);
      if (requestMeta == null) {
        requestMeta = new RequestMeta();
        contextStore.put(request, requestMeta);
      }
      requestMeta.setTableName(value);
    }
  }
}
