/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.glassfish.embeddable.BootstrapProperties
import org.glassfish.embeddable.Deployer
import org.glassfish.embeddable.GlassFish
import org.glassfish.embeddable.GlassFishProperties
import org.glassfish.embeddable.GlassFishRuntime
import org.glassfish.embeddable.archive.ScatteredArchive

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.SERVER

/**
 * Unfortunately because we're using an embedded GlassFish instance, we aren't exercising the standard
 * OSGi setup that requires {@link io.opentelemetry.auto.instrumentation.javaclassloader.ClassloadingInstrumentation}.
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
      operationName entryPointName()
      spanKind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key()}" Long
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" endpoint.status
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_URL.key()}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        // exception bodies are not yet recorded
        // TODO(anuraaga): Bodies do seem to be recorded for these endpoints here, update to make assertion more precise.
        "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key()}" { it == responseContentLength || endpoint == EXCEPTION || endpoint == ERROR || endpoint == NOT_FOUND || endpoint == REDIRECT }
        "servlet.context" "/$context"
        "servlet.path" endpoint.path
        if (endpoint.query) {
          "$MoreAttributes.HTTP_QUERY" endpoint.query
        }
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

