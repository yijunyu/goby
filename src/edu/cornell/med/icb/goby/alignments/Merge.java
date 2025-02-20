/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This file is part of the Goby IO API.
 *
 *     The Goby IO API is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The Goby IO API is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with the Goby IO API.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.cornell.med.icb.goby.alignments;

import edu.cornell.med.icb.goby.alignments.filters.AbstractAlignmentEntryFilter;
import edu.cornell.med.icb.goby.alignments.filters.BestScoreAmbiguityAlignmentFilter;
import edu.cornell.med.icb.goby.alignments.filters.TranscriptBestScoreAlignmentFilter;
import edu.cornell.med.icb.identifier.DoubleIndexedIdentifier;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Merge alignments.   Merge is used when assembling results after searching by
 * chromosome or by transcripts.  It is especially useful if the reference does
 * not fit into memory of a computer used for alignment, for example, when
 * searching against transcripts.
 * <p/>
 * Each input file must have been generated by searching the same set of
 * reads against different subset of reference sequences. Merging consists of
 * putting back the results as if the set of reads had been searched against
 * the combined reference.
 * <p/>
 * Several merging strategies are supported by this class. For instance, one
 * strategy assumes that each set of reads provided as input was searched
 * against a different chromosome (or contig). Another strategy assumes that
 * the reads were aligned to cDNA/transcript individual reference sequences.
 *
 * @author Kevin Dorff
 * @author Fabien Campagne
 *         <p/>
 *         Date: May 5, 2009
 */
public class Merge {

    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = Logger.getLogger(Merge.class);
    private String geneTranscriptMapFile;
    private boolean verbose;
    private int k;
    private ObjectList<int[]> referenceIndexPermutation;

    public Merge(final String geneTranscriptMapFile, final int k) {
        super();
        this.geneTranscriptMapFile = geneTranscriptMapFile;
        this.k = k;
    }

    public Merge(final int k) {
        super();
        this.k = k;
    }

    public void setK(final int k) {
        this.k = k;
    }

    public void setSilent(final boolean status) {
        this.verbose = !status;
    }

    public void setGeneTranscriptMapFile(final String geneTranscriptMapFile) {
        this.geneTranscriptMapFile = geneTranscriptMapFile;
    }

