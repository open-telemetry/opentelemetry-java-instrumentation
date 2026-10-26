/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;
import org.springframework.remoting.rmi.RmiServiceExporter;
import org.springframework.remoting.support.RemoteExporter;
import org.springframework.stereotype.Component;
import springrmi.app.SpringRmiGreeter;
import springrmi.app.SpringRmiGreeterImpl;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpringRmiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static ConfigurableApplicationContext serverAppContext;
  private static ConfigurableApplicationContext clientAppContext;
  private static ConfigurableApplicationContext xmlAppContext;
  private static EJBContainer ejbContainer;

  static int registryPort;

  @Component
  private static class ServerConfig {
    @Bean
    static RemoteExporter registerRmiExporter() {
      SpringRmiGreeter greeter = new SpringRmiGreeterImpl();

      RmiServiceExporter exporter = new RmiServiceExporter();
      exporter.setServiceName("springRmiGreeter");
      exporter.setServiceInterface(SpringRmiGreeter.class);
      exporter.setService(greeter);
      exporter.setRegistryPort(registryPort);

      return exporter;
    }
  }

  @Component
  private static class ClientConfig {
    @Bean
    static RmiProxyFactoryBean rmiProxy() {
      RmiProxyFactoryBean bean = new RmiProxyFactoryBean();
      bean.setServiceInterface(SpringRmiGreeter.class);
      bean.setServiceUrl("rmi://localhost:" + registryPort + "/springRmiGreeter");
      return bean;
    }
  }

  @BeforeAll
  static void beforeAll() throws Exception {
    registryPort = PortUtils.findOpenPort();

    Map<String, Object> map = new HashMap<>();
    map.put(EJBContainer.APP_NAME, "test");
    map.put(EJBContainer.MODULES, new File("build/classes/java/test"));
    ejbContainer = EJBContainer.createEJBContainer(map);
    cleanup.deferAfterAll(ejbContainer);

    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");

    SpringApplication serverApp = new SpringApplication(ServerConfig.class);
    serverApp.setDefaultProperties(props);
    serverAppContext = serverApp.run();
    cleanup.deferAfterAll(serverAppContext);

    SpringApplication clientApp = new SpringApplication(ClientConfig.class);
    clientApp.setDefaultProperties(props);
    clientAppContext = clientApp.run();
    cleanup.deferAfterAll(clientAppContext);

    xmlAppContext = new ClassPathXmlApplicationContext("spring-rmi.xml");
    cleanup.deferAfterAll(xmlAppContext);
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum TestSource {
    RMI(
        clientAppContext,
        "springrmi.app.SpringRmiGreeterImpl",
        "spring_rmi",
        IllegalStateException.class),
    EJB(xmlAppContext, "springrmi.app.ejb.SpringRmiGreeterRemote", "java_rmi", EJBException.class);

    final ApplicationContext appContext;
    final String remoteClassName;
    final String serverSystem;
    final Class<? extends Throwable> expectedException;

    TestSource(
        ApplicationContext appContext,
        String remoteClassName,
        String serverSystem,
        Class<? extends Throwable> expectedException) {
      this.appContext = appContext;
      this.remoteClassName = remoteClassName;
      this.serverSystem = serverSystem;
      this.expectedException = expectedException;
    }
  }

  @ParameterizedTest(autoCloseArguments = false)
  @EnumSource(TestSource.class)
  void clientCallCreatesSpans(TestSource testSource) throws RemoteException {
    SpringRmiGreeter client = testSource.appContext.getBean(SpringRmiGreeter.class);
    String response = testing.runWithSpan("parent", () -> client.hello("Test Name"));
    assertThat(response).isEqualTo("Hello Test Name");
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent());
          assertions.add(
              span ->
                  span.hasName("springrmi.app.SpringRmiGreeter/hello")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                          equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                          equalTo(
                              RPC_SERVICE,
                              emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeter" : null),
                          equalTo(
                              RPC_METHOD,
                              emitPreviewRpcSemconv()
                                  ? "springrmi.app.SpringRmiGreeter/hello"
                                  : "hello")));
          if (testSource == TestSource.RMI) {
            assertions.add(
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "springrmi.app.SpringRmiGreeter/hello"
                                : testSource.remoteClassName + "/hello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                RPC_SYSTEM, emitOldRpcSemconv() ? testSource.serverSystem : null),
                            equalTo(
                                RPC_SYSTEM_NAME,
                                emitPreviewRpcSemconv() ? testSource.serverSystem : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? testSource.remoteClassName : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeter/hello"
                                    : "hello")));
          }

          trace.hasSpansSatisfyingExactly(assertions);
        });
    assertMetrics(testSource == TestSource.RMI, null, "springrmi.app.SpringRmiGreeter/hello");
  }

  @Test
  void inheritedAndOverloadedMethodsUseExactInterfaceMethods() throws RemoteException {
    SpringRmiGreeter client = clientAppContext.getBean(SpringRmiGreeter.class);
    List<String> responses =
        testing.runWithSpan(
            "parent",
            () ->
                asList(
                    client.inherited("value"), client.overloaded("value"), client.overloaded(1)));

    assertThat(responses).containsExactly("Inherited value", "String value", "Integer 1");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("springrmi.app.SpringRmiGreeterParent/inherited")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "springrmi.app.SpringRmiGreeterParent/inherited"
                                : "springrmi.app.SpringRmiGreeterImpl/inherited")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeterImpl" : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeterParent/inherited"
                                    : "inherited")),
                span ->
                    span.hasName("springrmi.app.SpringRmiGreeterParent/overloaded")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "springrmi.app.SpringRmiGreeterParent/overloaded"
                                : "springrmi.app.SpringRmiGreeterImpl/overloaded")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeterImpl" : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeterParent/overloaded"
                                    : "overloaded")),
                span ->
                    span.hasName("springrmi.app.SpringRmiGreeterIntegerOverload/overloaded")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "springrmi.app.SpringRmiGreeterIntegerOverload/overloaded"
                                : "springrmi.app.SpringRmiGreeterImpl/overloaded")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(5))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeterImpl" : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeterIntegerOverload/overloaded"
                                    : "overloaded"))));
    assertMetrics(
        true,
        null,
        "springrmi.app.SpringRmiGreeterParent/inherited",
        "springrmi.app.SpringRmiGreeterParent/overloaded",
        "springrmi.app.SpringRmiGreeterIntegerOverload/overloaded");
  }

  @ParameterizedTest(autoCloseArguments = false)
  @EnumSource(TestSource.class)
  void throwsException(TestSource testSource) {
    SpringRmiGreeter client = testSource.appContext.getBean(SpringRmiGreeter.class);
    Throwable error = catchThrowable(() -> testing.runWithSpan("parent", client::exceptional));
    assertThat(error).isInstanceOf(testSource.expectedException);
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(
              span ->
                  span.hasName("parent")
                      .hasKind(SpanKind.INTERNAL)
                      .hasStatus(StatusData.error())
                      .hasNoParent()
                      .hasEventsSatisfyingExactly(
                          event ->
                              event
                                  .hasName("exception")
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(EXCEPTION_TYPE, error.getClass().getCanonicalName()),
                                      equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                      satisfies(
                                          EXCEPTION_STACKTRACE,
                                          val -> val.isInstanceOf(String.class)))));
          assertions.add(
              span ->
                  span.hasName("springrmi.app.SpringRmiGreeter/exceptional")
                      .hasKind(SpanKind.CLIENT)
                      .hasStatus(StatusData.error())
                      .hasParent(trace.getSpan(0))
                      .hasEventsSatisfyingExactly(
                          event ->
                              event
                                  .hasName("exception")
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(EXCEPTION_TYPE, error.getClass().getCanonicalName()),
                                      equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                      satisfies(
                                          EXCEPTION_STACKTRACE,
                                          val -> val.isInstanceOf(String.class))))
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                          equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                          equalTo(
                              RPC_SERVICE,
                              emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeter" : null),
                          equalTo(
                              RPC_METHOD,
                              emitPreviewRpcSemconv()
                                  ? "springrmi.app.SpringRmiGreeter/exceptional"
                                  : "exceptional"),
                          equalTo(
                              ERROR_TYPE,
                              emitPreviewRpcSemconv() ? error.getClass().getName() : null)));
          if (testSource == TestSource.RMI) {
            assertions.add(
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "springrmi.app.SpringRmiGreeter/exceptional"
                                : testSource.remoteClassName + "/exceptional")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, error.getClass().getCanonicalName()),
                                        equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                RPC_SYSTEM, emitOldRpcSemconv() ? testSource.serverSystem : null),
                            equalTo(
                                RPC_SYSTEM_NAME,
                                emitPreviewRpcSemconv() ? testSource.serverSystem : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? testSource.remoteClassName : null),
                            equalTo(
                                RPC_METHOD,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeter/exceptional"
                                    : "exceptional"),
                            equalTo(
                                ERROR_TYPE,
                                emitPreviewRpcSemconv() ? error.getClass().getName() : null)));
          }

          trace.hasSpansSatisfyingExactly(assertions);
        });
    assertMetrics(
        testSource == TestSource.RMI,
        error.getClass().getName(),
        "springrmi.app.SpringRmiGreeter/exceptional");
  }

  private static void assertMetrics(boolean hasServer, String errorType, String... methods) {
    if (!emitPreviewRpcSemconv()) {
      assertThat(testing.metrics())
          .noneMatch(
              metric ->
                  metric
                      .getInstrumentationScopeInfo()
                      .getName()
                      .equals("io.opentelemetry.spring-rmi-4.0"));
      return;
    }

    assertDurationMetrics("client", errorType, methods);
    if (hasServer) {
      assertDurationMetrics("server", errorType, methods);
    }
  }

  private static void assertDurationMetrics(String role, String errorType, String... methods) {
    List<Consumer<HistogramPointAssert>> points = new ArrayList<>();
    for (String method : methods) {
      points.add(
          point ->
              point
                  .hasCount(1)
                  .hasSumGreaterThan(0)
                  .hasBucketBoundaries(
                      0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5, 10, 50, 100)
                  .hasAttributesSatisfyingExactly(
                      equalTo(RPC_SYSTEM_NAME, "spring_rmi"),
                      equalTo(RPC_METHOD, method),
                      equalTo(ERROR_TYPE, errorType)));
    }
    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-rmi-4.0",
        metric ->
            metric
                .hasName("rpc." + role + ".call.duration")
                .hasUnit("s")
                .hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(points)));

    if (emitOldRpcSemconv()) {
      Map<Attributes, Long> counts = new HashMap<>();
      for (String method : methods) {
        Attributes attributes =
            Attributes.builder()
                .put(RPC_SYSTEM, "spring_rmi")
                .put(
                    RPC_SERVICE,
                    role.equals("client")
                        ? method.substring(0, method.indexOf('/'))
                        : "springrmi.app.SpringRmiGreeterImpl")
                .put(RPC_METHOD, method.substring(method.indexOf('/') + 1))
                .build();
        counts.merge(attributes, 1L, Long::sum);
      }
      List<Consumer<HistogramPointAssert>> oldPoints = new ArrayList<>();
      counts.forEach(
          (attributes, count) ->
              oldPoints.add(point -> point.hasCount(count).hasAttributes(attributes)));
      testing.waitAndAssertMetrics(
          "io.opentelemetry.spring-rmi-4.0",
          metric ->
              metric
                  .hasName("rpc." + role + ".duration")
                  .hasUnit("ms")
                  .hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(oldPoints)));
    }
  }
}
