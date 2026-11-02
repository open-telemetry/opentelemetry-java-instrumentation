/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0.annotations;

import static io.opentelemetry.instrumentation.test.utils.ClassUtils.getClassName;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ErrorAttributes.ErrorTypeValues.OTHER;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0.annotations.JavaInterfaces.Jax;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JaxRsAnnotations1InstrumentationTest {

  private static final boolean CONTROLLER_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.common.controller-telemetry.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "/a",
            new Jax() {
              @Path("/a")
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/b",
            new Jax() {
              @GET
              @Path("/b")
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/interface/c",
            new InterfaceWithPath() {
              @POST
              @Path("/c")
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/interface",
            new InterfaceWithPath() {
              @HEAD
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/abstract/d",
            new AbstractClassWithPath() {
              @POST
              @Path("/d")
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/abstract",
            new AbstractClassWithPath() {
              @PUT
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/child/e",
            new ChildClassWithPath() {
              @OPTIONS
              @Path("/e")
              @Override
              public void call() {}
            }),
        Arguments.of(
            "/child/call",
            new ChildClassWithPath() {
              @DELETE
              @Override
              public void call() {}
            }),
        Arguments.of("/child/call", new ChildClassWithPath()),
        Arguments.of("/child/call", new JavaInterfaces.ChildClassOnInterface()),
        Arguments.of("/child/call", new JavaInterfaces.DefaultChildClassOnInterface()));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("provideArguments")
  void createSpanForAnnotatedMethod(String path, Jax action) {
    String className = getClassName(action.getClass());
    testing.runWithHttpServerSpan(action::call);

    Consumer<SpanDataAssert> serverSpan =
        span ->
            span.hasName(CONTROLLER_TELEMETRY_ENABLED ? "GET " + path : "GET")
                .hasKind(SpanKind.SERVER)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(HTTP_REQUEST_METHOD, "GET"),
                    equalTo(HTTP_ROUTE, CONTROLLER_TELEMETRY_ENABLED ? path : null),
                    equalTo(ERROR_TYPE, OTHER));
    if (CONTROLLER_TELEMETRY_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  serverSpan,
                  span ->
                      span.hasName(className + ".call")
                          .satisfies(hasScopeName("io.opentelemetry.jaxrs-1.0-annotations"))
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(CODE_FUNCTION_NAME, action.getClass().getName() + ".call"))));
    } else {
      testing.waitAndAssertTraces(trace -> trace.hasSpansSatisfyingExactly(serverSpan));
    }
  }

  @Test
  void notAnnotatedMethod() {
    Jax action =
        new Jax() {
          @Override
          public void call() {}
        };
    testing.runWithHttpServerSpan(action::call);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"), equalTo(ERROR_TYPE, OTHER))));
  }

  @Test
  void covariantInterfaceMethod() {
    CovariantInterfaceResource action = new CovariantInterfaceResourceImpl();
    testing.runWithHttpServerSpan((ThrowingRunnable<RuntimeException>) action::call);
    assertCovariantRoute("/covariant-interface/interface", CovariantInterfaceResourceImpl.class);
  }

  @Test
  void covariantSuperclassMethod() {
    CovariantSuperclassResource action = new CovariantSuperclassResourceImpl();
    testing.runWithHttpServerSpan((ThrowingRunnable<RuntimeException>) action::call);
    assertCovariantRoute("/covariant-superclass/superclass", CovariantSuperclassResourceImpl.class);
  }

  @Test
  void covariantMethodWithDifferentParametersIsNotMatched() {
    CovariantInterfaceResourceImpl action = new CovariantInterfaceResourceImpl();
    testing.runWithHttpServerSpan((ThrowingRunnable<RuntimeException>) () -> action.call(1L));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"), equalTo(ERROR_TYPE, OTHER))));
  }

  private static void assertCovariantRoute(String route, Class<?> resourceClass) {
    testing.waitAndAssertTraces(
        trace -> {
          Consumer<SpanDataAssert> serverSpan =
              span ->
                  span.hasName(CONTROLLER_TELEMETRY_ENABLED ? "GET " + route : "GET")
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(HTTP_REQUEST_METHOD, "GET"),
                          equalTo(HTTP_ROUTE, CONTROLLER_TELEMETRY_ENABLED ? route : null),
                          equalTo(ERROR_TYPE, OTHER));
          if (CONTROLLER_TELEMETRY_ENABLED) {
            trace.hasSpansSatisfyingExactly(
                serverSpan,
                span ->
                    span.hasName(getClassName(resourceClass) + ".call")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_FUNCTION_NAME, resourceClass.getName() + ".call")));
          } else {
            trace.hasSpansSatisfyingExactly(serverSpan);
          }
        });
  }

  @Path("/interface")
  interface InterfaceWithPath extends Jax {
    @GET
    @Override
    void call();
  }

  @Path("/abstract")
  abstract static class AbstractClassWithPath implements Jax {
    @PUT
    @Override
    public abstract void call();
  }

  @Path("child")
  static class ChildClassWithPath extends AbstractClassWithPath {
    @Path("call")
    @POST
    @Override
    public void call() {}
  }

  @Path("covariant-interface")
  interface CovariantInterfaceResource {
    @GET
    @Path("interface")
    Object call();

    @GET
    @Path("overload")
    Object call(Integer value);
  }

  static class CovariantInterfaceResourceImpl implements CovariantInterfaceResource {
    @Override
    public String call() {
      return "";
    }

    @Override
    public Object call(Integer value) {
      return value;
    }

    public String call(Long value) {
      return value.toString();
    }
  }

  @Path("covariant-superclass")
  abstract static class CovariantSuperclassResource {
    @GET
    @Path("superclass")
    public abstract Object call();

    @GET
    @Path("overload")
    public Object call(Integer value) {
      return value;
    }
  }

  static class CovariantSuperclassResourceImpl extends CovariantSuperclassResource {
    @Override
    public String call() {
      return "";
    }
  }
}
