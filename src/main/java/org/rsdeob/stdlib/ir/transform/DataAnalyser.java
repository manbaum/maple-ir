package org.rsdeob.stdlib.ir.transform;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;
import org.rsdeob.stdlib.ir.api.ICodeListener;

public abstract class DataAnalyser<N extends FastGraphVertex, E extends FlowEdge<N>, S> implements ICodeListener<N> {
	
	protected final FlowGraph<N, E> graph;
	protected final Map<N, S> in;
	protected final Map<N, S> out;
	protected final LinkedList<N> queue;
	
	public DataAnalyser(FlowGraph<N, E> graph) {
		this.graph = graph;
		this.queue = new LinkedList<>();
		in = new HashMap<>();
		out = new HashMap<>();
		run();
	}
	
	protected void init() {
		// set initial data states
		for(N n : graph.vertices()) {
			in.put(n, newState());
			out.put(n, newState());
		}
	}

	
	@Override
	public void commit() {
		processImpl();
	}
	
	private void run() {
		init();
		commit();
	}
	
	public S in(N n) {
		return in.get(n);
	}
	
	public S out(N n) {
		return out.get(n);
	}
	
	public FlowGraph<N, E> getGraph() {
		return graph;
	}
	
	protected void merge(S mainBranch, S mergingBranch) {
		S result = newState();
		merge(mainBranch, mergingBranch, result);
		copy(result, mainBranch);
	}
	
	public void appendQueue(N n) {
		queue.add(n);
	}
	
	@Override
	public void update(N n) {
		queue.add(n);
	}

	@Override
	public void replaced(N old, N n) {
		if(old != n) {
			remove(old);
			update(n);
		}
	}

	@Override
	public void remove(N n) {
		in.remove(n);
		out.remove(n);
		queue.remove(n);
	}	
	
	protected abstract S newState();

	protected abstract S newEntryState();

	protected abstract void copy(S src, S dst);
	
	protected abstract boolean equals(S s1, S s2);
	
	protected abstract void merge(S in1, S in2, S out);
	
	protected abstract void execute(N n, S currentOut, S currentIn);
	
	protected abstract void processImpl();
}