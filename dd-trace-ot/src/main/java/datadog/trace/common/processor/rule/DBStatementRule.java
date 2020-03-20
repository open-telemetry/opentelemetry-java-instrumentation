package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/**
 * Converts db.statement tag to resource name. This is later set to sql.query by the datadog agent
 * after obfuscation.
 */
public class DBStatementRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"DBStatementAsResourceName"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    if (tags.containsKey(Tags.DB_STATEMENT.getKey())) {
      // Special case: Mongo
      // Skip the decorators
      if (tags.containsKey(Tags.COMPONENT.getKey())
          && "java-mongo".equals(tags.get(Tags.COMPONENT.getKey()))) {
        return;
      }

      final String statement = tags.get(Tags.DB_STATEMENT.getKey()).toString();
      if (!statement.isEmpty()) {
        span.setResourceName(statement);
      }
      span.setTag(Tags.DB_STATEMENT.getKey(), (String) null); // Remove the tag
    }
  }
}
