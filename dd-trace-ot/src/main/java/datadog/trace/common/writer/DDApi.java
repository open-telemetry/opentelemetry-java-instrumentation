package datadog.trace.common.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/** The API pointing to a DD agent */
@Slf4j
public class DDApi {

  private static final String TRACES_ENDPOINT_V3 = "/v0.3/traces";
  private static final String SERVICES_ENDPOINT_V3 = "/v0.3/services";
  private static final String TRACES_ENDPOINT_V4 = "/v0.4/traces";
  private static final String SERVICES_ENDPOINT_V4 = "/v0.4/services";
  private static final long SECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toSeconds(5);

  private final String tracesEndpoint;
  private final String servicesEndpoint;
  private final List<ResponseListener> responseListeners = new ArrayList<>();

  private final RateLimiter loggingRateLimiter =
      RateLimiter.create(1.0 / SECONDS_BETWEEN_ERROR_LOG);

  private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

  public DDApi(final String host, final int port) {
    this(
        host,
        port,
        traceEndpointAvailable("http://" + host + ":" + port + TRACES_ENDPOINT_V4)
            && serviceEndpointAvailable("http://" + host + ":" + port + SERVICES_ENDPOINT_V4));
  }

  DDApi(final String host, final int port, final boolean v4EndpointsAvailable) {
    if (v4EndpointsAvailable) {
      this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT_V4;
      this.servicesEndpoint = "http://" + host + ":" + port + SERVICES_ENDPOINT_V4;
    } else {
      log.debug("API v0.4 endpoints not available. Downgrading to v0.3");
      this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT_V3;
      this.servicesEndpoint = "http://" + host + ":" + port + SERVICES_ENDPOINT_V3;
    }
  }

  public void addResponseListener(final ResponseListener listener) {
    if (!responseListeners.contains(listener)) {
      responseListeners.add(listener);
    }
  }

  /**
   * Send traces to the DD agent
   *
   * @param traces the traces to be sent
   * @return the staus code returned
   */
  public boolean sendTraces(final List<List<DDSpan>> traces) {
    return putContent("traces", tracesEndpoint, traces, traces.size());
  }

  /**
   * Send service extra information to the services endpoint
   *
   * @param services the services to be sent
   */
  public boolean sendServices(final Map<String, Service> services) {
    if (services == null) {
      return true;
    }
    return putContent("services", servicesEndpoint, services, services.size());
  }

  /**
   * PUT to an endpoint the provided JSON content
   *
   * @param content
   * @return the status code
   */
  private boolean putContent(
      final String type, final String endpoint, final Object content, final int size) {
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(endpoint);

      final OutputStream out = httpCon.getOutputStream();
      objectMapper.writeValue(out, content);
      out.flush();
      out.close();

      String responseString = null;
      {
        final BufferedReader responseReader =
            new BufferedReader(new InputStreamReader(httpCon.getInputStream()));
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
              "Error while sending {} {} to the DD agent. Status: {}, ResponseMessage: ",
              size,
              type,
              responseCode,
              httpCon.getResponseMessage());
        } else if (loggingRateLimiter.tryAcquire()) {
          log.warn(
              "Error while sending {} {} to the DD agent. Status: {} (going silent for {} seconds)",
              size,
              type,
              responseCode,
              httpCon.getResponseMessage(),
              SECONDS_BETWEEN_ERROR_LOG);
        }
        return false;
      }

      log.debug("Succesfully sent {} {} to the DD agent.", size, type);

      try {
        if (null != responseString
            && !"".equals(responseString.trim())
            && !"OK".equalsIgnoreCase(responseString.trim())) {
          final JsonNode response = objectMapper.readTree(responseString);
          for (final ResponseListener listener : responseListeners) {
            listener.onResponse(endpoint, response);
          }
        }
      } catch (final IOException e) {
        log.debug("failed to parse DD agent response: " + responseString, e);
      }
      return true;

    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("Error while sending " + size + " " + type + " to the DD agent.", e);
      } else if (loggingRateLimiter.tryAcquire()) {
        log.warn(
            "Error while sending {} {} to the DD agent. {}: {} (going silent for {} seconds)",
            size,
            type,
            e.getClass().getName(),
            e.getMessage(),
            SECONDS_BETWEEN_ERROR_LOG);
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
    httpCon.setRequestProperty("Datadog-Meta-Lang", "java");
    httpCon.setRequestProperty("Datadog-Meta-Lang-Version", DDTraceOTInfo.JAVA_VERSION);
    httpCon.setRequestProperty("Datadog-Meta-Lang-Interpreter", DDTraceOTInfo.JAVA_VM_NAME);
    httpCon.setRequestProperty("Datadog-Meta-Tracer-Version", DDTraceOTInfo.VERSION);
    return httpCon;
  }

  @Override
  public String toString() {
    return "DDApi { tracesEndpoint=" + tracesEndpoint + " }";
  }

  public static interface ResponseListener {
    /** Invoked after the api receives a response from the core agent. */
    void onResponse(String endpoint, JsonNode responseJson);
  }
}
