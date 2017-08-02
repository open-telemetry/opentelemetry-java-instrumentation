package com.datadoghq.trace;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DDSpanBuilderTest {

  private DDTracer tracer;

  @Before
  public void setUp() throws Exception {
    tracer = new DDTracer();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void shouldBuildSimpleSpan() {

    final String expectedName = "fakeName";
    final DDSpan span = tracer.buildSpan(expectedName).withServiceName("foo").startManual();
    assertThat(span.getOperationName()).isEqualTo(expectedName);
  }

  @Test
  public void shouldBuildMoreComplexSpan() {

    final String expectedName = "fakeName";
    final Map tags =
        new HashMap<String, Object>() {
          {
            put("1", true);
            put("2", "fakeString");
            put("3", 42.0);
          }
        };

    DDSpan span =
        tracer
            .buildSpan(expectedName)
            .withServiceName("foo")
            .withTag("1", (Boolean) tags.get("1"))
            .withTag("2", (String) tags.get("2"))
            .withTag("3", (Number) tags.get("3"))
            .startManual();

    assertThat(span.getOperationName()).isEqualTo(expectedName);
    assertThat(span.getTags()).containsAllEntriesOf(tags);

    // with no tag provided

    span = tracer.buildSpan(expectedName).withServiceName("foo").startManual();

    assertThat(span.getTags()).isNotNull();
    assertThat(span.getTags().size()).isEqualTo(2);

    // with all custom fields provided
    final String expectedResource = "fakeResource";
    final String expectedService = "fakeService";
    final String expectedType = "fakeType";

    span =
        tracer
            .buildSpan(expectedName)
            .withServiceName("foo")
            .withResourceName(expectedResource)
            .withServiceName(expectedService)
            .withErrorFlag()
            .withSpanType(expectedType)
            .startManual();

    final DDSpanContext actualContext = span.context();

    assertThat(actualContext.getResourceName()).isEqualTo(expectedResource);
    assertThat(actualContext.getErrorFlag()).isTrue();
    assertThat(actualContext.getServiceName()).isEqualTo(expectedService);
    assertThat(actualContext.getSpanType()).isEqualTo(expectedType);
    assertThat(actualContext.getTags().get(DDTags.THREAD_NAME))
        .isEqualTo(Thread.currentThread().getName());
    assertThat(actualContext.getTags().get(DDTags.THREAD_ID))
        .isEqualTo(Thread.currentThread().getId());
  }

  @Test
  public void shouldBuildSpanTimestampInNano() {

    // time in micro
    final long expectedTimestamp = 487517802L * 1000 * 1000L;
    final String expectedName = "fakeName";

    DDSpan span =
        tracer
            .buildSpan(expectedName)
            .withServiceName("foo")
            .withStartTimestamp(expectedTimestamp)
            .start();

    // get return nano time
    assertThat(span.getStartTime()).isEqualTo(expectedTimestamp * 1000L);

    // auto-timestamp in nanoseconds
    final long tick = System.currentTimeMillis();
    span = tracer.buildSpan(expectedName).withServiceName("foo").startManual();

    // Give a range of +/- 2 millis
    assertThat(span.getStartTime())
        .isBetween(MILLISECONDS.toNanos(tick - 2), MILLISECONDS.toNanos(tick + 2));
  }

  @Test
  public void shouldLinkToParentSpan() {

    final long spanId = 1L;
    final long expectedParentId = spanId;

    final DDSpanContext mockedContext = mock(DDSpanContext.class);

    when(mockedContext.getSpanId()).thenReturn(spanId);
    when(mockedContext.getServiceName()).thenReturn("foo");

    final String expectedName = "fakeName";

    final DDSpan span =
        tracer
            .buildSpan(expectedName)
            .withServiceName("foo")
            .asChildOf(mockedContext)
            .startManual();

    final DDSpanContext actualContext = span.context();

    assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);
  }

  @Test
  public void shouldInheritOfTheDDParentAttributes() {

    final String expectedName = "fakeName";
    final String expectedParentServiceName = "fakeServiceName";
    final String expectedParentResourceName = "fakeResourceName";
    final String expectedParentType = "fakeType";
    final String expectedChildServiceName = "fakeServiceName-child";
    final String expectedChildResourceName = "fakeResourceName-child";
    final String expectedChildType = "fakeType-child";
    final String expectedBaggageItemKey = "fakeKey";
    final String expectedBaggageItemValue = "fakeValue";

    final DDSpan parent =
        tracer
            .buildSpan(expectedName)
            .withServiceName("foo")
            .withResourceName(expectedParentResourceName)
            .withSpanType(expectedParentType)
            .startManual();

    parent.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue);

    // ServiceName and SpanType are always set by the parent  if they are not present in the child
    DDSpan span =
        tracer
            .buildSpan(expectedName)
            .withServiceName(expectedParentServiceName)
            .asChildOf(parent)
            .startManual();

    assertThat(span.getOperationName()).isEqualTo(expectedName);
    assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);
    assertThat(span.context().getServiceName()).isEqualTo(expectedParentServiceName);
    assertThat(span.context().getResourceName()).isNotEqualTo(expectedParentResourceName);
    assertThat(span.context().getSpanType()).isEqualTo(expectedParentType);

    // ServiceName and SpanType are always overwritten by the child  if they are present
    span =
        tracer
            .buildSpan(expectedName)
            .withServiceName(expectedChildServiceName)
            .withResourceName(expectedChildResourceName)
            .withSpanType(expectedChildType)
            .asChildOf(parent)
            .startManual();

    assertThat(span.getOperationName()).isEqualTo(expectedName);
    assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);
    assertThat(span.context().getServiceName()).isEqualTo(expectedChildServiceName);
    assertThat(span.context().getResourceName()).isEqualTo(expectedChildResourceName);
    assertThat(span.context().getSpanType()).isEqualTo(expectedChildType);
  }

  @Test
  public void shouldTrackAllSpanInTrace() throws InterruptedException {

    final ArrayList<DDSpan> spans = new ArrayList<>();
    final int nbSamples = 10;

    // root (aka spans[0]) is the parent
    // others are just for fun

    final DDSpan root = tracer.buildSpan("fake_O").withServiceName("foo").startManual();
    spans.add(root);

    Thread.sleep(200);
    final long tickEnd = System.currentTimeMillis();

    for (int i = 1; i <= 10; i++) {
      spans.add(
          tracer
              .buildSpan("fake_" + i)
              .withServiceName("foo")
              .asChildOf(spans.get(i - 1))
              .startManual());
    }
    spans.get(1).finish(tickEnd);

    assertThat(root.context().getTrace()).hasSize(nbSamples + 1);
    assertThat(root.context().getTrace()).containsAll(spans);
    assertThat(spans.get((int) (Math.random() * nbSamples)).context().getTrace())
        .containsAll(spans);
  }
}
