package org.evosuite.coverage.specmining;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.assertion.ArgumentValueTraceEntry;
import org.evosuite.assertion.ArgumentValueTraceObserver;
import org.evosuite.assertion.NullTraceEntry;
import org.evosuite.assertion.NullTraceObserver;
import org.evosuite.assertion.PrimitiveTraceEntry;
import org.evosuite.assertion.PrimitiveTraceObserver;
import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.archive.Archive;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.metaheuristics.SearchListener;
import org.evosuite.runtime.testdata.EvoSuiteLocalAddress;
import org.evosuite.runtime.vfs.VirtualFileSystem;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.UnixOperatingSystemMXBean;

public class LTLFitnessFunctionsAgeingSearchListener implements SearchListener {

	private static Logger logger = LoggerFactory.getLogger(LTLFitnessFunctionsAgeingSearchListener.class);

	public static final boolean isEnabled = false;
	
	@Override
	public void searchStarted(GeneticAlgorithm<?> algorithm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void iteration(GeneticAlgorithm<?> algorithm) {
		Archive<TestFitnessFunction, TestChromosome> archive = Archive.getArchiveInstance();
		
		for (TestFitnessFunction target : archive.getUncoveredTargets()) {
			if (!(target instanceof LtlCoverageTestFitness)) {
				continue;
			}
			
			LtlCoverageTestFitness ltlFitness = (LtlCoverageTestFitness) target;
			
			TestChromosome solution = archive.getSolution(ltlFitness);
			if (solution != null && solution.getFitness(ltlFitness) < 1.0 && ltlFitness.age > -1) { // increase age. Assume that age is set to 0 once it joins the currentGoals
				ltlFitness.age += 1;
			}
			
		}
	}

	@Override
	public void searchFinished(GeneticAlgorithm<?> algorithm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fitnessEvaluation(Chromosome individual) {
		


		
	}

	@Override
	public void modification(Chromosome individual) {
		// TODO Auto-generated method stub
		
	}
	

}
