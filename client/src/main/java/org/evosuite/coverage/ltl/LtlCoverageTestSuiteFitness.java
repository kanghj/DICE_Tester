package org.evosuite.coverage.ltl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.testcase.ExecutableChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;

public class LtlCoverageTestSuiteFitness  extends TestSuiteFitnessFunction {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4153155606949108943L;
	private final Set<LtlCoverageTestFitness> goals = new HashSet<>();

	
	public LtlCoverageTestSuiteFitness() {
	    goals.addAll(new LtlCoverageFactory().getCoverageGoals());
	}
	
	@Override
	public double getFitness(AbstractTestSuiteChromosome<? extends ExecutableChromosome> suite) {
	    double fitness = 0.0;

	    List<ExecutionResult> results = runTestSuite(suite);
	    Set<LtlCoverageTestFitness> coveredGoals = new HashSet<>();
	    for(LtlCoverageTestFitness goal : goals) {
	        for(ExecutionResult result : results) {
	            if(goal.isCovered(result)) {
	            	coveredGoals.add(goal);
	                break;
	            }
	        }
	    }
	    fitness = goals.size() - coveredGoals.size();   

	    for (ExecutionResult result : results) {
			if (result.hasTimeout() || result.hasTestException()) {
				fitness = goals.size();
				break;
			}
		}

	    updateIndividual(this, suite, fitness);
	    suite.setNumOfCoveredGoals(this, coveredGoals.size());        
	    if (!goals.isEmpty())
	        suite.setCoverage(this, (double) coveredGoals.size() / (double) goals.size());
	    else
	        suite.setCoverage(this, 1.0);           
	    return fitness;
	}

}
