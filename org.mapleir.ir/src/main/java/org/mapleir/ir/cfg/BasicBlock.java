package org.mapleir.ir.cfg;

import static org.mapleir.stdlib.util.StringHelper.createBlockName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.ImmediateEdge;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

public class BasicBlock implements FastGraphVertex, Comparable<BasicBlock>, List<Stmt> {
	
	public static final int FLAG_NO_MERGE = 0x1;
	
	private int id;
	private final ControlFlowGraph cfg;
	private LabelNode label;
	private final List<Stmt> statements;
	private int flags = 0;
	// private Map<ExceptionRange<BasicBlock>, Integer> ranges;
	
	public BasicBlock(ControlFlowGraph cfg, int id, LabelNode label) {
		this.cfg = cfg;
		this.id = id;
		this.label = label;
		
		statements = new ArrayList<>();
		// ranges = new HashMap<>();
	}
	
	public boolean isFlagSet(int flag) {
		return (flags & flag) == flag;
	}
	
	public void setFlag(int flag, boolean b) {
		if(b) {
			flags |= flag;
		} else {
			flags ^= flag;
		}
	}
	
	public void setFlags(int flags) {
		this.flags = flags;
	}
	
	public ControlFlowGraph getGraph() {
		return cfg;
	}
	
	@Override
	public boolean add(Stmt stmt) {
		boolean ret = statements.add(stmt);
		stmt.setBlock(this);
		return ret;
	}

	@Override
	public void add(int index, Stmt stmt) {
		statements.add(index, stmt);
		stmt.setBlock(this);
	}
	
	@Override
	public boolean remove(Object o) {
		boolean ret = statements.remove(o);
		if (o instanceof Stmt)
			((Stmt) o).setBlock(null);
		return ret;
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		return statements.containsAll(c);
	}
	
	@Override
	public boolean addAll(Collection<? extends Stmt> c) {
		for (Stmt stmt : c)
			add(stmt);
		return c.size() != 0;
	}
	
	@Override
	public boolean addAll(int index, Collection<? extends Stmt> c) {
		for (Stmt stmt : c)
			add(index++, stmt);
		return c.size() != 0;
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		boolean ret = false;
		for (Object o : c)
			ret = remove(o) || ret;
		return ret;
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		boolean ret = false;
		Iterator<Stmt> it = iterator();
		while (it.hasNext()) {
			Stmt stmt = it.next();
			if (!c.contains(stmt)) {
				it.remove();
				stmt.setBlock(null);
				ret = true;
			}
		}
		return ret;
	}
	
	@Override
	public Stmt remove(int index) {
		Stmt stmt = statements.remove(index);
		stmt.setBlock(null);
		return stmt;
	}
	
	@Override
	public boolean contains(Object o) {
		return statements.contains(o);
	}
	
	@Override
	public boolean isEmpty() {
		return statements.isEmpty();
	}
	
	@Override
	public int indexOf(Object o) {
		return statements.indexOf(o);
	}
	
	@Override
	public int lastIndexOf(Object o) {
		return statements.lastIndexOf(o);
	}
	
	@Override
	public Stmt get(int index) {
		return statements.get(index);
	}
	
	@Override
	public Stmt set(int index, Stmt stmt) {
		stmt.setBlock(this);
		return statements.set(index, stmt);
	}
	
	@Override
	public int size() {
		return statements.size();
	}
	
	@Override
	public void clear() {
		Iterator<Stmt> it = statements.iterator();
		while(it.hasNext()) {
			Stmt s = it.next();
			s.setBlock(null);
			it.remove();
		}
	}
	
	@Override
	public Iterator<Stmt> iterator() {
		return statements.iterator();
	}
		
	@Override
	public ListIterator<Stmt> listIterator() {
		return statements.listIterator();
	}
	
	@Override
	public ListIterator<Stmt> listIterator(int index) {
		return statements.listIterator(index);
	}
	
	@Override
	public Object[] toArray() {
		return statements.toArray();
	}
	
	@Override
	public <T> T[] toArray(T[] a) {
		return statements.toArray(a);
	}
	
