package datadog.trace.tracer.writer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import datadog.trace.tracer.LogRateLimiter;
import datadog.trace.tracer.Trace;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

@Slf4j
class AgentClient {

  static final String TRACES_ENDPOINT = "/v0.4/traces";

  static final String CONTENT_TYPE = "Content-Type";
  static final String MSGPACK = "application/msgpack";
  static final String DATADOG_META_LANG = "Datadog-Meta-Lang";
  static final String DATADOG_META_LANG_VERSION = "Datadog-Meta-Lang-Version";
  static final String DATADOG_META_LANG_INTERPRETER = "Datadog-Meta-Lang-Interpreter";
  static final String DATADOG_META_TRACER_VERSION = "Datadog-Meta-Tracer-Version";
  static final String X_DATADOG_TRACE_COUNT = "X-Datadog-Trace-Count";

  static final int CONNECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(1);
  static final int READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(1);

  private static final long MILLISECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toMillis(5);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());
  private static final LogRateLimiter LOG_RATE_LIMITER =
      new LogRateLimiter(log, MILLISECONDS_BETWEEN_ERROR_LOG);

  @Getter private final URL agentUrl;

  AgentClient(final String host, final int port) {
    final String url = "http://" + host + ":" + port + TRACES_ENDPOINT;
    try {
      agentUrl = new URL(url);
    } catch (final MalformedURLException e) {
      // This should essentially mean agent should bail out from installing, we cannot meaningfully
      // recover from this.
      throw new RuntimeException("Cannot parse agent url: " + url, e);
    }
  }

  /**
   * Send traces to the Datadog agent
   *
   * @param traces the traces to be sent
   * @param traceCount total number of traces
   */
  public SampleRateByService sendTraces(final List<Trace> traces, final int traceCount) {
    final TracesRequest request = new TracesRequest(traces);
    try {
      final HttpURLConnection connection = createHttpConnection();
      connection.setRequestProperty(X_DATADOG_TRACE_COUNT, String.valueOf(traceCount));

      try (final OutputStream out = connection.getOutputStream()) {
        OBJECT_MAPPER.writeValue(out, request);
      }

      final int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        throw new IOException(
            String.format(
                "Error while sending %d of %d traces to the DD agent. Status: %d, ResponseMessage: %s",
                traces.size(), traceCount, responseCode, connection.getResponseMessage()));
      }

      try (final InputStream in = connection.getInputStream()) {
        final TracesResponse response = OBJECT_MAPPER.readValue(in, TracesResponse.class);
        return response.getSampleRateByService();
      }
    } catch (final IOException e) {
      LOG_RATE_LIMITER.warn(
          "Error while sending {} of {} traces to the DD agent.", traces.size(), traceCount, e);
    }
    return null;
  }

  private HttpURLConnection createHttpConnection() throws IOException {
    final HttpURLConnection connection = (HttpURLConnection) agentUrl.openConnection();
    connection.setDoOutput(true);
    connection.setDoInput(true);

    // It is important to have timeout for agent request here: we need to finish request in some
    // reasonable amount
    // of time to allow following requests to be run.
    connection.setConnectTimeout(CONNECT_TIMEOUT);
    connection.setReadTimeout(READ_TIMEOUT);

    connection.setRequestMethod("PUT");
    connection.setRequestProperty(CONTENT_TYPE, MSGPACK);
    connection.setRequestProperty(DATADOG_META_LANG, "java");

    // TODO: set these variables properly!!!
    connection.setRequestProperty(DATADOG_META_LANG_VERSION, "TODO: DDTraceOTInfo.JAVA_VERSION");
    connection.setRequestProperty(
        DATADOG_META_LANG_INTERPRETER, "TODO: DDTraceOTInfo.JAVA_VM_NAME");
    connection.setRequestProperty(DATADOG_META_TRACER_VERSION, "TODO: DDTraceOTInfo.VERSION");

    return connection;
  }

  private static class TracesRequest {

    private final List<Trace> traces;

    TracesRequest(final List<Trace> traces) {
      this.traces = Collections.unmodifiableList(traces);
    }

    @JsonValue
    public List<Trace> getTraces() {
      return traces;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class TracesResponse {

    private final SampleRateByService sampleRateByService;

    @JsonCreator
    TracesResponse(@JsonProperty("rate_by_service") final SampleRateByService sampleRateByService) {
      this.sampleRateByService = sampleRateByService;
    }

    public SampleRateByService getSampleRateByService() {
      return sampleRateByService;
    }
  }
}
