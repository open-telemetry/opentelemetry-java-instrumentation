/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.base.HttpServerTest
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

  // Simple class name plus method name of the entry point of the given servlet container.
  // "Entry point" here means the first filter or servlet that accepts incoming requests.
  // This will serve as a default name of the SERVER span created for this request.
  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    "HttpServlet.service"
  }
}
