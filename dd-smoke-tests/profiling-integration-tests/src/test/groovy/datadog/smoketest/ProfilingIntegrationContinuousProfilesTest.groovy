package datadog.smoketest

import com.datadog.profiling.testing.ProfilingTestUtils
import com.google.common.collect.Multimap
import net.jpountz.lz4.LZ4FrameInputStream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.openjdk.jmc.common.item.Aggregators
import org.openjdk.jmc.common.item.Attribute
import org.openjdk.jmc.common.item.IItemCollection
import org.openjdk.jmc.common.item.ItemFilters
import org.openjdk.jmc.common.unit.UnitLookup
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit

import java.time.Instant
import java.util.concurrent.TimeUnit

class ProfilingIntegrationContinuousProfilesTest extends AbstractSmokeTest {

  // This needs to give enough time for test app to start up and recording to happen
  private static final int REQUEST_WAIT_TIMEOUT = 40

  private final MockWebServer server = new MockWebServer()

  @Override
  ProcessBuilder createProcessBuilder() {
    String profilingShadowJar = System.getProperty("datadog.smoketest.profiling.shadowJar.path")

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.add("-Ddd.profiling.continuous.to.periodic.upload.ratio=0") // Disable periodic profiles
    command.addAll((String[]) ["-jar", profilingShadowJar])
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))
    return processBuilder
  }

  def setup() {
    server.start(profilingPort)
  }

  def cleanup() {
    try {
      server.shutdown()
    } catch (final IOException e) {
      // Looks like this happens for some unclear reason, but should not affect tests
    }
  }

  def "test continuous recording"() {
    setup:
    server.enqueue(new MockResponse().setResponseCode(200))

    when:
    RecordedRequest firstRequest = server.takeRequest(REQUEST_WAIT_TIMEOUT, TimeUnit.SECONDS)
    Multimap<String, Object> firstRequestParameters =
      ProfilingTestUtils.parseProfilingRequestParameters(firstRequest)

    then:
    firstRequest.getRequestUrl().toString() == profilingUrl
    firstRequest.getHeader("DD-API-KEY") == API_KEY

    firstRequestParameters.get("recording-name").get(0) == 'dd-profiling'
    firstRequestParameters.get("format").get(0) == "jfr"
    firstRequestParameters.get("type").get(0) == "jfr-continuous"
    firstRequestParameters.get("runtime").get(0) == "jvm"

    def firstStartTime = Instant.parse(firstRequestParameters.get("recording-start").get(0))
    def firstEndTime = Instant.parse(firstRequestParameters.get("recording-end").get(0))
    firstStartTime != null
    firstEndTime != null
    def duration = firstEndTime.toEpochMilli() - firstStartTime.toEpochMilli()
    duration > TimeUnit.SECONDS.toMillis(PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS - 2)
    duration < TimeUnit.SECONDS.toMillis(PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS + 2)

    Map<String, String> requestTags = ProfilingTestUtils.parseTags(firstRequestParameters.get("tags[]"))
    requestTags.get("service") == "smoke-test-java-app"
    requestTags.get("language") == "jvm"
    requestTags.get("runtime-id") != null
    requestTags.get("host") == InetAddress.getLocalHost().getHostName()

    firstRequestParameters.get("chunk-data").get(0) != null

    when:
    RecordedRequest secondRequest = server.takeRequest(REQUEST_WAIT_TIMEOUT, TimeUnit.SECONDS)
    Multimap<String, Object> secondRequestParameters =
      ProfilingTestUtils.parseProfilingRequestParameters(secondRequest)

    then:
    secondRequest.getRequestUrl().toString() == profilingUrl
    secondRequest.getHeader("DD-API-KEY") == API_KEY

    secondRequestParameters.get("recording-name").get(0) == 'dd-profiling'
    def secondStartTime = Instant.parse(secondRequestParameters.get("recording-start").get(0))
    def period = secondStartTime.toEpochMilli() - firstStartTime.toEpochMilli()
    period > TimeUnit.SECONDS.toMillis(PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS - 2)
    period < TimeUnit.SECONDS.toMillis(PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS + 2)

    firstRequestParameters.get("chunk-data").get(0) != null

    IItemCollection events = JfrLoaderToolkit.loadEvents(new LZ4FrameInputStream(new ByteArrayInputStream(secondRequestParameters.get("chunk-data").get(0))))
    IItemCollection scopeEvents = events.apply(ItemFilters.type("datadog.Scope"))

    scopeEvents.size() > 0

    def cpuTimeAttr = Attribute.attr("cpuTime", "cpuTime", UnitLookup.TIMESPAN)

    // filter out scope events without CPU time data
    def filteredScopeEvents = scopeEvents.apply(ItemFilters.more(cpuTimeAttr, UnitLookup.NANOSECOND.quantity(Long.MIN_VALUE)))
    // make sure there is at least one scope event with CPU time data
    filteredScopeEvents.size() > 0

    filteredScopeEvents.getAggregate(Aggregators.min("datadog.Scope", cpuTimeAttr)).longValue() >= 10_000L

    IItemCollection exceptionSampleEvents = events.apply(ItemFilters.type("datadog.ExceptionSample"))
    exceptionSampleEvents.size() > 0
  }
}
