package org.evosuite.coverage.ltl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.evosuite.Properties;
import org.evosuite.assertion.OutputTrace;
import org.evosuite.assertion.OutputTraceEntry;
import org.evosuite.assertion.PrimitiveTraceEntry;
import org.evosuite.coverage.specmining.SpecMiningUtils;
import org.evosuite.ga.archive.Archive;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.LoggingUtils;
import org.objectweb.asm.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.Modifier;

public class LtlCoverageTestFitness extends TestFitnessFunction {

	private static final long serialVersionUID = 7867377725218652621L;

	
    private final Class<?> className;
    private final String start;
    private final String end;
    private final String label;
    
    public int age = -1; // how many generations has this been uncovered for?
    
    private static Map<String, String> properMethodNames = new HashMap<>();
    public static Set<String> covered = new HashSet<>();
    public static int numInstances = 0;
    public static int numRetired = 0;
    
    
    public static Set<String> pureMethods = new HashSet<>();
    
	private static Logger logger = LoggerFactory.getLogger(LtlCoverageTestFitness.class);
	
	public static final boolean allowEvolutionWithoutLTLFitness = false; // if true, we want to find which properties get covered. But we do not let this influence the test evolution

    
	public LtlCoverageTestFitness(Class<?> className, String start, String end, String label) {
		super();
		this.className = className;
		this.start = start;
		this.end = end;
		this.label = label;
		
		numInstances+= 1;
		assert label.equals("NIF") ||  label.equals("NF") || 
				label.equals("AIP") || label.equals("AP") || 
				label.equals("AF") || label.equals("AIF"); 
	}

	public Class<?> getClassName() {
		return className;
	}

	public String getStart() {
		return start;
	}

	public String getEnd() {
		return end;
	}
	
	public String getLabel() {
		return label;
	}
    
    @Override
	public String toString() {
		return "LtlCoverage [className=" + className + ", start=" + start + ", end=" + end + ", label="
				+ label + "]";
	}
    
    public String convenientLTLForm() {
    	return "LTL:" + label + " " + start + " " + end;
    }

	@Override
    public double getFitness(TestChromosome individual, ExecutionResult result) {
        double fitness = 1.0;
        
        if (result.hasTimeout()) {
        	return fitness;
        }
        if (result.hasLeak) {
        	return fitness;
        }
        if (result.hasTestException()) {   
        	return fitness;
        }
                
        Set<Integer> exceptionPositions = result.getPositionsWhereExceptionsWereThrown();
        
        Map<VariableReference, List<String>> traces = new HashMap<>();
        Map<VariableReference, List<String>> tracesDebugInfo = new HashMap<>();
        Map<String, Set<Class<?>>> classOfVar = new HashMap<>();
        
        for (Statement stmt : result.test) {
        	
        	boolean isExceptionThrownByStmt = exceptionPositions.contains(stmt.getPosition());
        	
        	VariableReference objectToTrace = SpecMiningUtils.tracedObject(classOfVar, stmt);
        	
        	String methodName = SpecMiningUtils.methodName(result, stmt, isExceptionThrownByStmt,
        			result.getExceptionThrownAtPosition(stmt.getPosition()) != null ?
        			result.getExceptionThrownAtPosition(stmt.getPosition()).getClass().getSimpleName() :
        				null, 
        			result.test, false);
        	

        	if (isExceptionThrownByStmt) {
                break;
        	}
        	if (objectToTrace == null) {
        		continue;
        	}
        	if (methodName == null || methodName.isEmpty()) {
        		continue;
        	}
        	if (!traces.containsKey(objectToTrace)) {
            	traces.put(objectToTrace, new ArrayList<>());
            	tracesDebugInfo.put(objectToTrace, new ArrayList<>());
        	}
        	if (methodName != null) {
	        	traces.get(objectToTrace).add(methodName);
	        	tracesDebugInfo.get(objectToTrace).add(stmt.getCode() + " @ " + stmt.getPosition());
        	}

        	
        }
        
        for (Entry<VariableReference, List<String>> entry : traces.entrySet()) {
       	 	Set<Class<?>> currentClass = classOfVar.get(entry.getKey().getName());
	       	
	       	if (currentClass == null
	       			|| currentClass.isEmpty()
	       			|| (currentClass.stream().noneMatch(currentClazz -> currentClazz.equals(className) ||  className.isAssignableFrom(currentClazz))))  {
	       		
	       		continue;
	       	}
	       	

	       	float traceValue = 1.0f;
	       	if (this.label.equals("NIF")) {
	       		traceValue = neverFollowed(entry.getValue(), this.start, this.end, true);
	       	} else if (this.label.equals("AIP")) {
	       		traceValue = alwaysPreceded(entry.getValue(), this.start, this.end, true);
	       	} else if (this.label.equals("NF")) {
	       		traceValue = neverFollowed(entry.getValue(), this.start, this.end, false);
	       	} else if (this.label.equals("AP")) {
	       		traceValue = alwaysPreceded(entry.getValue(), this.start, this.end, false);
	       	} else if (this.label.equals("AF")) {
	       		traceValue = alwaysFollowed(entry.getValue(), this.start, this.end, false);
	       	} else if (this.label.equals("AIF")) {
	       		traceValue = alwaysFollowed(entry.getValue(), this.start, this.end, true);
	       	} else {
	       		throw new RuntimeException("unknown label: >" + label + "< ");
	       	}
	       	fitness = Math.min(fitness, traceValue);   	
	       	if (fitness == 0.0 && !covered.contains(this.convenientLTLForm())) {
//	            logger.warn("covering trace of " + this + " is ");
//	            logger.warn("\t variable is " + entry.getKey());
//	            logger.warn("\t " + entry.getValue());
//	            logger.warn("\t debug info is " + tracesDebugInfo.get(entry.getKey()));
//	            logger.warn("exceptional positions" + exceptionPositions);

	            
	            covered.add(this.convenientLTLForm());
	            
	            
	            try {
	            	SpecMiningUtils.writeTracesToFile( entry.getValue(), Properties.getTargetClassAndDontInitialise(), "./");
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("cannot write to traces file", e);
				}
	            break;
	            
	       	}
        }
        
        
        if (allowEvolutionWithoutLTLFitness) {
        	updateIndividual(this, individual, 1.0);
        	return 1.0; // in other words, its never covered from the "fitness" perspective
        }
        
        assert fitness >= 0.0;
        updateIndividual(this, individual, fitness);

        if (fitness == 0.0) {
        	LoggingUtils.logWarnAtMostOnce(logger, "Fitness is 0 has been reached!");
            individual.getTestCase().addCoveredGoal(this);
            
        }

        if (Properties.TEST_ARCHIVE) {
            Archive.getArchiveInstance().updateArchive(this, individual, fitness);
        }

        return fitness;
    	
    }
	
