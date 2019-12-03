package datadog.opentracing.decorators;

import java.util.Arrays;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {

    return Arrays.asList(
        new DBStatementAsResourceName(),
        new DBTypeDecorator(),
        new ErrorFlag(),
        new OperationDecorator(),
        new ResourceNameDecorator(),
        new SpanTypeDecorator(),
        new Status404Decorator(),
        new Status5XXDecorator(),
        new URLAsResourceName());
  }
}
