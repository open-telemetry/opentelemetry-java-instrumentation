package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This span decorator is a simple mapping to override the operation DB tags. The operation name of
 * DB decorators are handled by the DBTypeDecorator
 */
public class OperationDecorator extends AbstractDecorator {

  static final Map<String, String> MAPPINGS;

  static {
    final Map<String, String> mappings = new HashMap<>();
    // Component name <> Operation name
    mappings.put("java-aws-sdk", "aws.http");
    // FIXME: JMS ops card is low (jms-send or jms-receive), may be this mapping is useless
    mappings.put("java-jms", "jms");
    mappings.put("okhttp", "okhttp.http");
    // Cassandra, Mongo, JDBC are set via DBTypeDecorator
    MAPPINGS = Collections.unmodifiableMap(mappings);
  }

  public OperationDecorator() {
    super();
    setMatchingTag(Tags.COMPONENT.getKey());
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (MAPPINGS.containsKey(String.valueOf(value))) {
      context.setOperationName(MAPPINGS.get(String.valueOf(value)));
    }
    return true;
  }
}