	@Override
	public List<Stmt> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException();
	}
	
	public void transfer(BasicBlock to) {
		Iterator<Stmt> it = statements.iterator();
		while(it.hasNext()) {
			Stmt s = it.next();
			to.statements.add(s);
			s.setBlock(to);
			it.remove();
		}
	}
	
	public void transferUp(BasicBlock dst, int to) {
		// FIXME: faster
		for(int i=to - 1; i >= 0; i--) {
			Stmt s = statements.remove(0);
			dst.add(s);
			s.setBlock(dst);
		}
	}

	@Override
	public String getDisplayName() {
		return createBlockName(id);
	}

	public void setId(int i) {
		id = i;
	}
	
	@Override
	public int getNumericId() {
		return id;
	}
	
	public List<ExceptionRange<BasicBlock>> getProtectingRanges() {
		/* Iterator<Entry<ExceptionRange<BasicBlock>, Integer>> it = ranges.entrySet().iterator();
		while(it.hasNext()) {
			Entry<ExceptionRange<BasicBlock>, Integer> e = it.next();
			ExceptionRange<BasicBlock> key = e.getKey();
			if(!cfg.getRanges().contains(key) || !key.containsVertex(this)) {
				it.remove();
				continue;
			}
			int oldhc = e.getValue();
			int newhc = key.hashCode();
			if(oldhc != newhc) {
				e.setValue(newhc);
			}
		} */
		
		List<ExceptionRange<BasicBlock>> ranges = new ArrayList<>();
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			if(er.containsVertex(this)) {
				ranges.add(er);
			}
		}
		return ranges;
	}
	
	public boolean isHandler() {
		for(FlowEdge<BasicBlock> e : cfg.getReverseEdges(this)) {
			if(e instanceof TryCatchEdge) {
				if(e.dst() == this) {
					return true;
				} else {
					throw new IllegalStateException("incoming throw edge for " + getDisplayName() + " with dst " + e.dst().getDisplayName());
				}
			}
		}
		return false;
	}
	
	public Set<FlowEdge<BasicBlock>> getPredecessors() {
		return new HashSet<>(cfg.getReverseEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getPredecessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getPredecessors();
		set.removeIf(e.negate());
		return set;
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors() {
		return new HashSet<>(cfg.getEdges(this));
	}

	public Set<FlowEdge<BasicBlock>> getSuccessors(Predicate<? super FlowEdge<BasicBlock>> e) {
		Set<FlowEdge<BasicBlock>> set = getSuccessors();
		set.removeIf(e.negate());
		return set;
	}

	public List<BasicBlock> getJumpEdges() {
		List<BasicBlock> jes = new ArrayList<>();
		for (FlowEdge<BasicBlock> e : cfg.getEdges(this)) {
			if (!(e instanceof ImmediateEdge)) {
				jes.add(e.dst());
			}
		}
		return jes;
	}
	
	private Set<FlowEdge<BasicBlock>> findImmediatesImpl(Set<FlowEdge<BasicBlock>> set) {
		Set<FlowEdge<BasicBlock>> iset = new HashSet<>();
		for(FlowEdge<BasicBlock> e : set) {
			if(e instanceof ImmediateEdge) {
				iset.add(e);
			}
		}
		return iset;
	}
	
	private FlowEdge<BasicBlock> findSingleImmediateImpl(Set<FlowEdge<BasicBlock>> _set) {
		Set<FlowEdge<BasicBlock>> set = findImmediatesImpl(_set);
		int size = set.size();
		if(size == 0) {
			return null;
		} else if(size > 1) {
			throw new IllegalStateException(set.toString());
		} else {
			return set.iterator().next();
		}
	}

	public ImmediateEdge<BasicBlock> getImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getEdges(this));
	}
	
	public BasicBlock getImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getEdges(this));
		if(e != null) {
			return e.dst();
		} else {
			return null;
		}
	}
	
	public ImmediateEdge<BasicBlock> getIncomingImmediateEdge() {
		return (ImmediateEdge<BasicBlock>) findSingleImmediateImpl(cfg.getReverseEdges(this));
	}

	public BasicBlock getIncomingImmediate() {
		FlowEdge<BasicBlock> e =  findSingleImmediateImpl(cfg.getReverseEdges(this));
		if(e != null) {
			return e.src();
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		return String.format("Block #%s", createBlockName(id)/* (%s), label != null ? label.hashCode() : "dummy"*/);
	}

	@Override
	public int compareTo(BasicBlock o) {
		return Integer.compare(id, o.id);
	}

	// TODO: Why does this break stuff????
	// @Override
	// public boolean equals(Object o) {
	// 	if (this == o)
	// 		return true;
	// 	if (o == null || getClass() != o.getClass())
	// 		return false;
	//
	// 	BasicBlock bb = (BasicBlock) o;
	//
	// 	assert ((id == bb.id) == (this == bb));
	// 	return id == bb.id;
	// }
	//
	// @Override
	// public int hashCode() {
	// 	return id;
	// }

	public void resetLabel() {
		label = new LabelNode();
	}

	public Label getLabel() {
		return label.getLabel();
	}
	
	public LabelNode getLabelNode() {
		return label;
	}
	
//	public void checkConsistency() {
//		for (Stmt stmt : statements)
//			if (stmt.getBlock() != this)
//				throw new IllegalStateException("Orphaned child " + stmt);
//	}

	public int getFlags() {
		return flags;
	}
}
