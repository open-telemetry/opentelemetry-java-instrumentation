/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0.AsyncHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;

public class AsyncHttpClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.asynchttpclient.AsyncHttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("executeRequest")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")))
            .and(isPublic()),
        this.getClass().getName() + "$ExecuteRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) AsyncHandler<?> handler,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      RequestContext requestContext = new RequestContext(parentContext, request);
      if (!instrumenter().shouldStart(parentContext, requestContext)) {
        return;
      }

      Context context = instrumenter().start(parentContext, requestContext);
      requestContext.setContext(context);

      // TODO (trask) instead of using VirtualField, wrap the AsyncHandler in an
      // instrumented AsyncHandler which delegates to the original AsyncHandler
      // (similar to other http client instrumentations, and needed for library instrumentation)
      //
      // when doing this, note that AsyncHttpClient has different behavior if the AsyncHandler also
      // implements ProgressAsyncHandler or StreamedAsyncHandler (or both)
      // so four wrappers are needed to match the different combinations so that the wrapper won't
      // affect the behavior
      //
      // when doing this, also note that there was a breaking change in AsyncHandler between 2.0 and
      // 2.1, so the instrumentation module will need to be essentially duplicated (or a common
      // module introduced)

      VirtualField.find(AsyncHandler.class, RequestContext.class).set(handler, requestContext);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
      // span ended in ResponseAdvice or ResponseFailureAdvice
    }
  }
}
