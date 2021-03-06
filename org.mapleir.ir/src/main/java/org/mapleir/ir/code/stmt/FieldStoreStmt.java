package org.mapleir.ir.code.stmt;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Expr.Precedence;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class FieldStoreStmt extends Stmt {

	private Expr instanceExpression;
	private Expr valueExpression;
	private String owner;
	private String name;
	private String desc;

	public FieldStoreStmt(Expr instanceExpression, Expr valueExpression, String owner, String name, String desc) {
		super(FIELD_STORE);
		this.instanceExpression = instanceExpression;
		this.valueExpression = valueExpression;
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		
		overwrite(instanceExpression, 0);
		overwrite(valueExpression, instanceExpression == null ? 0 : 1);
	}

	public boolean isStatic() {
		return getInstanceExpression() == null;
	}
	
	public Expr getInstanceExpression() {
		return instanceExpression;
	}

	public void setInstanceExpression(Expr instanceExpression) {
		if (this.instanceExpression == null && instanceExpression != null) {
			this.instanceExpression = instanceExpression;
			overwrite(valueExpression, 1);
			overwrite(this.instanceExpression, 0);
		} else if (this.instanceExpression != null && instanceExpression == null) {
			this.instanceExpression = instanceExpression;
			overwrite(valueExpression, 0);
			overwrite(null, 1);
		} else {
			this.instanceExpression = instanceExpression;
			overwrite(this.instanceExpression, 0);
		}
	}

	public Expr getValueExpression() {
		return valueExpression;
	}

	public void setValueExpression(Expr valueExpression) {
		this.valueExpression = valueExpression;
		overwrite(valueExpression, instanceExpression == null ? 0 : 1);
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (instanceExpression != null && ptr == 0) {
			setInstanceExpression(read(0));
		} else if (instanceExpression == null && ptr == 0) {
			setValueExpression(read(0));
		} else if (ptr == 1) {
			setValueExpression(read(1));
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (instanceExpression != null) {
			int selfPriority = Precedence.MEMBER_ACCESS.ordinal();
			int basePriority = instanceExpression.getPrecedence();
			if (basePriority > selfPriority)
				printer.print('(');
			instanceExpression.toString(printer);
			if (basePriority > selfPriority)
				printer.print(')');
		} else
			printer.print(owner.replace('/', '.'));
		printer.print('.');
		printer.print(name);
		printer.print(" = ");
		valueExpression.toString(printer);
		printer.print(';');
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		if (instanceExpression != null)
			instanceExpression.toCode(visitor, cfg);
		valueExpression.toCode(visitor, cfg);
		if (TypeUtils.isPrimitive(Type.getType(desc))) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(valueExpression.getType(), Type.getType(desc));
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}
		visitor.visitFieldInsn(instanceExpression != null ? Opcodes.PUTFIELD : Opcodes.PUTSTATIC, owner, name, desc);		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public FieldStoreStmt copy() {
		return new FieldStoreStmt(instanceExpression == null ? null : instanceExpression.copy(), valueExpression.copy(), owner, name, desc);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof FieldStoreStmt) {
			FieldStoreStmt store = (FieldStoreStmt) s;
			return owner.equals(store.owner) && name.equals(store.name) && desc.equals(store.desc) &&
					instanceExpression.equivalent(store.instanceExpression) && valueExpression.equivalent(store.valueExpression);
		}
		return false;
	}
}