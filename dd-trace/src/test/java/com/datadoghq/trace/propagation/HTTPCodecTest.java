package com.datadoghq.trace.propagation;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.datadoghq.trace.DDSpanContext;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Created by gpolaert on 6/23/17. */
public class HTTPCodecTest {

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-datadog-trace-id";
  private static final String SPAN_ID_KEY = "x-datadog-parent-id";

  @Test
  public void shoudAddHttpHeaders() {

    final DDSpanContext mockedContext =
        new DDSpanContext(
            1L,
            2L,
            0L,
            "fakeService",
            "fakeOperation",
            "fakeResource",
            new HashMap<String, String>() {
              {
                put("k1", "v1");
                put("k2", "v2");
              }
            },
            false,
            "fakeType",
            null,
            null,
            null);

    final Map<String, String> carrier = new HashMap<>();

    final HTTPCodec codec = new HTTPCodec();
    codec.inject(mockedContext, new TextMapInjectAdapter(carrier));

    assertThat(carrier.get(TRACE_ID_KEY)).isEqualTo("1");
    assertThat(carrier.get(SPAN_ID_KEY)).isEqualTo("2");
    assertThat(carrier.get(OT_BAGGAGE_PREFIX + "k1")).isEqualTo("v1");
    assertThat(carrier.get(OT_BAGGAGE_PREFIX + "k2")).isEqualTo("v2");
  }

  @Test
  public void shoudReadHttpHeaders() {

    final Map<String, String> actual =
        new HashMap<String, String>() {
          {
            put(TRACE_ID_KEY, "1");
            put(SPAN_ID_KEY, "2");
            put(OT_BAGGAGE_PREFIX + "k1", "v1");
            put(OT_BAGGAGE_PREFIX + "k2", "v2");
          }
        };

    final HTTPCodec codec = new HTTPCodec();
    final DDSpanContext context = codec.extract(new TextMapExtractAdapter(actual));

    assertThat(context.getTraceId()).isEqualTo(1l);
    assertThat(context.getSpanId()).isEqualTo(2l);
    assertThat(context.getBaggageItem("k1")).isEqualTo("v1");
    assertThat(context.getBaggageItem("k2")).isEqualTo("v2");
  }
}
