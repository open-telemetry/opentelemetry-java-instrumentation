/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.SERVER

import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.glassfish.embeddable.BootstrapProperties
import org.glassfish.embeddable.Deployer
import org.glassfish.embeddable.GlassFish
import org.glassfish.embeddable.GlassFishProperties
import org.glassfish.embeddable.GlassFishRuntime
import org.glassfish.embeddable.archive.ScatteredArchive

/**
 * Unfortunately because we're using an embedded GlassFish instance, we aren't exercising the standard
 * OSGi setup that requires {@link io.opentelemetry.javaagent.instrumentation.javaclassloader.ClassloadingInstrumentation}.
 */
// TODO: Figure out a better way to test with OSGi included.
class GlassFishServerTest extends HttpServerTest<GlassFish> {

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
  boolean redirectHasBody() {
    true
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", Long responseContentLength = null, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name entryPointName()
      kind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
        "${SemanticAttributes.NET_PEER_PORT.key()}" Long
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" endpoint.status
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_URL.key()}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key()}" TEST_USER_AGENT
        "${SemanticAttributes.HTTP_CLIENT_IP.key()}" TEST_CLIENT_IP
      }
    }
  }

  //Simple class name plus method name of the entry point of the given servlet container.
  //"Entry point" here means the first filter or servlet that accepts incoming requests.
  //This will serve as a default name of the SERVER span created for this request.
  protected String entryPointName() {
    'HttpServlet.service'
  }
}

