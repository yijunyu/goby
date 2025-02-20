/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
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

package edu.cornell.med.icb.goby.stats;

import edu.cornell.med.icb.goby.algorithmic.data.GroupComparison;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Set;

/**
 * @author Fabien Campagne
 *         Date: Jan 11, 2010
 *         Time: 3:35:24 PM
 */
public class DifferentialExpressionCalculator {
    private final Set<String> groups;
    private final Map<String, String> sampleToGroupMap;
    private final IndexedIdentifier elementLabels;
    private int elementsPerSample;
    private int numberOfSamples;
    private final Map<String, IntArrayList> sampleToCounts;
    private Object2DoubleMap<String> sampleProportions;
    private final IntArrayList lengths;
    private final Int2IntMap elementLabelToElementType;
    private Object2IntMap<String> sampleToSumCount;
    private boolean runInParallel;

    public boolean isRunInParallel() {
        return runInParallel;
    }

    public void setRunInParallel(boolean runInParallel) {
        this.runInParallel = runInParallel;
    }

    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(BullardUpperQuartileNormalization.class);
    private ObjectArrayList<String> allSamples;


    /**
     * Return the type of the element.
     *
     * @param elementId
     * @return ElementType
     */
    public ElementType getElementType(final MutableString elementId) {
        final int elementIndex = elementLabels.get(elementId);
        final int ordinal = elementLabelToElementType.get(elementIndex);
        if (ordinal == -1) {
            return ElementType.OTHER;
        } else {
            return ElementType.values()[ordinal];
        }
    }

    /**
     * Force the recalculation of SumOverlapCounts. This is useful to ensure that all the elements are counted for each sample.
     */
    public void resetSumOverlapCounts() {
        sampleToSumCount.clear();
    }

    /**
     * Associate each sample to the default group "all-samples".
     */
    public void createDefaultGroup() {

        for (final String sampleId : sampleToCounts.keySet()) {
            associateSampleToGroup(sampleId, "all-samples/all-samples");
        }
    }

    public enum ElementType {
        EXON,
        TRANSCRIPT,
        GENE,
        OTHER
    }

    /**
     * The number of alignment entries observed in each sample.
     */
    private final Object2LongMap<String> numAlignedInSample;

    public DifferentialExpressionCalculator() {
        super();
        groups = new ObjectArraySet<String>();
        elementLabels = new IndexedIdentifier(100000);
        sampleToGroupMap = new Object2ObjectOpenHashMap<String, String>();
        numAlignedInSample = new Object2LongOpenHashMap<String>();
        sampleToCounts = new Object2ObjectOpenHashMap<String, IntArrayList>();
        lengths = new IntArrayList();
        elementLabelToElementType = new Int2IntAVLTreeMap();
        elementLabelToElementType.defaultReturnValue(-1);
        sampleToSumCount = new Object2IntRBTreeMap<String>();
        sampleToSumCount.defaultReturnValue(-1);
    }

    public double calculateNormalized(final int readCountInt, final int annotLength, final double normalizationFactor) {
        final double readCount = readCountInt;
        final double length = annotLength; // in bases
        final double sampleReadCount = normalizationFactor; // in reads
        return readCount / (length / 1000.0d) / (normalizationFactor / 1E6d);
    }

    public double calculateNormalized(final double readCountInt, final int annotLength, final double normalizationFactor) {
        final double readCount = readCountInt;
        final double length = annotLength; // in bases
        final double sampleReadCount = normalizationFactor; // in reads
        return readCount / (length / 1000.0d) / (normalizationFactor / 1E6d);
    }

    public synchronized void defineGroup(final String label) {
        groups.add(label);
    }

    /**
     * Define an element but don't specify the type. The version with the type is the preferred version.
     *
     * @param label element-id
     * @return the index of the element
     */
    public synchronized int defineElement(final String label) {
        return defineElement(label, ElementType.OTHER);
    }

    /**
     * Define an element and it's type.
     *
     * @param label element-id
     * @param type  the type (gene, exon, ...)
     * @return the index of the element
     */
    public synchronized int defineElement(final String label, final ElementType type) {
        final MutableString elementLabel = new MutableString(label).compact();
        final int elementIndex = elementLabels.registerIdentifier(elementLabel);
        if (elementLabelToElementType.get(elementIndex) == -1) {
            // Don't REPLACE the ElementType for a label. If it was set before, leave it alone.
            elementLabelToElementType.put(elementIndex, type.ordinal());
        }
        return elementIndex;
    }

