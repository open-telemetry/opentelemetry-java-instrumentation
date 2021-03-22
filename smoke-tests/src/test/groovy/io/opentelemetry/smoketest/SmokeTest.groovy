/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import static java.util.stream.Collectors.toSet

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.smoketest.windows.WindowsTestContainerManager
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.ToStringConsumer
import spock.lang.Shared
import spock.lang.Specification

abstract class SmokeTest extends Specification {
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*")

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  protected static final OkHttpClient CLIENT = OkHttpUtils.client()

  protected static final TestContainerManager containerManager = createContainerManager()

  @Shared
  protected String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path")

  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected Map<String, String> getExtraEnv() {
    return Collections.emptyMap()
  }
  /**
   * Subclasses can override this method to customise target application's environment
   */
  protected void customizeContainer(GenericContainer container) {
  }

  def setupSpec() {
    containerManager.startEnvironment()
  }

  def startTarget(int jdk) {
    startTarget(String.valueOf(jdk), null, false)
  }

  def startTarget(String jdk, String serverVersion, boolean windows) {
    def targetImage = getTargetImage(jdk, serverVersion, windows)
    return containerManager.startTarget(targetImage, agentPath, extraEnv, getWaitStrategy())
  }

  protected abstract String getTargetImage(String jdk)

  protected String getTargetImage(String jdk, String serverVersion, boolean windows) {
    return getTargetImage(jdk)
  }

  protected TargetWaitStrategy getWaitStrategy() {
    return null
  }

  def cleanup() {
    CLIENT.newCall(new Request.Builder()
      .url("http://localhost:${containerManager.getBackendMappedPort()}/clear-requests")
      .build())
      .execute()
      .close()
  }

  def stopTarget() {
    containerManager.stopTarget()
  }

  def cleanupSpec() {
    containerManager.stopEnvironment()
  }

  protected static Stream<AnyValue> findResourceAttribute(Collection<ExportTraceServiceRequest> traces,
                                                          String attributeKey) {
    return traces.stream()
      .flatMap { it.getResourceSpansList().stream() }
      .flatMap { it.getResource().getAttributesList().stream() }
      .filter { it.key == attributeKey }
      .map { it.value }
  }

  protected static int countSpansByName(Collection<ExportTraceServiceRequest> traces, String spanName) {
    return getSpanStream(traces).filter { it.name == spanName }.count()
  }

  protected static Stream<Span> getSpanStream(Collection<ExportTraceServiceRequest> traces) {
    return traces.stream()
      .flatMap { it.getResourceSpansList().stream() }
      .flatMap { it.getInstrumentationLibrarySpansList().stream() }
      .flatMap { it.getSpansList().stream() }
  }

  protected Collection<ExportTraceServiceRequest> waitForTraces() {
    def content = waitForContent()

    return OBJECT_MAPPER.readTree(content).collect {
      def builder = ExportTraceServiceRequest.newBuilder()
      // TODO(anuraaga): Register parser into object mapper to avoid de -> re -> deserialize.
      JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder)
      return builder.build()
    }
  }

  private String waitForContent() {
    long previousSize = 0
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
    String content = "[]"
    while (System.currentTimeMillis() < deadline) {
      def body = content = CLIENT.newCall(new Request.Builder()
        .url("http://localhost:${containerManager.getBackendMappedPort()}/get-requests")
        .build())
        .execute()
        .body()
      try {
        content = body.string()
      } finally {
        body.close()
      }
      if (content.length() > 2 && content.length() == previousSize) {
        break
      }
      previousSize = content.length()
      println "Curent content size $previousSize"
      TimeUnit.MILLISECONDS.sleep(500)
    }

    return content
  }

  protected static Set<String> getLoggedTraceIds(ToStringConsumer output) {
    output.toUtf8String().lines()
      .flatMap(SmokeTest.&findTraceId)
      .collect(toSet())
  }

  private static Stream<String> findTraceId(String log) {
    def m = TRACE_ID_PATTERN.matcher(log)
    m.matches() ? Stream.of(m.group("traceId")) : Stream.empty() as Stream<String>
  }

  protected static boolean isVersionLogged(ToStringConsumer output, String version) {
    output.toUtf8String().lines()
      .filter({ it.contains("opentelemetry-javaagent - version: " + version) })
      .findFirst()
      .isPresent()
  }

  // TODO(anuraaga): Delete after https://github.com/open-telemetry/opentelemetry-java/pull/2750
  static String bytesToHex(byte[] bytes) {
    char[] dest = new char[bytes.length * 2]
    bytesToBase16(bytes, dest)
    return new String(dest)
  }

  private static void bytesToBase16(byte[] bytes, char[] dest) {
    for (int i = 0; i < bytes.length; i++) {
      byteToBase16(bytes[i], dest, i * 2)
    }
  }

  private static void byteToBase16(byte value, char[] dest, int destOffset) {
    int b = value & 0xFF
    dest[destOffset] = ENCODING[b]
    dest[destOffset + 1] = ENCODING[b | 0x100]
  }

  private static final String ALPHABET = "0123456789abcdef"
  private static final char[] ENCODING = buildEncodingArray()

  private static char[] buildEncodingArray() {
    char[] encoding = new char[512]
    for (int i = 0; i < 256; ++i) {
      encoding[i] = ALPHABET.charAt(i >>> 4)
      encoding[i | 0x100] = ALPHABET.charAt(i & 0xF)
    }
    return encoding
  }

  private static TestContainerManager createContainerManager() {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows")

    if (isWindows && "1" != System.getenv("USE_LINUX_CONTAINERS")) {
      return new WindowsTestContainerManager()
    } else {
      return new LinuxTestContainerManager()
    }
  }

}
