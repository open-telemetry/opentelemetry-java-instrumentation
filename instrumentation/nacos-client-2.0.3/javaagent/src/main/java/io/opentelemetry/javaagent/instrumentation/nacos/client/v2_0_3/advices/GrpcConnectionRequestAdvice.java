package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.advices;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.NacosClientSingletons.instrumenter;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.NacosClientRequest;
import net.bytebuddy.asm.Advice;

public class GrpcConnectionRequestAdvice {
  @SuppressWarnings("unused")
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void requestEnter(
      @Advice.This Object thisObject,
      @Advice.Argument(0) Request request,
      @Advice.Local("otelRequest") NacosClientRequest nacosClientRequest,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    Context parentContext = currentContext();
    nacosClientRequest = NacosClientRequest.createRequest("request", thisObject.getClass(),
        request);
    if (!instrumenter().shouldStart(parentContext, nacosClientRequest)) {
      return;
    }
    context = instrumenter().start(parentContext, nacosClientRequest);
    scope = context.makeCurrent();
  }

  @SuppressWarnings("unused")
  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  public static void requestExit(
      @Advice.Return Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelRequest") NacosClientRequest nacosClientRequest,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();
    instrumenter().end(context, nacosClientRequest, response, throwable);
  }
}