	public float neverFollowed(List<String> events, String eventA, String eventB, boolean immediately) {
		int seenEventAAt = -1;
		int i = 0;
		int counter = 0;
		int distance = 999;
		for (String event : events) {
			if (event.equals(eventA)) {
				seenEventAAt = i; 
				
				counter = 0;
			} else if (seenEventAAt != -1) {
				if (!pureMethods.contains(event)) {
					counter += 1;
				}
			}
			
			if (i != seenEventAAt && event.equals(eventB) && seenEventAAt != -1) {
				distance = Math.min(distance, counter);
			}
			
			i ++;
		}
		
		if (seenEventAAt == -1) {
			if (distance < 999) {
				// have seen event B
				return 0.66f;
			}
			return 1;
		}
		
		if (immediately) {
			return distance <= 1 ? 0 : 0.33f ;
		} else {
			return distance < 999 ? 0 : 0.33f;
		}
	}
	
	public float alwaysPreceded(List<String> events, String eventA, String eventB, boolean immediately) {
		int seenEventAAt = -1;
		int seenEventBAt = -1;
		int distance = 999;
		int counter = 0;
		
		for (int i = events.size() - 1; i >= 0; i--) {
			String event = events.get(i);
			if (event.equals(eventA)) {
				seenEventAAt = i; 
				counter=  0;
			} else if (seenEventAAt != -1) {
				if (!pureMethods.contains(event)) {
					counter += 1;
				}
			}
			
			if (event.equals(eventB)) {
				seenEventBAt = i;
			}
			
			if (seenEventAAt != -1 && event.equals(eventB)) {
				distance = Math.min(distance, counter);
			}
		}
		
		if (seenEventAAt == -1) { // didn't see A
			if (seenEventBAt == -1) {
				return 1; // not a counter-example if we do not see eventA
			} else {
				return 0.66f; 
			}
		} else if (seenEventBAt == -1) {
			return 0.66f;
		}
		
		if (immediately) {
			return distance <= 1? 0.33f : 0;
		} else {
			return distance == 999 ?  0.33f : 0 ;
		}
	}

	
	public float alwaysFollowed(List<String> events, String eventA, String eventB, boolean immediately) {
		int seenEventAAt = -1;
		int seenEventBAt = -1;
		int distance = 999;
		int counter = 0;
		
		for (int i = 0; i < events.size(); i++) {
			String event = events.get(i);
			if (event.equals(eventA)) {
				seenEventAAt = i; 
				counter=  0;
			} else if (seenEventAAt != -1) {
				if (!pureMethods.contains(event)) {
					counter += 1;
				}
			}
			
			if (event.equals(eventB)) {
				seenEventBAt = i;
			}
			
			if (event.equals(eventB) && seenEventAAt != -1) {
				distance = Math.min(distance, counter);
			}
		}
		
		if (seenEventAAt == -1) { // didn't see A
			if (seenEventBAt == -1) {
				return 1; // not a counter-example if we do not see eventA
			} else {
				return 0.66f; 
			}
		} else if (seenEventBAt == -1) {
			return 0.66f;
		}
		
		if (immediately) {
			return distance <= 1 ? 0.33f : 0; // 0.33 if it supports 
		} else {
			return distance < 999 ? 0.33f : 0;
		}
		
	}
	

	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		result = prime * result + ((end == null) ? 0 : end.hashCode());
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime * result + ((start == null) ? 0 : start.hashCode());
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
		LtlCoverageTestFitness other = (LtlCoverageTestFitness) obj;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		if (end == null) {
			if (other.end != null)
				return false;
		} else if (!end.equals(other.end))
			return false;

