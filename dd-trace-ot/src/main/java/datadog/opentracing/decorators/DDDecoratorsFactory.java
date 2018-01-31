package datadog.opentracing.decorators;

import java.util.ArrayList;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {
    List<AbstractDecorator> builtin = new ArrayList<AbstractDecorator>(8);
    {
      final HTTPComponent httpDecorator1 = new HTTPComponent();
      httpDecorator1.setMatchingTag("component");
      httpDecorator1.setMatchingValue("okhttp");
      builtin.add(httpDecorator1);
    }
    {
      final HTTPComponent httpDecorator2 = new HTTPComponent();
      httpDecorator2.setMatchingTag("component");
      httpDecorator2.setMatchingValue("java-aws-sdk");
      builtin.add(httpDecorator2);
    }
    builtin.add(new ErrorFlag());
    builtin.add(new DBTypeDecorator());
    builtin.add(new DBStatementAsResourceName());
    builtin.add(new OperationDecorator());
    builtin.add(new Status404Decorator());
    builtin.add(new URLAsResourceName());

    return builtin;
  }
}
