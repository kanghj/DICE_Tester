package org.evosuite.coverage.methodpair;

import org.evosuite.EvoSuite;
import org.evosuite.Properties;
import org.evosuite.SystemTestBase;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.strategy.TestGenerationStrategy;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.junit.Assert;
import org.junit.Test;

import com.examples.with.different.packagename.ExampleObserverClass;
import com.examples.with.different.packagename.SingleMethod;

public class MethodPairCoverageSystemTest extends SystemTestBase {

	@Test
	public void testMethodFitnessSimpleExample() {
	    EvoSuite evosuite = new EvoSuite();

	    String targetClass = SingleMethod.class.getCanonicalName();
	    Properties.TARGET_CLASS = targetClass; 
	    Properties.CRITERION = new Properties.Criterion[]{Properties.Criterion.METHODPAIR};
	    String[] command = new String[] { "-generateSuite", "-class", targetClass };
	    Object result = evosuite.parseCommandLine(command);
	    GeneticAlgorithm<?> ga = getGAFromResult(result);
	    TestSuiteChromosome best = (TestSuiteChromosome) ga.getBestIndividual();        
	    
	    System.out.println("EvolvedTestSuite:\n" + best);
	    for(TestFitnessFunction goal : TestGenerationStrategy.getFitnessFactories().get(0).getCoverageGoals()) {
	        System.out.println("Goal: "+goal);
	    }
	    
	    int goals = TestGenerationStrategy.getFitnessFactories().get(0).getCoverageGoals().size(); // assuming single fitness function
	    Assert.assertEquals(2, goals );
	    Assert.assertEquals("Non-optimal coverage: ", 1d, best.getCoverage(), 0.001);
	    
	}
	
//	@Test
//	public void testMethodPairFitness() {
//	    EvoSuite evosuite = new EvoSuite();
//
//	    String targetClass = ExampleObserverClass.class.getCanonicalName();
//	    Properties.TARGET_CLASS = targetClass;
//	    Properties.CRITERION = new Properties.Criterion[]{Properties.Criterion.METHODPAIR};
//
//	    String[] command = new String[] { "-generateSuite", "-class", targetClass };
//	    Object result = evosuite.parseCommandLine(command);
//	    GeneticAlgorithm<?> ga = getGAFromResult(result);
//	    TestSuiteChromosome best = (TestSuiteChromosome) ga.getBestIndividual();
//
//	    System.out.println("EvolvedTestSuite:\n" + best);
//	    for(TestFitnessFunction goal : TestGenerationStrategy.getFitnessFactories().get(0).getCoverageGoals()) {
//	        System.out.println("Goal: "+goal);
//	    }
//	    int goals = TestGenerationStrategy.getFitnessFactories().get(0).getCoverageGoals().size(); // assuming single fitness function
//	    Assert.assertEquals(6, goals );
//	    Assert.assertEquals("Non-optimal coverage: ", 1d, best.getCoverage(), 0.001);
//	}
}
