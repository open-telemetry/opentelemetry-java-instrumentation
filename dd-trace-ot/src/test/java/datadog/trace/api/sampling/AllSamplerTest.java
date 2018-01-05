package datadog.trace.api.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import datadog.opentracing.DDSpan;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;
import org.mockito.Mock;

public class AllSamplerTest {

  @Mock DDSpan mockSpan;

  private final AllSampler sampler = new AllSampler();

  @Test
  public void testAllSampler() {

    for (int i = 0; i < 500; i++) {
      assertThat(sampler.doSample(mockSpan)).isTrue();
    }
  }

  @Test
  public void testSkipTagPatternSampler() {

    final Map<String, Object> tags = new HashMap<>();
    mockSpan = mock(DDSpan.class);
    when(mockSpan.getTags()).thenReturn(tags).thenReturn(tags);

    sampler.addSkipTagPattern("http.url", Pattern.compile(".*/hello"));

    tags.put("http.url", "http://a/hello");
    assertThat(sampler.sample(mockSpan)).isFalse();

    tags.put("http.url", "http://a/hello2");
    assertThat(sampler.sample(mockSpan)).isTrue();
  }
}
