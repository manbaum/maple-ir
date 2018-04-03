package org.mapleir.ir.antlr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.DefaultInvocationResolver;
import org.mapleir.app.service.InstalledRuntimeClassSource;
import org.mapleir.app.service.LibraryClassSource;
import org.mapleir.ir.antlr.error.CompilationException;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.impl.VersionedLocal;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.topdank.byteengineer.commons.data.JarInfo;
import org.topdank.byteio.in.SingleJarDownloader;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class AntlrBoot {

	public static void main(String[] args) {		
		VarExpr v = new VarExpr(new VersionedLocal(new AtomicInteger(0), 1, 0), Type.INT_TYPE);
		CopyPhiStmt copy = new CopyPhiStmt(v, new PhiExpr(new HashMap<>()));
		System.out.println(copy);
		
		try {
			InputStream testInputStream = AntlrBoot.class.getResourceAsStream("/sample.txt");
			ANTLRInputStream input = new ANTLRInputStream(testInputStream);

			mapleirLexer lexer = new mapleirLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			mapleirParser parser = new mapleirParser(tokens);

			// Start parsing
//			parser.program();
//			System.out.println(parser.classType().getText());
			
			JFrame frame = new JFrame("Antlr AST");
	        JPanel panel = new JPanel();
	        
	        File rtjar = new File("../org.mapleir.main/res/rt.jar");
	        SingleJarDownloader<ClassNode> downloader = new SingleJarDownloader<>(new JarInfo(rtjar));
	        downloader.download();
	        
	        ApplicationClassSource classPath = new ApplicationClassSource("test-cp", Collections.emptyMap());
	        classPath.addLibraries(new LibraryClassSource(classPath, downloader.getJarContents().getClassContents()),
	        		new InstalledRuntimeClassSource(classPath));
	        
	        CompilationDriver driver = new CompilationDriver(classPath, new DefaultInvocationResolver(classPath));
	        try {
	        	driver.process(parser);
	        } catch(CompilationException e) {
	        	e.printStackTrace();
	        }
	        
//	        System.out.println("comments: ");
//	        for(Token t : tokens.getTokens()) {
//	        	if(t.getChannel() == 2) {
////	        		System.out.println("c: " +t.getText());
//	        	} else {
//	        		List<Token> comments = tokens.getHiddenTokensToLeft(t.getTokenIndex());
//	        		
//	        		if(comments != null && !comments.isEmpty()) {
//	        			System.out.println("comments with: " + t.getText());
//	        			for(Token c : comments) {
//	        				System.out.println("c: " + c.getText());
//	        			}
//	        		}
//	        	}
////	        	System.out.println(t);
//	        }
	        
	        TreeViewer viewr = new TreeViewer(Arrays.asList(
	                parser.getRuleNames()), driver.unit);
	        viewr.setScale(1.3);//scale a little
	        panel.add(viewr);
	        frame.add(panel);
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.setSize(800, 600);
	        frame.setLocationRelativeTo(null);
	        frame.setVisible(true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}