package datadog.trace.common.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import datadog.opentracing.DDBaseSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.Service;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/** The API pointing to a DD agent */
@Slf4j
public class DDApi {

  private static final String TRACES_ENDPOINT = "/v0.3/traces";
  private static final String SERVICES_ENDPOINT = "/v0.3/services";
  private static final long SECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toSeconds(5);

  private final String tracesEndpoint;
  private final String servicesEndpoint;

  private final RateLimiter loggingRateLimiter =
      RateLimiter.create(1.0 / SECONDS_BETWEEN_ERROR_LOG);

  private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

  public DDApi(final String host, final int port) {
    this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT;
    this.servicesEndpoint = "http://" + host + ":" + port + SERVICES_ENDPOINT;
  }

  /**
   * Send traces to the DD agent
   *
   * @param traces the traces to be sent
   * @return the staus code returned
   */
  public boolean sendTraces(final List<List<DDBaseSpan<?>>> traces) {
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

  private HttpURLConnection getHttpURLConnection(final String endpoint) throws IOException {
    final HttpURLConnection httpCon;
    final URL url = new URL(endpoint);
    httpCon = (HttpURLConnection) url.openConnection();
    httpCon.setDoOutput(true);
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
}
