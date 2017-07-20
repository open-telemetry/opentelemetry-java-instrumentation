package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTracer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** The API pointing to a DD agent */
@Slf4j
public class DDApi {

  private static final String TRACES_ENDPOINT = "/v0.3/traces";

  private final String tracesEndpoint;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonFactory jsonFactory = objectMapper.getFactory();

  public DDApi(final String host, final int port) {
    this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT;
  }

  /**
   * Send traces to the DD agent
   *
   * @param traces the traces to be sent
   * @return the staus code returned
   */
  public boolean sendTraces(final List<List<DDBaseSpan<?>>> traces) {
    final int status = callPUT(traces);
    if (status == 200) {
      log.debug("Succesfully sent {} traces to the DD agent.", traces.size());
      return true;
    } else {
      log.warn("Error while sending {} traces to the DD agent. Status: {}", traces.size(), status);
      return false;
    }
  }

  /**
   * PUT to an endpoint the provided JSON content
   *
   * @param endpoint
   * @param content
   * @return the status code
   */
  private int callPUT(final Object content) {
    HttpURLConnection httpCon = null;
    try {
      httpCon = getHttpURLConnection();
    } catch (final Exception e) {
      log.warn("Error thrown before PUT call to the DD agent.", e);
      return -1;
    }

    try {
      final OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
      final JsonGenerator jsonGen = jsonFactory.createGenerator(out);
      objectMapper.writeValue(jsonGen, content);
      jsonGen.flush();
      jsonGen.close();
      final int responseCode = httpCon.getResponseCode();
      if (responseCode == 200) {
        log.debug("Sent the payload to the DD agent.");
      } else {
        log.warn(
            "Could not send the payload to the DD agent. Status: {} ResponseMessage: {}",
            httpCon.getResponseCode(),
            httpCon.getResponseMessage());
      }
      return responseCode;
    } catch (final Exception e) {
      log.warn("Could not send the payload to the DD agent.", e);
      return -1;
    }
  }

  private HttpURLConnection getHttpURLConnection() throws IOException {
    final HttpURLConnection httpCon;
    final URL url = new URL(tracesEndpoint);
    httpCon = (HttpURLConnection) url.openConnection();
    httpCon.setDoOutput(true);
    httpCon.setRequestMethod("PUT");
    httpCon.setRequestProperty("Content-Type", "application/json");
    httpCon.setRequestProperty("Datadog-Meta-Lang", "java");
    httpCon.setRequestProperty("Datadog-Meta-Lang-Version", DDTracer.JAVA_VERSION);
    httpCon.setRequestProperty("Datadog-Meta-Lang-Interpreter", DDTracer.JAVA_VM_NAME);
    httpCon.setRequestProperty("Datadog-Meta-Tracer-Version", DDTracer.CURRENT_VERSION);
    return httpCon;
  }
}