    public void merge(final List<File> inputFiles, final String outputFile) throws IOException {
        // we will store one target index permutation for each input file in the following list:
        referenceIndexPermutation = new ObjectArrayList<int[]>();

        int maxNumberOfReads = Integer.MIN_VALUE;
        if (verbose) {
            System.out.println("Finding max number of reads...");
            System.out.flush();
        }

        int mergedReferenceIndex = 0;
        final IndexedIdentifier mergedTargetIdentifiers = new IndexedIdentifier();
        final Int2IntMap mergedTargetLengths = new Int2IntOpenHashMap();
        final IntSet numberOfReadsSet = new IntArraySet();
        int minQueryIndex = Integer.MAX_VALUE;
        for (final File inputFile : inputFiles) {
            final AlignmentReaderImpl reader = new AlignmentReaderImpl(inputFile.toString());
            reader.readHeader();
            message("Found input file with " + reader.getNumberOfTargets() + " target(s)");

            mergedReferenceIndex = constructTargetIndexPermutations(mergedReferenceIndex,
                    mergedTargetIdentifiers, mergedTargetLengths, reader);

            minQueryIndex = Math.min(minQueryIndex, reader.getSmallestSplitQueryIndex());

            numberOfReadsSet.add(reader.getNumberOfQueries());
            reader.close();

            final AlignmentTooManyHitsReader tmhReader = new AlignmentTooManyHitsReader(inputFile.toString());
            for (final int queryIndex : tmhReader.getQueryIndices()) {
                minQueryIndex = Math.min(minQueryIndex, queryIndex);
            }

        }
        if (numberOfReadsSet.size() != 1) {
            message("Aborting: the input alignments must have exactly the same number of reads, "
                    + "we found different number of reads in " + numberOfReadsSet + " input files:");
            return;

        }
        maxNumberOfReads = numberOfReadsSet.iterator().nextInt();

        message("... max number of reads was " + maxNumberOfReads);

        final AbstractAlignmentEntryFilter entryFilter = getFilter(maxNumberOfReads, minQueryIndex);
        entryFilter.setTargetIdentifiers(mergedTargetIdentifiers);

        ProgressLogger progress = new ProgressLogger(LOG);
        progress.expectedUpdates = inputFiles.size();
        progress.start();
        float totalNumberOfLogicalEntries = 0;

        message("First pass: determine which reads should be kept in the merged alignment.");
        int totalNumberOfEntries = 0;
        for (final File inputFile : inputFiles) {
            message("Scanning " + inputFile.getName());
            final AlignmentReaderImpl reader = new AlignmentReaderImpl(inputFile.toString());
            reader.readHeader();
            entryFilter.setTargetIdentifiers(reader.getTargetIdentifiers());
            while (reader.hasNext()) {
                final Alignments.AlignmentEntry entry = reader.next();
                entryFilter.inspectEntry(entry);
                ++totalNumberOfEntries;
                totalNumberOfLogicalEntries += entry.getMultiplicity();
            }
            progress.update();
            reader.close();
        }
        progress.stop();
        entryFilter.postProcessing();


        message(String.format("Found %d logical alignment entries.", (int) totalNumberOfLogicalEntries));
        message("Prepare merged too many hits information.");
        prepareMergedTooManyHits(outputFile, maxNumberOfReads, minQueryIndex,
                inputFiles.toArray(new File[inputFiles.size()]));

        message("Second pass: writing the merged alignment.");

        int wrote = 0;
        int skipped = 0;
        int skippedTooManyHits = 0;
        int skippedNotBestScore = 0;

        final AlignmentWriterImpl writer = new AlignmentWriterImpl(outputFile);
        progress = new ProgressLogger(LOG);
        progress.expectedUpdates = totalNumberOfEntries;
        progress.start();

        if (mergedTargetIdentifiers.size() > 0) {
            // set merged target info in the merged header:
            writer.setTargetIdentifiers(mergedTargetIdentifiers);
        }

        int inputFileIndex = 0;
        // use the merged too many hits info:
        final AlignmentTooManyHitsReader tmhReader = new AlignmentTooManyHitsReader(outputFile);
        final IntSet queriesIndicesAligned = new IntOpenHashSet();

        for (final File inputFile : inputFiles) {
            final String basename = inputFile.toString();
            final AlignmentReaderImpl reader = new AlignmentReaderImpl(basename);
            reader.readHeader();
            entryFilter.setTargetIdentifiers(reader.getTargetIdentifiers());
            final AlignmentTooManyHitsReader specificTmhReader = new AlignmentTooManyHitsReader(basename);
            while (reader.hasNext()) {
                final Alignments.AlignmentEntry entry = reader.next();
                progress.lightUpdate();
                //    System.out.println("Processing queryIndex "+entry.getQueryIndex());
                if (entryFilter.shouldRetainEntry(entry)) {
                    final int queryIndex = entry.getQueryIndex();
                    final int matchLength = specificTmhReader.getLengthOfMatch(queryIndex);

                    if (!tmhReader.isQueryAmbiguous(queryIndex, k, matchLength)) {
                        //switch in the mergedTargetIndex and append to writer:
                        final int newTargetIndex = referenceIndexPermutation.get(inputFileIndex)[entry.getTargetIndex()];
                        Alignments.AlignmentEntry entry1 = entry;
                        entry1 = entry1.newBuilderForType().mergeFrom(entry1).setTargetIndex(newTargetIndex).build();
                        writer.appendEntry(entry1);
                        wrote += entry.getMultiplicity();
                        queriesIndicesAligned.add(entry.getQueryIndex());
                    } else {
                        skipped += entry.getMultiplicity();
                        skippedTooManyHits += entry.getMultiplicity();
                        //     System.out.println("too many hits for queryIndex "+entry.getQueryIndex());
                        // since query hits too many locations in the reference sequences..
                    }
                } else {
                    skipped += entry.getMultiplicity();
                    skippedNotBestScore += entry.getMultiplicity();
                    // "not best score for queryIndex "+entry.getQueryIndex());
                    // since this  alignment does not have the best score of the reference locations aligned
                    // with this specific query/read.
                }
                if (((wrote + skipped) % 1000000) == 0) {
                    printStatus(wrote + skipped, wrote, skipped, skippedTooManyHits, skippedNotBestScore);
                }

            }
            reader.close();
            inputFileIndex++;
        }
        progress.stop();

        writer.setNumTargets(mergedReferenceIndex);
        final int[] targetLengths = new int[mergedReferenceIndex];
        for (int i = 0; i < mergedReferenceIndex; i++) {
            targetLengths[i] = mergedTargetLengths.get(i);
        }
        writer.setTargetLengths(targetLengths);

        printStatus((int) totalNumberOfLogicalEntries, wrote, skipped, skippedTooManyHits, skippedNotBestScore);
        if (verbose) {
            writer.printStats(System.out);
        }
        entryFilter.printStats();
        final float numQuerySequences = maxNumberOfReads;
        final float percentWritten = ((float) wrote) * 100f / totalNumberOfLogicalEntries;
        final float skippedPercent = ((float) skipped * 100f / totalNumberOfLogicalEntries);
        final float skippedTooManyHitsPercent =
                ((float) skippedTooManyHits) * 100f / totalNumberOfLogicalEntries;
        final float skippedNotBestScorePercent =
                ((float) skippedNotBestScore * 100f / totalNumberOfLogicalEntries);
        float percentAligned = queriesIndicesAligned.size();
        percentAligned /= numQuerySequences;
        percentAligned *= 100f;

        final double percentEntriesRetained = ((double) wrote) / numQuerySequences * 100d;
        writer.putStatistic("entries.written.number", wrote);
        writer.putStatistic("entries.written.percent", percentWritten);
        writer.putStatistic("entries.input.logical.number", totalNumberOfLogicalEntries);
        writer.putStatistic("entries.input.number", totalNumberOfEntries);
        writer.putStatistic("skipped.Total.percent", skippedPercent);
        writer.putStatistic("skipped.TooManyHits.percent", skippedTooManyHitsPercent);
        writer.putStatistic("skipped.TooManyHits.number", skippedTooManyHits);
        writer.putStatistic("skipped.NotBestScore.percent", skippedNotBestScorePercent);
        writer.putStatistic("skipped.NotBestScore.number", skippedNotBestScore);
        writer.putStatistic("entries.retained.percent", percentEntriesRetained);
        writer.putStatistic("number.Query", maxNumberOfReads);
        writer.putStatistic("number.Target", mergedTargetIdentifiers.size());
        writer.putStatistic("reads.aligned.number", queriesIndicesAligned.size());
        writer.putStatistic("reads.aligned.percent", percentAligned);
        writer.close();

        message("Percent aligned: " + percentAligned);
    }

