/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.test.hello_web_service.HelloRequest;
import io.opentelemetry.test.hello_web_service.HelloResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.util.ClassUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

public class AbstractJaxWs3Test extends AbstractHttpServerUsingTest<Server> {
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
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"));
    webAppContext.getMetaData().addWebInfResource(Resource.newClassPathResource("/"));

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

  private HelloResponse makeRequest(String name) {
    HelloRequest request = new HelloRequest();
    request.setName(name);
    return (HelloResponse)
        webServiceTemplate.marshalSendAndReceive(getServiceAddress("HelloService"), request);
  }

  @Test
  void successfulRequest() {
    HelloResponse response = makeRequest("Test");

    assertThat(response.getMessage()).isEqualTo("Hello Test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(serverSpanName("hello"))
                            .hasNoParent()
                            .hasKind(SpanKind.SERVER)
                            .hasStatus(StatusData.unset()),
                    span ->
                        span.hasName("HelloService/hello")
                            .hasParent(trace.getSpan(0))
                            .hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void failingRequest() {
    assertThatThrownBy(() -> makeRequest("exception"))
        .isInstanceOf(SoapFaultClientException.class)
        .hasMessage("hello exception");

    Exception expectedException = new IllegalStateException("hello exception");
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(serverSpanName("hello"))
                            .hasNoParent()
                            .hasKind(SpanKind.SERVER)
                            .hasStatus(StatusData.error()),
                    span ->
                        span.hasName("HelloService/hello")
                            .hasParent(trace.getSpan(0))
                            .hasKind(SpanKind.INTERNAL)
                            .hasStatus(StatusData.error())
                            .hasException(expectedException)));
  }

  private String serverSpanName(String operation) {
    return getContextPath() + "/ws/HelloService/" + operation;
  }
}
