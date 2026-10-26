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
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;
import org.springframework.remoting.rmi.RmiServiceExporter;
import org.springframework.remoting.support.RemoteExporter;
import org.springframework.remoting.support.RemoteInvocation;
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
  }

  @ParameterizedTest
  @MethodSource("invalidInvocations")
  void unknownMethodsUseFallbackServerSpan(
      String methodName, Class<?>[] parameterTypes, Object[] arguments) throws RemoteException {
    RmiProxyFactoryBean proxyFactory = new RmiProxyFactoryBean();
    proxyFactory.setServiceInterface(SpringRmiGreeter.class);
    proxyFactory.setServiceUrl("rmi://localhost:" + registryPort + "/springRmiGreeter");
    proxyFactory.setRemoteInvocationFactory(
        ignored -> new RemoteInvocation(methodName, parameterTypes, arguments));
    proxyFactory.afterPropertiesSet();
    SpringRmiGreeter client = (SpringRmiGreeter) proxyFactory.getObject();

    assertThat(catchThrowable(() -> client.hello("Test Name"))).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("springrmi.app.SpringRmiGreeter/hello")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent(),
                span ->
                    span.hasName(
                            emitPreviewRpcSemconv()
                                ? "spring_rmi"
                                : "springrmi.app.SpringRmiGreeterImpl/" + methodName)
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "spring_rmi" : null),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "spring_rmi" : null),
                            equalTo(
                                RPC_SERVICE,
                                emitOldRpcSemconv() ? "springrmi.app.SpringRmiGreeterImpl" : null),
                            equalTo(RPC_METHOD, emitPreviewRpcSemconv() ? "_OTHER" : methodName),
                            equalTo(
                                RPC_METHOD_ORIGINAL,
                                emitPreviewRpcSemconv()
                                    ? "springrmi.app.SpringRmiGreeter/" + methodName
                                    : null),
                            equalTo(
                                ERROR_TYPE,
                                emitPreviewRpcSemconv()
                                    ? NoSuchMethodException.class.getName()
                                    : null))));
  }

  private static Stream<Arguments> invalidInvocations() {
    return Stream.of(
        Arguments.of("unknown", new Class<?>[] {String.class}, new Object[] {"value"}),
        Arguments.of("hello", new Class<?>[] {Integer.class}, new Object[] {1}));
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
  }
}
