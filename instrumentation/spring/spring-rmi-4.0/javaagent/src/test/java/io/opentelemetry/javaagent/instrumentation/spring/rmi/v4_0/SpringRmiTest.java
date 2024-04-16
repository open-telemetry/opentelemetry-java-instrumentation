/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.naming.NamingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;
import org.springframework.remoting.rmi.RmiServiceExporter;
import org.springframework.remoting.support.RemoteExporter;
import org.springframework.stereotype.Component;
import springrmi.app.SpringRmiGreeter;
import springrmi.app.SpringRmiGreeterImpl;
import springrmi.app.ejb.SpringRmiEjbMock;

class SpringRmiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext serverAppContext;

  private static ConfigurableApplicationContext clientAppContext;

  private static ConfigurableApplicationContext xmlAppContext;

  static int registryPort;

  @Component
  private static class ServerConfig {
    @Bean
    static RemoteExporter registerRMIExporter() throws NamingException {
      SpringRmiGreeter greeter = new SpringRmiGreeterImpl();

      RmiServiceExporter exporter = new RmiServiceExporter();
      exporter.setServiceName("springRmiGreeter");
      exporter.setServiceInterface(SpringRmiGreeter.class);
      exporter.setService(greeter);
      exporter.setRegistryPort(registryPort);

      // Register in JNDI for remote-slsb testing
      SimpleNamingContextBuilder builder = new SimpleNamingContextBuilder();
      builder.bind("springrmi/test/springRmiGreeter", new SpringRmiEjbMock(greeter));
      builder.activate();

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
  static void beforeAll() {
    registryPort = PortUtils.findOpenPort();

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
  }

  static Stream<Arguments> generateParams() {
    return Stream.of(
        Arguments.of(clientAppContext, "springrmi.app.SpringRmiGreeterImpl", "spring_rmi"),
        Arguments.of(xmlAppContext, "springrmi.app.ejb.SpringRmiGreeterRemote", "java_rmi"));
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("generateParams")
  void clientCallCreatesSpans(
      ApplicationContext appContext, String remoteClass, String serverSystem)
      throws RemoteException {
    SpringRmiGreeter client = appContext.getBean(SpringRmiGreeter.class);
    String response = testing.runWithSpan("parent", () -> client.hello("Test Name"));
    assertEquals(response, "Hello Test Name");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("springrmi.app.SpringRmiGreeter/hello")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "spring_rmi"),
                            equalTo(
                                RpcIncubatingAttributes.RPC_SERVICE,
                                "springrmi.app.SpringRmiGreeter"),
                            equalTo(RpcIncubatingAttributes.RPC_METHOD, "hello")),
                span ->
                    span.hasName(remoteClass + "/hello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, serverSystem),
                            equalTo(RpcIncubatingAttributes.RPC_SERVICE, remoteClass),
                            equalTo(RpcIncubatingAttributes.RPC_METHOD, "hello"))));
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("generateParams")
  void throwsException(ApplicationContext appContext, String remoteClass, String serverSystem) {
    SpringRmiGreeter client = appContext.getBean(SpringRmiGreeter.class);
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class, () -> testing.runWithSpan("parent", client::exceptional));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
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
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            error.getClass().getCanonicalName()),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            error.getMessage()),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)))),
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
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            error.getClass().getCanonicalName()),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            error.getMessage()),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfying(
                            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, "spring_rmi"),
                            equalTo(
                                RpcIncubatingAttributes.RPC_SERVICE,
                                "springrmi.app.SpringRmiGreeter"),
                            equalTo(RpcIncubatingAttributes.RPC_METHOD, "exceptional")),
                span ->
                    span.hasName(remoteClass + "/exceptional")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfying(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            error.getClass().getCanonicalName()),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            error.getMessage()),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfying(
                            equalTo(RpcIncubatingAttributes.RPC_SYSTEM, serverSystem),
                            equalTo(RpcIncubatingAttributes.RPC_SERVICE, remoteClass),
                            equalTo(RpcIncubatingAttributes.RPC_METHOD, "exceptional"))));
  }
}
