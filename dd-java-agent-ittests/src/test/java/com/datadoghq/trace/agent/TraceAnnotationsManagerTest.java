package com.datadoghq.trace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.ErrorFlag;
import com.datadoghq.trace.writer.ListWriter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;

public class TraceAnnotationsManagerTest {

  private final ListWriter writer = new ListWriter();
  private final DDTracer tracer = new DDTracer(writer);

  @Before
  public void beforeTest() throws Exception {
    try {
      GlobalTracer.register(tracer);
    } catch (final Exception e) {
      // Force it anyway using reflexion
      final Field field = GlobalTracer.class.getDeclaredField("tracer");
      field.setAccessible(true);
      field.set(null, tracer);
    }
    writer.start();
  }

  @Test
  public void testSimpleCaseAnnotations() {
    //Test single span in new trace
    SayTracedHello.sayHello();

    assertThat(writer.firstTrace().size()).isEqualTo(1);
    assertThat(writer.firstTrace().get(0).getOperationName()).isEqualTo("SAY_HELLO");
    assertThat(writer.firstTrace().get(0).getServiceName()).isEqualTo("test");
  }

  @Test
  public void testComplexCaseAnnotations() {

    //Test new trace with 2 children spans
    SayTracedHello.sayHELLOsayHA();
    assertThat(writer.firstTrace().size()).isEqualTo(3);
    final long parentId = writer.firstTrace().get(0).context().getTraceId();

    assertThat(writer.firstTrace().get(0).getOperationName()).isEqualTo("NEW_TRACE");
    assertThat(writer.firstTrace().get(0).getParentId()).isEqualTo(0); //ROOT / no parent
    assertThat(writer.firstTrace().get(0).context().getParentId()).isEqualTo(0);
    assertThat(writer.firstTrace().get(0).getServiceName()).isEqualTo("test2");

    assertThat(writer.firstTrace().get(1).getOperationName()).isEqualTo("SAY_HELLO");
    assertThat(writer.firstTrace().get(1).getServiceName()).isEqualTo("test");
    assertThat(writer.firstTrace().get(1).getParentId()).isEqualTo(parentId);

    assertThat(writer.firstTrace().get(2).getOperationName()).isEqualTo("SAY_HA");
    assertThat(writer.firstTrace().get(2).getParentId()).isEqualTo(parentId);
    assertThat(writer.firstTrace().get(2).context().getSpanType()).isEqualTo("DB");
  }

  @Test
  public void testExceptionExit() {

    tracer.addDecorator(new ErrorFlag());

    try {
      SayTracedHello.sayERROR();
    } catch (final Throwable ex) {
      // DO NOTHING
    }
    assertThat(writer.firstTrace().get(0).getOperationName()).isEqualTo("ERROR");
    assertThat(writer.firstTrace().get(0).getTags().get(Tags.ERROR.getKey())).isEqualTo("true");
    assertThat(writer.firstTrace().get(0).getError()).isEqualTo(1);
  }
}
