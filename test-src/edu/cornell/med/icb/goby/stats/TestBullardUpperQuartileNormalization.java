package edu.cornell.med.icb.goby.stats;

import it.unimi.dsi.lang.MutableString;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.Random;

/**
 * @author Fabien Campagne
 *         Date: Mar 28, 2010
 *         Time: 9:54:21 AM
 */
public class TestBullardUpperQuartileNormalization {
    @Test
    public void testNormalize() {
        final DifferentialExpressionCalculator deCalc = makeDiffExprCalc();
        final RpkmLikeNormalizationMethod method = new BullardUpperQuartileNormalization();
        method.normalize(deCalc, "group-A", "group-B");
        assertTrue("normalization denominator must be large", method.getDenominator(deCalc, "A-1") > 75000);
    }

    public DifferentialExpressionCalculator makeDiffExprCalc() {
        final Random randomEngine = new Random();
        final DifferentialExpressionCalculator deCalc = new DifferentialExpressionCalculator() {

            @Override
            public int getOverlapCount(final String sample, final MutableString elementId) {
                if (sample.startsWith("A")) {
                    return (int) (2 * Math.abs(randomEngine.nextDouble() * 1000));
                } else {
                    return (int) Math.abs(randomEngine.nextDouble() * 1000);
                }

                // fold change A/B = 2
            }
            @Override
            public int getSumOverlapCounts(final String sample) {
                if (sample.startsWith("A")) {
                    return (int) (2 * Math.abs(randomEngine.nextDouble() * 100000));
                } else {
                    return (int) Math.abs(randomEngine.nextDouble() * 100000);
                }
            }
        };
        final int numElements = 1000;
        for (int i = 1; i < numElements; i++) {
            deCalc.defineElement("id-" + i);
        }

        deCalc.defineGroup("A");
        deCalc.defineGroup("B");
        final int numReplicates = 20;
        deCalc.reserve(2, numReplicates * 2);

        for (int i = 0; i < numReplicates; i++) {
            deCalc.associateSampleToGroup("A-" + i, "group-A");
            deCalc.associateSampleToGroup("B-" + i, "group-B");
        }

        return deCalc;
    }
}
