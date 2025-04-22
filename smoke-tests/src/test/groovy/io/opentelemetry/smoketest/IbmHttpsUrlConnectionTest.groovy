/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.time.Duration

import static io.opentelemetry.smoketest.TestContainerManager.useWindowsContainers

@IgnoreIf({ useWindowsContainers() })
class IbmHttpsUrlConnectionTest extends Specification {
  private static final Logger logger = LoggerFactory.getLogger(IbmHttpsUrlConnectionTest)

  private static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar"
  private static final String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")
  private static final int BACKEND_PORT = 8080
  private static final String BACKEND_ALIAS = "backend"

  private final Network network = Network.newNetwork()

  def "test https url connection"() {
    setup:
    GenericContainer backend =
        new GenericContainer<>(
            DockerImageName.parse(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20221127.3559314891"))
            .withExposedPorts(BACKEND_PORT)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx128m")
            .waitingFor(Wait.forHttp("/health").forPort(BACKEND_PORT))
            .withNetwork(network)
            .withNetworkAliases(BACKEND_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(logger))
    backend.start()

    def telemetryRetriever = new TelemetryRetriever(backend.getMappedPort(BACKEND_PORT))

    GenericContainer target =
      new GenericContainer<>(DockerImageName.parse("ibmjava:8-sdk"))
        .withStartupTimeout(Duration.ofMinutes(5))
        .withNetwork(network)
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withCopyFileToContainer(
          MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("ibmhttpsurlconnection/IbmHttpsUrlConnectionTest.java"), "/IbmHttpsUrlConnectionTest.java")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("ibmhttpsurlconnection/start.sh", 777), "/start.sh")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("ibmhttpsurlconnection/test.sh", 777), "/test.sh")
        .waitingFor(
          Wait.forLogMessage(".*started.*\\n", 1)
        )
        .withCommand("/bin/sh", "-c", "/start.sh")
    target.start()

    when:
    Container.ExecResult result = target.execInContainer("/bin/sh", "-c", "/test.sh")
    then:
    result.getExitCode() == 0

    TraceInspector traces = new TraceInspector(telemetryRetriever.waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Client span in the distributed trace"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_CLIENT) == 1

    and: "Expected span names"
    traces.countSpansByName("GET") == 1

    cleanup:
    System.err.println(result.toString())
    target.stop()
    backend.stop()
  }
}
