package datadog.trace.instrumentation.aws.v0;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.AmazonWebServiceRequest;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
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
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("com.amazonaws.AmazonWebServiceRequest"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RequestMeta",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
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
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
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
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
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
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
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
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
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
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore =
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
