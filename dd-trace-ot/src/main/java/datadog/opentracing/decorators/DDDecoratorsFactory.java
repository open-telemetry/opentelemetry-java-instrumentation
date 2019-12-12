package datadog.opentracing.decorators;

import java.util.Arrays;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {

    return Arrays.asList(new DBTypeDecorator(), new SpanTypeDecorator());
  }
}
