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
package org.evosuite.ga.stoppingconditions;

import org.apache.commons.lang3.ArrayUtils;
import org.evosuite.Properties;
import org.evosuite.Properties.Criterion;
import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NoNewGoodCasesStoppingCondition extends StoppingConditionImpl {

	private static final long serialVersionUID = -1243434L;

	private static Logger logger = LoggerFactory.getLogger(NoNewGoodCasesStoppingCondition.class);

	
	private double lastCovered = 0;
	public static int numberOfIterationsNoChange = 0;

	/**
	 * {@inheritDoc}
	 *
	 * Update information on currently lowest fitness
	 */
	@Override
	public void iteration(GeneticAlgorithm<?> algorithm) {
		int currentCovered = LtlCoverageTestFitness.covered.size();
		if (currentCovered == lastCovered) {
			numberOfIterationsNoChange++;
		} else {
			numberOfIterationsNoChange = 0;
		}
		lastCovered = currentCovered;
//		logger.warn("iteration." + "lastCovered=" + lastCovered + ",numberOfIterationsNoChange=" + numberOfIterationsNoChange);
	}

	/**
	 * {@inheritDoc}
	 *
	 */
	@Override
	public boolean isFinished() {
		return false; 
		//ArrayUtils.contains(Properties.CRITERION, Criterion.LTLCOVERAGE) && (numberOfIterationsNoChange > Integer.MAX_VALUE 
		//		|| LtlCoverageTestFitness.numInstances == (lastCovered + LtlCoverageTestFitness.numRetired));
	}

	/**
	 * {@inheritDoc}
	 *
	 */
	@Override
	public void reset() {
		numberOfIterationsNoChange = 0; 
		lastCovered  = 0;
	}

	/* (non-Javadoc)
	 * @see org.evosuite.ga.StoppingCondition#setLimit(int)
	 */
	/** {@inheritDoc} */
	@Override
	public void setLimit(long limit) {
		// Do nothing
	}

	/** {@inheritDoc} */
	@Override
	public long getLimit() {
		return LtlCoverageTestFitness.numInstances;
	}

	/* (non-Javadoc)
	 * @see org.evosuite.ga.StoppingCondition#getCurrentValue()
	 */
	/** {@inheritDoc} */
	@Override
	public long getCurrentValue() {
		return numberOfIterationsNoChange;
	}

	/**
	 * <p>setFinished</p>
	 */
	public void setFinished() {
		throw new RuntimeException("not implemented...!");
	}

	/** {@inheritDoc} */
	@Override
	public void forceCurrentValue(long value) {
		// TODO Auto-generated method stub
		// TODO ?
	}

}
