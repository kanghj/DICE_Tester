package org.evosuite.coverage.noleak;

import java.util.ArrayList;
import java.util.List;

import org.evosuite.Properties;
import org.evosuite.coverage.ltl.LtlCoverageTestFitness;
import org.evosuite.testsuite.AbstractFitnessFactory;

public class NoLeakFactory extends AbstractFitnessFactory<NoLeakTestFitness> {

	@Override
	public List<NoLeakTestFitness> getCoverageGoals() {
		List<NoLeakTestFitness> oneThing = new ArrayList<>();
		String className = Properties.TARGET_CLASS;
		oneThing.add(new NoLeakTestFitness(className));
		return oneThing;
	}

}
