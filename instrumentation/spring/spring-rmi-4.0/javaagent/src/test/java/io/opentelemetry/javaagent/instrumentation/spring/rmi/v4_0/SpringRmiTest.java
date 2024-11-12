/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

class SpringRmiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

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
    map.put(EJBContainer.MODULES, new java.io.File("build/classes/java/test"));
    ejbContainer = EJBContainer.createEJBContainer(map);

    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");

    SpringApplication serverApp = new SpringApplication(ServerConfig.class);
    serverApp.setDefaultProperties(props);
    serverAppContext = serverApp.run();

    SpringApplication clientApp = new SpringApplication(ClientConfig.class);
    clientApp.setDefaultProperties(props);
    clientAppContext = clientApp.run();

    xmlAppContext = new ClassPathXmlApplicationContext("spring-rmi.xml");
  }

  @AfterAll
  static void afterAll() {
    serverAppContext.close();
    clientAppContext.close();
    xmlAppContext.close();
    ejbContainer.close();
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
    assertEquals(response, "Hello Test Name");
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent());
          assertions.add(
              span ->
                  span.hasName("springrmi.app.SpringRmiGreeter/hello")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          equalTo(RPC_SYSTEM, "spring_rmi"),
                          equalTo(RPC_SERVICE, "springrmi.app.SpringRmiGreeter"),
                          equalTo(RPC_METHOD, "hello")));
          if (testSource == TestSource.RMI) {
            assertions.add(
                span ->
                    span.hasName(testSource.remoteClassName + "/hello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            equalTo(RPC_SYSTEM, testSource.serverSystem),
                            equalTo(RPC_SERVICE, testSource.remoteClassName),
                            equalTo(RPC_METHOD, "hello")));
          }

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  @ParameterizedTest(autoCloseArguments = false)
  @EnumSource(TestSource.class)
  void throwsException(TestSource testSource) {
    SpringRmiGreeter client = testSource.appContext.getBean(SpringRmiGreeter.class);
    Throwable error =
        assertThrows(
            testSource.expectedException, () -> testing.runWithSpan("parent", client::exceptional));
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
                                  .hasAttributesSatisfying(
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
                                  .hasAttributesSatisfying(
                                      equalTo(EXCEPTION_TYPE, error.getClass().getCanonicalName()),
                                      equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                      satisfies(
                                          EXCEPTION_STACKTRACE,
                                          val -> val.isInstanceOf(String.class))))
                      .hasAttributesSatisfying(
                          equalTo(RPC_SYSTEM, "spring_rmi"),
                          equalTo(RPC_SERVICE, "springrmi.app.SpringRmiGreeter"),
                          equalTo(RPC_METHOD, "exceptional")));
          if (testSource == TestSource.RMI) {
            assertions.add(
                span ->
                    span.hasName(testSource.remoteClassName + "/exceptional")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfying(
                                        equalTo(
                                            EXCEPTION_TYPE, error.getClass().getCanonicalName()),
                                        equalTo(EXCEPTION_MESSAGE, error.getMessage()),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfying(
                            equalTo(RPC_SYSTEM, testSource.serverSystem),
                            equalTo(RPC_SERVICE, testSource.remoteClassName),
                            equalTo(RPC_METHOD, "exceptional")));
          }

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }
}
