package com.datadoghq.asyncpatterns.examples;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager;
import io.opentracing.contrib.spanmanager.SpanManager.ManagedSpan;
import io.opentracing.contrib.tracerresolver.TracerResolver;

class ExampleRunnable implements Runnable {
	private final SpanManager spanManager;
	private final Span currentSpanFromCaller;
	public static final Tracer tracer = TracerResolver.resolveTracer();

	ExampleRunnable(SpanManager spanManager) {
		this(spanManager, NoopSpan.INSTANCE);
	}

	private ExampleRunnable(SpanManager spanManager, Span currentSpanFromCaller) {
		this.spanManager = spanManager;
		this.currentSpanFromCaller = currentSpanFromCaller;
	}

	ExampleRunnable withCurrentSpan() {
		return new ExampleRunnable(spanManager, spanManager.current().getSpan());
	}

	@Override
	public void run() {
		try (ManagedSpan parent = spanManager.activate(currentSpanFromCaller)) {
			
			myMethod();
			
			// Any background code that requires tracing
			// and may use spanManager.current().getSpan()

		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void myMethod(){
		
		try (Span appSpan = tracer.buildSpan("myMethod").asChildOf(spanManager.current().getSpan()).start();           // start appSpan
                ManagedSpan managed = spanManager.activate(appSpan)) {
			
			Thread.sleep(100);
			
		} catch(Exception e){
			e.printStackTrace();
		}
	
	}
	
	
	public static void main(String[] args) throws Throwable{
		
		SpanManager spanManager = DefaultSpanManager.getInstance();
        ExampleRunnable runnable = new ExampleRunnable(spanManager);

        try (Span appSpan = tracer.buildSpan("main").start();           // start appSpan
                ManagedSpan managed = spanManager.activate(appSpan)) {  // update current Span
        
        	Thread.sleep(100);
        	
            Thread example = new Thread(runnable.withCurrentSpan());
            example.start();
            example.join();
            
        } // managed.deactivate() + appSpan.finish()
        
       
	}
}