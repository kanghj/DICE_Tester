package org.evosuite.ga;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.evosuite.ga.operators.crossover.MiddleCrossOver;
import org.junit.Test;

public class MiddleCrossOverTest {
	@Test
	public void testSinglePointCrossOver() throws ConstructionFailedException {
	    DummyChromosome parent1 = new DummyChromosome(1, 2, 3, 4);
	    DummyChromosome parent2 = new DummyChromosome(5, 6);

	    MiddleCrossOver xover = new MiddleCrossOver();

	    DummyChromosome offspring1 = new DummyChromosome(parent1);
	    DummyChromosome offspring2 = new DummyChromosome(parent2);

	    xover.crossOver(offspring1, offspring2);

	    assertEquals(Arrays.asList(1, 2, 6), offspring1.getGenes());
	    assertEquals(Arrays.asList(5, 3, 4), offspring2.getGenes());
	}
}
