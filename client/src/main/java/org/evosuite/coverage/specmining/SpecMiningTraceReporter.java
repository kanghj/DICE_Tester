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
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.metaheuristics.SearchListener;
import org.evosuite.runtime.testdata.EvoSuiteLocalAddress;
import org.evosuite.runtime.vfs.VirtualFileSystem;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.UnixOperatingSystemMXBean;

public class SpecMiningTraceReporter implements SearchListener {

	private static Logger logger = LoggerFactory.getLogger(SpecMiningTraceReporter.class);

	public static final boolean isEnabled = false;
	
	@Override
	public void searchStarted(GeneticAlgorithm<?> algorithm) {		
	}

	@Override
	public void iteration(GeneticAlgorithm<?> algorithm) {
		if (!isEnabled) {
			return;
		}
		
		List<ExecutionResult> results = new ArrayList<>();
		List<Boolean> hasLeaks = new ArrayList<>();
		
		List<TestCase> tests = new ArrayList<>();
		
		for (Chromosome individual: algorithm.getPopulation()) {

			String name = Properties.TARGET_CLASS.substring(Properties.TARGET_CLASS.lastIndexOf(".") + 1);
			String testDir = Properties.TEST_DIR;
			
			if(individual instanceof TestSuiteChromosome) {
				TestSuiteChromosome suite = (TestSuiteChromosome)individual;
				
				
				
	//			logger.warn("VirtualFileSystem.class.getProtectionDomain().getCodeSource().getLocation()");
	//			logger.warn(VirtualFileSystem.class.getProtectionDomain().getCodeSource().getLocation().toString() );
				
				for (TestChromosome testChromosome : suite.getTestChromosomes()) {
					// have to rerun it. The old result may be missing primitivetrace observer
//					PrimitiveTraceObserver primitiveObserver = new PrimitiveTraceObserver();
//					NullTraceObserver nullObserver = new NullTraceObserver();
//					ArgumentValueTraceObserver argsValueObserver = new ArgumentValueTraceObserver();
//					
//					TestCaseExecutor.getInstance().addObserver(primitiveObserver);
//					TestCaseExecutor.getInstance().addObserver(nullObserver);
//					TestCaseExecutor.getInstance().addObserver(argsValueObserver);
//		
	//				int numberOfResourcesNow = VirtualFileSystem.getInstance().getNumberOfLeakingResources();
					
//					ExecutionResult result = TestCaseExecutor.runTest(testChromosome.getTestCase());
//					result.setTrace(primitiveObserver.getTrace(), PrimitiveTraceEntry.class);
//					result.setTrace(nullObserver.getTrace(), NullTraceEntry.class);
//					result.setTrace(argsValueObserver.getTrace(), ArgumentValueTraceEntry.class);
//					
//					
		
					results.add(testChromosome.getLastExecutionResult());
					hasLeaks.add(testChromosome.getLastExecutionResult().hasLeak);
//					
//					TestCaseExecutor.getInstance().removeObserver(primitiveObserver);
//					TestCaseExecutor.getInstance().removeObserver(nullObserver);
//					TestCaseExecutor.getInstance().removeObserver(argsValueObserver);
//			
				}
				
	
			} else if (individual instanceof TestChromosome) {
				PrimitiveTraceObserver primitiveObserver = new PrimitiveTraceObserver();
				NullTraceObserver nullObserver = new NullTraceObserver();
				ArgumentValueTraceObserver argsValueObserver = new ArgumentValueTraceObserver();
//				
//				TestCaseExecutor.getInstance().addObserver(primitiveObserver);
//				TestCaseExecutor.getInstance().addObserver(nullObserver);
//				TestCaseExecutor.getInstance().addObserver(argsValueObserver);
//	
//				int numberOfResourcesNow = VirtualFileSystem.getInstance().getNumberOfLeakingResources();
				ExecutionResult result;
				if (((TestChromosome) individual).getLastExecutionResult() == null) {
					 result = TestCaseExecutor.runTest(((TestChromosome) individual).getTestCase());
				} else {
					result = ((TestChromosome) individual).getLastExecutionResult();
				}
//				result.setTrace(primitiveObserver.getTrace(), PrimitiveTraceEntry.class);
//				result.setTrace(nullObserver.getTrace(), NullTraceEntry.class);
//				result.setTrace(argsValueObserver.getTrace(), ArgumentValueTraceEntry.class);
//				
				tests.add(((TestChromosome) individual).getTestCase());
	
				results.add(result);
				hasLeaks.add(result.hasLeak);
//				
//				TestCaseExecutor.getInstance().removeObserver(primitiveObserver);
//				TestCaseExecutor.getInstance().removeObserver(nullObserver);
//				TestCaseExecutor.getInstance().removeObserver(argsValueObserver);
				
				
			}
			try {
				if (SpecMiningUtils.isFirstRun()) { 
					File directory = new File(testDir);
				    if (! directory.exists()){
				    	directory.mkdir();
				    }
					SpecMiningUtils.writeTracesToFile(tests, results,hasLeaks, testDir + File.separator );
					LoggingUtils.logWarnAtMostOnce(logger, "write to file in fitnessEvaluation");
				}
			} catch (IOException e) {
				logger.error("Error writing traces to file", e);
			}
		}
	}

	@Override
	public void searchFinished(GeneticAlgorithm<?> algorithm) {
		
	}

	@Override
	public void fitnessEvaluation(Chromosome individual) {
		


		
	}

	@Override
	public void modification(Chromosome individual) {
		
	}
	

}
