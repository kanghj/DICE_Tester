package org.evosuite.coverage.methodpair;

import java.util.Set;

import org.evosuite.coverage.specmining.SpecMiningUtils;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.EntityWithParametersStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;

public class MethodPairTestFitness extends TestFitnessFunction {
	private final String className;
    private final String methodName1;
    private final String methodName2;
    	
    public MethodPairTestFitness(String className, String methodName1, String methodName2) {
        this.className = className;
        this.methodName1 = methodName1;
        this.methodName2 = methodName2;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName1() {
        return methodName1;
    }

    public String getMethodName2() {
        return methodName2;
    }
    
    @Override
    public double getFitness(TestChromosome individual, ExecutionResult result) {
        double fitness = 1.0;
        
        Set<Integer> exceptionPositions = result.getPositionsWhereExceptionsWereThrown();           

        boolean haveSeenMethod1 = false;
        boolean haveSeenMethod1RightBefore = false;

        int distance = 0;
        for (Statement stmt : result.test) {
            if ((stmt instanceof MethodStatement || stmt instanceof ConstructorStatement)) {
                EntityWithParametersStatement ps = (EntityWithParametersStatement)stmt;
                String className  = ps.getDeclaringClassName();
//                String methodName = ps.getMethodName() + ps.getDescriptor();
                
                String methodName = SpecMiningUtils.methodName(result, stmt, hasExceptionAtLine(exceptionPositions, stmt),
                				result.getExceptionThrownAtPosition(stmt.getPosition()) != null ?
                				result.getExceptionThrownAtPosition(stmt.getPosition()).getClass().getSimpleName() :
                				null,
                		result.test);
                if (methodName == null) {
                	continue;
                }

                if(haveSeenMethod1) {
                    if (this.className.equals(className) && this.methodName2.equals(methodName)) {
                    	if (haveSeenMethod1RightBefore ) {
	                        fitness = 0.0;
	                        break;
                    	} else {
                    		fitness = Math.min(0.1 * distance, 1.0);
                    	}
                    } 
                	// reset! Because we are interested in consecutive invocations!
                    haveSeenMethod1RightBefore = false;
                    distance += 1;
                    
                }  
                if (this.className.equals(className) && this.methodName1.equals(methodName)) {
                    haveSeenMethod1 = true;
                    haveSeenMethod1RightBefore = true;
                 
                } 
            }
            if (hasExceptionAtLine(exceptionPositions, stmt))
                break;
        }
        updateIndividual(this, individual, fitness);
        return fitness;

    }

	private boolean hasExceptionAtLine(Set<Integer> exceptionPositions, Statement stmt) {
		return exceptionPositions.contains(stmt.getPosition());
	}
    
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodPairTestFitness that = (MethodPairTestFitness) o;

        if (className != null ? !className.equals(that.className) : that.className != null) return false;
        if (methodName1 != null ? !methodName1.equals(that.methodName1) : that.methodName1 != null) return false;
        return methodName2 != null ? methodName2.equals(that.methodName2) : that.methodName2 == null;

    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (methodName1 != null ? methodName1.hashCode() : 0);
        result = 31 * result + (methodName2 != null ? methodName2.hashCode() : 0);
        return result;
    }
    
    @Override
    public int compareTo(TestFitnessFunction other) {
        if (other instanceof MethodPairTestFitness) {
            MethodPairTestFitness otherMethodFitness = (MethodPairTestFitness) other;
            if (className.equals(otherMethodFitness.getClassName())) {
                if(methodName1.equals(otherMethodFitness.getMethodName1()))
                   return methodName2.compareTo(otherMethodFitness.getMethodName2());
                else
                    return methodName1.compareTo(otherMethodFitness.getMethodName1());
            }
            else
                return className.compareTo(otherMethodFitness.getClassName());
        }
        return compareClassName(other);
    }

	@Override
	public String getTargetClass() {
		return methodName1;
	}

	@Override
	public String getTargetMethod() {
		return methodName1;
	}
	
	@Override
	public String toString() {
		return methodName1 + "," + methodName2;
		
	}
    
}
