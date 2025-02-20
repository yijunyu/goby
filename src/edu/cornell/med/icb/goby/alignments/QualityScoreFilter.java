/*
 * Copyright (C) 2009-2011 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.cornell.med.icb.goby.alignments;

import edu.cornell.med.icb.goby.util.dynoptions.DynamicOptionClient;
import edu.cornell.med.icb.goby.util.dynoptions.RegisterThis;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.apache.commons.math.random.MersenneTwister;

import java.util.Arrays;

/**
 * @author Fabien Campagne
 *         Date: Mar 23, 2011
 *         Time: 11:16:18 AM
 */
public class QualityScoreFilter extends GenotypeFilter {
    private byte scoreThreshold = 30;
    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(QualityScoreFilter.class, "scoreThreshold:Phred score threshold to keep bases.:30",
            "NoRandomGuard:The random guard prevents this filter from removing genotypes with overall low quality that stack at the same site and agree. Random guards are default since Goby 2.3 and should be used, but this option can produice Goby 2.2.1 behaviour.:false");

    private boolean noRandomSampling;


    public static DynamicOptionClient doc() {
        return doc;
    }

    /**
     * Setting this attribute to true will make the filter compatible with Goby 2.2.1
     * @param noRandomSampling True for Goby 2.2.1 behavior, False for 2.3+ (default).
     */
    public void setNoRandomSampling(boolean noRandomSampling) {
        this.noRandomSampling = noRandomSampling;
    }

    public QualityScoreFilter() {
        scoreThreshold = doc.getByte("scoreThreshold");
        noRandomSampling =doc.getBoolean("NoRandomGuard");
    }

    public String describe() {
        return "q<" + scoreThreshold;
    }

    int thresholdPerSample[];

    @Override
    public int getThresholdForSample(int sampleIndex) {
        return thresholdPerSample[sampleIndex];
    }

    int[] removed = new int[5];


    @Override
    public void initStorage(int numSamples) {
        super.initStorage(numSamples);
        if (proportions == null) {
            proportions = new double[numSamples][SampleCountInfo.BASE_MAX_INDEX];

        } else {
            for (int i = 0; i < numSamples; i++) {
                Arrays.fill(proportions[i], 0);
            }
        }
    }

    @Override
    public void filterGenotypes(DiscoverVariantPositionData list,
                                SampleCountInfo[] sampleCounts,
                                ObjectSet<PositionBaseInfo> filteredList) {
        resetCounters();
        initStorage(sampleCounts.length);
        estimateGenotypeProportions(sampleCounts);
        // create a random number generator and initialize according to counts at this site. This is done to keep sites
        // independent of each other, so that processing one slice or an entire genome yields the same result for each
        // site.
        int seed = Arrays.deepHashCode(sampleCounts);
        MersenneTwister randomGenerator = new MersenneTwister(seed);

        if (thresholdPerSample == null) {
            thresholdPerSample = new int[sampleCounts.length];

        } else {
            Arrays.fill(thresholdPerSample, 0);
        }
        for (final PositionBaseInfo info : list) {
            numScreened++;
            if (!info.matchesReference && info.qualityScore < scoreThreshold) {
                if (!filteredList.contains(info)) {
                    if (info.to != '-' && info.from != '-') {

                        // indels have a quality score  of zero but should not be removed at this stage.
                        final SampleCountInfo sampleCountInfo = sampleCounts[info.readerIndex];
                        final int baseIndex = sampleCountInfo.baseIndex(info.to);

                        final double randomValue = randomGenerator.nextDouble();
                        if (noRandomSampling || randomValue > proportions[info.readerIndex][baseIndex]) {
                            // we reject the base if the randomValue is greater than the proportion of the genotype
                            // prior filtering.
                            // Rationale: most bases with low quality will have a low proportion of genotypes (because
                            // errors stack at the same position only by chance and yield small proportions compared to
                            // the other genotypes. A genotype with a strong proportion is more likely to be correct,
                            // irrespective of quality scores.
                            sampleCountInfo.suggestRemovingGenotype(baseIndex,info.matchesForwardStrand);
                            removeGenotype(info, filteredList);
                            thresholdPerSample[info.readerIndex]++;
                        }
                    }
                }
            }
        }
        filterIndels(list, sampleCounts);
        /*
       TODO: enable this when we store quality score for context of indels:
       if (list.hasCandidateIndels()) {
            // remove candidate indels if they don't make the base quality threshold (threshold determined by bases observed
            // at that position):
            for (final EquivalentIndelRegion indel : list.getIndels()) {
                for (final byte baseQuality : indel.getQualityScoresInContext()) {
                    if (baseQuality < scoreThreshold) {
                        list.failIndel(indel);
                        if (indel.matchesReference()) {
                            refCountRemovedPerSample[indel.sampleIndex]++;
                        } else {
                            varCountRemovedPerSample[indel.sampleIndex]++;
                        }
                        break;
                    }
                }
            }
        }

        */
        adjustGenotypes(list, filteredList, sampleCounts);

        // adjust refCount and varCount:
        adjustRefVarCounts(sampleCounts);
    }

    private double proportions[][];

    private void estimateGenotypeProportions(SampleCountInfo[] sampleCounts) {
        for (SampleCountInfo sci : sampleCounts) {
            int sumCount = 0;
            for (int genotypeIndex = 0; genotypeIndex < SampleCountInfo.BASE_MAX_INDEX; genotypeIndex++) {
                int genotypeCount = sci.getGenotypeCount(genotypeIndex);
                proportions[sci.sampleIndex][genotypeIndex] += genotypeCount;
                sumCount += genotypeCount;
            }
            for (int genotypeIndex = 0; genotypeIndex < SampleCountInfo.BASE_MAX_INDEX; genotypeIndex++) {
                proportions[sci.sampleIndex][genotypeIndex] /= (double) sumCount;
            }
        }
    }


}
