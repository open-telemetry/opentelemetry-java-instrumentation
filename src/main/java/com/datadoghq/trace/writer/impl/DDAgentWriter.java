package com.datadoghq.trace.writer.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import com.datadoghq.trace.Writer;

import io.opentracing.Span;

public class DDAgentWriter implements Writer {

	protected static final String DEFAULT_HOSTNAME = "localhost";
	protected static final int DEFAULT_PORT = 8126;

	protected static final int DEFAULT_MAX_SPANS = 1000;
	protected static final int DEFAULT_BATCH_SIZE = 10;
	protected static final int DEFAULT_MAX_SERVICES = 1000;
	protected static final long DEFAULT_TIMEOUT = 5000;

	private final Semaphore tokens;
	protected final BlockingQueue<List<Span>> traces;
	protected final Thread asyncWriterThread;

	protected final DDApi api;

	public DDAgentWriter() {
		super();
		tokens = new Semaphore(DEFAULT_MAX_SPANS);
		traces = new ArrayBlockingQueue<List<Span>>(DEFAULT_MAX_SPANS);

		api = new DDApi(DEFAULT_HOSTNAME, DEFAULT_PORT);

		asyncWriterThread = new Thread(new SpansSendingTask(), "dd.DDAgentWriter-SpansSendingTask");
		asyncWriterThread.setDaemon(true);
		asyncWriterThread.start();
	}

	public void write(List<Span> trace) {
		//Try to add a new span in the queue
		boolean proceed = tokens.tryAcquire(trace.size());

		if(proceed){
			traces.add(trace);
		}else{
			//It was not possible to add the span the queue is full!
			//FIXME proper logging
			System.out.println("Cannot add the following trace as the async queue is full: "+trace);
		}
	}

	public void close() {
		asyncWriterThread.interrupt();
	}

	protected class SpansSendingTask implements Runnable {
		
		protected final List<List<Span>> payload = new ArrayList<List<Span>>();
		
		public void run() {
			while (true) {
				try {
					//WAIT until a new span comes
					payload.add(traces.take());

					//Drain all spans up to a certain batch suze
					traces.drainTo(payload, DEFAULT_BATCH_SIZE);

					//SEND the payload to the agent
					api.sendTraces(payload);

					//Compute the number of spans sent
					int spansCount = 0;
					for(List<Span> trace:payload){
						spansCount+=trace.size();
					}
					
					//Force garbage collect of the payload
					payload.clear();

					//Release the tokens
					tokens.release(spansCount);
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
