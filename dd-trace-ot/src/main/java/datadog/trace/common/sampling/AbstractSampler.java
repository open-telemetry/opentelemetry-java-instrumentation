package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public abstract class AbstractSampler implements Sampler {

  /** Sample tags */
  protected Map<String, Pattern> skipTagsPatterns = new HashMap<>();

  @Override
  public boolean sample(final DDSpan span) {

    // Filter by tag values
    for (final Entry<String, Pattern> entry : skipTagsPatterns.entrySet()) {
      final Object value = span.getTags().get(entry.getKey());
      if (value != null) {
        final String strValue = String.valueOf(value);
        final Pattern skipPattern = entry.getValue();
        if (skipPattern.matcher(strValue).matches()) {
          return false;
        }
      }
    }

    return doSample(span);
  }

  /**
   * Pattern based skipping of tag values
   *
   * @param tag
   * @param skipPattern
   */
  public void addSkipTagPattern(final String tag, final Pattern skipPattern) {
    skipTagsPatterns.put(tag, skipPattern);
  }

  protected abstract boolean doSample(DDSpan span);
}
