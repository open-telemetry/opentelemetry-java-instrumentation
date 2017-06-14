package com.datadoghq.trace.writer;

import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.DDBaseSpan;

/**
 *	List writer used by tests mostly
 */
public class ListWriter implements Writer {

	protected List<List<DDBaseSpan<?>>> list = new ArrayList<List<DDBaseSpan<?>>>();
	
	public List<List<DDBaseSpan<?>>> getList() {
		return list;
	}
	
	public List<DDBaseSpan<?>> firstTrace() {
		return list.get(0);
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
