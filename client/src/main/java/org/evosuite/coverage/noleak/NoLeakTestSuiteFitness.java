package org.evosuite.coverage.noleak;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.coverage.ltl.LtlCoverageFactory;
import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.testcase.ExecutableChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;

public class NoLeakTestSuiteFitness  extends TestSuiteFitnessFunction {

	
	public NoLeakTestSuiteFitness() {
	}
	
	@Override
	public double getFitness(AbstractTestSuiteChromosome<? extends ExecutableChromosome> suite) {
		boolean isCovered = false;
	    

	    
	    NoLeakTestFitness fitnessTester = new NoLeakFactory().getCoverageGoals().get(0);
	    List<ExecutionResult> results = runTestSuite(suite);
	
	    
	    for(ExecutionResult result : results) {
            if (fitnessTester.isCovered(result)) {
            	isCovered = true;
            }
        }

	    for (ExecutionResult result : results) {
			if (result.hasTimeout() || result.hasTestException()) {
				isCovered = false;
				break;
			}
		}

	    if (isCovered) {
		    updateIndividual(this, suite, 0.0);
		    suite.setNumOfCoveredGoals(this, 1);        
	  
	        suite.setCoverage(this, 1);
	        return 1.0;
	    } else {
	    	updateIndividual(this, suite, 0.0);
		    suite.setNumOfCoveredGoals(this, 0);        
	  
	        suite.setCoverage(this, 0);
	    	return 0;
	    }
	           
	    
	}
}
