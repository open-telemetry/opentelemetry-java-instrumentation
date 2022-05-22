/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import test.CdiRestResource;
import test.EjbRestResource;
import test.RestApplication;

@ExtendWith(ArquillianExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RunAsClient
public abstract class AbstractArquillianRestTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  public final WebClient client = WebClient.of();

  @ArquillianResource public URI url;

  @Deployment
  static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive.class)
        .addClass(RestApplication.class)
        .addClass(CdiRestResource.class)
        .addClass(EjbRestResource.class)
        .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
  }

  private String getContextRoot() {
    return url.getPath();
  }

  @Test
  public void testHelloCdiRestResource() {
    testHelloRequest("rest-app/cdiHello", "CdiRestResource");
  }

  @Test
  public void testHelloEjbRestResource() {
    testHelloRequest("rest-app/ejbHello", "EjbRestResource");
  }

  // @ParameterizedTest doesn't work correctly with arquillian, all exceptions (assertion errors)
  // thrown from the test method are ignored
  private void testHelloRequest(String path, String className) {
    AggregatedHttpResponse response = client.get(url.resolve(path).toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextRoot() + path).hasKind(SpanKind.SERVER).hasNoParent(),
                span -> span.hasName(className + ".hello").hasParent(trace.getSpan(0))));
  }
}
