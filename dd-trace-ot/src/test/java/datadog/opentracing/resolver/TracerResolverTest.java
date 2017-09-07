package datadog.opentracing.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import datadog.opentracing.DDTracer;
import datadog.opentracing.decorators.AbstractDecorator;
import datadog.opentracing.decorators.HTTPComponent;
import datadog.opentracing.decorators.URLAsResourceName;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;

public class TracerResolverTest {

  @Test
  public void testResolve() {
    final DDTracerResolver tracerResolver = new DDTracerResolver();
    final DDTracer tracer = (DDTracer) tracerResolver.resolve();

    // for HTTP decorators
    List<AbstractDecorator> decorators = tracer.getSpanContextDecorators("component");

    assertThat(decorators.size()).isEqualTo(2);
    AbstractDecorator decorator = decorators.get(0);
    assertThat(decorator.getClass()).isEqualTo(HTTPComponent.class);
    final HTTPComponent httpServiceDecorator = (HTTPComponent) decorator;
    assertThat(httpServiceDecorator.getMatchingTag()).isEqualTo("component");
    assertThat(httpServiceDecorator.getMatchingValue()).isEqualTo("hello");
    assertThat(httpServiceDecorator.getSetValue()).isEqualTo("world");

    // for URL decorators
    decorators = tracer.getSpanContextDecorators("http.url");
    assertThat(decorators.size()).isEqualTo(1);

    decorator = decorators.get(0);
    assertThat(decorator.getClass()).isEqualTo(URLAsResourceName.class);
  }

  @Test
  public void testResolveTracer() throws Exception {
    final Field tracerField = GlobalTracer.class.getDeclaredField("tracer");
    tracerField.setAccessible(true);
    tracerField.set(null, NoopTracerFactory.create());

    assertThat(GlobalTracer.isRegistered()).isFalse();

    final Tracer tracer = TracerResolver.resolveTracer();

    assertThat(GlobalTracer.isRegistered()).isFalse();
    assertThat(tracer).isInstanceOf(DDTracer.class);
  }

  @Test
  public void testRegisterTracer() throws Exception {
    final Field tracerField = GlobalTracer.class.getDeclaredField("tracer");
    tracerField.setAccessible(true);
    tracerField.set(null, NoopTracerFactory.create());

    assertThat(GlobalTracer.isRegistered()).isFalse();

    DDTracerResolver.registerTracer();

    assertThat(GlobalTracer.isRegistered()).isTrue();
    assertThat(tracerField.get(null)).isInstanceOf(DDTracer.class);

    tracerField.set(null, NoopTracerFactory.create());
  }
}
