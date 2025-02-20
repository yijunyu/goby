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

import edu.cornell.med.icb.goby.algorithmic.data.GroupComparison;
import edu.cornell.med.icb.goby.modes.DiscoverSequenceVariantsMode;
import edu.cornell.med.icb.goby.modes.SequenceVariationOutputFormat;
import edu.cornell.med.icb.goby.readers.vcf.ColumnType;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceInterface;
import edu.cornell.med.icb.goby.stats.VCFWriter;
import edu.cornell.med.icb.goby.util.OutputInfo;
import org.apache.commons.math.MathException;
import org.apache.commons.math.stat.inference.TTest;
import org.apache.commons.math.stat.inference.TTestImpl;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Fabien Campagne
 *         Date: Mar 21, 2011
 *         Time: 2:37:43 PM
 */
public class AlleleFrequencyOutputFormat implements SequenceVariationOutputFormat {
    private int refIdColumnIndex;
    private int positionColumnIndex;
    private int numberOfGroups;
    private int numberOfSamples;
    private int[] refCountsPerSample;
    private int[] variantsCountPerSample;
    private VCFWriter statsWriter;
    String[] samples;
    private boolean outputVCF;
    private int refPropFieldIndex;
    private int biomartFieldIndex;
    GenotypesOutputFormat genotypeFormatter = new GenotypesOutputFormat();
    private int depthFieldIndex;
    // values in each group, indexed by group
    private double[][] valuesGroupsA;
    private double[][] valuesGroupsB;
    private int pValueIndex[];
    private boolean eventObserved;
    /**
     * Index of the INFO fields that hold average reference proportions.
     */
    private int[] averageRPGroupsIndex;
    /**
     * Average referene proportion per group.
     */
    private float[] averageRPPerGroup;
    /**
     * The number of samples in each group.
     */
    private float[] numSamplesPerGroup;
    /**
     * Index of the INFO field that holds effect size.
     */
    private int effectSizeInfoIndex;
    private float minimumAllelelicDifference = 0.1f;
    private ArrayList<GroupComparison> groupComparisons;
    private int numberOfComparisons;

    public void setWriteFieldGroupAssociations(boolean writeFieldGroupAssociations) {
        this.writeFieldGroupAssociations = writeFieldGroupAssociations;
    }

    private boolean writeFieldGroupAssociations=true;


    public void defineColumns(OutputInfo outputInfo, DiscoverSequenceVariantsMode mode) {
        samples = mode.getSamples();
        statsWriter = new VCFWriter(outputInfo.getPrintWriter());
        statsWriter.setWriteFieldGroupAssociations(writeFieldGroupAssociations);
        biomartFieldIndex = statsWriter.defineField("INFO", "BIOMART_COORDS", 1, ColumnType.String, "Coordinates for use with Biomart.");
        genotypeFormatter.defineInfoFields(statsWriter);
        groupComparisons = mode.getGroupComparisons();
        numberOfComparisons = groupComparisons.size();
        pValueIndex = new int[numberOfComparisons];
        for (final GroupComparison comparison : groupComparisons) {
            pValueIndex[comparison.index] = statsWriter.defineField("INFO",
                    String.format("P[%s/%s]", comparison.nameGroup1, comparison.nameGroup2),
                    1, ColumnType.Float,
                    String.format("P-values of a t-test comparing arcsin transformed values of the ratio between groups %s and %s.",
                            comparison.nameGroup1, comparison.nameGroup2),
                    "p-value", "statistic");

        }
        for (int groupIndex = 0; groupIndex < numberOfGroups; groupIndex++) {
            String group = mode.getGroups()[groupIndex];
            averageRPGroupsIndex[groupIndex] = statsWriter.defineField("INFO", String.format("RP_%s", group), 1, ColumnType.Float,
                    String.format("Proportion of reference allele  (count(ref)/(sum count other alleles) averaged over group %s", group));
        }
        String[] groups = mode.getGroups();
        effectSizeInfoIndex = statsWriter.defineField("INFO", String.format("ES_%s_%s", groups[0], groups[1]), 1, ColumnType.Float,
                String.format("Effect size between groups, expressed as the absolute value of the difference between average reference proportion estimated in each group. abs(refProp[%s] - refProp[%s]).", groups[0], groups[1]));
        depthFieldIndex = statsWriter.defineField("INFO", "DP",
                1, ColumnType.Integer, "Total depth of sequencing across groups at this site");
        genotypeFormatter.defineGenotypeField(statsWriter);
        refPropFieldIndex = statsWriter.defineField("FORMAT", "RP", 1, ColumnType.Float,
                "Proportion of reference allele in the sample (count(ref)/(sum count other alleles)");


        readerIndexToGroupIndex = mode.getReaderIndexToGroupIndex();
        int countA = 0;
        int countB = 0;
        for (final int groupIndex : readerIndexToGroupIndex) {
            countA += groupIndex == 1 ? 0 : 1;
            countB += groupIndex == 1 ? 1 : 0;
        }
        valuesGroupsA = new double[numberOfComparisons][countA];
        valuesGroupsB = new double[numberOfComparisons][countB];
        for (final GroupComparison comparison : groupComparisons) {
            valuesGroupsA[comparison.index] = new double[countA];
            valuesGroupsB[comparison.index] = new double[countB];
        }
        statsWriter.defineSamples(samples);
        statsWriter.writeHeader();
        valuesGroupAIndex = new int[numberOfComparisons];
        valuesGroupBIndex = new int[numberOfComparisons];
    }

