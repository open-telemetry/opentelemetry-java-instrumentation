/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_1.AersopikeSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.aerospike.client.Key;
import com.aerospike.client.command.Command;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AsyncCommandInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperClass(
        named("com.aerospike.client.async.AsyncCommand")
            .and(not(named("com.aerospike.client.command.Command"))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(isPublic()), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This Command command,
        @Advice.Local("otelAerospikeRequest") AerospikeRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.AllArguments Object[] objects) {
      Key key = null;
      for (Object object : objects) {
        if (object instanceof Key) {
          key = (Key) object;
          break;
        }
      }
      if (key == null) {
        return;
      }
      VirtualField<Command, AerospikeRequestContext> virtualField =
          VirtualField.find(Command.class, AerospikeRequestContext.class);
      AerospikeRequestContext requestContext = virtualField.get(command);
      if (requestContext != null) {
        return;
      }
      Context parentContext = currentContext();
      request =
          AerospikeRequest.create(command.getClass().getSimpleName().toUpperCase(Locale.ROOT), key);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }
      context = instrumenter().start(parentContext, request);
      AerospikeRequestContext aerospikeRequestContext =
          AerospikeRequestContext.attach(request, context);
      scope = context.makeCurrent();

      virtualField.set(command, aerospikeRequestContext);
      if (scope != null) {
        scope.close();
      }
    }
  }
}