    private void printStatus(final int totalNumberOfLogicalEntries, final int wrote, final int skipped, final int skippedTooManyHits, final int skippedNotBestScore) {
        message(String.format("Wrote %,d  skipped: %,d %f%% too many hits %f%% notBestScore: %f%%",
                wrote, skipped,
                ((float) skipped / ((float) totalNumberOfLogicalEntries) * 100f),
                (float) skippedTooManyHits / ((float) totalNumberOfLogicalEntries) * 100f,
                ((float) skippedNotBestScore / ((float) totalNumberOfLogicalEntries) * 100f)));
    }

    public static void prepareMergedTooManyHits(
            final String outputFilename, final int numberOfReads, final int minQueryIndex,
            final File[] inputFiles) throws IOException {
        final String[] inputFilenames = new String[inputFiles.length];
        int i = 0;
        for (final File file : inputFiles) {
            inputFilenames[i++] = file.toString();
        }
        prepareMergedTooManyHits(outputFilename, numberOfReads, minQueryIndex, inputFilenames);
    }

    /**
     * Merge too many hits data structures across alignments.
     *
     * @param outputFile    Destination basename for merged too many hits data structure.
     * @param numberOfReads the number of reads
     * @param minQueryIndex the minimum query index, in the case where we are working with a split read situation
     * @param basenames     Input alignment basenames
     * @return number of reads
     * @throws IOException error processing
     */
    public static int prepareMergedTooManyHits(
            final String outputFile, int numberOfReads, final int minQueryIndex,
            final String... basenames) throws IOException {
        final Int2IntMap tmhMap = new Int2IntAVLTreeMap();
        tmhMap.defaultReturnValue(0);
        // accumulate too many hits over all the input alignments:
        int consensusAlignerThreshold = Integer.MAX_VALUE;
        LOG.debug("TMH first pass");

        // numberOfReads does not include the TMH reads that are past the aligned entries.
        int maxQueryIndex = numberOfReads - 1;
        int maxCapacity = 0;
        for (final String basename : basenames) {
            LOG.debug("processing " + basename);
            final AlignmentTooManyHitsReader tmhReader = new AlignmentTooManyHitsReader(basename);
            IntSet queryIndices = tmhReader.getQueryIndices();
            maxCapacity = Math.max(maxCapacity, queryIndices.size());
            for (final int queryIndex : queryIndices) {
                maxQueryIndex = Math.max(maxQueryIndex, queryIndex);
            }
            tmhReader.close();
        }
        LOG.debug("TMH second pass");
        numberOfReads = maxQueryIndex + 1;
        final Int2IntMap queryIndex2MaxDepth = new Int2IntAVLTreeMap();
        queryIndex2MaxDepth.defaultReturnValue(-1);
        // calculate maxDepth for each query sequence:
        for (final String basename : basenames) {
            LOG.debug("processing " + basename);
            final AlignmentTooManyHitsReader tmhReader = new AlignmentTooManyHitsReader(basename);
            //   System.out.println("Found aligner-threshold=" + tmhReader.getAlignerThreshold());
            consensusAlignerThreshold = Math.min(consensusAlignerThreshold, tmhReader.getAlignerThreshold());

            final IntSet queryIndices = tmhReader.getQueryIndices();
            for (final int queryIndex : queryIndices) {
                final int currentMatchLength = tmhReader.getLengthOfMatch(queryIndex);
                final int maxDepth = Math.max(currentMatchLength, queryIndex2MaxDepth.get(queryIndex));
                if (maxDepth != -1) {
                    queryIndex2MaxDepth.put(queryIndex, maxDepth);
                }
            }
            tmhReader.close();
        }
        boolean foundDepth = false;

        ProgressLogger pg = new ProgressLogger(LOG);
        pg.priority = Level.DEBUG;
        pg.expectedUpdates = basenames.length;
        pg.start("TMH third pass");

        for (final String basename : basenames) {
            LOG.debug("processing " + basename);
            final AlignmentTooManyHitsReader tmhReader = new AlignmentTooManyHitsReader(basename);
            final IntSet queryIndices = tmhReader.getQueryIndices();
            for (final int queryIndex : queryIndices) {
                final int depthForBasename = tmhReader.getLengthOfMatch(queryIndex);

                if (depthForBasename == queryIndex2MaxDepth.get(queryIndex)) {
                    if (depthForBasename != 1) {
                        final int newValue = tmhMap.get(queryIndex) + tmhReader.getNumberOfHits(queryIndex);
                        tmhMap.put(queryIndex, newValue);
                        foundDepth = true;
                    }
                }
            }
            tmhReader.close();
            pg.update();

        }
        pg.done();
        pg.start("TMH fourth pass");

        if (!foundDepth) {
            System.out.println("Warning: could not find depth/max-length-of-match in too many hits information.");
        }
        final AlignmentTooManyHitsWriter mergedTmhWriter = new AlignmentTooManyHitsWriter(outputFile, consensusAlignerThreshold);

        pg.expectedUpdates = tmhMap.size();
        pg.displayFreeMemory=true;
        for (final Int2IntMap.Entry entry : tmhMap.int2IntEntrySet()) {
            final int queryIndex=entry.getIntKey();
            final int value=entry.getIntValue();
            mergedTmhWriter.append(queryIndex, value, queryIndex2MaxDepth.get(queryIndex));
            pg.lightUpdate();
        }
        mergedTmhWriter.close();
        pg.done();
        return numberOfReads;
    }