    public Int2IntMap getElementLabelToElementTypeMap() {
        return elementLabelToElementType;
    }

    /**
     * Define the number of sequence bases that this element spans. The bases do not need to be contiguous (i.e., multiple
     * exons of a transcript).
     *
     * @param elementIndex index of the element associated with length.
     * @param length       length of the element.
     */
    public synchronized void defineElementLength(final int elementIndex, final int length) {
        if (lengths.size() == elementIndex) {
            lengths.add(length);
        } else {
            if (lengths.size() < elementIndex) {
                lengths.size(elementIndex + 1);
            }
            lengths.set(elementIndex, length);
        }
    }

    public synchronized void associateSampleToGroup(final String sample, final String group) {
        sampleToGroupMap.put(sample, group);
    }

    /**
     * Return the length of an element.
     *
     * @param elementId
     * @return
     */
    public int getElementLength(final MutableString elementId) {
        final int elementIndex = elementLabels.getInt(elementId);
        return lengths.get(elementIndex);
    }

    /**
     * Observe counts for a specific element and sample.
     *
     * @param sample    sample id.
     * @param elementId element id.
     * @param count     Number of reads that can be assigned to the element.
     */
    public void observe(final String sample, final String elementId, final double count) {

        this.observe(sample, elementId, (int) Math.round(count));
    }

    /**
     * Observe counts for a sample.
     *
     * @param sample    sample id.
     * @param elementId element id.
     * @param count     Number of reads that can be assigned to the element.
     */
    public void observe(final String sample, final String elementId, final int count) {


        IntArrayList counts = sampleToCounts.get(sample);
        // the following looks a bit complicated. We are trying to avoid synchronizing every time observe is called.
        // This would slow the whole process too much. Instead, we synchronize only when we need to create a counts
        // data structure for a new sample, which should not happen too often.
        if (counts == null) {
            synchronized (this) {
                counts = sampleToCounts.get(sample);
                if (counts == null) {
                    counts = new IntArrayList(elementsPerSample);
                    counts.size(elementsPerSample);
                    sampleToCounts.put(sample, counts);
                }
            }
        }


        final int elementIndex = elementLabels.getInt(new MutableString(elementId));
        counts.set(elementIndex, count);
    }

    /**
     * Return the element index of element identified by id.
     *
     * @param elementId id of the element.
     * @return index of the element.
     */
    public int getElementIndex(final String elementId) {
        return elementLabels.getInt(new MutableString(elementId));

    }

    /**
     * Define the number of alignment entries found in each sample.
     *
     * @param sampleId            The sample
     * @param numAlignedInSamples The number of alignment entries observed in the sample.
     */
    public synchronized void setNumAlignedInSample(final String sampleId, final long numAlignedInSamples) {
        numAlignedInSample.put(sampleId, numAlignedInSamples);
    }

    /**
     * Return the number of alignment entries found in the sample.
     *
     * @param sampleId Identifier of the sample.
     * @return the number of alignment entries found in the sample.
     */
    public synchronized long getNumAlignedInSample(final String sampleId) {
        return numAlignedInSample.getLong(sampleId);
    }

    /**
     * Get the sampleToGroupMap object.
     *
     * @return map of sample id's to group names.
     */
    public Map<String, String> getSampleToGroupMap() {
        return sampleToGroupMap;
    }

    /**
     * Get the group that the sample belongs to.
     *
     * @param sampleId Id of the sample.
     * @return group id to which this sample belongs to.
     */
    public String getGroup(String sampleId) {
        return sampleToGroupMap.get(sampleId);
    }

    public DifferentialExpressionResults compare(DifferentialExpressionResults results,
                                                 final NormalizationMethod method,
                                                 final StatisticCalculator tester,
                                                 final GroupComparison comparison) {
        return compare(results, method, tester, comparison.nameGroup1, comparison.nameGroup2);
    }

    public DifferentialExpressionResults compare(DifferentialExpressionResults results,
                                                 final NormalizationMethod method,
                                                 final StatisticCalculator tester,
                                                 final String... group) {
        if (results == null) {
            results = new DifferentialExpressionResults();
        }
        if (tester.installed()) {
            if (tester.canDo(group)) {
                tester.setResults(results);
                return tester.evaluate(this, method, results, group);
            } else {
                LOG.warn("The number of groups to compare is not supported by the calculator ");
                return results;
            }
        } else {
            LOG.warn("Diffexp test=" + tester.getClass().getName() + " is not 'installed' and will not be run.");
            return results;
        }
    }

