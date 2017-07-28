package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import io.opentracing.tag.Tags;
import java.util.HashMap;
import java.util.Map;

/**
 * This span decorator is a simple mapping to override the operation DB tags. The operation name of
 * DB integration are handled by the DBTypeDecorator
 */
public class OperationDecorator extends AbstractDecorator {

  static final Map<String, String> MAPPINGS =
      new HashMap<String, String>() {
        {
          // Component name <> Operation name
          put("apache-httpclient", "apache.http");
          put("java-aws-sdk", "aws.http");
          // Jetty + Tomcat (same integration used)
          put("java-web-servlet", "servlet.request");
          // FIXME: JMS ops card is low (jms-send or jms-receive), may be this mapping is useless
          put("java-jms", "jms");
          put("okhttp", "okhttp.http");
          // Cassandra, Mongo, JDBC are set via DBTypeDecorator
        }
      };

  public OperationDecorator() {
    super();
    this.setMatchingTag(Tags.COMPONENT.getKey());
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {

    if (MAPPINGS.containsKey(String.valueOf(value))) {
      context.setOperationName(MAPPINGS.get(String.valueOf(value)));
      return true;
    }
    return false;
  }
}
