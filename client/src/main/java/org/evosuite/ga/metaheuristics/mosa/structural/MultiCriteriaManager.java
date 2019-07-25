/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics.mosa.structural;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.TimeController;
import org.evosuite.Properties.Criterion;
import org.evosuite.coverage.branch.BranchCoverageFactory;
import org.evosuite.coverage.branch.BranchCoverageGoal;
import org.evosuite.coverage.branch.BranchCoverageTestFitness;
import org.evosuite.coverage.cbranch.CBranchTestFitness;
import org.evosuite.coverage.exception.ExceptionCoverageFactory;
import org.evosuite.coverage.exception.ExceptionCoverageHelper;
import org.evosuite.coverage.exception.ExceptionCoverageTestFitness;
import org.evosuite.coverage.exception.TryCatchCoverageTestFitness;
import org.evosuite.coverage.io.input.InputCoverageTestFitness;
import org.evosuite.coverage.io.output.OutputCoverageTestFitness;
import org.evosuite.coverage.line.LineCoverageTestFitness;
import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.coverage.method.MethodCoverageTestFitness;
import org.evosuite.coverage.method.MethodNoExceptionCoverageTestFitness;
import org.evosuite.coverage.mutation.StrongMutationTestFitness;
import org.evosuite.coverage.mutation.WeakMutationTestFitness;
import org.evosuite.coverage.statement.StatementCoverageTestFitness;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.graphs.cfg.BytecodeInstruction;
import org.evosuite.graphs.cfg.BytecodeInstructionPool;
import org.evosuite.graphs.cfg.ControlDependency;
import org.evosuite.setup.CallContext;
import org.evosuite.setup.DependencyAnalysis;
import org.evosuite.setup.callgraph.CallGraph;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.utils.ArrayUtil;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.evosuite.Properties.Criterion.*;