    private int constructTargetIndexPermutations(int mergedReferenceIndex,
                                                 final IndexedIdentifier mergedTargetIdentifiers,
                                                 final Int2IntMap mergedTargetLengths,
                                                 final AlignmentReaderImpl reader) {
        final int[] targetLengths = reader.getTargetLength();
        final int targetLengthCount = ArrayUtils.getLength(targetLengths);

        // merge targets : each target in the merged alignment will receives a merged targetIndex.
        final Int2IntMap tempPermutation = new Int2IntOpenHashMap();

        final IndexedIdentifier targetIdentifers = reader.getTargetIdentifiers();
        final DoubleIndexedIdentifier backward = new DoubleIndexedIdentifier(targetIdentifers);

        for (int i = 0; i < reader.getNumberOfTargets(); i++) {

            MutableString id = backward.size() != 0 ? backward.getId(i) :
                    new MutableString(String.valueOf(mergedTargetIdentifiers.size()));
            final int newIndex = mergedTargetIdentifiers.registerIdentifier(id);
            tempPermutation.put(i, newIndex);

            final MutableString targetId = backward.getId(i);
            if (targetId != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("tempPermutation associating targetId: " + targetId
                            + " to index " + newIndex);
                }
                mergedTargetIdentifiers.registerIdentifier(targetId);
                final int targetLength;
                if (i < targetLengthCount) {
                    targetLength = targetLengths[i];
                } else {
                    targetLength = 0;
                }
                mergedTargetLengths.put(newIndex, targetLength);
            } else {

            }

        }

