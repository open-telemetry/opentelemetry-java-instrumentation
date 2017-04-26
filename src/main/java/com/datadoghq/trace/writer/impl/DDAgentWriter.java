package com.datadoghq.trace.writer.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.datadoghq.trace.Writer;

import io.opentracing.Span;

public class DDAgentWriter implements Writer {

	protected static final String DEFAULT_HOSTNAME = "localhost";
	protected static final int DEFAULT_PORT = 8126;
	
	protected static final int DEFAULT_MAX_TRACES = 1000;
	protected static final int DEFAULT_BATCH_SIZE = 10;
	protected static final int DEFAULT_MAX_SERVICES = 1000;
	protected static final long DEFAULT_TIMEOUT = 5000;

	protected final BlockingQueue<Span> commandQueue;
	protected final Thread asyncWriterThread;

	public DDAgentWriter() {
		super();
		commandQueue = new ArrayBlockingQueue<Span>(DEFAULT_MAX_TRACES);
		
		asyncWriterThread = new Thread(new SpansSendingTask(), "dd.DDAgentWriter-SpansSendingTask");
		asyncWriterThread.setDaemon(true);
		asyncWriterThread.start();
	}

	public void write(Span span) {
		try{
			//Try to add a new span in the queue
			commandQueue.add(span);
		}catch(IllegalStateException e){
			//It was not possible to add the span the queue is full!
			//FIXME proper logging
			System.out.println("Cannot add the following span as the async queue is full: "+span);
		}
	}

	public void close() {
		asyncWriterThread.interrupt();
	}

	protected class SpansSendingTask implements Runnable {
		public void run() {
			while (true) {
				try {
					//Wait until a new span comes
					Span span = commandQueue.take();
					
					//Drain all spans up to a certain batch suze
					List<Span> spans = new ArrayList<Span>();
					spans.add(span);
					commandQueue.drainTo(spans, DEFAULT_BATCH_SIZE);
					
					//Then write to the agent
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					// FIXME proper logging
					e.printStackTrace();
					
					//The thread was interrupted, we break the LOOP
					break;
				}
			}
		}
	}
}
