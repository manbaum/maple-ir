package org.mapleir.flowgraph.edges;

import java.util.List;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class TryCatchEdge<N extends FastGraphVertex> extends AbstractFlowEdge<N> {

	public final ExceptionRange<N> erange;
	private int hashcode;
	
	public TryCatchEdge(N src, N dst) {
		super(TRYCATCH, src, dst);
		this.erange = null;
	}
	
	public TryCatchEdge(N src, ExceptionRange<N> erange) {
		super(TRYCATCH, src, erange.getHandler());
		this.erange = erange;
		recalcHashcode();
	}
	
	private void recalcHashcode() {
		hashcode = 31 + (erange == null ? 0 : erange.hashCode());
		hashcode += 31 * hashcode + src.getNumericId();
		hashcode += 31 * hashcode + dst.getNumericId();
	}

	@Override
	public String toGraphString() {
		return "Handler";
	}

	@Override
	public String toString() {
		List<N> l = erange.get();
		return String.format("TryCatch range: [%s...%s] -> %s (%s)", l.get(0).getDisplayName(), l.get(l.size() - 1).getDisplayName()/*ExceptionRange.rangetoString(erange.get())*/, dst.getDisplayName(), erange.getTypes());
	}

	@Override
	public String toInverseString() {
		return String.format("TryCatch handler: %s <- range: [%s...%s] (%s)", dst.getDisplayName(), erange.get().iterator().next()/*ExceptionRange.rangetoString(erange.get())*/, src.getDisplayName(), erange.getTypes());
	}

	@Override
	public TryCatchEdge<N> clone(N src, N dst) {
		return new TryCatchEdge<>(src, erange);
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TryCatchEdge<?> other = (TryCatchEdge<?>) obj;
		if (erange == null) {
			if (other.erange != null)
				return false;
		} else if (!erange.equals(other.erange))
			return false;
		return true;
	}
}
