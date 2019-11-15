package datadog.opentracing.decorators;

import datadog.trace.api.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {

    final List<AbstractDecorator> decorators =
        new ArrayList<>(
            Arrays.asList(
                new AnalyticsSampleRateDecorator(),
                new DBStatementAsResourceName(),
                new DBTypeDecorator(),
                new ErrorFlag(),
                new ForceManualDropDecorator(),
                new ForceManualKeepDecorator(),
                new OperationDecorator(),
                new PeerServiceDecorator(),
                new ResourceNameDecorator(),
                new ServiceNameDecorator(),
                new ServiceNameDecorator("service", false),
                new ServletContextDecorator(),
                new SpanTypeDecorator(),
                new Status404Decorator(),
                new Status5XXDecorator(),
                new URLAsResourceName()));

    for (final String splitByTag : Config.get().getSplitByTags()) {
      decorators.add(new ServiceNameDecorator(splitByTag, true));
    }

    return decorators;
  }
}
