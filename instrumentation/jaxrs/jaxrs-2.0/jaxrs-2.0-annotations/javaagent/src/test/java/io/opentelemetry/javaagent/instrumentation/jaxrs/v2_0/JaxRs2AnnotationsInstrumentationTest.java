/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.test.utils.ClassUtils.getClassName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JavaInterfaces.Jax;
import io.opentelemetry.semconv.ErrorAttributes;
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

class JaxRs2AnnotationsInstrumentationTest {

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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + path)
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_ROUTE, path),
                            equalTo(ERROR_TYPE, ErrorAttributes.ErrorTypeValues.OTHER)),
                span ->
                    span.hasName(className + ".call")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, action.getClass().getName()),
                            equalTo(CODE_FUNCTION, "call"))));
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
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(ERROR_TYPE, ErrorAttributes.ErrorTypeValues.OTHER))));
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
}
