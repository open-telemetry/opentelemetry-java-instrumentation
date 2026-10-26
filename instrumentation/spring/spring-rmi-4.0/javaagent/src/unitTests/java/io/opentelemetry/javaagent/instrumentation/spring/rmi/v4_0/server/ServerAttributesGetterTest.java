/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // legacy RPC attributes
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ServerAttributesGetterTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @ParameterizedTest
  @MethodSource("requests")
  void emitsServerMethodAttributes(
      String mode,
      Class<?> serviceInterface,
      String methodName,
      Class<?> parameterType,
      String rpcMethod,
      String originalMethod) {
    SpringRmiServerRequest request =
        new SpringRmiServerRequest(
            ClassAndMethod.create(Object.class, methodName),
            serviceInterface,
            methodName,
            new Class<?>[] {parameterType});
    String previous = System.getProperty("otel.semconv-stability.preview");
    System.setProperty("otel.semconv-stability.preview", mode);
    try {
      ServerAttributesGetter getter = new ServerAttributesGetter();
      Instrumenter<SpringRmiServerRequest, Void> instrumenter =
          Instrumenter.<SpringRmiServerRequest, Void>builder(
                  testing.getOpenTelemetry(), "test", RpcSpanNameExtractor.create(getter))
              .addAttributesExtractor(RpcServerAttributesExtractor.create(getter))
              .buildInstrumenter(SpanKindExtractor.alwaysServer());
      Context context = instrumenter.start(Context.root(), request);
      instrumenter.end(context, request, null, null);

      boolean preview = !mode.isEmpty();
      boolean old = !mode.equals("rpc");
      assertThat(testing.getSpans())
          .singleElement()
          .satisfies(
              span ->
                  assertThat(span)
                      .hasName(
                          preview
                              ? (rpcMethod.equals("_OTHER") ? "spring_rmi" : rpcMethod)
                              : Object.class.getName() + "/" + methodName)
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, old ? "spring_rmi" : null),
                          equalTo(RPC_SERVICE, old ? Object.class.getName() : null),
                          equalTo(RPC_SYSTEM_NAME, preview ? "spring_rmi" : null),
                          equalTo(RPC_METHOD, preview ? rpcMethod : methodName),
                          equalTo(RPC_METHOD_ORIGINAL, preview ? originalMethod : null)));
    } finally {
      if (previous == null) {
        System.clearProperty("otel.semconv-stability.preview");
      } else {
        System.setProperty("otel.semconv-stability.preview", previous);
      }
    }
  }

  private static Stream<Arguments> requests() {
    return Stream.of("", "rpc", "rpc/dup")
        .flatMap(
            mode ->
                Stream.of(
                    Arguments.of(
                        mode,
                        Service.class,
                        "known",
                        String.class,
                        ParentService.class.getName() + "/known",
                        null),
                    Arguments.of(
                        mode,
                        Service.class,
                        "known",
                        Integer.class,
                        Service.class.getName() + "/known",
                        null),
                    Arguments.of(
                        mode,
                        Service.class,
                        "missing",
                        String.class,
                        "_OTHER",
                        Service.class.getName() + "/missing"),
                    Arguments.of(
                        mode,
                        Service.class,
                        "known",
                        Double.class,
                        "_OTHER",
                        Service.class.getName() + "/known"),
                    Arguments.of(mode, null, "known", String.class, "_OTHER", "known")));
  }

  public interface ParentService {
    void known(String value);
  }

  public interface Service extends ParentService {
    void known(Integer value);
  }
}
