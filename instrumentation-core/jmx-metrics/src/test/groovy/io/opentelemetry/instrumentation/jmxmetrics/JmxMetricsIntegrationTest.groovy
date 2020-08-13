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

package io.opentelemetry.instrumentation.jmxmetrics

import static io.opentelemetry.proto.metrics.v1.MetricDescriptor.Type.SUMMARY
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc
import io.opentelemetry.proto.common.v1.InstrumentationLibrary
import io.opentelemetry.proto.common.v1.StringKeyValue
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics
import io.opentelemetry.proto.metrics.v1.Metric
import io.opentelemetry.proto.metrics.v1.MetricDescriptor
import io.opentelemetry.proto.metrics.v1.ResourceMetrics
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint
import java.time.Duration
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.MountableFile
import spock.lang.Shared
import spock.lang.Specification

class JmxMetricsIntegrationTest extends Specification {

  @Shared
  def cassandraContainer

  @Shared
  def jmxExtensionAppContainer

  @Shared
  def collector = new Collector()
  @Shared
  def collectorServer = ServerBuilder.forPort(55680).addService(collector).build()

  def setupSpec() {
    Testcontainers.exposeHostPorts(55680)

    def jarPath = System.getProperty("shadow.jar.path")

    def scriptName = "script.groovy"
    def scriptPath = ClassLoader.getSystemClassLoader().getResource(scriptName).getPath()

    def configName = "config.properties"
    def configPath = ClassLoader.getSystemClassLoader().getResource(configName).getPath()

    def cassandraDockerfile = ("FROM cassandra:3.11\n"
        + "ENV LOCAL_JMX=no\n"
        + "RUN echo 'cassandra cassandra' > /etc/cassandra/jmxremote.password\n"
        + "RUN chmod 0400 /etc/cassandra/jmxremote.password\n")

    def network = Network.SHARED

    cassandraContainer =
          new GenericContainer<>(
                  new ImageFromDockerfile().withFileFromString("Dockerfile", cassandraDockerfile))
              .withNetwork(network)
              .withNetworkAliases("cassandra")
              .withExposedPorts(7199)
              .withStartupTimeout(Duration.ofSeconds(120))
              .waitingFor(Wait.forListeningPort())
    cassandraContainer.start()

    jmxExtensionAppContainer =
          new GenericContainer<>("openjdk:7u111-jre-alpine")
              .withNetwork(network)
              .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/OpenTelemetryJava.jar")
              .withCopyFileToContainer(
                  MountableFile.forHostPath(scriptPath), "/app/${scriptName}")
              .withCopyFileToContainer(
                  MountableFile.forHostPath(configPath), "/app/${configName}")
              .withCommand("java -cp /app/OpenTelemetryJava.jar "
                      + "-Dotel.jmx.metrics.username=cassandra "
                      + "-Dotel.jmx.metrics.password=cassandra "
                      + "io.opentelemetry.instrumentation.jmxmetrics.JmxMetrics "
                      + "-config /app/config.properties")
              .withStartupTimeout(Duration.ofSeconds(120))
              .waitingFor(Wait.forLogMessage(".*Started GroovyRunner.*", 1))
              .dependsOn(cassandraContainer)
    jmxExtensionAppContainer.start()

    expect:
    cassandraContainer.isRunning()
    jmxExtensionAppContainer.isRunning()
  }

  def setup() {
    collectorServer.start()
  }

  def cleanupSpec() {
    collectorServer.shutdown()
  }

  def "end to end"() {
    when:
    List<ResourceMetrics> receivedMetrics = collector.getReceivedMetrics()
    then:
    assertEquals(1, receivedMetrics.size())
    ResourceMetrics receivedMetric = receivedMetrics.get(0)

    List<InstrumentationLibraryMetrics> ilMetrics =
        receivedMetric.getInstrumentationLibraryMetricsList()
    assertEquals(1, ilMetrics.size())
    InstrumentationLibraryMetrics ilMetric = ilMetrics.get(0)

    InstrumentationLibrary il = ilMetric.getInstrumentationLibrary()
    assertEquals("jmx-metrics", il.getName())
    assertEquals("0.0.1", il.getVersion())

    List<Metric> metrics = ilMetric.getMetricsList()
    assertEquals(1, metrics.size())

    Metric metric = metrics.get(0)
    MetricDescriptor md = metric.getMetricDescriptor()
    assertEquals("cassandra.storage.load", md.getName())
    assertEquals("Size, in bytes, of the on disk data size this node manages", md.getDescription())
    assertEquals("By", md.getUnit())
    assertEquals(SUMMARY, md.getType())

    List<SummaryDataPoint> datapoints = metric.getSummaryDataPointsList()
    assertEquals(1, datapoints.size())
    SummaryDataPoint datapoint = datapoints.get(0)

    List<StringKeyValue> labels = datapoint.getLabelsList()
    assertEquals(1, labels.size())
    assertEquals(
        StringKeyValue.newBuilder().setKey("myKey").setValue("myVal").build(), labels.get(0))

    assertEquals(1, datapoint.getCount())
    double sum = datapoint.getSum()
    assertEquals(sum, datapoint.getPercentileValues(0).getValue(), 0)
    assertEquals(sum, datapoint.getPercentileValues(1).getValue(), 0)
  }

  static final class Collector extends MetricsServiceGrpc.MetricsServiceImplBase {
    private final List<ResourceMetrics> receivedMetrics = new ArrayList<>()
    private final Object monitor = new Object()

    @Override
    void export(
        ExportMetricsServiceRequest request,
        StreamObserver<ExportMetricsServiceResponse> responseObserver) {
      synchronized (receivedMetrics) {
        receivedMetrics.addAll(request.getResourceMetricsList())
      }
      synchronized (monitor) {
        monitor.notify()
      }
      responseObserver.onNext(ExportMetricsServiceResponse.newBuilder().build())
      responseObserver.onCompleted()
    }

    List<ResourceMetrics> getReceivedMetrics() {
      List<ResourceMetrics> received
      try {
        synchronized (monitor) {
          monitor.wait(15000)
        }
      } catch (final InterruptedException e) {
        assertTrue(e.getMessage(), false)
      }

      synchronized (receivedMetrics) {
        received = new ArrayList<>(receivedMetrics)
        receivedMetrics.clear()
      }
      return received
    }
  }
}