public class MultiCriteriaManager<T extends Chromosome> extends StructuralGoalManager<T> implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(MultiCriteriaManager.class);

	private static final long serialVersionUID = 8161137239404885564L;

	protected BranchFitnessGraph<T, FitnessFunction<T>> graph;

	protected Map<BranchCoverageTestFitness, Set<FitnessFunction<T>>> dependencies;
	protected Map<MethodCoverageTestFitness, Set<FitnessFunction<T>>> methodDependencies = new LinkedHashMap<>();
	
	
	public int numberOfTimesTriedForBatch = 0;
	public static int numberOfTimesCalucaltedFitness = 0;
	public int batchOfFitnessIndex = 0;
	protected LinkedHashMap<String, Set<FitnessFunction<T>>> batchesOfFitness = new LinkedHashMap<>();
	
	protected Map<FitnessFunction, Set<FitnessFunction<T>>> temporalDependencies;

	protected final Map<Integer, FitnessFunction<T>> branchCoverageTrueMap = new LinkedHashMap<Integer, FitnessFunction<T>>();
	protected final Map<Integer, FitnessFunction<T>> branchCoverageFalseMap = new LinkedHashMap<Integer, FitnessFunction<T>>();
	private final Map<String, FitnessFunction<T>> branchlessMethodCoverageMap = new LinkedHashMap<String, FitnessFunction<T>>();

	public MultiCriteriaManager(List<FitnessFunction<T>> fitnessFunctions) {
		super(fitnessFunctions);

		// initialize the dependency graph among branches 
		this.graph = getControlDepencies4Branches(fitnessFunctions);

		// initialize the dependency graph between branches and other coverage targets (e.g., statements)
		// let's derive the dependency graph between branches and other coverage targets (e.g., statements)
		for (Criterion criterion : Properties.CRITERION){
			switch (criterion){
				case BRANCH:
					break; // branches have been handled by getControlDepencies4Branches
				case EXCEPTION:
					break; // exception coverage is handled by calculateFitness
				case LINE:
					addDependencies4Line();
					break;
				case STATEMENT:
					addDependencies4Statement();
					break;
				case WEAKMUTATION:
					addDependencies4WeakMutation();
					break;
				case STRONGMUTATION:
					addDependencies4StrongMutation();
					break;
				case METHOD:
					addDependencies4Methods();
					break;
				case INPUT:
					addDependencies4Input();
					break;
				case OUTPUT:
					addDependencies4Output();
					break;
				case TRYCATCH:
					addDependencies4TryCatch();
					break;
				case METHODNOEXCEPTION:
					addDependencies4MethodsNoException();
					break;
				case CBRANCH:
					addDependencies4CBranch();
					break;
			
					
				case LTLCOVERAGE:
					addDependencies4LTL();
					break;
				default:
					LoggingUtils.getEvoLogger().error("The criterion {} is not currently supported in DynaMOSA", criterion.name());
			}
		}

		// initialize current goals
		this.currentGoals.addAll(graph.getRootBranches());
	}

	@SuppressWarnings("unchecked")
	private void addDependencies4TryCatch() {
		logger.debug("Added dependencies for Try-Catch");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof TryCatchCoverageTestFitness){
				TryCatchCoverageTestFitness stmt = (TryCatchCoverageTestFitness) ff;
				BranchCoverageTestFitness branch = new BranchCoverageTestFitness(stmt.getBranchGoal());
				this.dependencies.get(branch).add((FitnessFunction<T>) stmt);
			}
		}
	}

	private void initializeMaps(Set<FitnessFunction<T>> set){
		for (FitnessFunction<T> ff : set) {
			BranchCoverageTestFitness goal = (BranchCoverageTestFitness) ff;
			// Skip instrumented branches - we only want real branches
			if(goal.getBranch() != null) {
				if(goal.getBranch().isInstrumented()) {
					continue;
				}
			}

			if (goal.getBranch() == null) {
				branchlessMethodCoverageMap.put(goal.getClassName() + "."
						+ goal.getMethod(), ff);
			} else {
				if (goal.getBranchExpressionValue())
					branchCoverageTrueMap.put(goal.getBranch().getActualBranchId(), ff);
				else
					branchCoverageFalseMap.put(goal.getBranch().getActualBranchId(), ff);
			}
		}
	}

	private void addDependencies4Output() {
		logger.debug("Added dependencies for Output");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof OutputCoverageTestFitness){
				OutputCoverageTestFitness output = (OutputCoverageTestFitness) ff;
				ClassLoader loader = TestGenerationContext.getInstance().getClassLoaderForSUT();
				BytecodeInstructionPool pool = BytecodeInstructionPool.getInstance(loader);
				if (pool.getInstructionsIn(output.getClassName(), output.getMethod()) == null){
					this.currentGoals.add(ff);
					continue;
				}
				for (BytecodeInstruction instruction : pool.getInstructionsIn(output.getClassName(), output.getMethod())) {
					if (instruction.getBasicBlock() != null){
						Set<ControlDependency> cds = instruction.getBasicBlock().getControlDependencies();
						if (cds.size()==0){
							this.currentGoals.add(ff);
						} else {
							for (ControlDependency cd : cds) {
								BranchCoverageTestFitness fitness = BranchCoverageFactory.createBranchCoverageTestFitness(cd);
								this.dependencies.get(fitness).add(ff);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * This methods derive the dependencies between {@link InputCoverageTestFitness} and branches. 
	 * Therefore, it is used to update 'this.dependencies'
	 */
	private void addDependencies4Input() {
		logger.debug("Added dependencies for Input");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof InputCoverageTestFitness){
				InputCoverageTestFitness input = (InputCoverageTestFitness) ff;
				ClassLoader loader = TestGenerationContext.getInstance().getClassLoaderForSUT();
				BytecodeInstructionPool pool = BytecodeInstructionPool.getInstance(loader);
				if (pool.getInstructionsIn(input.getClassName(), input.getMethod()) == null) {
					this.currentGoals.add(ff);
					continue;
				}
				for (BytecodeInstruction instruction : pool.getInstructionsIn(input.getClassName(), input.getMethod())) {
					if (instruction.getBasicBlock() != null){
						Set<ControlDependency> cds = instruction.getBasicBlock().getControlDependencies();
						if (cds.size()==0){
							this.currentGoals.add(ff);
						} else {
							for (ControlDependency cd : cds) {
								BranchCoverageTestFitness fitness = BranchCoverageFactory.createBranchCoverageTestFitness(cd);
								this.dependencies.get(fitness).add(ff);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * This methods derive the dependencies between {@link MethodCoverageTestFitness} and branches. 
	 * Therefore, it is used to update 'this.dependencies'
	 */
	@SuppressWarnings("unchecked")
	private void addDependencies4Methods() {
		logger.debug("Added dependencies for Methods");
		for (BranchCoverageTestFitness branch : this.dependencies.keySet()){
			MethodCoverageTestFitness method = new MethodCoverageTestFitness(branch.getClassName(), branch.getMethod());
			this.dependencies.get(branch).add((FitnessFunction<T>) method);
		}
	}

	/**
	 * This methods derive the dependencies between {@link MethodNoExceptionCoverageTestFitness} and branches.
	 * Therefore, it is used to update 'this.dependencies'
	 */
	@SuppressWarnings("unchecked")
	private void addDependencies4MethodsNoException() {
		logger.debug("Added dependencies for MethodsNoException");
		for (BranchCoverageTestFitness branch : this.dependencies.keySet()){
			MethodNoExceptionCoverageTestFitness method = new MethodNoExceptionCoverageTestFitness(branch.getClassName(), branch.getMethod());
			this.dependencies.get(branch).add((FitnessFunction<T>) method);
		}
	}

	/**
	 * This methods derive the dependencies between {@link CBranchTestFitness} and branches.
	 * Therefore, it is used to update 'this.dependencies'
	 */
	@SuppressWarnings("unchecked")
	private void addDependencies4CBranch() {
		logger.debug("Added dependencies for CBranch");
		CallGraph callGraph = DependencyAnalysis.getCallGraph();
		for (BranchCoverageTestFitness branch : this.dependencies.keySet()) {
			for (CallContext context : callGraph.getMethodEntryPoint(branch.getClassName(), branch.getMethod())) {
				CBranchTestFitness cBranch = new CBranchTestFitness(branch.getBranchGoal(), context);
				this.dependencies.get(branch).add((FitnessFunction<T>) cBranch);
				logger.debug("Added context branch: " + cBranch.toString());
			}
		}
	}
	

	
	/**
	 * This methods derive the dependencies between {@link CBranchTestFitness} and branches.
	 * Therefore, it is used to update 'this.dependencies'
	 */
	@SuppressWarnings("unchecked")
	private void addDependencies4LTL() {
		logger.debug("Added dependencies for LTL");
		
	
		
//		Random rand = new Random();
		List<FitnessFunction> uncoveredGoals = new ArrayList<>(this.getUncoveredGoals());
		Collections.shuffle(uncoveredGoals);
		for (FitnessFunction<T> ff : uncoveredGoals) {
			if (ff instanceof LtlCoverageTestFitness){
				
				LtlCoverageTestFitness ltlFitness = (LtlCoverageTestFitness) ff;
				String lhs = ltlFitness.getStart();
				String rhs = ltlFitness.getEnd();
				
//				new MethodCoverageTestFitness();
				
//				if (!batchesOfFitness.containsKey(lhs)) {
//					batchesOfFitness.put(lhs, new HashSet<>());
//				}
//				batchesOfFitness.get(lhs).add(ff);
//			
				
				MethodCoverageTestFitness method = new MethodCoverageTestFitness(ltlFitness.getTargetClass(), ltlFitness.getTargetMethod());

				if (!LtlCoverageTestFitness.allowEvolutionWithoutLTLFitness) {
					if (!methodDependencies.containsKey(method)) {
						methodDependencies.put(method, new HashSet<>());
					}
					methodDependencies.get(method).add(ff);
				} else {
					this.currentGoals.add(ff);
				}
			}
		}
		
		if (LtlCoverageTestFitness.allowEvolutionWithoutLTLFitness) {
			logger.warn("Using allowEvolutionWithoutLTLFitness");
		}
//		for (Entry<MethodCoverageTestFitness, Set<FitnessFunction<T>>> entry : methodDependencies.entrySet() ) {
//			for (FitnessFunction<T> ff : entry.getValue()) {
//				logger.warn("::: " + entry.getKey() + " ->"  +ff);
//			}
//		}

//		for (Entry<String, Set<FitnessFunction<T>>> entry : batchesOfFitness.entrySet()) {
//		if (index == 0) { // add the first batch into current goals
//			this.currentGoals.addAll(entry.getValue());
//			for (FitnessFunction ff : entry.getValue()) {
//				LoggingUtils.logWarnAtMostOnce(logger, "LTL goal : " + ff);
//			}
//			break; // only first iteration
//		}
//		}
	}


	/**
	 * This methods derive the dependencies between {@link WeakMutationTestFitness} and branches. 
	 * Therefore, it is used to update 'this.dependencies'
	 */
	private void addDependencies4WeakMutation() {
		logger.debug("Added dependencies for Weak-Mutation");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof WeakMutationTestFitness){
				WeakMutationTestFitness mutation = (WeakMutationTestFitness) ff;
				Set<BranchCoverageGoal> goals = mutation.getMutation().getControlDependencies();
				if (goals.size() == 0){
					this.currentGoals.add(ff);
				} else {
					for (BranchCoverageGoal goal : goals) {
						BranchCoverageTestFitness fitness = new BranchCoverageTestFitness(goal);
						this.dependencies.get(fitness).add(ff);
					}
				}
			}
		}
	}

	/**
	 * This methods derive the dependencies between {@link org.evosuite.coverage.mutation.StrongMutationTestFitness} and branches.
	 * Therefore, it is used to update 'this.dependencies'
	 */
	private void addDependencies4StrongMutation() {
		logger.debug("Added dependencies for Strong-Mutation");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof StrongMutationTestFitness){
				StrongMutationTestFitness mutation = (StrongMutationTestFitness) ff;
				Set<BranchCoverageGoal> goals = mutation.getMutation().getControlDependencies();
				if (goals.size() == 0){
					this.currentGoals.add(ff);
				} else {
					for (BranchCoverageGoal goal : goals) {
						BranchCoverageTestFitness fitness = new BranchCoverageTestFitness(goal);
						this.dependencies.get(fitness).add(ff);
					}
				}
			}
		}
	}

	/**
	 * This methods derive the dependencies between  {@link LineCoverageTestFitness} and branches. 
	 * Therefore, it is used to update 'this.dependencies'
	 */
	private void addDependencies4Line() {
		logger.debug("Added dependencies for Lines");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof LineCoverageTestFitness){
				LineCoverageTestFitness line = (LineCoverageTestFitness) ff;
				ClassLoader loader = TestGenerationContext.getInstance().getClassLoaderForSUT();
				BytecodeInstructionPool pool = BytecodeInstructionPool.getInstance(loader);
				BytecodeInstruction instruction = pool.getFirstInstructionAtLineNumber(line.getClassName(), line.getMethod(), line.getLine());
				Set<ControlDependency> cds = instruction.getControlDependencies();
				if(cds.size() == 0)
					this.currentGoals.add(ff);
				else {
					for (ControlDependency cd : cds) {
						BranchCoverageTestFitness fitness = BranchCoverageFactory.createBranchCoverageTestFitness(cd);
						this.dependencies.get(fitness).add(ff);
					}
				}
			}
		}
	}

	/**
	 * This methods derive the dependencies between  {@link StatementCoverageTestFitness} and branches. 
	 * Therefore, it is used to update 'this.dependencies'
	 */
	@SuppressWarnings("unchecked")
	private void addDependencies4Statement() {
		logger.debug("Added dependencies for Statements");
		for (FitnessFunction<T> ff : this.getUncoveredGoals()){
			if (ff instanceof StatementCoverageTestFitness){
				StatementCoverageTestFitness stmt = (StatementCoverageTestFitness) ff;
				if (stmt.getBranchFitnesses().size() == 0)
					this.currentGoals.add(ff);
				else {
					for (BranchCoverageTestFitness branch : stmt.getBranchFitnesses()) {
						this.dependencies.get(branch).add((FitnessFunction<T>) stmt);
					}
				}
			}
		}
	}



	@SuppressWarnings("unchecked")
	@Override
	public void calculateFitness(T c) {
		// run the test
		TestCase test = ((TestChromosome) c).getTestCase();
		ExecutionResult result = TestCaseExecutor.runTest(test);
		((TestChromosome) c).setLastExecutionResult(result);
		c.setChanged(false);

		if (result.hasTimeout() || result.hasTestException()){
			for (FitnessFunction<T> f : currentGoals)
				c.setFitness(f, Double.MAX_VALUE);
			return;
		}

		// 1) we update the set of currents goals
		Set<FitnessFunction<T>> visitedTargets = new LinkedHashSet<FitnessFunction<T>>(getUncoveredGoals().size()*2);
		LinkedList<FitnessFunction<T>> targets = new LinkedList<FitnessFunction<T>>();
		targets.addAll(this.currentGoals);

		
		boolean insertByBatch = false;
		if (insertByBatch) {
			Iterator<Entry<String, Set<FitnessFunction<T>>>> batchesIter = batchesOfFitness.entrySet().iterator();
			
			Iterator<FitnessFunction<T>> iter = null;
			Entry<String, Set<FitnessFunction<T>>> prevBatch = null;
			Entry<String, Set<FitnessFunction<T>>> currentBatch = null;
			for (int i = 0; i < batchOfFitnessIndex + 1; i++) {
				prevBatch = currentBatch;
				if (!batchesIter.hasNext()) {
					break;
				}
				currentBatch = batchesIter.next();
				iter = currentBatch.getValue().iterator();
			}
	
			if (iter != null) {
				while (iter.hasNext()) {
					LoggingUtils.logWarnAtMostOnce(logger, "iterating through the ltl fitnesses!");
					FitnessFunction<T> fitnessFunction = iter.next();
					double value = fitnessFunction.getFitness(c);
					if (value <= 0.001) {
						// no need to update covered goals. The loop later will do it!
						iter.remove();
						logger.warn("iterating through the ltl fitnesses: removed! Remaining: " + currentBatch.getValue().size());
					}
				}
				
	//			logger.warn("after removing. Left with " + currentBatch.getValue().size());
				
				numberOfTimesTriedForBatch += 1;
				if (numberOfTimesCalucaltedFitness % 100 == 0) {
					logger.warn("Fitnesses left : "  + currentBatch.getValue().size());
					logger.warn("At fitness calculation #" + numberOfTimesCalucaltedFitness);
				}
				numberOfTimesCalucaltedFitness+= 1;
				
				if (numberOfTimesTriedForBatch > 100 
						|| currentBatch.getValue().size() < 25) {
					// time to advance (either all covered, or not able to cover anymore and we got tired of retrying)
					// or the batch has shrunk enough
				
					logger.warn("Advancing Batch (of LTL) index. method=" + currentBatch.getKey() + ". Left to Cover : " + currentBatch.getValue().size());
					logger.warn("At fitness calculation #" + numberOfTimesCalucaltedFitness);
					
					batchOfFitnessIndex += 1;
					if (batchesIter.hasNext()) { 
						prevBatch = currentBatch;
						currentBatch = batchesIter.next();
					}
					if (batchOfFitnessIndex < batchesOfFitness.size()) {
						logger.warn("next batch size is " + currentBatch.getValue().size());
						for (FitnessFunction<T> fitnessFunction : currentBatch.getValue()) {
							currentGoals.add(fitnessFunction);			
						}
						if (prevBatch != null) {
							for (FitnessFunction<T> fitnessFunction : prevBatch.getValue()) {
								currentGoals.remove(fitnessFunction);			
							}
						}
					}
					
					numberOfTimesTriedForBatch = 0;
				} 
			}
		}
		
		boolean hasChanged = false;
		
		while (targets.size()>0){
			FitnessFunction<T> fitnessFunction = targets.poll();

			int past_size = visitedTargets.size();
			visitedTargets.add(fitnessFunction);
			if (past_size == visitedTargets.size())
				continue;

			double value = fitnessFunction.getFitness(c);
			if (value == 0.0) {
				updateCoveredGoals(fitnessFunction, c);
				
				if (fitnessFunction instanceof BranchCoverageTestFitness){
					for (FitnessFunction<T> child : graph.getStructuralChildren(fitnessFunction)){
						targets.addLast(child);
					}
					for (FitnessFunction<T> dependentTarget : dependencies.get(fitnessFunction)){
						targets.addLast(dependentTarget);
					}
				}
				
				if (fitnessFunction instanceof MethodCoverageTestFitness) {
					MethodCoverageTestFitness methodFitness = (MethodCoverageTestFitness) fitnessFunction;

					if (methodDependencies.containsKey(fitnessFunction)) {
//						logger.warn("\t\t\t adding " + methodDependencies.get(fitnessFunction).size());
						for (FitnessFunction<T> dependentTarget : methodDependencies.get(fitnessFunction)){
							targets.addLast(dependentTarget);
							
							if (dependentTarget instanceof LtlCoverageTestFitness && ((LtlCoverageTestFitness) dependentTarget).age == -1) {
								if (!LtlCoverageTestFitness.allowEvolutionWithoutLTLFitness) {
									((LtlCoverageTestFitness) dependentTarget).age = 0; // initialize the age to set it as ready for aging
								}
							}
						}
					} else {
//						logger.warn("CLEARED method no deps");
					}
				}
			} else {
				if (fitnessFunction instanceof LtlCoverageTestFitness) {
					Random r = new Random();
					if (((LtlCoverageTestFitness)fitnessFunction).age > 100 ) {
						// we have seen this target long enough, but still no counter-examples yet. Maybe it's a true correct temporal rule afterall.
						// free up effort for evolving tests such that tests related to this uncoverable target can be removed.
						// a good guess for age is 77 as "sensitivity level of δ = 0.05 and a significance level of
						// α = 0.01, Jeffrey’s interval gives us N = 77."
						// but... 100 worked better...
						currentGoals.remove(fitnessFunction);
						LtlCoverageTestFitness.numRetired += 1;
						logger.warn("Expiring/Removing goal due to old age> " + (100) + " Goal: "+ fitnessFunction.toString() );
						
						hasChanged = true;
					} else {
						currentGoals.add(fitnessFunction);
					}
				} else {
					currentGoals.add(fitnessFunction);
				}
			}	
		}
		
		if (hasChanged) {
			logger.warn("\t\t# Current Goals = " + currentGoals.size() + ". time left:" + TimeController.getInstance().getLeftTimeBeforeEnd());
		}

		
		currentGoals.removeAll(this.getCoveredGoals());
		// 2) we update the archive
		for (Integer branchid : result.getTrace().getCoveredFalseBranches()){
			FitnessFunction<T> branch = this.branchCoverageFalseMap.get(branchid);
			if (branch == null)
				continue;
			updateCoveredGoals((FitnessFunction<T>) branch, c);
		}
		for (Integer branchid : result.getTrace().getCoveredTrueBranches()){
			FitnessFunction<T> branch = this.branchCoverageTrueMap.get(branchid);
			if (branch == null)
				continue;
			updateCoveredGoals((FitnessFunction<T>) branch, c);
		}
		for (String method : result.getTrace().getCoveredBranchlessMethods()){
			FitnessFunction<T> branch = this.branchlessMethodCoverageMap.get(method);
			if (branch == null)
				continue;
			updateCoveredGoals((FitnessFunction<T>) branch, c);
		}

		// let's manage the exception coverage
		if (ArrayUtil.contains(Properties.CRITERION, EXCEPTION)){
			// if one of the coverage criterion is Criterion.EXCEPTION,
			// then we have to analyze the results of the execution do look
			// for generated exceptions
			Set<ExceptionCoverageTestFitness> set = deriveCoveredExceptions(c);
			for (ExceptionCoverageTestFitness exp : set){
				// let's update the list of fitness functions 
				updateCoveredGoals((FitnessFunction<T>) exp, c);
				// new covered exceptions (goals) have to be added to the archive
				if (!ExceptionCoverageFactory.getGoals().containsKey(exp.getKey())){
					// let's update the newly discovered exceptions to ExceptionCoverageFactory 
					ExceptionCoverageFactory.getGoals().put(exp.getKey(), exp);
				}
			}
		}
	}

	/**
	 * This method analyzes the execution results of a TestChromosome looking for generated exceptions.
	 * Such exceptions are converted in instances of the class {@link ExceptionCoverageTestFitness},
	 * which are additional covered goals when using as criterion {@link Properties.Criterion Exception}
	 * @param t TestChromosome to analyze
	 * @return list of exception goals being covered by t
	 */
	public Set<ExceptionCoverageTestFitness> deriveCoveredExceptions(T t){
		Set<ExceptionCoverageTestFitness> covered_exceptions = new LinkedHashSet<ExceptionCoverageTestFitness>();
		TestChromosome testCh = (TestChromosome) t;
		ExecutionResult result = testCh.getLastExecutionResult();
		
		if(result.calledReflection())
			return covered_exceptions;

		for (Integer i : result.getPositionsWhereExceptionsWereThrown()) {
			if(ExceptionCoverageHelper.shouldSkip(result,i)){
				continue;
			}

			Class<?> exceptionClass = ExceptionCoverageHelper.getExceptionClass(result,i);
			String methodIdentifier = ExceptionCoverageHelper.getMethodIdentifier(result, i); //eg name+descriptor
			boolean sutException = ExceptionCoverageHelper.isSutException(result,i); // was the exception originated by a direct call on the SUT?

			/*
			 * We only consider exceptions that were thrown by calling directly the SUT (not the other
			 * used libraries). However, this would ignore cases in which the SUT is indirectly tested
			 * through another class
			 */

			if (sutException) {

				ExceptionCoverageTestFitness.ExceptionType type = ExceptionCoverageHelper.getType(result,i);
				/*
				 * Add goal to list of fitness functions to solve
				 */
				ExceptionCoverageTestFitness goal = new ExceptionCoverageTestFitness(Properties.TARGET_CLASS, methodIdentifier, exceptionClass, type);
				covered_exceptions.add(goal);
			}
		}
		return covered_exceptions;
	}

	public BranchFitnessGraph getControlDepencies4Branches(List<FitnessFunction<T>> fitnessFunctions){
		Set<FitnessFunction<T>> setOfBranches = new LinkedHashSet<FitnessFunction<T>>();
		this.dependencies = new LinkedHashMap();

		List<BranchCoverageTestFitness> branches = new BranchCoverageFactory().getCoverageGoals();
		for (BranchCoverageTestFitness branch : branches){
			setOfBranches.add((FitnessFunction<T>) branch);
			this.dependencies.put(branch, new LinkedHashSet<FitnessFunction<T>>());
		}

		// initialize the maps
		this.initializeMaps(setOfBranches);

		return new BranchFitnessGraph<T, FitnessFunction<T>>(setOfBranches);
	}
}
