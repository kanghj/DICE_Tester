package org.evosuite.coverage.noleak;

import java.util.Set;

import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.statements.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoLeakTestFitness extends TestFitnessFunction {
	private static Logger logger = LoggerFactory.getLogger(NoLeakTestFitness.class);
	
	private String targetClass;
	

	public NoLeakTestFitness(String targetClass) {
		super();
		this.targetClass = targetClass;
	}

	@Override
	public double getFitness(TestChromosome individual, ExecutionResult result) {
		
		double fitness;
        if (!result.hasLeak) {
        	fitness = 0; 
        } else {
        	fitness = 1;
        }
        updateIndividual(this, individual, fitness);
        
        return fitness;
	}

	@Override
	public int compareTo(TestFitnessFunction other) {
		return compareClassName(other);
	}

	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((targetClass == null) ? 0 : targetClass.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NoLeakTestFitness other = (NoLeakTestFitness) obj;
		if (targetClass == null) {
			if (other.targetClass != null)
				return false;
		} else if (!targetClass.equals(other.targetClass))
			return false;
		return true;
	}

	@Override
	public String getTargetClass() {
		return targetClass;
	}

	@Override
	public String getTargetMethod() {
		return null;
	}

    
}
