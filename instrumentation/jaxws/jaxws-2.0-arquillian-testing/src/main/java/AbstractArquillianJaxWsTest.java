/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.URI;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import test.EjbHelloServiceImpl;
import test.HelloService;
import test.HelloServiceImpl;

@ExtendWith(ArquillianExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RunAsClient
public abstract class AbstractArquillianJaxWsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public final WebClient client = WebClient.of();

  @ArquillianResource public URI url;

  @Deployment
  static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive.class)
        .addClass(HelloService.class)
        .addClass(HelloServiceImpl.class)
        .addClass(EjbHelloServiceImpl.class)
        .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
  }

  private String getContextRoot() {
    return url.getPath();
  }

  protected String getServicePath(String service) {
    return service;
  }

  private String getAddress(String service) {
    return url.resolve(getServicePath(service)).toString();
  }

  @ParameterizedTest
  @ValueSource(strings = {"HelloService", "EjbHelloService"})
  public void testHelloRequest(String service) {
    String soapMessage =
        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://opentelemetry.io/test/hello-web-service\">"
            + "   <soapenv:Header/>"
            + "   <soapenv:Body>"
            + "      <hel:helloRequest>"
            + "         <name>Test</name>"
            + "      </hel:helloRequest>"
            + "   </soapenv:Body>"
            + "</soapenv:Envelope>";

    AggregatedHttpResponse response =
        client.post(getAddress(service), soapMessage).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("message").text()).isEqualTo("Hello Test");

    String methodName = "hello";
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, serverSpanName(service, methodName)).hasNoParent(),
                span -> assertHandlerSpan(span, service, methodName).hasParent(trace.getSpan(0)),
                span ->
                    assertAnnotationHandlerSpan(span, service, methodName)
                        .hasParent(trace.getSpan(1))));
  }

  private String serverSpanName(String service, String operation) {
    return getContextRoot() + getServicePath(service) + "/" + service + "/" + operation;
  }

  private static SpanDataAssert assertServerSpan(SpanDataAssert span, String operation) {
    return span.hasName(operation).hasKind(SpanKind.SERVER);
  }

  private static SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String service, String methodName) {
    return span.hasName(service + "/" + methodName).hasKind(SpanKind.INTERNAL);
  }

  private static SpanDataAssert assertAnnotationHandlerSpan(
      SpanDataAssert span, String service, String methodName) {
    return span.hasName(service + "Impl." + methodName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfying(
            attrs -> {
              assertThat(attrs)
                  .containsEntry(SemanticAttributes.CODE_NAMESPACE, "test." + service + "Impl");
              assertThat(attrs).containsEntry(SemanticAttributes.CODE_FUNCTION, methodName);
            });
  }
}
