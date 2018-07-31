package datadog.opentracing.decorators;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators(
      final Map<String, String> mappings) {
    final HTTPComponent httpDecorator = new HTTPComponent();
    httpDecorator.setMatchingTag("component");
    httpDecorator.setMatchingValue("java-aws-sdk");

    return Arrays.asList(
        new DBStatementAsResourceName(),
        new DBTypeDecorator(),
        new ErrorFlag(),
        httpDecorator,
        new OperationDecorator(),
        new PeerServiceDecorator(),
        new ResourceNameDecorator(),
        new ServiceDecorator(),
        new ServiceNameDecorator(),
        new ServletContextDecorator(),
        new SpanTypeDecorator(),
        new Status5XXDecorator(),
        new Status404Decorator(),
        new URLAsResourceName());
  }
}
