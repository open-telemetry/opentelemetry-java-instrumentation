package datadog.opentracing.decorators;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators(
      final Map<String, String> mappings) {
    final HTTPComponent httpDecorator1 = new HTTPComponent();
    httpDecorator1.setMatchingTag("component");
    httpDecorator1.setMatchingValue("okhttp");

    final HTTPComponent httpDecorator2 = new HTTPComponent();
    httpDecorator2.setMatchingTag("component");
    httpDecorator2.setMatchingValue("java-aws-sdk");

    return Arrays.asList(
        new DBStatementAsResourceName(),
        new DBTypeDecorator(),
        new ErrorFlag(),
        httpDecorator1,
        httpDecorator2,
        new OperationDecorator(),
        new ResourceNameDecorator(),
        new ServiceNameDecorator(),
        new ServletContextDecorator(),
        new SpanTypeDecorator(),
        new Status5XXDecorator(),
        new Status404Decorator(),
        new URLAsResourceName());
  }
}
