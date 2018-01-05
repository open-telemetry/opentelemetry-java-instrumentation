package datadog.opentracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import datadog.trace.api.DDTags;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DDSpanSerializationTest {

  ObjectMapper serializer;
  DDSpan span;
  Map<String, Object> expected = Maps.newHashMap();

  @Before
  public void setUp() throws Exception {

    final Map<String, String> baggage = new HashMap<>();
    baggage.put("a-baggage", "value");
    final Map<String, Object> tags = new HashMap<>();
    baggage.put("k1", "v1");

    expected.put("meta", baggage);
    expected.put("service", "service");
    expected.put("error", 0);
    expected.put("type", "type");
    expected.put("name", "operation");
    expected.put("duration", 33000);
    expected.put("resource", "operation");
    expected.put("start", 100000);
    expected.put("span_id", 2l);
    expected.put("parent_id", 0l);
    expected.put("trace_id", 1l);

    final DDSpanContext context =
        new DDSpanContext(
            1L,
            2L,
            0L,
            "service",
            "operation",
            null,
            new HashMap<>(baggage),
            false,
            "type",
            tags,
            null,
            null);

    baggage.put(DDTags.THREAD_NAME, Thread.currentThread().getName());
    baggage.put(DDTags.THREAD_ID, String.valueOf(Thread.currentThread().getId()));

    span = new DDSpan(100L, context);
    span.finish(133L);
    serializer = new ObjectMapper();
  }

  @Test
  public void test() throws Exception {
    assertThat(serializer.readTree(serializer.writeValueAsString(span)))
        .isEqualTo(serializer.readTree(serializer.writeValueAsString(expected)));
  }
}
