package org.rsdeob.stdlib.cfg.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class ConditionalJumpStatement extends Statement {

	public enum ComparisonType {
		EQ("=="), NE("!="), LT("<"), GE(">="), GT(">"), LE("<="), ;

		private final String sign;

		private ComparisonType(String sign) {
			this.sign = sign;
		}

		public String getSign() {
			return sign;
		}

		public static ComparisonType getType(int opcode) {
			switch (opcode) {
				case Opcodes.IF_ACMPEQ:
				case Opcodes.IF_ICMPEQ:
				case Opcodes.IFEQ:
					return EQ;
				case Opcodes.IF_ACMPNE:
				case Opcodes.IF_ICMPNE:
				case Opcodes.IFNE:
					return NE;
				case Opcodes.IF_ICMPGT:
				case Opcodes.IFGT:
					return GT;
				case Opcodes.IF_ICMPGE:
				case Opcodes.IFGE:
					return GE;
				case Opcodes.IF_ICMPLT:
				case Opcodes.IFLT:
					return LT;
				case Opcodes.IF_ICMPLE:
				case Opcodes.IFLE:
					return LE;
				default:
					throw new IllegalArgumentException(Printer.OPCODES[opcode]);
			}
		}
	}

	private Expression left;
	private Expression right;
	private BasicBlock trueSuccessor;
	private ComparisonType type;

	public ConditionalJumpStatement(Expression left, Expression right, BasicBlock trueSuccessor, ComparisonType type) {
		setLeft(left);
		setRight(right);
		setTrueSuccessor(trueSuccessor);
		setType(type);
	}

	public Expression getLeft() {
		return left;
	}

	public void setLeft(Expression left) {
		this.left = left;
		overwrite(left, 0);
	}

	public Expression getRight() {
		return right;
	}

	public void setRight(Expression right) {
		this.right = right;
		overwrite(right, 1);
	}

	public BasicBlock getTrueSuccessor() {
		return trueSuccessor;
	}

	public void setTrueSuccessor(BasicBlock trueSuccessor) {
		this.trueSuccessor = trueSuccessor;
	}

	public ComparisonType getType() {
		return type;
	}

	public void setType(ComparisonType type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setLeft((Expression) read(ptr));
		} else if (ptr == 1) {
			setRight((Expression) read(ptr));
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("if ");
		printer.print('(');
		left.toString(printer);
		printer.print(" " + type.getSign() + " ");
		right.toString(printer);
		printer.print(')');
		printer.tab();
		printer.print("\nGOTO   #" + trueSuccessor.getId());
		printer.untab();
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		Type opType = TypeUtils.resolveBinOpType(left.getType(), right.getType());

		if (TypeUtils.isObjectRef(opType)) {
			boolean isNull = right instanceof ConstantExpression && ((ConstantExpression) right).getConstant() == null;
			if (type != ComparisonType.EQ && type != ComparisonType.NE) {
				throw new IllegalArgumentException(type.toString());
			}

			left.toCode(visitor);
			if (isNull) {
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IFNULL : Opcodes.IFNONNULL, trueSuccessor.getLabel().getLabel());
			} else {
				right.toCode(visitor);
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IF_ACMPEQ : Opcodes.IF_ACMPNE, trueSuccessor.getLabel().getLabel());
			}
		} else if (opType == Type.INT_TYPE) {
			boolean canShorten = right instanceof ConstantExpression && ((ConstantExpression) right).getConstant() instanceof Number
					&& ((Number) ((ConstantExpression) right).getConstant()).intValue() == 0;

			left.toCode(visitor);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			if (canShorten) {
				visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel().getLabel());
			} else {
				right.toCode(visitor);
				cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
				for (int i = 0; i < cast.length; i++) {
					visitor.visitInsn(cast[i]);
				}
				visitor.visitJumpInsn(Opcodes.IF_ICMPEQ + type.ordinal(), trueSuccessor.getLabel().getLabel());
			}
		} else if (opType == Type.LONG_TYPE) {
			left.toCode(visitor);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn(Opcodes.LCMP);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel().getLabel());
		} else if (opType == Type.FLOAT_TYPE) {
			left.toCode(visitor);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.FCMPL : Opcodes.FCMPG);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel().getLabel());
		} else if (opType == Type.DOUBLE_TYPE) {
			left.toCode(visitor);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.DCMPL : Opcodes.DCMPG);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel().getLabel());
		} else {
			throw new IllegalArgumentException(opType.toString());
		}
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return left.canChangeLogic() || right.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return left.isAffectedBy(stmt) || right.isAffectedBy(stmt);
	}
}