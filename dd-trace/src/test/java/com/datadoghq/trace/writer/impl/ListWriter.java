package com.datadoghq.trace.writer.impl;

import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.writer.Writer;

/**
 *	
 */
public class ListWriter implements Writer {

	protected List<List<DDBaseSpan<?>>> list = new ArrayList<List<DDBaseSpan<?>>>();
	
	public List<List<DDBaseSpan<?>>> getList() {
		return list;
	}

	@Override
	public void write(List<DDBaseSpan<?>> trace) {
		list.add(trace);
	}

	@Override
	public void start() {
		list.clear();
	}

	@Override
	public void close() {
		list.clear();
	}

}
