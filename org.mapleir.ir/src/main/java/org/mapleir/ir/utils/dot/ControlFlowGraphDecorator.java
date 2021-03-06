package org.mapleir.ir.utils.dot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;
import org.mapleir.stdlib.util.TabbedStringWriter;

public class ControlFlowGraphDecorator implements DotPropertyDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {
	
	public static final int OPT_EDGES = 0x01;
	public static final int OPT_HIDE_HANDLER_EDGES = 0x02;
	public static final int OPT_STMTS = 0x04;
	public static final int OPT_SIMPLE_EDGES = 0x08;
	
	protected int flags = 0;
	
	public int getFlags() {
			return flags;
		}
		
	public ControlFlowGraphDecorator setFlags(int flags) {
		this.flags = flags;
		return this;
	}
	
	@Override
	public void decorateNodeProperties(ControlFlowGraph g, BasicBlock n, Map<String, Object> nprops) {
		List<BasicBlock> order = new ArrayList<>(g.vertices());
		
		nprops.put("shape", "box");
		nprops.put("labeljust", "l");
		
		if(g.getEntries().contains(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "red");
		} else if(g.getEdges(n).size() == 0) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "green");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(order.indexOf(n)).append(". ").append(n.getDisplayName());
		if((flags & OPT_STMTS) != 0) {
			sb.append("\\l");
			StringBuilder sb2 = new StringBuilder();
			outputBlock(n, sb2);
			sb.append(sb2.toString().replace("\n", "\\l"));
		}
		nprops.put("label", sb.toString());
	}
	
	private void outputBlock(BasicBlock n, StringBuilder sb2) {
		Iterator<Stmt> it = n.iterator();
		TabbedStringWriter sw = new TabbedStringWriter();
		int insn = 0;
		while(it.hasNext()) {
			Stmt stmt = it.next();
			sw.print(insn++ + ". ");
			stmt.toString(sw);
			sw.print("\n");
		}
		sb2.append(sw.toString());
	}

	@Override
	public void decorateEdgeProperties(ControlFlowGraph g, BasicBlock n, FlowEdge<BasicBlock> e, Map<String, Object> eprops) {
		if((flags & OPT_EDGES) != 0)
			eprops.put("label", (flags & OPT_SIMPLE_EDGES) != 0 ? e.getClass().getSimpleName().replace("Edge", "") : e.toGraphString());
	}
	
	@Override
	public boolean isNodePrintable(ControlFlowGraph g, BasicBlock n) {
//		return n.getLabelNode() != null;
		return true;
	}
	
	@Override
	public boolean isEdgePrintable(ControlFlowGraph g, BasicBlock n, FlowEdge<BasicBlock> e) {
		return !((flags & OPT_HIDE_HANDLER_EDGES) != 0 && e instanceof TryCatchEdge);
	}
}
