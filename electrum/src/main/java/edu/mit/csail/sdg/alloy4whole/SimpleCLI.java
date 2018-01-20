/* Alloy Analyzer 4 -- Copyright (c) 2006-2009, Felix Chang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package edu.mit.csail.sdg.alloy4whole;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.OurDialog;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;

public final class SimpleCLI {

    private static final class SimpleReporter extends A4Reporter {
        private Logger LOGGER = LoggerFactory.getLogger(A4Reporter.class);

        public SimpleReporter() throws IOException { }

        @Override public void debug(String msg) { 
        		if (System.getProperty("debug","no").equals("yes"))
        			LOGGER.debug(msg); 
        	}

        @Override public void parse(String msg) { debug(msg); }

        @Override public void typecheck(String msg) { debug(msg); }

        public void info(String msg) { LOGGER.info(msg); }

        @Override public void warning(ErrorWarning msg) { LOGGER.warn(msg.msg); }

        @Override public void scope(String msg) { debug(msg); }

        @Override public void bound(String msg) { debug(msg); }

        @Override public void translate(String solver, int bitwidth, int maxseq, int skolemDepth, int symmetry) {
        		debug("Solver="+solver+" Bitwidth="+bitwidth+" MaxSeq="+maxseq+" Symmetry="+(symmetry>0 ? (""+symmetry) : "OFF")+"\n");
        }

        @Override public void solve(int primaryVars, int totalVars, int clauses) {
            debug(totalVars+" vars. "+primaryVars+" primary vars. "+clauses+" clauses.\n");
        }

        @Override public void resultCNF(String filename) {}

        @Override public void resultSAT(Object command, long solvingTime, Object solution) {
            if (!(command instanceof Command)) return;
            Command cmd = (Command)command;
            StringBuilder sb = new StringBuilder();
            sb.append(cmd.check ? "   Counterexample found. " : "   Instance found. ");
            if (cmd.check) sb.append("Assertion is invalid"); else sb.append("Predicate is consistent");
            if (cmd.expects==0) sb.append(", contrary to expectation"); else if (cmd.expects==1) sb.append(", as expected");
            sb.append(". "+solvingTime+"ms.\n\n");
            info(sb.toString());
        }

        @Override public void resultUNSAT(Object command, long solvingTime, Object solution) {
            if (!(command instanceof Command)) return;
            Command cmd = (Command)command;
            StringBuilder sb = new StringBuilder();
            sb.append(cmd.check ? "   No counterexample found." : "   No instance found.");
            if (cmd.check) sb.append(" Assertion may be valid"); else sb.append(" Predicate may be inconsistent");
            if (cmd.expects==1) sb.append(", contrary to expectation"); else if (cmd.expects==0) sb.append(", as expected");
            sb.append(". "+solvingTime+"ms.\n\n");
            info(sb.toString());
        }
    }

    private SimpleCLI() { }

    private static Options options() {
    		Options options = new Options();

    		options.addOption(Option.builder("d")
    				.longOpt("decomposed")
    				.hasArg(true)
    				.argName("threads")
    				.optionalArg(true)
    				.required(false)
    				.desc("run in decomposed mode").build());
    		
    		options.addOption(Option.builder("c")
    				.longOpt("command")
    				.hasArg(true)
    				.argName("command")
    				.optionalArg(false)
    				.required(false)
    				.desc("select command").build());
    		
    		options.addOption(Option.builder("v")
    				.longOpt("verbose")
    				.hasArg(false)
    				.required(false)
    				.desc("run in debug mode").build());
    		
    		OptionGroup g = new OptionGroup();
    		g.addOption(Option.builder("x").longOpt("nuXmv").hasArg(false).desc("select nuXmv unbounded solver").build());
    		g.addOption(Option.builder("m").longOpt("miniSAT").hasArg(false).desc("select miniSAT bounded solver").build());
    		g.addOption(Option.builder("g").longOpt("glucose").hasArg(false).desc("select glucose unbounded solver").build());
    		g.addOption(Option.builder("n").longOpt("NuSMV").hasArg(false).desc("select NuSMV unbounded solver").build());
    		g.addOption(Option.builder("s").longOpt("SAT4J").hasArg(false).desc("select SAT4J bounded solver").build());
    		g.setRequired(true);
    		
    		options.addOptionGroup(g);

    		return options;
    }
    
    public static void main(String[] args) throws Exception {
    		CommandLine cmd = null;	
    		try {
    			CommandLineParser parser = new DefaultParser();
    			cmd = parser.parse(options(), args, true);
    		} catch(ParseException exp) {
    	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
    	        HelpFormatter formatter = new HelpFormatter();
    	        formatter.printHelp("electrum [options] [FILE]",options());
    	        return;
    	    }
    	
    		if (cmd.hasOption("v")) System.setProperty("debug","yes");

    		copyFromJAR();
        final String binary = alloyHome() + fs + "binary";
        try {
            System.setProperty("java.library.path", binary);
            // The above line is actually useless on Sun JDK/JRE (see Sun's bug ID 4280189)
            // The following 4 lines should work for Sun's JDK/JRE (though they probably won't work for others)
            String[] newarray = new String[]{binary};
            java.lang.reflect.Field old = ClassLoader.class.getDeclaredField("usr_paths");
            old.setAccessible(true);
            old.set(null,newarray);
        } catch (Throwable ex) { }
        
		final SimpleReporter rep = new SimpleReporter();
		String filename = args[args.length - 1];
		try {
			rep.info("Parsing " + filename + ".\n");
			Module world = CompUtil.parseEverything_fromFile(rep, null, filename);
			List<Command> cmds = world.getAllCommands();
			A4Options options = new A4Options();
			options.originalFilename = filename;
			options.solver = A4Options.SatSolver.MiniSatJNI;
			if (cmd.hasOption("SAT4J"))
				options.solver = A4Options.SatSolver.SAT4J;
			else if (cmd.hasOption("glucose"))
				options.solver = A4Options.SatSolver.GlucoseJNI;
			else if (cmd.hasOption("NuSMV"))
				options.solver = A4Options.SatSolver.ElectrodS;
			else if (cmd.hasOption("NuSMV"))
				options.solver = A4Options.SatSolver.ElectrodS;
			else if (cmd.hasOption("nuXmv"))
				options.solver = A4Options.SatSolver.ElectrodX;

			if (cmd.hasOption("decomposed"))
				if (cmd.getOptionValue("decomposed") != null)
					options.decomposed = Integer.valueOf(cmd.getOptionValue("decomposed"));
				else
					options.decomposed = 1;
			else
				options.decomposed = 0;
			int i0=0, i1=cmds.size();
			if (cmd.hasOption("command")) {
				i0 = Integer.valueOf(cmd.getOptionValue("command"));
				i1 = i0+1;
			} else {
				rep.info("Running all commands.");
			}
			for (int i = i0; i < i1; i++) {
				Command c = cmds.get(i);
				rep.info("Executing \"" + c + "\"\n");
				options.skolemDepth = 2;
				A4Solution s = TranslateAlloyToKodkod.execute_commandFromBook(rep, world.getAllReachableSigs(), c,
						options);
				System.exit(0);
			}
		} catch (Throwable ex) {
			rep.info("An error occurred.");
			rep.debug("\n\nException: " + ex);
		}
	}
    
    /** The system-specific file separator (forward-slash on UNIX, back-slash on Windows, etc.) */
    private static final String fs = System.getProperty("file.separator");

    /** Copy the required files from the JAR into a temporary directory. */
    private static void copyFromJAR() {
        final String platformBinary = alloyHome() + fs + "binary";
        // Write a few test files
        try {
            (new File(platformBinary)).mkdirs();
            Util.writeAll(platformBinary + fs + "tmp.cnf", "p cnf 3 1\n1 0\n");
        } catch(Err er) {
            // The error will be caught later by the "berkmin" or "spear" test
        }
        // Copy the platform-dependent binaries
//        System.out.println("Will copy libs from "+" to "+platformBinary);
        Util.copy(true, false, platformBinary,
           "libminisat.so", "libminisatx1.so", "libminisat.jnilib", "libminisat.dylib", "libglucose.so", "libglucose.dylib", "libglucose.jnilib",
           "libminisatprover.so", "libminisatproverx1.so", "libminisatprover.jnilib", "libminisatprover.dylib",
           "libzchaff.so", "libzchaffx1.so", "libzchaff.jnilib", "libzchaff.dylib",
           "libglucose.dylib", "libglucose.so", "libglucose.jnilib",
           "berkmin", "spear");
        Util.copy(false, false, platformBinary,
           "minisat.dll", "minisatprover.dll", "zchaff.dll",
           "berkmin.exe", "spear.exe");
        // Copy the model files
        Util.copy(false, true, alloyHome(),
           "models/book/appendixA/addressBook1.als", "models/book/appendixA/addressBook2.als", "models/book/appendixA/barbers.als",
           "models/book/appendixA/closure.als", "models/book/appendixA/distribution.als", "models/book/appendixA/phones.als",
           "models/book/appendixA/prison.als", "models/book/appendixA/properties.als", "models/book/appendixA/ring.als",
           "models/book/appendixA/spanning.als", "models/book/appendixA/tree.als", "models/book/appendixA/tube.als", "models/book/appendixA/undirected.als",
           "models/book/appendixE/hotel.thm", "models/book/appendixE/p300-hotel.als", "models/book/appendixE/p303-hotel.als", "models/book/appendixE/p306-hotel.als",
           "models/book/chapter2/addressBook1a.als", "models/book/chapter2/addressBook1b.als", "models/book/chapter2/addressBook1c.als",
           "models/book/chapter2/addressBook1d.als", "models/book/chapter2/addressBook1e.als", "models/book/chapter2/addressBook1f.als",
           "models/book/chapter2/addressBook1g.als", "models/book/chapter2/addressBook1h.als", "models/book/chapter2/addressBook2a.als",
           "models/book/chapter2/addressBook2b.als", "models/book/chapter2/addressBook2c.als", "models/book/chapter2/addressBook2d.als",
           "models/book/chapter2/addressBook2e.als", "models/book/chapter2/addressBook3a.als", "models/book/chapter2/addressBook3b.als",
           "models/book/chapter2/addressBook3c.als", "models/book/chapter2/addressBook3d.als", "models/book/chapter2/theme.thm",
           "models/book/chapter4/filesystem.als", "models/book/chapter4/grandpa1.als",
           "models/book/chapter4/grandpa2.als", "models/book/chapter4/grandpa3.als", "models/book/chapter4/lights.als",
           "models/book/chapter5/addressBook.als", "models/book/chapter5/lists.als", "models/book/chapter5/sets1.als", "models/book/chapter5/sets2.als",
           "models/book/chapter6/hotel.thm", "models/book/chapter6/hotel1.als", "models/book/chapter6/hotel2.als",
           "models/book/chapter6/hotel3.als", "models/book/chapter6/hotel4.als", "models/book/chapter6/mediaAssets.als",
           "models/book/chapter6/memory/abstractMemory.als", "models/book/chapter6/memory/cacheMemory.als",
           "models/book/chapter6/memory/checkCache.als", "models/book/chapter6/memory/checkFixedSize.als",
           "models/book/chapter6/memory/fixedSizeMemory.als", "models/book/chapter6/memory/fixedSizeMemory_H.als",
           "models/book/chapter6/ringElection.thm", "models/book/chapter6/ringElection1.als", "models/book/chapter6/ringElection2.als",
           "models/examples/algorithms/dijkstra.als", "models/examples/algorithms/dijkstra.thm",
           "models/examples/algorithms/messaging.als", "models/examples/algorithms/messaging.thm",
           "models/examples/algorithms/opt_spantree.als", "models/examples/algorithms/opt_spantree.thm",
           "models/examples/algorithms/peterson.als",
           "models/examples/algorithms/ringlead.als", "models/examples/algorithms/ringlead.thm",
           "models/examples/algorithms/s_ringlead.als",
           "models/examples/algorithms/stable_mutex_ring.als", "models/examples/algorithms/stable_mutex_ring.thm",
           "models/examples/algorithms/stable_orient_ring.als", "models/examples/algorithms/stable_orient_ring.thm",
           "models/examples/algorithms/stable_ringlead.als", "models/examples/algorithms/stable_ringlead.thm",
           "models/examples/case_studies/INSLabel.als", "models/examples/case_studies/chord.als",
           "models/examples/case_studies/chord2.als", "models/examples/case_studies/chordbugmodel.als",
           "models/examples/case_studies/com.als", "models/examples/case_studies/firewire.als", "models/examples/case_studies/firewire.thm",
           "models/examples/case_studies/ins.als", "models/examples/case_studies/iolus.als",
           "models/examples/case_studies/sync.als", "models/examples/case_studies/syncimpl.als",
           "models/examples/puzzles/farmer.als", "models/examples/puzzles/farmer.thm",
           "models/examples/puzzles/handshake.als", "models/examples/puzzles/handshake.thm",
           "models/examples/puzzles/hanoi.als", "models/examples/puzzles/hanoi.thm",
           "models/examples/systems/file_system.als", "models/examples/systems/file_system.thm",
           "models/examples/systems/javatypes_soundness.als",
           "models/examples/systems/lists.als", "models/examples/systems/lists.thm",
           "models/examples/systems/marksweepgc.als", "models/examples/systems/views.als",
           "models/examples/toys/birthday.als", "models/examples/toys/birthday.thm",
           "models/examples/toys/ceilingsAndFloors.als", "models/examples/toys/ceilingsAndFloors.thm",
           "models/examples/toys/genealogy.als", "models/examples/toys/genealogy.thm",
           "models/examples/toys/grandpa.als", "models/examples/toys/grandpa.thm",
           "models/examples/toys/javatypes.als", "models/examples/toys/life.als", "models/examples/toys/life.thm",
           "models/examples/toys/numbering.als", "models/examples/toys/railway.als", "models/examples/toys/railway.thm",
           "models/examples/toys/trivial.als",
           "models/examples/tutorial/farmer.als",
           "models/util/boolean.als", "models/util/graph.als", "models/util/integer.als", "models/util/natural.als",
           "models/util/ordering.als", "models/util/relation.als", "models/util/seqrel.als", "models/util/sequence.als",
           "models/util/sequniv.als", "models/util/ternary.als", "models/util/time.als", "models/Temporal_Examples/firewire.ele", // pt.uminho.haslab
                "models/Temporal_Examples/hotel.ele","models/Temporal_Examples/lift_spl.ele","models/Temporal_Examples/ring.ele", // pt.uminho.haslab
                "models/Temporal_Examples/span_tree.ele", "models/Temporal_Examples/ex1.ele" // pt.uminho.haslab
           );
        // Record the locations
        System.setProperty("alloy.theme0", alloyHome() + fs + "models");
        System.setProperty("alloy.home", alloyHome());
//        System.setProperty("debug", "no"); // [HASLab]: this breaks iteration!
    }
    
    private static synchronized String alloyHome() {
        String temp=System.getProperty("java.io.tmpdir");
        if (temp==null || temp.length()==0)
            OurDialog.fatal("Error. JVM need to specify a temporary directory using java.io.tmpdir property.");
        String username=System.getProperty("user.name");
        File tempfile=new File(temp+File.separatorChar+"alloy4tmp40-"+(username==null?"":username));
        tempfile.mkdirs();
        String ans=Util.canon(tempfile.getPath());
        if (!tempfile.isDirectory()) {
            OurDialog.fatal("Error. Cannot create the temporary directory "+ans);
        }
        if (!Util.onWindows()) {
            String[] args={"chmod", "700", ans};
            try {Runtime.getRuntime().exec(args).waitFor();}
            catch (Throwable ex) {} // We only intend to make a best effort.
        }
        return ans;
    }
}
