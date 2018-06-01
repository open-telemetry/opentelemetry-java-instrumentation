package datadog.trace.common.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/** The API pointing to a DD agent */
@Slf4j
public class DDApi {
  private static final String DATADOG_META_LANG = "Datadog-Meta-Lang";
  private static final String DATADOG_META_LANG_VERSION = "Datadog-Meta-Lang-Version";
  private static final String DATADOG_META_LANG_INTERPRETER = "Datadog-Meta-Lang-Interpreter";
  private static final String DATADOG_META_TRACER_VERSION = "Datadog-Meta-Tracer-Version";
  private static final String X_DATADOG_TRACE_COUNT = "X-Datadog-Trace-Count";

  private static final String TRACES_ENDPOINT_V3 = "/v0.3/traces";
  private static final String TRACES_ENDPOINT_V4 = "/v0.4/traces";
  private static final long MILLISECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toMillis(5);

  private final String tracesEndpoint;
  private final List<ResponseListener> responseListeners = new ArrayList<>();

  private AtomicInteger traceCount;
  private volatile long nextAllowedLogTime = 0;

  private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

  public DDApi(final String host, final int port) {
    this(host, port, traceEndpointAvailable("http://" + host + ":" + port + TRACES_ENDPOINT_V4));
  }

  DDApi(final String host, final int port, final boolean v4EndpointsAvailable) {
    if (v4EndpointsAvailable) {
      this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT_V4;
    } else {
      log.debug("API v0.4 endpoints not available. Downgrading to v0.3");
      this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT_V3;
    }
  }

  public void addResponseListener(final ResponseListener listener) {
    if (!responseListeners.contains(listener)) {
      responseListeners.add(listener);
    }
  }

  public void addTraceCounter(final AtomicInteger traceCount) {
    this.traceCount = traceCount;
  }

  /**
   * Send traces to the DD agent
   *
   * @param traces the traces to be sent
   * @return the staus code returned
   */
  public boolean sendTraces(final List<List<DDSpan>> traces) {
    final int totalSize = traceCount == null ? traces.size() : traceCount.getAndSet(0);
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(tracesEndpoint);
      httpCon.setRequestProperty(X_DATADOG_TRACE_COUNT, String.valueOf(totalSize));

      final OutputStream out = httpCon.getOutputStream();
      objectMapper.writeValue(out, traces);
      out.flush();
      out.close();

      String responseString = null;
      {
        final BufferedReader responseReader =
            new BufferedReader(
                new InputStreamReader(httpCon.getInputStream(), StandardCharsets.UTF_8));
        final StringBuilder sb = new StringBuilder();

        String line = null;
        while ((line = responseReader.readLine()) != null) {
          sb.append(line);
        }
        responseReader.close();

        responseString = sb.toString();
      }

      final int responseCode = httpCon.getResponseCode();
      if (responseCode != 200) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Error while sending {} of {} traces to the DD agent. Status: {}, ResponseMessage: ",
              traces.size(),
              totalSize,
              responseCode,
              httpCon.getResponseMessage());
        } else if (nextAllowedLogTime < System.currentTimeMillis()) {
          nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
          log.warn(
              "Error while sending {} of {} traces to the DD agent. Status: {} (going silent for {} seconds)",
              traces.size(),
              totalSize,
              responseCode,
              httpCon.getResponseMessage(),
              TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
        }
        return false;
      }

      log.debug("Succesfully sent {} of {} traces to the DD agent.", traces.size(), totalSize);

      try {
        if (null != responseString
            && !"".equals(responseString.trim())
            && !"OK".equalsIgnoreCase(responseString.trim())) {
          final JsonNode response = objectMapper.readTree(responseString);
          for (final ResponseListener listener : responseListeners) {
            listener.onResponse(tracesEndpoint, response);
          }
        }
      } catch (final IOException e) {
        log.debug("failed to parse DD agent response: " + responseString, e);
      }
      return true;

    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Error while sending "
                + traces.size()
                + " of "
                + totalSize
                + " traces to the DD agent.",
            e);
      } else if (nextAllowedLogTime < System.currentTimeMillis()) {
        nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
        log.warn(
            "Error while sending {} of {} traces to the DD agent. {}: {} (going silent for {} minutes)",
            traces.size(),
            totalSize,
            e.getClass().getName(),
            e.getMessage(),
            TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
      }
      return false;
    }
  }

  private static boolean traceEndpointAvailable(final String endpoint) {
    return endpointAvailable(endpoint, Collections.emptyList(), true);
  }

  private static boolean serviceEndpointAvailable(final String endpoint) {
    return endpointAvailable(endpoint, Collections.emptyMap(), true);
  }

  private static boolean endpointAvailable(
      final String endpoint, final Object data, final boolean retry) {
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(endpoint);

      // This is potentially called in premain, so we want to fail fast.
      httpCon.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(1));
      httpCon.setReadTimeout((int) TimeUnit.SECONDS.toMillis(1));

      final OutputStream out = httpCon.getOutputStream();
      objectMapper.writeValue(out, data);
      out.flush();
      out.close();

      return httpCon.getResponseCode() == 200;
    } catch (final IOException e) {
      if (retry) {
        return endpointAvailable(endpoint, data, false);
      }
    }
    return false;
  }

  private static HttpURLConnection getHttpURLConnection(final String endpoint) throws IOException {
    final HttpURLConnection httpCon;
    final URL url = new URL(endpoint);
    httpCon = (HttpURLConnection) url.openConnection();
    httpCon.setDoOutput(true);
    httpCon.setDoInput(true);
    httpCon.setRequestMethod("PUT");
    httpCon.setRequestProperty("Content-Type", "application/msgpack");
    httpCon.setRequestProperty(DATADOG_META_LANG, "java");
    httpCon.setRequestProperty(DATADOG_META_LANG_VERSION, DDTraceOTInfo.JAVA_VERSION);
    httpCon.setRequestProperty(DATADOG_META_LANG_INTERPRETER, DDTraceOTInfo.JAVA_VM_NAME);
    httpCon.setRequestProperty(DATADOG_META_TRACER_VERSION, DDTraceOTInfo.VERSION);

    return httpCon;
  }

  @Override
  public String toString() {
    return "DDApi { tracesEndpoint=" + tracesEndpoint + " }";
  }

  public interface ResponseListener {
    /** Invoked after the api receives a response from the core agent. */
    void onResponse(String endpoint, JsonNode responseJson);
  }
}
