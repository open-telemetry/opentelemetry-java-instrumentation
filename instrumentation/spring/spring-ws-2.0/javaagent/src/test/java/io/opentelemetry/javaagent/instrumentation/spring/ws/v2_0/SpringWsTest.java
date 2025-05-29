/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.test.hello_web_service.HelloRequest;
import io.opentelemetry.test.hello_web_service.HelloRequestSoapAction;
import io.opentelemetry.test.hello_web_service.HelloRequestWsAction;
import io.opentelemetry.test.hello_web_service.HelloResponse;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.util.ClassUtils;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpringWsTest extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private static final Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

  @BeforeAll
  void setup() {
    startServer();
  }

  @AfterAll
  void cleanup() {
    cleanupServer();
  }

  @Override
  protected ConfigurableApplicationContext setupServer() throws Exception {
    SpringApplication app = new SpringApplication(AppConfig.class, WebServiceConfig.class);
    app.setDefaultProperties(
        ImmutableMap.of(
            "server.port",
            port,
            "server.context-path",
            getContextPath(),
            "server.servlet.contextPath",
            getContextPath(),
            "server.error.include-message",
            "always"));
    marshaller.setPackagesToScan(ClassUtils.getPackageName(HelloRequest.class));
    marshaller.afterPropertiesSet();
    return app.run();
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext configurableApplicationContext)
      throws Exception {
    configurableApplicationContext.close();
  }

  @Override
  protected String getContextPath() {
    return "/xyz";
  }

  HelloResponse makeRequest(String methodName, String name) throws URISyntaxException {
    WebServiceTemplate webServiceTemplate = new WebServiceTemplate(marshaller);

    Object request;
    WebServiceMessageCallback callback = null;
    if ("hello".equals(methodName)) {
      HelloRequest req = new HelloRequest();
      req.setName(name);
      request = req;
    } else if ("helloSoapAction".equals(methodName)) {
      HelloRequestSoapAction req = new HelloRequestSoapAction();
      req.setName(name);
      request = req;
      callback = new SoapActionCallback("http://opentelemetry.io/test/hello-soap-action");
    } else if ("helloWsAction".equals(methodName)) {
      HelloRequestWsAction req = new HelloRequestWsAction();
      req.setName(name);
      request = req;
      callback = new ActionCallback("http://opentelemetry.io/test/hello-ws-action");
    } else {
      throw new IllegalArgumentException(methodName);
    }

    return (HelloResponse)
        webServiceTemplate.marshalSendAndReceive(
            address.resolve("ws").toString(), request, callback);
  }

  @ParameterizedTest
  @ValueSource(strings = {"hello", "helloSoapAction", "helloWsAction"})
  void testMethodName(String methodName) throws URISyntaxException {
    HelloResponse response = makeRequest(methodName, "Test");

    assertThat(response.getMessage()).isEqualTo("Hello Test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasNoParent().hasName("POST /xyz/ws/*").hasKind(SpanKind.SERVER),
                span ->
                    span.hasParent(trace.getSpan(0))
                        .hasName("HelloEndpoint." + methodName)
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                "io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0.HelloEndpoint"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"hello", "helloSoapAction", "helloWsAction"})
  void testMethodNameException(String methodName) {
    assertThatThrownBy(() -> makeRequest(methodName, "exception"))
        .isInstanceOf(SoapFaultClientException.class)
        .hasMessage("hello exception");

    Exception expectedException = new Exception("hello exception");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasNoParent()
                        .hasName("POST /xyz/ws/*")
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error()),
                span ->
                    span.hasParent(trace.getSpan(0))
                        .hasName("HelloEndpoint." + methodName)
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            expectedException.getClass().getCanonicalName()),
                                        equalTo(EXCEPTION_MESSAGE, expectedException.getMessage()),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                "io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0.HelloEndpoint"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName))));
  }
}