    public DifferentialExpressionResults compare(final StatisticCalculator tester,
                                                 final NormalizationMethod method,
                                                 final String... group) {
        final DifferentialExpressionResults results = new DifferentialExpressionResults();
        if (tester.installed()) {
            assert !tester.canDo(group) : "The number of groups to compare is not supported by the specified calculator.";
            tester.setResults(results);
            return tester.evaluate(this, method, results, group);
        } else {
            return results;
        }
    }

    /**
     * Reserve storage for specified number of elements and samples. One DE test will be
     * conducted for each element.
     *
     * @param elementsPerSample
     * @param numberOfSamples
     */
    public void reserve(final int elementsPerSample, final int numberOfSamples) {
        this.elementsPerSample = elementsPerSample;
        this.numberOfSamples = numberOfSamples;
        lengths.size(elementsPerSample);
    }

    /**
     * Returns the sample ids that belong to a group.
     *
     * @param groupId Id of the group.
     * @return The set of samples that belong to group
     */
    public ObjectArraySet<String> getSamples(final String groupId) {
        final ObjectArraySet<String> samples = new ObjectArraySet<String>();
        for (final String sampleId : sampleToGroupMap.keySet()) {
            if (sampleToGroupMap.get(sampleId).equals(groupId)) {
                samples.add(sampleId);
            }
        }
        return samples;
    }

    public ObjectSet<MutableString> getElementIds() {
        return elementLabels.keySet();
    }

    /**
     * Get the normalized expression value an element in a given sample.  Equivalent
     * to calling the {@link NormalizationMethod#getNormalizedExpressionValue(DifferentialExpressionCalculator, String, it.unimi.dsi.lang.MutableString)}
     * method.
     *
     * @param sampleId            sample identifier
     * @param normalizationMethod Normalization method (adjusts the denominator of the RPKM value).
     * @param elementId           the element for which a normalized expression value is sought.
     * @return normalized expression value scaled by length and global normalization method.
     */
    public double getNormalizedExpressionValue(final String sampleId, final NormalizationMethod normalizationMethod, final MutableString elementId) {
        return normalizationMethod.getNormalizedExpressionValue(this, sampleId, elementId);

    }

    /**
     * Get the stored overlap count for an element in a given sample.
     *
     * @param sample
     * @param elementId
     * @return
     */
    public int getOverlapCount(final String sample, final MutableString elementId) {
        final IntArrayList counts = sampleToCounts.get(sample);
        if (counts == null) {
            return 0;
        }
        final int elementIndex = elementLabels.get(elementId);

        if (elementIndex < counts.size()) {
            return counts.get(elementIndex);
        } else {
            return 0;
        }
    }

    /**
     * Returns the sum of counts in a given sample.
     *
     * @param sample
     * @return Returns the sum of counts in a given sample.
     */
    public synchronized int getSumOverlapCounts(final String sample) {


        final int sumCountsCached = sampleToSumCount.getInt(sample);
        if (sumCountsCached != -1) {
            return sumCountsCached;
        }
        int sumCounts = 0;
        final IntArrayList counts = sampleToCounts.get(sample);
        if (counts == null) {
            return 0;
        }
        for (final int count : counts) {
            sumCounts += count;
        }
        sampleToSumCount.put(sample, sumCounts);
        return sumCounts;
    }

    public String[] samples() {
        final Set<String> sampleSet = sampleToGroupMap.keySet();
        return sampleSet.toArray(new String[sampleSet.size()]);
    }

    /**
     * Returns the proportion of counts that originate from a certain sample. The number of counts in the sample
     * is divided by the sum of counts over all the samples in the experiment.
     *
     * @param sample
     * @return
     */
    public double getSampleProportion(final String sample) {
        if (sampleProportions == null) {
            sampleProportions = new Object2DoubleArrayMap<String>();
            sampleProportions.defaultReturnValue(-1);
        }
        final double proportion;

        if (!sampleProportions.containsKey(sample)) {
            int sumOverSamples = 0;
            for (final String s : samples()) {
                sumOverSamples += getSumOverlapCounts(s);
            }
            for (final String s : samples()) {
                final double sampleProportion =
                        ((double) getSumOverlapCounts(s)) / (double) sumOverSamples;
                this.sampleProportions.put(s, sampleProportion);
            }
        }
        proportion = sampleProportions.get(sample);
        assert proportion != -1 : " Proportion must be defined for sample " + sample;
        return proportion;
    }


}
