package com.datadoghq.trace.writer.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.Writer;

import io.opentracing.Span;

/**
 * This writer write provided traces to the a DD agent which is most of time located on the same host.
 * 
 * It handles writes asynchronuously so the calling threads are automatically released. However, if too much spans are collected
 * the writers can reach a state where it is forced to drop incoming spans.
 */
public class DDAgentWriter implements Writer {

	protected static final Logger logger = LoggerFactory.getLogger(DDAgentWriter.class.getName());
	
	/**
	 * Default location of the DD agent
	 */
	protected static final String DEFAULT_HOSTNAME = "localhost";
	protected static final int DEFAULT_PORT = 8126;
	
	/**
	 * Maximum number of spans kept in memory
	 */
	protected static final int DEFAULT_MAX_SPANS = 1000;
	
	/**
	 * Maximum number of traces sent to the DD agent API at once
	 */
	protected static final int DEFAULT_BATCH_SIZE = 10;

	/**
	 * Used to ensure that we don't keep too many spans (while the blocking queue collect traces...)
	 */
	private final Semaphore tokens;
	
	/**
	 * In memory collection of traces waiting for departure
	 */
	protected final BlockingQueue<List<Span>> traces;
	
	/**
	 * Async worker that posts the spans to the DD agent
	 */
	protected final Thread asyncWriterThread;

	/**
	 * The DD agent api
	 */
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

	/* (non-Javadoc)
	 * @see com.datadoghq.trace.Writer#write(java.util.List)
	 */
	public void write(List<Span> trace) {
		//Try to add a new span in the queue
		boolean proceed = tokens.tryAcquire(trace.size());

		if(proceed){
			traces.add(trace);
		}else{
			logger.warn("Cannot add a trace of "+trace.size()+" as the async queue is full. Queue max size:"+DEFAULT_MAX_SPANS);
		}
	}

	/* (non-Javadoc)
	 * @see com.datadoghq.trace.Writer#close()
	 */
	public void close() {
		asyncWriterThread.interrupt();
		try {
			asyncWriterThread.join();
		} catch (InterruptedException e) {
			logger.info("Writer properly closed and async writer interrupted.");
		}
	}

	/**
	 * Infinite tasks blocking until some spans come in the blocking queue.
	 */
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
					logger.debug("Async writer about to write "+payload.size()+" traces.");
					api.sendTraces(payload);

					//Compute the number of spans sent
					int spansCount = 0;
					for(List<Span> trace:payload){
						spansCount+=trace.size();
					}
					logger.debug("Async writer just sent "+spansCount+" spans through "+payload.size()+" traces");

					//Force garbage collect of the payload
					payload.clear();

					//Release the tokens
					tokens.release(spansCount);
				} catch (InterruptedException e) {
					logger.info("Async writer interrupted.");

					//The thread was interrupted, we break the LOOP
					break;
				}
			}
		}
	}
}
