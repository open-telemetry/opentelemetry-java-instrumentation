/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.awssdk.v1_11;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.AmazonWebServiceRequest;
import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RequestInstrumentation extends Instrumenter.Default {

  public RequestInstrumentation() {
    super("aws-sdk");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("com.amazonaws.AmazonWebServiceRequest");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("com.amazonaws.services.")
        .and(extendsClass(named("com.amazonaws.AmazonWebServiceRequest")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RequestMeta",
    };
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

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.amazonaws.AmazonWebServiceRequest", packageName + ".RequestMeta");
  }

  public static class BucketNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) final String value,
        @Advice.This final AmazonWebServiceRequest request) {
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
        @Advice.Argument(0) final String value,
        @Advice.This final AmazonWebServiceRequest request) {
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
        @Advice.Argument(0) final String value,
        @Advice.This final AmazonWebServiceRequest request) {
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
        @Advice.Argument(0) final String value,
        @Advice.This final AmazonWebServiceRequest request) {
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
        @Advice.Argument(0) final String value,
        @Advice.This final AmazonWebServiceRequest request) {
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
