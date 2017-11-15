package org.mapleir.ir.antlr.analysis;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ssaopt.Constraint;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.FieldStoreStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.mapleir.stdlib.collections.ClassHelper;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class TypeAnalysis {
	
	public static Map<VersionedLocal, Type> analyse(ApplicationClassSource source, ControlFlowGraph cfg, Map<VersionedLocal, Type> argTypes) {
		TypeAnalysis typeAnalysis = new TypeAnalysis(source, cfg, argTypes);
		// typeAnalysis.optimiseWorklist();
		typeAnalysis.populateWorklist();
		typeAnalysis.run();
		
		Map<VersionedLocal, Type> myResults = new HashMap<>();
		for(Entry<VersionedLocal, LocalType> e : typeAnalysis.results.entrySet()) {
			System.out.println(e);
			LocalType lt = e.getValue();
			if(!lt.isTop) {
				myResults.put(e.getKey(), lt.type);
			}
		}
		
		return myResults;
	}

	private final ApplicationClassSource source;
	private final ControlFlowGraph cfg;
	private final List<BasicBlock> topoOrder;
	private final Map<VersionedLocal, Type> argTypes;
	private final LocalsPool pool;
	private final LinkedList<Stmt> worklist;
	private final Map<VersionedLocal, LocalType> results;
	private final Map<Local, Set<Constraint>> constraints;
	
	private TypeAnalysis(ApplicationClassSource source, ControlFlowGraph cfg, Map<VersionedLocal, Type> argTypes) {
		this.source = source;
		this.cfg = cfg;
		this.argTypes = argTypes;
		this.pool = cfg.getLocals();
		// topo is probably optimal
		this.topoOrder = computeTopoOrder();
		
		// Set<VersionedLocal> ssaLocals = getSSALocals(pool);
		// this.worklist = new LinkedList<>(ssaLocals);
		this.worklist = new LinkedList<>();
		this.results = new HashMap<>();
		this.constraints = new HashMap<>();
	}
	
	private List<BasicBlock> computeTopoOrder() {
		ExtendedDfs<BasicBlock> dfs = new ExtendedDfs<>(cfg, ExtendedDfs.TOPO);
		dfs.run(cfg.getEntries().iterator().next());
		return dfs.getTopoOrder();
	}
	
	@SuppressWarnings("unchecked")
	private static Set<VersionedLocal> getSSALocals(LocalsPool pool) {
		@SuppressWarnings("rawtypes")
		Set ssaLocals = pool.getAll(p -> p instanceof VersionedLocal);
		return ssaLocals;
	}
	
	private void populateWorklist() {
	    for(BasicBlock b : topoOrder) {
	        worklist.addAll(b);
	    }
	}
	
	/*private void optimiseWorklist() {
		Collections.sort(worklist, new Comparator<VersionedLocal>() {
			@Override
			public int compare(VersionedLocal o1, VersionedLocal o2) {
				AbstractCopyStmt copy1 = pool.defs.get(o1);
				AbstractCopyStmt copy2 = pool.defs.get(o2);
				
				BasicBlock b1 = copy1.getBlock();
				BasicBlock b2 = copy2.getBlock();
				
				if(b1 != b2) {
					return Integer.compare(topoOrder.indexOf(b1), topoOrder.indexOf(b2));
				} else {
					return Integer.compare(b1.indexOf(copy1), b1.indexOf(copy2));
				}
			}
		});
	}*/
	
	private boolean hasComputedType(VersionedLocal l) {
		return results.containsKey(l) && !results.get(l).isTop;
	}
	
	private void run() {
        /* Due to the loss of type information in the IR textcode,
         * we need to type the code and fill in artifacts that are
         * context-dependent on these types.
         * 
         * We start off with the given facts:
         *   -The type of each parameter of the function is equal
         *    to the type given by the method descriptor.
         *   -If the method is virtual, the first local variable is
         *    a type representation of the class that the method is
         *    declared in.
         *   -Each variable that is assigned to the source expr
         *    of catch() is given the type indicated in the handler
         *    table.
         *    
         * Each expression that uses a variable must have that variable
         * declared before it is used. In SSA this could be either a phi
         * or a regular local variable. In either case, this variable
         * must be typed before the expression can be evaluated. Therefore
         * we visit the code in reverse postorder to ensure that the
         * 'parents' or predecessor statements of a statement are visited
         * before the statement itself is.
         * 
         * If it is determined at a point that a statement cannot be properly
         * typed due to a lack of information. - What do we do? TOP indicates
         * that one of the predecessors was untyped, but it can't be since they
         * must be declared before reaching a use? Maybe this can happen with phis?
         * 
         * 
         */
	    
	    worklistLoop: while(!worklist.isEmpty()) {
	        Stmt stmt = worklist.pop();
	        int opcode = stmt.getOpcode();
	        
	        switch(opcode) {
	            case Opcode.LOCAL_LOAD: {
	                CopyVarStmt cvs = (CopyVarStmt) stmt;
	                break;
	            }
	            case Opcode.PHI_STORE: {
	                CopyPhiStmt cps = (CopyPhiStmt) stmt;
	                break;
	            }
	            case Opcode.FIELD_STORE: {
	                FieldStoreStmt fss = (FieldStoreStmt) stmt;
	                
	                break;
	            }
	        }
	    }
		/*worklistLoop: while(!worklist.isEmpty()) {
			VersionedLocal l = worklist.pop();

			AbstractCopyStmt copyStmt = pool.defs.get(l);
			
			if(copyStmt.getOpcode() == Opcode.PHI_STORE) {
				PhiExpr phiExpr = (PhiExpr) copyStmt.getExpression();
				List<VersionedLocal> srcLocals = new ArrayList<>();
				for(Expr e : phiExpr.getArguments().values()) {
					VarExpr varExpr = (VarExpr) e;
					VersionedLocal src = (VersionedLocal) varExpr.getLocal();
					srcLocals.add(src);
				}
				srcLocals.remove(l);
				
				Set<Type> srcTypes = new HashSet<>();
				for(VersionedLocal src : srcLocals) {
					if(!hasComputedType(src)) {
						worklist.addAll(srcLocals);
						continue worklistLoop;
					} else {
						srcTypes.add(results.get(src).type);
					}
				}
				
				LocalType lub = computeLeastUpperBound(srcTypes);
				results.put(l, lub);
				
			} else if(copyStmt.getOpcode() == Opcode.LOCAL_STORE) {
				if(hasComputedType(l)) {
					results.put(l, results.get(l));
				} else {
					if(argTypes.containsKey(l)) {
						results.put(l, new LocalType(argTypes.get(l)));
					} else {
						Expr rhs = copyStmt.getExpression();
						LocalType computedType = computeStaticType(rhs);
						
						if(computedType.isTop) {
							worklist.add(l);
						} else {
							results.put(l, computedType);
						}
					}
				}
			} else {
				throw new IllegalStateException(String.format("unknown copystmt format: %s", copyStmt));
			}
		}*/
		
		System.out.println(worklist);
	}
	
	/*private LocalType computeStaticType(Expr e) {
		int opcode = e.getOpcode();
		
		switch(opcode) {
			case CONST_LOAD: {
				ConstantExpr constExpr = (ConstantExpr) e;
				return new LocalType(constExpr.getType());
			}
			case FIELD_LOAD: {
				FieldLoadExpr fieldLoadExpr = (FieldLoadExpr) e;
				return new LocalType(Type.getType(fieldLoadExpr.getDesc()));
			}
			case ARRAY_LOAD: {
				ArrayLoadExpr arrayLoadExpr = (ArrayLoadExpr) e;
				LocalType arrayType = computeStaticType(arrayLoadExpr.getArrayExpression());
				System.out.println("TODO: " + arrayLoadExpr);
				return arrayType;
			}
			case LOCAL_LOAD: {
				VarExpr varExpr = (VarExpr) e;
				VersionedLocal vl = (VersionedLocal) varExpr.getLocal();
				
				if(hasComputedType(vl)) {
					return results.get(vl);
				} else {
					return LocalType.TOP;
				}
			}
			case INVOKE:
			case DYNAMIC_INVOKE:
			case INIT_OBJ: {
				Invocation invocation = (Invocation) e;
				Type returnType = Type.getReturnType(invocation.getDesc());
				if(returnType.getSort() == Type.VOID) {
					return LocalType.BOTTOM;
				} else {
					return new LocalType(returnType);
				}
			}
			case ARITHMETIC: {
				ArithmeticExpr arithmeticExpr = (ArithmeticExpr) e;
				LocalType left = computeStaticType(arithmeticExpr.getLeft());
				LocalType right = computeStaticType(arithmeticExpr.getRight());
				
				if(left.isBottom || right.isBottom) {
					return LocalType.BOTTOM;
				} else if(left.isTop || right.isTop) {
					return LocalType.TOP;
				} else {
					return new LocalType(ArithmeticExpr.getType(left.type, right.type, arithmeticExpr.getOperator()));
				}
			}
			case NEGATE: {
				NegationExpr negationExpr = (NegationExpr) e;
				return computeStaticType(negationExpr.getExpression());
			}
			case NEW_ARRAY: {
				NewArrayExpr newArrayExpr = (NewArrayExpr) e;
				System.out.println("NAE: " + newArrayExpr);
				return new LocalType(newArrayExpr.getType());
			}
			case ARRAY_LEN: 
			case COMPARE: 
			case ALLOC_OBJ: 
			case CAST: 
			case CATCH: {
				return new LocalType(e.getType());
			}
			default: {
				throw new UnsupportedOperationException(String.format("Unknown expr, type=%s", Opcode.opname(opcode)));
			}
		}
	}*/
	
	private LocalType computeLeastUpperBound(Set<Type> types) {
		if(types.size() == 0) {
			throw new IllegalStateException();
		}
		
		Type lub = null;
		
		for(Type t : types) {
			if(lub != null) {
				boolean prim1 = isPrimitive(t);
				boolean prim2 = isPrimitive(lub);
				
				/* primitive and reference slot at the same
				 * time, problem. */
				if(!(prim1 ^ prim2)) {
					return LocalType.BOTTOM;
				} else {
					if(prim1) {
						/* both prim */
						if(!t.equals(lub)) {
							return LocalType.BOTTOM;
						}
					} else {
						System.out.println(t);
						System.out.println(lub);
					}
				}
			} else {
				lub = t;
			}
		}

		return new LocalType(lub);
	}
	
	private String getCommonSuperType(String type1, String type2) {
    	ClassNode ccn = source.findClassNode(type1);
    	ClassNode dcn = source.findClassNode(type2);
    	
    	if(ccn == null) {
    		ClassNode c = ClassHelper.create(type1);
    		if(c == null) {
    			return "java/lang/Object";
    		}
    		throw new UnsupportedOperationException(c.toString());
    	}
    	
    	if(dcn == null) {
    		ClassNode c = ClassHelper.create(type2);
    		if(c == null) {
    			return "java/lang/Object";
    		}
    		throw new UnsupportedOperationException(c.toString());
    	}
    	
    	ClassTree tree = source.getClassTree();
    	Collection<ClassNode> c = tree.getAllParents(ccn);
    	Collection<ClassNode> d = tree.getAllParents(dcn);
        
        if(c.contains(dcn))
        	return type1;
        
        if(d.contains(ccn))
        	return type2;
        
        if(Modifier.isInterface(ccn.access) || Modifier.isInterface(dcn.access)) {
        	return "java/lang/Object";
        } else {
        	do {
        		ClassNode nccn = source.findClassNode(ccn.superName);
        		if(nccn == null)
        			break;
        		ccn = nccn;
        		c = tree.getAllParents(ccn);
        	} while(!c.contains(dcn));
        	return ccn.name;
        }
    }
	
	private static boolean isPrimitive(Type t) {
		int sort = t.getSort();
		return sort != Type.OBJECT && sort != Type.ARRAY;
	}
	
	private static class LocalType {
		static final LocalType TOP = new LocalType(true, false);
		static final LocalType BOTTOM = new LocalType(false, true);
		
		private boolean isTop;
		private boolean isBottom;
		private Type type;
		
		private LocalType(boolean isTop, boolean isBottom) {
			this.isTop = isTop;
			this.isBottom = isBottom;
			
			if(isTop == isBottom) {
				throw new IllegalStateException();
			}
		}
		
		public LocalType(Type type) {
			this.type = type;
			isTop = false;
			isBottom = false;
		}
		
		@Override
		public String toString() {
			return "LocalType [isTop=" + isTop + ", isBottom=" + isBottom + ", type=" + type + "]";
		}
	}
}