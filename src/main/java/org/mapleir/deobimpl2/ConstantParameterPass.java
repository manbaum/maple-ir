package org.mapleir.deobimpl2;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpression;
import org.mapleir.ir.code.expr.InitialisedObjectExpression;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConstantParameterPass implements ICompilerPass, Opcode {
	
	private static final Comparator<Integer> INTEGER_ORDERER = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compareUnsigned(o1, o2);
		}
	};
	
	private final Map<MethodNode, Set<Expr>> calls;
	private final Map<MethodNode, Set<Integer>> dead;
	private final Set<MethodNode> processMethods;
	private final Set<MethodNode> cantfix;
	private final Set<Expr> processedExprs;
	
	public ConstantParameterPass() {
		calls = new HashMap<>();
		dead  = new HashMap<>();
		processMethods = new HashSet<>();
		cantfix = new HashSet<>();
		processedExprs = new HashSet<>();
	}
	
	@Override
	public String getId() {
		return "Argument-Prune";
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		calls.clear();
		dead.clear();
		processedExprs.clear();
		processMethods.clear();
		
		Map<MethodNode, List<List<Expr>>> args = new HashMap<>();
		Map<MethodNode, int[]> paramIndices = new HashMap<>();
		
		IRCallTracer tracer = new IRCallTracer(cxt) {
			@Override
			protected void visitMethod(MethodNode m) {
				Type[] paramTypes = Type.getArgumentTypes(m.desc);
				List<List<Expr>> lists = new ArrayList<>(paramTypes.length);
				int[] idxs = new int[paramTypes.length];
				int idx = 0;
				if((m.access & Opcodes.ACC_STATIC) == 0) {
					idx++;
				}
				for(int i=0; i < paramTypes.length; i++) {
					lists.add(new ArrayList<>());

					idxs[i] = idx;
					idx += paramTypes[i].getSize();
				}
				paramIndices.put(m, idxs);
				args.put(m, lists);
				calls.put(m, new HashSet<>());
			}
			
			@Override
			protected void processedInvocation(MethodNode caller, MethodNode callee, Expr e) {
				calls.get(callee).add(e);
				
				Expr[] params;
				
				if(e.getOpcode() == INVOKE) {
					params = ((InvocationExpression) e).getParameterArguments();
				} else if(e.getOpcode() == INIT_OBJ) {
					params = ((InitialisedObjectExpression) e).getArgumentExpressions();
				} else {
					throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, e));
				}
				
				for(int i=0; i < params.length; i++) {
					args.get(callee).get(i).add(params[i]);
				}
			}
		};
		

		for(MethodNode mn : cxt.getActiveMethods()) {
			tracer.trace(mn);
		}
		
		int kp = 0;
		
		for(MethodNode mn : cxt.getActiveMethods()) {
			ControlFlowGraph cfg = cxt.getIR(mn);
			
			List<List<Expr>> argExprs = args.get(mn);

			Set<Integer> deadParams = new TreeSet<>(INTEGER_ORDERER);
						
			for(int i=0; i < argExprs.size(); i++) {
				List<Expr> l = argExprs.get(i);
				ConstantExpression c = getConstantValue(l);
				
				if(c != null) {
					LocalsPool pool = cfg.getLocals();
					int argLocalIndex = paramIndices.get(mn)[i];
					VersionedLocal argLocal = pool.get(argLocalIndex, 0, false);
					AbstractCopyStatement argDef = pool.defs.get(argLocal);
					
					boolean removeDef = true;
					
					/* demote the def from a synthetic
					 * copy to a normal one. */
					VarExpression dv = argDef.getVariable().copy();
					
					VersionedLocal spill = pool.makeLatestVersion(argLocal);
					dv.setLocal(spill);
					
					CopyVarStatement copy = new CopyVarStatement(dv, c.copy());
					BasicBlock b = argDef.getBlock();
					argDef.delete();
					argDef = copy;
					b.add(copy);
					
					pool.defs.remove(argLocal);
					pool.defs.put(spill, copy);
					
					Set<VarExpression> spillUses = new HashSet<>();
					pool.uses.put(spill, spillUses);
					
					Iterator<VarExpression> it = pool.uses.get(argLocal).iterator();
					while(it.hasNext()) {
						VarExpression v = it.next();
						
						if(v.getParent() == null) {
							/* the use is in a phi, we can't
							 * remove the def. */
							removeDef = false;
							spillUses.add(v);
							v.setLocal(spill);
						} else {
							CodeUnit par = v.getParent();
							par.overwrite(c.copy(), par.indexOf(v));
						}
					}

					pool.uses.remove(argLocal);
					
					if(removeDef) {
						argDef.delete();
					}
					
					deadParams.add(i);
				} else if(isMultiVal(l)) {
//					System.out.printf("Multivalue param for %s @ arg%d:   %s.%n", mn, i, l);
				}
			}

			if(deadParams.size() > 0) {
				dead.put(mn, deadParams);
			}
		}
				
		for(;;) {
			int s = kp;
			
			Iterator<Entry<MethodNode, Set<Integer>>> it = dead.entrySet().iterator();
			while(it.hasNext()) {
				Entry<MethodNode, Set<Integer>> e = it.next();
				
				MethodNode mn = e.getKey();
				
				int k = fixDeadParameters(cxt, mn);
				if(k > 0) {
					kp += k;
				}
			}
			
			if(s == kp) {
				break;
			}
		}
		
		cantfix.removeAll(processMethods);
		
		System.out.println("  can't fix:");
		for(MethodNode m : cantfix) {
			System.out.println("    " + m + ":: " + dead.get(m));
		}
		
		System.out.printf("Removed %d constant paramters.%n", kp);
	}
	
	private int fixDeadParameters(IContext cxt, MethodNode mn) {
		if(processMethods.contains(mn)) {
			return 0;
		}
		
		Set<Integer> deadSet;
		Set<MethodNode> chain = null;
		
		ClassTree tree = cxt.getClassTree();
		
		if(!Modifier.isStatic(mn.access) && !mn.name.equals("<init>")) {
			chain = getVirtualChain(cxt, mn.owner, mn.name, mn.desc);
			
//			{
//				Set<ClassNode> cc = tree.getAllBranches(mn.owner, false);
//				
//				System.out.println();
//				System.out.println("Start: " + mn + " -> " + chain);
//				System.out.println(" cc:: " + cc);
//			}
			
			if(!isActiveChain(chain)) {
				cantfix.addAll(chain);
				
				System.out.println();
				System.out.println("@" + mn);
				Set<ClassNode> cc = tree.getAllBranches(mn.owner, false);
				
				System.out.println(" class chain: " + cc);
				System.out.println("  inactive chain: " + chain);
				
				for(MethodNode m : chain) {
					System.out.println("  m: " + m + ", ds: " + dead.get(m));
				}
				
				for(MethodNode m : chain) {
					for(Expr c : calls.get(m)) {
						System.out.println("   1.  m: " + m + ", c: " + c);
					}
				}
				
				for(Expr c : calls.get(mn)) {
					System.out.println("   2.  c: " + c);
				}
				
				System.out.println();
				
				return 0;
				
			}
			
			/* find the common dead indices. */
			deadSet = new HashSet<>();
			for(MethodNode m : chain) {
				Set<Integer> set = dead.get(m);
				if(set != null) {
					deadSet.addAll(set);
				}
			}

			for(MethodNode m : chain) {
				Set<Integer> set = dead.get(m);
				if(set != null) {
					deadSet.retainAll(set);
				}
			}
			
		} else {
			deadSet = dead.get(mn);
		}
		
//		System.out.println("Remap: " + mn.owner.name + "." + mn.name + " " + mn.desc + " -> " + buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), deadSet));
		String newDesc = buildDesc(Type.getArgumentTypes(mn.desc), Type.getReturnType(mn.desc), deadSet);
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		if(Modifier.isStatic(mn.access)) {
			MethodNode conflict = resolver.resolveStaticCall(mn.owner.name, mn.name, newDesc);
			if(conflict != null) {
				// System.out.printf("  can't remap(s) %s because of %s.%n", mn, conflict);
				cantfix.add(mn);
				return 0;
			}
		} else {
			if(mn.name.equals("<init>")) {
				MethodNode conflict = resolver.resolveVirtualCall(mn.owner, mn.name, newDesc);
				if(conflict != null) {
					// System.out.printf("  can't remap(i) %s because of %s.%n", mn, conflict);
					cantfix.add(mn);
					return 0;
				}
			} else {
				Set<MethodNode> conflicts = getVirtualChain(cxt, mn.owner, mn.name, newDesc);
				if(conflicts.size() == 0) {
					remapMethods(chain, newDesc, deadSet);
					return deadSet.size();
				} else {
					// System.out.printf("  can't remap(v) %s because of %s.%n", mn, conflicts);
					cantfix.addAll(chain);
					return 0;
				}
			}
		}
		
		remapMethod(mn, newDesc, deadSet);
		return deadSet.size();
	}
	
	private void remapMethods(Set<MethodNode> methods, String newDesc, Set<Integer> deadSet) {
		for(MethodNode mn : methods) {
//			System.out.println(" 2. descmap: " + mn + " to " + newDesc);
			mn.desc = newDesc;
			processMethods.add(mn);
			
			for(Expr call : calls.get(mn)) {
				if(processedExprs.contains(call)) {
					continue;
				}
//				System.out.println("   2. fixing: " + call + " to " + mn);
				processedExprs.add(call);
				patchCall(mn, call, deadSet);
			}
		}
	}
	
	private void remapMethod(MethodNode mn, String newDesc, Set<Integer> dead) {
//		System.out.println(" 1. descmap: " + mn + " to " + newDesc);
		mn.desc = newDesc;
		processMethods.add(mn);
		
		for(Expr call : calls.get(mn)) {
			/* since the callgrapher finds all
			 * the methods in a hierarchy and considers
			 * it as a single invocation, a certain
			 * invocation may be considered multiple times. */
			if(processedExprs.contains(call)) {
				continue;
			}
//			System.out.println("   1. fixing: " + call + " to " + mn);
			processedExprs.add(call);
			patchCall(mn, call, dead);
		}
	}
	
	private void patchCall(MethodNode to, Expr call, Set<Integer> dead) {
		if(call.getOpcode() == Opcode.INIT_OBJ) {
			InitialisedObjectExpression init = (InitialisedObjectExpression) call;

			CodeUnit parent = init.getParent();
			Expr[] newArgs = buildArgs(init.getArgumentExpressions(), 0, dead);
			InitialisedObjectExpression init2 = new InitialisedObjectExpression(init.getType(), init.getOwner(), to.desc, newArgs);

			parent.overwrite(init2, parent.indexOf(init));
		} else if(call.getOpcode() == Opcode.INVOKE) {
			InvocationExpression invoke = (InvocationExpression) call;

			CodeUnit parent = invoke.getParent();
			
			Expr[] newArgs = buildArgs(invoke.getArgumentExpressions(), invoke.getCallType() == Opcodes.INVOKESTATIC ? 0 : -1, dead);
			InvocationExpression invoke2 = new InvocationExpression(invoke.getCallType(), newArgs, invoke.getOwner(), invoke.getName(), to.desc);
			
			parent.overwrite(invoke2, parent.indexOf(invoke));
		} else {
			throw new UnsupportedOperationException(call.toString());
		}
	}
	
	private static Expr[] buildArgs(Expr[] oldArgs, int off, Set<Integer> dead) {
		Expr[] newArgs = new Expr[oldArgs.length - dead.size()];

		int j = newArgs.length - 1;
		for(int i=oldArgs.length-1; i >= 0; i--) {
			Expr e = oldArgs[i];
			if(!dead.contains(i + off)) {
				newArgs[j--] = e;
			}
			e.unlink();
		}
		
		return newArgs;
	}
	
	private static String buildDesc(Type[] preParams, Type ret, Set<Integer> dead) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for(int i=0; i < preParams.length; i++) {
			if(!dead.contains(i)) {
				Type t = preParams[i];
				sb.append(t.toString());
			}
		}
		sb.append(")").append(ret.toString());
		return sb.toString();
	}
	
	private Set<MethodNode> getVirtualChain(IContext cxt, ClassNode cn, String name, String desc) {		
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode c : cxt.getClassTree().getAllBranches(cn, false)) {
			MethodNode mr = cxt.getInvocationResolver().resolveVirtualCall(c, name, desc);
			if(mr != null) {
				set.add(mr);
			}
		}
		return set;
	}
	
	private boolean isActiveChain(Set<MethodNode> chain) {
		if(chain.size() == 0) {
			throw new UnsupportedOperationException(chain.toString());
		} else if(chain.size() == 1) {
			return true;
		} else {
			Set<MethodNode> chain2 = new HashSet<>();
			chain2.addAll(chain);
			
			Iterator<MethodNode> it = chain2.iterator();
			while(it.hasNext()) {
				MethodNode m = it.next();
				if(dead.get(m) == null) {
					it.remove();
				}
			}
			
			if(chain2.size() == 0) {
				throw new UnsupportedOperationException(chain2.toString());
			} else if(chain2.size() == 1) {
				return true;
			}
			
			it = chain2.iterator();
			
			/* Find possible common dead indices that we can
			 * process. If there are none, then the chain is
			 * considered inactive. */
			Set<Integer> ret = new HashSet<>();
			
			ret.addAll(dead.get(it.next()));
			
			while(it.hasNext()) {
				ret.retainAll(dead.get(it.next()));
			}
			
			return ret.size() > 0;
		}
	}
	
	private static boolean isMultiVal(List<Expr> exprs) {
		if(exprs.size() <= 1) {
			return false;
		}
		
		for(Expr e : exprs) {
			if(e.getOpcode() != Opcode.CONST_LOAD) {
				return false;
			}
		}
		return true;
	}
	
	private static ConstantExpression getConstantValue(List<Expr> exprs) {
		ConstantExpression v = null;
		
		for(Expr e : exprs) {
			if(e.getOpcode() == Opcode.CONST_LOAD) {
				ConstantExpression c = (ConstantExpression) e;
				if(v == null) {
					v = c;
				} else {
					if(c.getConstant() != null && c.getConstant().equals(v.getConstant())) {
						v = c;
					} else {
						return null;
					}
				}
			} else {
				return null;
			}
		}
		
		return v;
	}
}