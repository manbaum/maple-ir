package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CastExpr extends Expr {

	private Expr expression;
	private Type type;

	public CastExpr(Expr expression, Type type) {
		super(CAST);
		setExpression(expression);
		this.type = type;
	}

	public Expr getExpression() {
		return expression;
	}

	public void setExpression(Expr expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	@Override
	public Expr copy() {
		return new CastExpr(expression.copy(), type);
	}

	@Override
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression(read(ptr));
	}

	@Override
	public Precedence getPrecedence0() {
		return Precedence.CAST;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int exprPriority = expression.getPrecedence();
		printer.print('(');
		printer.print(type.getClassName());
		printer.print(')');
		if (exprPriority > selfPriority) {
			printer.print('(');
		}
		expression.toString(printer);
		if (exprPriority > selfPriority) {
			printer.print(')');
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		if (TypeUtils.isObjectRef(getType())) {
			visitor.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
		} else {
			int[] instructions = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < instructions.length; i++) {
				visitor.visitInsn(instructions[i]);
			}
		}
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof CastExpr) {
			CastExpr cast = (CastExpr) s;
			return expression.equivalent(cast) && type.equals(cast.type);
		}
		return false;
	}
}