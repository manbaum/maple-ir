package org.mapleir.deob.interproc.geompa;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class VarNode extends ValNode implements Comparable<VarNode> {

	protected Object variable;
	protected Map<FieldNode, FieldRefNode> fields;
	protected int finishingNumber = 0;
	protected boolean interProcTarget = false;
	protected boolean interProcSource = false;
	protected int numDerefs = 0;

	VarNode(PAG pag, Object variable, Type t) {
		super(pag, t);
		/*if (!(t instanceof RefLikeType) || t instanceof AnySubType) {
			throw new RuntimeException("Attempt to create VarNode of type " + t);
		}*/
		this.variable = variable;
		pag.getVarNodeNumberer().add(this);
		setFinishingNumber(++pag.maxFinishNumber);
	}
	
	public Context context() {
		return null;
	}

	/** Returns all field ref nodes having this node as their base. */
	public Collection<FieldRefNode> getAllFieldRefs() {
		if (fields == null)
			return Collections.emptyList();
		return fields.values();
	}

	/**
	 * Returns the field ref node having this node as its base, and field as its field; null if nonexistent.
	 */
	public FieldRefNode dot(SparkField field) {
		return fields == null ? null : fields.get(field);
	}

	public int compareTo(Object o) {
		VarNode other = (VarNode) o;
		if (other.finishingNumber == finishingNumber && other != this) {
			G.v().out.println("This is: " + this + " with id " + getNumber() + " and number " + finishingNumber);
			G.v().out.println(
					"Other is: " + other + " with id " + other.getNumber() + " and number " + other.finishingNumber);
			throw new RuntimeException("Comparison error");
		}
		return other.finishingNumber - finishingNumber;
	}

	public void setFinishingNumber(int i) {
		finishingNumber = i;
		if (i > pag.maxFinishNumber)
			pag.maxFinishNumber = i;
	}

	/** Returns the underlying variable that this node represents. */
	public Object getVariable() {
		return variable;
	}

	/**
	 * Designates this node as the potential target of a interprocedural assignment edge which may be added during on-the-fly call graph updating.
	 */
	public void setInterProcTarget() {
		interProcTarget = true;
	}

	/**
	 * Returns true if this node is the potential target of a interprocedural assignment edge which may be added during on-the-fly call graph updating.
	 */
	public boolean isInterProcTarget() {
		return interProcTarget;
	}

	/**
	 * Designates this node as the potential source of a interprocedural assignment edge which may be added during on-the-fly call graph updating.
	 */
	public void setInterProcSource() {
		interProcSource = true;
	}

	/**
	 * Returns true if this node is the potential source of a interprocedural assignment edge which may be added during on-the-fly call graph updating.
	 */
	public boolean isInterProcSource() {
		return interProcSource;
	}

	/** Returns true if this VarNode represents the THIS pointer */
	public boolean isThisPtr() {
		if (variable instanceof Pair) {
			Pair o = (Pair) variable;
			return o.isThisParameter();
		}

		return false;
	}

	/** Registers a frn as having this node as its base. */
	void addField(FieldRefNode frn, FieldNode field) {
		if (fields == null)
			fields = new HashMap<>();
		fields.put(field, frn);
	}
}