    private final TTest mathCommonsTTest = new TTestImpl();

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
        this.numberOfGroups = numberOfGroups;
        this.numberOfSamples = numberOfSamples;

        refCountsPerSample = new int[numberOfSamples];
        variantsCountPerSample = new int[numberOfSamples];
        genotypeFormatter = new GenotypesOutputFormat();
        genotypeFormatter.allocateStorage(numberOfSamples, numberOfGroups);
        numSamplesPerGroup = new float[numberOfGroups];
        averageRPGroupsIndex = new int[numberOfGroups];
        averageRPPerGroup = new float[numberOfGroups];

    }

    int valuesGroupAIndex[];
    int valuesGroupBIndex[];

    public void writeRecord(DiscoverVariantIterateSortedAlignments iterator, SampleCountInfo[] sampleCounts,
                            int referenceIndex, int position, DiscoverVariantPositionData list,
                            int groupIndexA, int groupIndexB) {
        // report 1-based positions
        position = position + 1;
        fillVariantCountArrays(sampleCounts);
        // records are only written for site where at least one sample is bi-allelic
        if (!eventObserved) return;

        int totalCount = 0;
        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
            SampleCountInfo sci = sampleCounts[sampleIndex];
            int sumInSample = 0;
            for (int genotypeIndex = 0; genotypeIndex < sci.getGenotypeMaxIndex(); ++genotypeIndex) {
                final int genotypeCount = sci.getGenotypeCount(genotypeIndex);
                totalCount += genotypeCount;
                sumInSample += genotypeCount;
                assert genotypeCount >= 0 : "counts must not be negative.";
            }
            // must observe at least one base in each sample to write output for this position.
            if (sumInSample == 0) return;
        }
        if (totalCount == 0) return;
        statsWriter.setInfo(depthFieldIndex, totalCount);

        CharSequence currentReferenceId = iterator.getReferenceId(referenceIndex);

        statsWriter.setId(".");
        statsWriter.setInfo(biomartFieldIndex,
                String.format("%s:%d:%d", currentReferenceId, position,
                        position));
        statsWriter.setChromosome(currentReferenceId);
        statsWriter.setPosition(position);


        for (final GroupComparison comparison : groupComparisons) {
            Arrays.fill(valuesGroupsA[comparison.index], 0);
            Arrays.fill(valuesGroupsB[comparison.index], 0);
            Arrays.fill(valuesGroupAIndex, 0);
            Arrays.fill(valuesGroupBIndex, 0);

        }
        Arrays.fill(averageRPPerGroup, 0);
        Arrays.fill(numSamplesPerGroup, 0);

        for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
            int numAlleles = 0;
            totalCount = 0;
            final SampleCountInfo sampleCount = sampleCounts[sampleIndex];
            for (int genotypeIndex = 0; genotypeIndex < sampleCount.getGenotypeMaxIndex(); ++genotypeIndex) {

                final int count = sampleCount.getGenotypeCount(genotypeIndex);
                if (count > 0) numAlleles++;
                totalCount += count;
            }

            // estimate reference allele proportion:
            double refProportion = (double) sampleCounts[sampleIndex].refCount;
            refProportion /= sampleCounts[sampleIndex].refCount + sampleCounts[sampleIndex].varCount;
            statsWriter.setSampleValue(refPropFieldIndex, sampleIndex, refProportion);
            final int groupIndex = readerIndexToGroupIndex[sampleIndex];
            final double transformedValue = StrictMath.asin(StrictMath.sqrt(refProportion));

            averageRPPerGroup[groupIndex] += refProportion;
            for (final GroupComparison comparison : groupComparisons) {

                final int index = comparison.index;
                if (groupIndex == comparison.indexGroup1) {
                    valuesGroupsA[index][valuesGroupAIndex[index]++] = transformedValue;
                }
                if (groupIndex == comparison.indexGroup2) {
                    valuesGroupsB[index][valuesGroupBIndex[index]++] = transformedValue;
                }
            }
            numSamplesPerGroup[groupIndex]++;
        }

        for (int groupIndex = 0; groupIndex < numberOfGroups; groupIndex++) {
            averageRPPerGroup[groupIndex] /= numSamplesPerGroup[groupIndex];
            statsWriter.setInfo(averageRPGroupsIndex[groupIndex], averageRPPerGroup[groupIndex]);
        }
        // write effect size:
        float effectSize = Math.abs(averageRPPerGroup[0] - averageRPPerGroup[1]);
        statsWriter.setInfo(effectSizeInfoIndex, effectSize);

        genotypeFormatter.writeGenotypes(statsWriter, sampleCounts, position);
        if (!statsWriter.hasAlternateAllele() || effectSize < minimumAllelelicDifference) {
            // do not write a record if the position does not have an alternate allele or if the effect size is negligible.
            return;
        }
        for (final GroupComparison comparison : groupComparisons) {
            double pValue = 1;
            final int index = comparison.index;
            try {
                if (valuesGroupAIndex[index] >= 2 && valuesGroupBIndex[index] >= 2) { // need two samples per group
                    pValue = mathCommonsTTest.homoscedasticTTest(valuesGroupsA[index], valuesGroupsB[index]);
                }

            } catch (MathException e) {
                pValue = 1;
            }
            statsWriter.setInfo(pValueIndex[index], pValue);
        }
        statsWriter.writeRecord();
    }

    public void close() {
        statsWriter.close();
    }

    @Override
    public void setGenome(RandomAccessSequenceInterface genome) {

    }

    @Override
    public void setGenomeReferenceIndex(int index) {

    }

    public void outputVCF(boolean state) {
        outputVCF = state;
    }

    int[] readerIndexToGroupIndex;

    private void fillVariantCountArrays(SampleCountInfo[] sampleCounts) {
        eventObserved = false;

        for (SampleCountInfo csi : sampleCounts) {
            final int sampleIndex = csi.sampleIndex;
            variantsCountPerSample[sampleIndex] = csi.varCount;
            refCountsPerSample[sampleIndex] = csi.refCount;
            eventObserved |= variantsCountPerSample[sampleIndex] > 0 && refCountsPerSample[sampleIndex] > 0;
        }

    }

    public void setMinimumAllelicDifference(float minimumAllelelicDifference) {
        this.minimumAllelelicDifference = minimumAllelelicDifference;
    }
}
