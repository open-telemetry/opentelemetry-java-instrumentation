/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.example.hello.HelloController
import com.example.hello.TestFilter
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.container.test.api.RunAsClient
import org.jboss.arquillian.spock.ArquillianSputnik
import org.jboss.arquillian.test.api.ArquillianResource
import org.jboss.shrinkwrap.api.Archive
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.junit.runner.RunWith

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER

@RunWith(ArquillianSputnik)
@RunAsClient
// Test that OpenTelemetryHandlerMappingFilter injection works when spring libraries are in various
// locations inside deployment.
abstract class OpenTelemetryHandlerMappingFilterTest extends AgentInstrumentationSpecification {

  static WebClient client = WebClient.of()

  @ArquillianResource
  static URI url

  def getAddress(String service) {
    return url.resolve(service).toString()
  }

  def "test success"() {
    when:
    AggregatedHttpResponse response = client.get(getAddress("hello/world")).aggregate().join()

    then:
    response.status().code() == 200
    response.contentUtf8() == "hello world"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "/hello/{name}"
          kind SERVER
          hasNoParent()
        }
        span(1) {
          name "HelloController.hello"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }

  def "test exception"() {
    when:
    AggregatedHttpResponse response = client.get(getAddress("hello/exception")).aggregate().join()

    then:
    response.status().code() == 500

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "/hello/{name}"
          kind SERVER
          status StatusCode.ERROR
          hasNoParent()

          event(0) {
            eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key}" "javax.servlet.ServletException"
              "${SemanticAttributes.EXCEPTION_MESSAGE.key}" "exception"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key}" { it == null || it instanceof String }
            }
          }
        }
      }
    }
  }
}

// spring is inside ear/lib
class LibsInEarTest extends OpenTelemetryHandlerMappingFilterTest {
  @Deployment
  static Archive<?> createDeployment() {
    WebArchive war = ShrinkWrap.create(WebArchive, "test.war")
      .addAsWebInfResource("web.xml")
      .addAsWebInfResource("dispatcher-servlet.xml")
      .addAsWebInfResource("applicationContext.xml")
      .addClass(HelloController)
      .addClass(TestFilter)

    EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive)
      .setApplicationXML("application.xml")
      .addAsModule(war)
      .addAsLibraries(new File("build/app-libs").listFiles())

    return ear
  }
}

// spring is inside war/WEB-INF/lib
class LibsInWarTest extends OpenTelemetryHandlerMappingFilterTest {
  @Deployment
  static Archive<?> createDeployment() {
    WebArchive war = ShrinkWrap.create(WebArchive, "test.war")
      .addAsWebInfResource("web.xml")
      .addAsWebInfResource("dispatcher-servlet.xml")
      .addAsWebInfResource("applicationContext.xml")
      .addClass(HelloController)
      .addClass(TestFilter)
      .addAsLibraries(new File("build/app-libs").listFiles())

    EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive)
      .setApplicationXML("application.xml")
      .addAsModule(war)

    return ear
  }
}

// Everything except spring-webmvc is in ear/lib, spring-webmvc is in war/WEB-INF/lib
class MixedLibsTest extends OpenTelemetryHandlerMappingFilterTest {
  @Deployment
  static Archive<?> createDeployment() {
    WebArchive war = ShrinkWrap.create(WebArchive, "test.war")
      .addAsWebInfResource("web.xml")
      .addAsWebInfResource("dispatcher-servlet.xml")
      .addAsWebInfResource("applicationContext.xml")
      .addClass(HelloController)
      .addClass(TestFilter)
      .addAsLibraries(new File("build/app-libs").listFiles(new FilenameFilter() {
        @Override
        boolean accept(File dir, String name) {
          return name.contains("spring-webmvc")
        }
      }))

    EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive)
      .setApplicationXML("application.xml")
      .addAsModule(war)
      .addAsLibraries(new File("build/app-libs").listFiles(new FilenameFilter() {
        @Override
        boolean accept(File dir, String name) {
          return !name.contains("spring-webmvc")
        }
      }))

    return ear
  }
}
