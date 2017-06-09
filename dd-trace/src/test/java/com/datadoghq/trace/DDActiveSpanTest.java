package com.datadoghq.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.datadoghq.trace.writer.ListWriter;

import io.opentracing.ActiveSpan.Continuation;

public class DDActiveSpanTest {

	private ListWriter listWriter = new ListWriter();
	private DDTracer ddTracer = new DDTracer(listWriter);

	@Before
	public void setUp(){
		listWriter.start();
	}
	
	@Test
	public void testThreadContextPropagation() {

		DDActiveSpan span1 = ddTracer.buildSpan("op1").startActive();

		assertThat(span1.getOperationName()).isEqualTo("op1");
		assertThat(ddTracer.activeSpan()).isEqualTo(span1);

		DDActiveSpan span2 = ddTracer.buildSpan("op2").startActive();
		assertThat(span2.getOperationName()).isEqualTo("op2");
		assertThat(span2.getParent()).isEqualTo(span1);
		assertThat(span2.context().getParentId()).isEqualTo(span1.getSpanId());
		assertThat(span2.context().getTraceId()).isEqualTo(span1.getTraceId());
		assertThat(ddTracer.activeSpan()).isEqualTo(span2);

		span2.deactivate();
		assertThat(ddTracer.activeSpan()).isEqualTo(span1);

		assertThat(listWriter.getList().size()).isEqualTo(0);
		span1.deactivate();
		assertThat(ddTracer.activeSpan()).isNull();

		assertThat(listWriter.getList().size()).isEqualTo(1);
		assertThat(listWriter.getList().get(0).size()).isEqualTo(2);
		assertThat(listWriter.getList().get(0).get(0)).isEqualTo(span1);
		assertThat(listWriter.getList().get(0).get(1)).isEqualTo(span2);
	}

	@Test
	public void testParentDeactivationFirst() {

		DDActiveSpan span1 = ddTracer.buildSpan("op1").startActive();

		assertThat(span1.getOperationName()).isEqualTo("op1");

		DDActiveSpan span2 = ddTracer.buildSpan("op2").startActive();

		assertThat(ddTracer.activeSpan()).isEqualTo(span2);

		span1.deactivate();

		//If parent span deactivated first => Span 2 remains the active span
		assertThat(ddTracer.activeSpan()).isEqualTo(span2);

		span2.deactivate();

		//If span2 comes to be deactivated 
		assertThat(ddTracer.activeSpan()).isNull();

		assertThat(listWriter.getList().size()).isEqualTo(1);
		assertThat(listWriter.getList().get(0).size()).isEqualTo(2);
		assertThat(listWriter.getList().get(0).get(0)).isEqualTo(span1);
		assertThat(listWriter.getList().get(0).get(1)).isEqualTo(span2);
	}

	@Test
	public void testContinuation() throws Exception{
		final DDActiveSpan span1 = ddTracer.buildSpan("op1").startActive();

		final Continuation continuation = span1.capture();

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				assertThat(ddTracer.activeSpan()).isNull();
				continuation.activate();
				assertThat(ddTracer.activeSpan()).isEqualTo(span1);
				DDActiveSpan span2 = ddTracer.buildSpan("op2").startActive();
				assertThat(ddTracer.activeSpan()).isEqualTo(span2);
				span2.deactivate();
				assertThat(ddTracer.activeSpan()).isEqualTo(span1);
			}
		});

		t.start();
		t.join();
		
		span1.deactivate();
		assertThat(ddTracer.activeSpan()).isNull();

		assertThat(listWriter.getList().size()).isEqualTo(1);
		assertThat(listWriter.getList().get(0).size()).isEqualTo(2);
		assertThat(listWriter.getList().get(0).get(0)).isEqualTo(span1);
	}

	@Test
	public void testMakeSpanActive() {
		final DDSpan manualSpan = ddTracer.buildSpan("op1").startManual();

		DDActiveSpan span1 = ddTracer.makeActive(manualSpan);

		assertThat(span1.getOperationName()).isEqualTo("op1");
		assertThat(ddTracer.activeSpan()).isEqualTo(span1);
		assertThat(manualSpan.context()).isEqualTo(span1.context());
		assertThat(manualSpan.startTimeNano).isEqualTo(span1.startTimeNano);
		assertThat(manualSpan.startTimeMicro).isEqualTo(span1.startTimeMicro);
	}
	
	@Test
	public void testCrossContextPropagation() {
		final DDSpan manualSpan = ddTracer.buildSpan("op1").startManual();
		
		DDActiveSpan activeSpan = ddTracer.buildSpan("op2").asChildOf(manualSpan.context()).startActive();
		
		assertThat(activeSpan.getParentId()).isEqualTo(manualSpan.getSpanId());
		assertThat(activeSpan.getTraceId()).isEqualTo(manualSpan.getTraceId());
	}
}
