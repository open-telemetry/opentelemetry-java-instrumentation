package datadog.trace.common.writer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.writer.unixdomainsockets.UnixDomainSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/** The API pointing to a DD agent */
@Slf4j
public class DDApi {
  private static final String DATADOG_META_LANG = "Datadog-Meta-Lang";
  private static final String DATADOG_META_LANG_VERSION = "Datadog-Meta-Lang-Version";
  private static final String DATADOG_META_LANG_INTERPRETER = "Datadog-Meta-Lang-Interpreter";
  private static final String DATADOG_META_TRACER_VERSION = "Datadog-Meta-Tracer-Version";
  private static final String X_DATADOG_TRACE_COUNT = "X-Datadog-Trace-Count";

  private static final int HTTP_TIMEOUT = 1; // 1 second for conenct/read/write operations
  private static final String TRACES_ENDPOINT_V3 = "v0.3/traces";
  private static final String TRACES_ENDPOINT_V4 = "v0.4/traces";
  private static final long MILLISECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toMillis(5);

  private final List<ResponseListener> responseListeners = new ArrayList<>();

  private volatile long nextAllowedLogTime = 0;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());
  private static final MediaType MSGPACK = MediaType.get("application/msgpack");

  private final OkHttpClient httpClient;
  private final HttpUrl tracesUrl;

  public DDApi(final String host, final int port, final String unixDomainSocketPath) {
    this(
        host,
        port,
        traceEndpointAvailable(getUrl(host, port, TRACES_ENDPOINT_V4), unixDomainSocketPath),
        unixDomainSocketPath);
  }

  DDApi(
      final String host,
      final int port,
      final boolean v4EndpointsAvailable,
      final String unixDomainSocketPath) {
    httpClient = buildHttpClient(unixDomainSocketPath);

    if (v4EndpointsAvailable) {
      tracesUrl = getUrl(host, port, TRACES_ENDPOINT_V4);
    } else {
      log.debug("API v0.4 endpoints not available. Downgrading to v0.3");
      tracesUrl = getUrl(host, port, TRACES_ENDPOINT_V3);
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
    final List<byte[]> serializedTraces = new ArrayList<>(traces.size());
    int sizeInBytes = 0;
    for (final List<DDSpan> trace : traces) {
      try {
        final byte[] serializedTrace = serializeTrace(trace);
        sizeInBytes += serializedTrace.length;
        serializedTraces.add(serializedTrace);
      } catch (final JsonProcessingException e) {
        log.warn("Error serializing trace", e);
      }
    }

    return sendSerializedTraces(serializedTraces.size(), sizeInBytes, serializedTraces);
  }

  byte[] serializeTrace(final List<DDSpan> trace) throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsBytes(trace);
  }

  boolean sendSerializedTraces(
      final int representativeCount, final Integer sizeInBytes, final List<byte[]> traces) {
    try {
      final RequestBody body =
          new RequestBody() {
            @Override
            public MediaType contentType() {
              return MSGPACK;
            }

            @Override
            public long contentLength() {
              final int traceCount = traces.size();
              // Need to allocate additional to handle MessagePacker.packArrayHeader
              if (traceCount < (1 << 4)) {
                return sizeInBytes + 1; // byte
              } else if (traceCount < (1 << 16)) {
                return sizeInBytes + 3; // byte + short
              } else {
                return sizeInBytes + 5; // byte + int
              }
            }

            @Override
            public void writeTo(final BufferedSink sink) throws IOException {
              final OutputStream out = sink.outputStream();
              final MessagePacker packer = MessagePack.newDefaultPacker(out);
              packer.packArrayHeader(traces.size());
              for (final byte[] trace : traces) {
                packer.writePayload(trace);
              }
              packer.close();
              out.close();
            }
          };
      final Request request =
          prepareRequest(tracesUrl)
              .addHeader(X_DATADOG_TRACE_COUNT, String.valueOf(representativeCount))
              .put(body)
              .build();

      try (final Response response = httpClient.newCall(request).execute()) {
        if (response.code() != 200) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Error while sending {} of {} traces to the DD agent. Status: {}, Response: {}, Body: {}",
                traces.size(),
                representativeCount,
                response.code(),
                response.message(),
                response.body().string());
          } else if (nextAllowedLogTime < System.currentTimeMillis()) {
            nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
            log.warn(
                "Error while sending {} of {} traces to the DD agent. Status: {} {} (going silent for {} minutes)",
                traces.size(),
                representativeCount,
                response.code(),
                response.message(),
                TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
          }
          return false;
        }

        log.debug(
            "Successfully sent {} of {} traces to the DD agent.",
            traces.size(),
            representativeCount);

        final String responseString = response.body().string().trim();
        try {
          if (!"".equals(responseString) && !"OK".equalsIgnoreCase(responseString)) {
            final JsonNode parsedResponse = OBJECT_MAPPER.readTree(responseString);
            final String endpoint = tracesUrl.toString();
            for (final ResponseListener listener : responseListeners) {
              listener.onResponse(endpoint, parsedResponse);
            }
          }
        } catch (final JsonParseException e) {
          log.debug("Failed to parse DD agent response: " + responseString, e);
        }
        return true;
      }
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Error while sending "
                + traces.size()
                + " of "
                + representativeCount
                + " traces to the DD agent.",
            e);
      } else if (nextAllowedLogTime < System.currentTimeMillis()) {
        nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
        log.warn(
            "Error while sending {} of {} traces to the DD agent. {}: {} (going silent for {} minutes)",
            traces.size(),
            representativeCount,
            e.getClass().getName(),
            e.getMessage(),
            TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
      }
      return false;
    }
  }

  private static boolean traceEndpointAvailable(
      final HttpUrl url, final String unixDomainSocketPath) {
    return endpointAvailable(url, unixDomainSocketPath, Collections.emptyList(), true);
  }

  private static boolean endpointAvailable(
      final HttpUrl url,
      final String unixDomainSocketPath,
      final Object data,
      final boolean retry) {
    try {
      final OkHttpClient client = buildHttpClient(unixDomainSocketPath);
      final RequestBody body = RequestBody.create(MSGPACK, OBJECT_MAPPER.writeValueAsBytes(data));
      final Request request = prepareRequest(url).put(body).build();

      try (final Response response = client.newCall(request).execute()) {
        return response.code() == 200;
      }
    } catch (final IOException e) {
      if (retry) {
        return endpointAvailable(url, unixDomainSocketPath, data, false);
      }
    }
    return false;
  }

  private static OkHttpClient buildHttpClient(final String unixDomainSocketPath) {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (unixDomainSocketPath != null) {
      builder = builder.socketFactory(new UnixDomainSocketFactory(new File(unixDomainSocketPath)));
    }
    return builder
        .connectTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)
        .build();
  }

  private static HttpUrl getUrl(final String host, final int port, final String endPoint) {
    return new HttpUrl.Builder()
        .scheme("http")
        .host(host)
        .port(port)
        .addEncodedPathSegments(endPoint)
        .build();
  }

  private static Request.Builder prepareRequest(final HttpUrl url) {
    return new Request.Builder()
        .url(url)
        .addHeader(DATADOG_META_LANG, "java")
        .addHeader(DATADOG_META_LANG_VERSION, DDTraceOTInfo.JAVA_VERSION)
        .addHeader(DATADOG_META_LANG_INTERPRETER, DDTraceOTInfo.JAVA_VM_NAME)
        .addHeader(DATADOG_META_TRACER_VERSION, DDTraceOTInfo.VERSION);
  }

  @Override
  public String toString() {
    return "DDApi { tracesUrl=" + tracesUrl + " }";
  }

  public interface ResponseListener {
    /** Invoked after the api receives a response from the core agent. */
    void onResponse(String endpoint, JsonNode responseJson);
  }
}
