import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.servlet3.Servlet3Decorator
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
class GlassFishServerTest extends HttpServerTest<GlassFish> {

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
  String component() {
    return Servlet3Decorator.DECORATE.component()
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
  void serverSpan(TraceAssert trace, int index, BigInteger traceID = null, BigInteger parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
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
        "$Tags.COMPONENT" component
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.PEER_PORT" Integer
        "$Tags.HTTP_STATUS" endpoint.status
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "servlet.context" "/$context"
        "servlet.path" endpoint.path
        "span.origin.type" { it.startsWith("TestServlets\$") || it == DefaultServlet.name }
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$DDTags.HTTP_QUERY" endpoint.query
        }
        defaultTags(true)
      }
    }
  }
}

