/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.instrument.Instrumentation;
import java.rmi.server.ObjID;
import java.util.Collections;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import sun.rmi.transport.Connection;

/**
 * Main entry point for transferring context between RMI service.
 *
 * <p>It injects into StreamRemoteCall constructor used for invoking remote tasks and performs a
 * backwards compatible check to ensure if the other side is prepared to receive context propagation
 * messages then if successful sends a context propagation message
 *
 * <p>Context propagation consist of a Serialized HashMap with all data set by usual context
 * injection, which includes things like sampling priority, trace and parent id
 *
 * <p>As well as optional baggage items
 *
 * <p>On the other side of the communication a special Dispatcher is created when a message with
 * CONTEXT_CALL_ID is received.
 *
 * <p>If the server is not instrumented first call will gracefully fail just like any other unknown
 * call. With small caveat that this first call needs to *not* have any parameters, since those will
 * not be read from connection and instead will be interpreted as another remote instruction, but
 * that instruction will essentially be garbage data and will cause the parsing loop to throw
 * exception and shutdown the connection which we do not want
 */
public class RmiClientContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("sun.rmi.transport.StreamRemoteCall"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("sun.rmi.transport.Connection")))
            .and(takesArgument(1, named("java.rmi.server.ObjID"))),
        getClass().getName() + "$StreamRemoteCallConstructorAdvice");

    // expose sun.rmi.transport.StreamRemoteCall to helper classes
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module) -> {
          if (JavaModule.isSupported()
              && classLoader == null
              && "sun.rmi.transport.StreamRemoteCall".equals(typeDescription.getName())
              && module != null) {
            Instrumentation instrumentation = InstrumentationHolder.getInstrumentation();
            ClassInjector.UsingInstrumentation.redefineModule(
                instrumentation,
                module,
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.singletonMap(
                    "sun.rmi.transport",
                    // AgentClassLoader is in unnamed module of the bootstrap class loader which is
                    // where helper classes are also
                    Collections.singleton(JavaModule.ofType(AgentClassLoader.class))),
                Collections.emptySet(),
                Collections.emptyMap());
          }
          return builder;
        });
  }

  @SuppressWarnings("unused")
  public static class StreamRemoteCallConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Connection c, @Advice.Argument(1) ObjID id) {
      if (!c.isReusable()) {
        return;
      }
      if (PROPAGATOR.isRmiInternalObject(id)) {
        return;
      }
      Context currentContext = Java8BytecodeBridge.currentContext();
      Span activeSpan = Java8BytecodeBridge.spanFromContext(currentContext);
      if (!activeSpan.getSpanContext().isValid()) {
        return;
      }

      // caching if a connection can support enhanced format
      VirtualField<Connection, Boolean> knownConnections =
          VirtualField.find(Connection.class, Boolean.class);

      PROPAGATOR.attemptToPropagateContext(knownConnections, c, currentContext);
    }
  }
}
