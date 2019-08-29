import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.servlet3.Servlet3Decorator
import io.opentracing.tag.Tags
import org.apache.catalina.servlets.DefaultServlet
import org.glassfish.embeddable.BootstrapProperties
import org.glassfish.embeddable.Deployer
import org.glassfish.embeddable.GlassFish
import org.glassfish.embeddable.GlassFishProperties
import org.glassfish.embeddable.GlassFishRuntime
import org.glassfish.embeddable.archive.ScatteredArchive

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

/**
 * Unfortunately because we're using an embedded GlassFish instance, we aren't exercising the standard
 * OSGi setup that required {@link datadog.trace.instrumentation.glassfish.GlassFishInstrumentation}.
 */
// TODO: Figure out a better way to test with OSGi included.
class GlassFishServerTest extends HttpServerTest<GlassFish, Servlet3Decorator> {

//  static {
//    System.setProperty("dd.integration.grizzly.enabled", "true")
//  }

  @Override
  URI buildAddress() {
    return new URI("http://localhost:$port/$context/")
  }

  String getContext() {
    "test-gf"
  }

  @Override
  GlassFish startServer(int port) {
    // Setup the deployment archive
    def testDir = new File(TestServlets.protectionDomain.codeSource.location.path)
    assert testDir.exists() && testDir.directory
    def testResourcesDir = new File(TestServlets.getResource("error.jsp").path).parentFile
    assert testResourcesDir.exists() && testResourcesDir.directory
    ScatteredArchive archive = new ScatteredArchive(context, ScatteredArchive.Type.WAR, testResourcesDir)
    archive.addClassPath(testDir)

    // Initialize the server
    BootstrapProperties bootstrapProperties = new BootstrapProperties()
    GlassFishRuntime glassfishRuntime = GlassFishRuntime.bootstrap(bootstrapProperties)
    GlassFishProperties glassfishProperties = new GlassFishProperties()
    glassfishProperties.setPort('http-listener', port)
    def server = glassfishRuntime.newGlassFish(glassfishProperties)
    server.start()

    // Deploy war to server
    Deployer deployer = server.getDeployer()
    println "Deploying $testDir.absolutePath with $testResourcesDir.absolutePath"
    deployer.deploy(archive.toURI())

    return server
  }

  @Override
  void stopServer(GlassFish server) {
    server.stop()
  }

  @Override
  Servlet3Decorator decorator() {
    return Servlet3Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  String expectedServiceName() {
    context
  }

  @Override
  boolean redirectHasBody() {
    true
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "servlet.context" "/$context"
        "span.origin.type" { it.startsWith("TestServlets\$") || it == DefaultServlet.name }

        defaultTags(true)
        "$Tags.COMPONENT.key" serverDecorator.component()
        if (endpoint.errored) {
          "$Tags.ERROR.key" endpoint.errored
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        "$Tags.HTTP_STATUS.key" endpoint.status
        "$Tags.HTTP_URL.key" "${endpoint.resolve(address)}"
        "$Tags.PEER_HOSTNAME.key" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT.key" Integer
        "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD.key" method
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
      }
    }
  }
}

