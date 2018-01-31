package datadog.opentracing.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import datadog.opentracing.DDTracer;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import org.junit.Test;

public class TracerResolverTest {

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
