package com.datadoghq.asyncpatterns.examples;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager;
import io.opentracing.contrib.spanmanager.SpanManager.ManagedSpan;
import io.opentracing.contrib.spanmanager.concurrent.SpanPropagatingExecutorService;
import io.opentracing.contrib.tracerresolver.TracerResolver;

public class ExampleThreadpool {

	public static final Tracer tracer = TracerResolver.resolveTracer();

	public static class TracedCall implements Callable<String> {
		SpanManager spanManager = DefaultSpanManager.getInstance();

		@Override
		public String call() {
			try(Span appSpan = tracer.buildSpan(Thread.currentThread().getName()).asChildOf(spanManager.current().getSpan()).start(); 
					ManagedSpan managed = spanManager.activate(appSpan)){
				Thread.sleep(100);
			}catch(Exception e){
				e.printStackTrace();
			}
			return Thread.currentThread().getName();
		}
	}

	public static void main(String[] args) throws Throwable{
		SpanManager spanManager = DefaultSpanManager.getInstance();
		ExecutorService threadpool = new SpanPropagatingExecutorService(Executors.newFixedThreadPool(2), spanManager);


		try (Span appSpan = tracer.buildSpan("main").start();   
				ManagedSpan current = spanManager.activate(appSpan)) {

			// scheduling the traced call:
			Future<String> result = threadpool.submit(new TracedCall());
			Future<String> result2 = threadpool.submit(new TracedCall());

			result.get();
			result2.get();
		}
	}
}