		if (label == null) {
			if (other.label != null)
				return false;
		} else if (!label.equals(other.label))
			return false;
		if (start == null) {
			if (other.start != null)
				return false;
		} else if (!start.equals(other.start))
			return false;
		return true;
	}

	@Override
	public int compareTo(TestFitnessFunction other) {
		if (other instanceof LtlCoverageTestFitness) {
			LtlCoverageTestFitness otherMethodFitness = (LtlCoverageTestFitness) other;
	        if (className.getCanonicalName().equals(otherMethodFitness.getClassName().getCanonicalName())) {
	            if(start.equals(otherMethodFitness.getStart())) {
	            	if (end.equals(otherMethodFitness.getEnd())) {
	            		return label.compareTo(otherMethodFitness.getLabel());
	            	} else {
	            		return end.compareTo(otherMethodFitness.getEnd());
	            	}
	            } else
	                return start.compareTo(otherMethodFitness.getStart());
	        }
	        else
	            return className.getCanonicalName().compareTo(otherMethodFitness.getClassName().getCanonicalName());
	    }
	    return compareClassName(other);
	}

	@Override
	public String getTargetClass() {
//		return className.getCanonicalName();
		return Properties.TARGET_CLASS;
	}

	@Override
	public String getTargetMethod() {
		
		
		String methodToTarget = start;
		populateProperNames(methodToTarget);
		
		return properMethodNames.containsKey(methodToTarget) ? properMethodNames.get(methodToTarget) : "dummy-method";
	}
	
	public String getSecondaryTargetMethod() {
		if (this.label.charAt(0) != 'N') {
			return null;
		}
		
		String methodToTarget = end;
		populateProperNames(methodToTarget);
		
		return properMethodNames.containsKey(methodToTarget) ? properMethodNames.get(methodToTarget) : "dummy-method";
	}
    

	private void populateProperNames(String methodToTarget) {
		if (!properMethodNames.containsKey(methodToTarget)) {
			 Method[] allMethods = Properties.getTargetClassAndDontInitialise().getMethods();
				Constructor<?>[] ctors = Properties.getTargetClassAndDontInitialise().getConstructors();

				
			
			String prefix = methodToTarget.split(":")[0];
			for (Method m : allMethods) {
				if (m.getName().equals(prefix)) {
					String name = m.getName() +		Type.getMethodDescriptor(m);
					properMethodNames.put(methodToTarget, name);
					break;
				}
			}
			
			if (!properMethodNames.containsKey(methodToTarget)) {
				// are we a constructor?
				boolean isCapitalFirstLetter = Character.isUpperCase(methodToTarget.charAt(0));

				
				for (Constructor c : ctors) {
					if (prefix.startsWith("<init") || isCapitalFirstLetter) {
						String methodName = "<init>" + Type.getConstructorDescriptor(c);
						properMethodNames.put(methodToTarget, methodName);
						break;
					}
				}
				
				if (!properMethodNames.containsKey(methodToTarget)) {
					
					for (Method m : allMethods) {
						if (Modifier.isStatic(m.getModifiers()) && m.getReturnType().equals(Properties.getTargetClassAndDontInitialise())) {
							String name = m.getName() +		Type.getMethodDescriptor(m);
							properMethodNames.put(methodToTarget, name);
							break;
						}
					}
				}
			
			}
			
			if (!properMethodNames.containsKey(methodToTarget)) {
				// still can't find the right name
				LoggingUtils.logErrorAtMostOnce(logger, "cannot find method name for " + methodToTarget);
				
//				for (Method m : allMethods) {
//					logger.error("\tname is " + m.getName() );
					
					
//				}
				
//				for (Constructor c : ctors) {
//					logger.error("\tname is " + c.getName() );
//				}
//				throw new RuntimeException("bad");
			}
			
			
		}
	}
	
	
	public static void addPureMethods(Collection<String> pures) {
		
		for (String pure: pures) {
			String nameOnly = pure.split("\\(")[0];
			pureMethods.add(nameOnly);
		}
	}

	
    
}
