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
        new AnalyticsSampleRateDecorator(),
        new OperationDecorator(),
        new PeerServiceDecorator(),
        new ResourceNameDecorator(),
        new ServiceDecorator(),
        new ServiceNameDecorator(),
        new ServletContextDecorator(),
        new SpanTypeDecorator(),
        new Status5XXDecorator(),
        new Status404Decorator(),
        new URLAsResourceName(),
        new ForceManualKeepDecorator(),
        new ForceManualDropDecorator()
    );
  }
}