        // transfer target index permutation to array for fast access:
        // final int size = tempPermutation.size();
        int size = 0;
        for (final int key : tempPermutation.keySet()) {
            size = Math.max(key + 1, size);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating array with size " + size);
        }
        final int[] newPermutation = new int[size];

        for (final int key : tempPermutation.keySet()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("about to get key : " + key);
            }
            newPermutation[key] = tempPermutation.get(key);
        }
        referenceIndexPermutation.add(newPermutation);
        return mergedTargetIdentifiers.size();
    }

    /**
     * Override this method to change the entry filter. By default, BestScoreAlignmentFilter is used
     * when no transcript information is provided.
     *
     * @param maxNumberOfReads The number of reads, may be used to initialize the filter.
     * @return An alignment filter.
     * @throws FileNotFoundException If the transcript to gene mapping file cannot be found.
     */
    public AbstractAlignmentEntryFilter getFilter(final int maxNumberOfReads, final int minQueryIndex) throws FileNotFoundException {
        final AbstractAlignmentEntryFilter entryFilter;
        if (geneTranscriptMapFile != null) {
            entryFilter = new TranscriptBestScoreAlignmentFilter(geneTranscriptMapFile, k, maxNumberOfReads, minQueryIndex);
        } else {
            entryFilter = new BestScoreAmbiguityAlignmentFilter(k, maxNumberOfReads, minQueryIndex);
        }
        return entryFilter;
    }

    private void message(final String message) {
        if (verbose) {
            System.out.println(message);
        } else {
            LOG.debug(message);
        }
    }
}
