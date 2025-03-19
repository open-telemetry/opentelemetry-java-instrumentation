/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello.HelloServiceImpl;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.test.hello_web_service.Hello2Request;
import io.opentelemetry.test.hello_web_service.Hello2Response;
import io.opentelemetry.test.hello_web_service.HelloRequest;
import io.opentelemetry.test.hello_web_service.HelloResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.util.ClassUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

@SuppressWarnings("deprecation") // using deprecated semconv
public class AbstractJaxWs2Test extends AbstractHttpServerUsingTest<Server> {
  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private final Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
  protected final WebServiceTemplate webServiceTemplate = new WebServiceTemplate(marshaller);

  @BeforeAll
  protected void setUp() throws Exception {
    marshaller.setPackagesToScan(ClassUtils.getPackageName(HelloRequest.class));
    marshaller.afterPropertiesSet();

    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Server setupServer() throws Exception {
    List<String> configurationClasses = new ArrayList<>();
    Collections.addAll(configurationClasses, WebAppContext.getDefaultConfigurationClasses());

    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    webAppContext.setConfigurationClasses(configurationClasses);
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"));
    webAppContext.getMetaData().getWebInfClassesDirs().add(Resource.newClassPathResource("/"));

    Server jettyServer = new Server(port);

    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  private String getServiceAddress(String serviceName) {
    return address.resolve("ws/" + serviceName).toString();
  }

  private Object makeRequest(TestMethod testMethod, String name) {
    return webServiceTemplate.marshalSendAndReceive(
        getServiceAddress("HelloService"), testMethod.request(name));
  }

  @ParameterizedTest
  @EnumSource(TestMethod.class)
  void successfulRequest(TestMethod testMethod) {
    Object response = makeRequest(testMethod, "Test");

    assertThat(testMethod.message(response)).isEqualTo("Hello Test");

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      Arrays.asList(
                          span ->
                              span.hasName(serverSpanName(testMethod.methodName()))
                                  .hasNoParent()
                                  .hasKind(SpanKind.SERVER)
                                  .hasStatus(StatusData.unset()),
                          span ->
                              span.hasName("HelloService/" + testMethod.methodName())
                                  .hasParent(trace.getSpan(0))
                                  .hasKind(SpanKind.INTERNAL)));
              if (hasAnnotationHandlerSpan(testMethod)) {
                assertions.add(
                    span ->
                        span.hasName("HelloServiceImpl." + testMethod.methodName())
                            .hasParent(trace.getSpan(1))
                            .hasKind(SpanKind.INTERNAL)
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                    HelloServiceImpl.class.getName()),
                                equalTo(
                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                    testMethod.methodName())));
              }

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @ParameterizedTest
  @EnumSource(TestMethod.class)
  void failingRequest(TestMethod testMethod) {
    assertThatThrownBy(() -> makeRequest(testMethod, "exception"))
        .isInstanceOf(SoapFaultClientException.class)
        .hasMessage("hello exception");

    Exception expectedException = new IllegalStateException("hello exception");
    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      Arrays.asList(
                          span ->
                              span.hasName(serverSpanName(testMethod.methodName()))
                                  .hasNoParent()
                                  .hasKind(SpanKind.SERVER)
                                  .hasStatus(StatusData.error()),
                          span ->
                              span.hasName("HelloService/" + testMethod.methodName())
                                  .hasParent(trace.getSpan(0))
                                  .hasKind(SpanKind.INTERNAL)
                                  .hasStatus(StatusData.error())
                                  .hasException(expectedException)));
              if (hasAnnotationHandlerSpan(testMethod)) {
                assertions.add(
                    span ->
                        span.hasName("HelloServiceImpl." + testMethod.methodName())
                            .hasParent(trace.getSpan(1))
                            .hasKind(SpanKind.INTERNAL)
                            .hasStatus(StatusData.error())
                            .hasException(expectedException)
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    CodeIncubatingAttributes.CODE_NAMESPACE,
                                    HelloServiceImpl.class.getName()),
                                equalTo(
                                    CodeIncubatingAttributes.CODE_FUNCTION,
                                    testMethod.methodName())));
              }

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  private static boolean hasAnnotationHandlerSpan(TestMethod testMethod) {
    return testMethod == TestMethod.HELLO;
  }

  private String serverSpanName(String operation) {
    return getContextPath() + "/ws/HelloService/" + operation;
  }

  enum TestMethod {
    HELLO {
      @Override
      Object request(String name) {
        HelloRequest request = new HelloRequest();
        request.setName(name);
        return request;
      }

      @Override
      String message(Object response) {
        return ((HelloResponse) response).getMessage();
      }
    },
    HELLO2 {
      @Override
      Object request(String name) {
        Hello2Request request = new Hello2Request();
        request.setName(name);
        return request;
      }

      @Override
      String message(Object response) {
        return ((Hello2Response) response).getMessage();
      }
    };

    String methodName() {
      return name().toLowerCase(Locale.ROOT);
    }

    abstract Object request(String name);

    abstract String message(Object response);
  }
}
