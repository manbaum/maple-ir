package org.mapleir.ir;

import org.mapleir.ir.analysis.DominanceLivenessAnalyser;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.BoissinotDestructor;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.SreedharDestructor;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.stdlib.collections.NodeTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

public class Benchmark {
	public static void main(String[] args) throws IOException {
		HashMap<String, List<MethodNode>> tests = new LinkedHashMap<>();
//		File testDir = new File("res/specjvm2008");
//		for (File testFile : testDir.listFiles()) {
//			if (testFile.isDirectory())
//				tests.put(testFile.getName(), getMethods(testFile.listFiles()));
//			else
//				tests.put(testFile.getName(), getMethods(testFile));
//		}
//
//		ClassReader cr = new ClassReader(Test.class.getCanonicalName());
//		ClassNode cn = new ClassNode();
//		cr.accept(cn, 0);
//		for (MethodNode m : cn.methods) {
//			if (m.name.startsWith("test")) {
//				List<MethodNode> methods = new ArrayList<>();
//				methods.add(m);
//				tests.put(m.name, methods);
//			}
//		}

		tests.put("fernflower", getMethods(new JarInfo(new File("res/fernflower.jar"))));

		benchmark(tests);
	}

	private static HashMap<String, Long> results = new LinkedHashMap<>();
	private static void benchCopies(HashMap<String, List<MethodNode>> tests) throws IOException {
		for (Entry<String, List<MethodNode>> test : tests.entrySet()) {
			results.clear();
			for (MethodNode m : test.getValue()) {
				try {
					ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
					new SreedharDestructor(cfg);
					recordCopies(cfg, "Sreedhar3");

					cfg = ControlFlowGraphBuilder.build(m);
					DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0000);
					recordCopies(cfg, "Boissinot");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0001);
					recordCopies(cfg, "BValue");

					cfg = ControlFlowGraphBuilder.build(m);
					resolver = new DominanceLivenessAnalyser(cfg, null);
					new BoissinotDestructor(cfg, resolver, 0b0011);
					recordCopies(cfg, "BSharing");
				} catch (RuntimeException e) {
					System.err.println(test.getKey());
					System.err.println(m.toString());
					throw new RuntimeException(e);
				}
			}
			printResults(test.getKey());
		}
		printResultsHeader();
	}

	private static void recordCopies(ControlFlowGraph cfg, String key) {
		results.put(key, results.getOrDefault(key, 0L) + countCopies(cfg));
	}

	private static void benchmark(HashMap<String, List<MethodNode>> tests) throws IOException {
		final int NUM_ITER = 100;
//		System.in.read();

		for (Entry<String, List<MethodNode>> test : tests.entrySet()) {
			results.clear();
			int k = 0;
			for (MethodNode m : test.getValue()) {
				k++;
				if (k < 1500)
					continue;
				System.out.println("  " + m.toString() + " (" + k + " / " + test.getValue().size() + ")");
				try {
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						time();
						new SreedharDestructor(cfg);
						time("Sreedhar3");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0000);
						time("Boissinot");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0001);
						time("BValue");
					}
					
					for (int i = 0; i < NUM_ITER; i++) {
						ControlFlowGraph cfg = ControlFlowGraphBuilder.build(m);
						DominanceLivenessAnalyser resolver = new DominanceLivenessAnalyser(cfg, null);
						time();
						new BoissinotDestructor(cfg, resolver, 0b0011);
						time("BSharing");
					}
				} catch (UnsupportedOperationException e) {
					System.err.println(e.getMessage());
				} catch (RuntimeException e) {
					throw new RuntimeException(e);
				}
			}
			printResults(test.getKey());
		}
		printResultsHeader();
	}

	private static void printResultsHeader() {
		System.out.print("testcase,");
		for (Iterator<String> iterator = results.keySet().iterator(); iterator.hasNext();) {
			System.out.print(iterator.next());
			if (iterator.hasNext())
				System.out.print(",");
		}
		System.out.println();
	}

	private static void printResults(String testName) {
		System.out.print(testName + ",");
		for (Iterator<Long> iterator = results.values().iterator(); iterator.hasNext();) {
			System.out.print(iterator.next());
			if (iterator.hasNext())
				System.out.print(",");
		}
		System.out.println();
	}

	private static long now = -1L;

	private static void time() {
		if (now != -1L)
			throw new IllegalStateException();
		now = System.nanoTime();
	}

	private static void time(String key) {
		long elapsed = System.nanoTime() - now;
		if (now == -1L)
			throw new IllegalStateException();
		results.put(key, results.getOrDefault(key, 0L) + elapsed);
		now = -1L;
	}

	private static List<MethodNode> getMethods(File[] files) throws IOException {
		List<MethodNode> methods = new ArrayList<>();
		for (File f : files)
			for (MethodNode m : getMethods(f))
				methods.add(m);
		return methods;
	}

	private static List<MethodNode> getMethods(File f) throws IOException {
		InputStream is = new FileInputStream(f);
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		return cn.methods;
	}

	private static List<MethodNode> getMethods(JarInfo jar) throws IOException {
		List<MethodNode> methods = new ArrayList<>();
		NodeTable<ClassNode> nt = new NodeTable<>();
		SingleJarDownloader<ClassNode> dl = new SingleJarDownloader<>(jar);
		dl.download();
		nt.putAll(dl.getJarContents().getClassContents().namedMap());
		for (ClassNode cn : nt)
			methods.addAll(cn.methods);
		return methods;
	}


	private static int countCopies(ControlFlowGraph cfg) {
//		System.out.println(cfg);
		int count = 0;
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b) {
				if (stmt instanceof AbstractCopyStatement) {
					AbstractCopyStatement copy = (AbstractCopyStatement) stmt;
					if (!copy.isSynthetic())
						count++;
				}
			}
		}
		return count;
	}
}