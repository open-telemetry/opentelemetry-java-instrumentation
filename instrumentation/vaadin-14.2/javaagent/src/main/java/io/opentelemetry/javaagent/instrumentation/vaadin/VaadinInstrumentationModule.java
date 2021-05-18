/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinTracer.tracer;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import elemental.json.JsonObject;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VaadinInstrumentationModule extends InstrumentationModule {

  public VaadinInstrumentationModule() {
    super("vaadin", "vaadin-14.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in vaadin 14.2
    return hasClassesNamed("com.vaadin.flow.server.frontend.installer.NodeInstaller");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new VaadinServiceInstrumentation(),
        new RequestHandlerInstrumentation(),
        new UiInstrumentation(),
        new RouterInstrumentation(),
        new JavaScriptBootstrapUiInstrumentation(),
        new RpcInvocationHandlerInstrumentation(),
        new ClientCallableRpcInstrumentation());
  }

  // add span around vaadin request processing code
  public static class VaadinServiceInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.vaadin.flow.server.VaadinService");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("handleRequest")
              .and(takesArgument(0, named("com.vaadin.flow.server.VaadinRequest")))
              .and(takesArgument(1, named("com.vaadin.flow.server.VaadinResponse"))),
          VaadinServiceInstrumentation.class.getName() + "$HandleRequestAdvice");
    }

    public static class HandleRequestAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.This VaadinService vaadinService,
          @Advice.Origin Method method,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        context = tracer().startVaadinServiceSpan(vaadinService, method);
        scope = context.makeCurrent();
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        scope.close();

        tracer().endVaadinServiceSpan(context, throwable);
      }
    }
  }

  // add spans around vaadin request handlers
  public static class RequestHandlerInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("com.vaadin.flow.server.RequestHandler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("com.vaadin.flow.server.RequestHandler"));
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("handleRequest")
              .and(takesArgument(0, named("com.vaadin.flow.server.VaadinSession")))
              .and(takesArgument(1, named("com.vaadin.flow.server.VaadinRequest")))
              .and(takesArgument(2, named("com.vaadin.flow.server.VaadinResponse"))),
          RequestHandlerInstrumentation.class.getName() + "$RequestHandlerAdvice");
    }

    public static class RequestHandlerAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.This RequestHandler requestHandler,
          @Advice.Origin Method method,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {

        context = tracer().startRequestHandlerSpan(requestHandler, method);
        if (context != null) {
          scope = context.makeCurrent();
        }
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Return boolean handled,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        if (scope == null) {
          return;
        }
        scope.close();

        tracer().endRequestHandlerSpan(context, throwable, handled);
      }
    }
  }

  // update server span name to route of current view
  public static class UiInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.vaadin.flow.component.UI");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      // setCurrent is called by some request handler when they have accepted the request
      // we can get the path of currently active route from ui
      transformer.applyAdviceToMethod(
          named("setCurrent").and(takesArgument(0, named("com.vaadin.flow.component.UI"))),
          UiInstrumentation.class.getName() + "$SetUiAdvice");
    }

    public static class SetUiAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(@Advice.Argument(0) UI ui) {
        tracer().updateServerSpanName(ui);
      }
    }
  }

  // set server span name on initial page load
  public static class RouterInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.vaadin.flow.router.Router");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("navigate")
              .and(takesArguments(4))
              .and(takesArgument(1, named("com.vaadin.flow.router.Location")))
              .and(takesArgument(2, named("com.vaadin.flow.router.NavigationTrigger"))),
          RouterInstrumentation.class.getName() + "$NavigateAdvice");
    }

    public static class NavigateAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.Argument(1) Location location,
          @Advice.Argument(2) NavigationTrigger navigationTrigger) {
        if (navigationTrigger == NavigationTrigger.PAGE_LOAD) {
          tracer().updateServerSpanName(location);
        }
      }
    }
  }

  // set server span name on initial page load, vaadin 15+
  public static class JavaScriptBootstrapUiInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.vaadin.flow.component.internal.JavaScriptBootstrapUI");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("connectClient"),
          JavaScriptBootstrapUiInstrumentation.class.getName() + "$ConnectViewAdvice");
    }

    public static class ConnectViewAdvice {
      @Advice.OnMethodExit(suppress = Throwable.class)
      public static void onExit(@Advice.This UI ui) {
        tracer().updateServerSpanName(ui);
      }
    }
  }

  // add span around rpc calls from javascript
  public static class RpcInvocationHandlerInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("com.vaadin.flow.server.communication.rpc.RpcInvocationHandler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(
          named("com.vaadin.flow.server.communication.rpc.RpcInvocationHandler"));
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("handle")
              .and(takesArgument(0, named("com.vaadin.flow.component.UI")))
              .and(takesArgument(1, named("elemental.json.JsonObject"))),
          RpcInvocationHandlerInstrumentation.class.getName() + "$RpcInvocationHandlerAdvice");
    }

    public static class RpcInvocationHandlerAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.This RpcInvocationHandler rpcInvocationHandler,
          @Advice.Origin Method method,
          @Advice.Argument(1) JsonObject jsonObject,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {

        context = tracer().startRpcInvocationHandlerSpan(rpcInvocationHandler, method, jsonObject);
        scope = context.makeCurrent();
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        scope.close();

        tracer().endSpan(context, throwable);
      }
    }
  }

  // add spans around calls to methods with @ClientCallable annotation
  public static class ClientCallableRpcInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named(
          "com.vaadin.flow.server.communication.rpc.PublishedServerEventHandlerRpcHandler");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          named("invokeMethod")
              .and(takesArgument(0, named("com.vaadin.flow.component.Component")))
              .and(takesArgument(1, named(Class.class.getName())))
              .and(takesArgument(2, named(String.class.getName())))
              .and(takesArgument(3, named("elemental.json.JsonArray")))
              .and(takesArgument(4, named(int.class.getName()))),
          ClientCallableRpcInstrumentation.class.getName() + "$InvokeAdvice");
    }

    public static class InvokeAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.Argument(1) Class<?> componentClass,
          @Advice.Argument(2) String methodName,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {

        context = tracer().startClientCallableSpan(componentClass, methodName);
        scope = context.makeCurrent();
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        scope.close();

        tracer().endSpan(context, throwable);
      }
    }
  }
}
