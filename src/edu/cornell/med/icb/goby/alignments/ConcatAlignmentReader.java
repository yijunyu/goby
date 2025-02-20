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

import edu.cornell.med.icb.goby.alignments.perms.ConcatenatePermutations;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.*;

/**
 * Read over a set of alignments. This aligner concatenates entries from the input alignment.
 * Reference sequences must match exactly across the input alignments.
 * Query are assumed to be entirely distinct and will be treated as independent observations (e.g.,
 * reads from multiple independent samples). To this effect, alignment entries read from
 * different input basenames, which would otherwise share an identical query index,
 * are renumbered with distinct query indices.
 *
 * @author Fabien Campagne
 *         Date: May 20, 2009
 *         Time: 5:06:01 PM
 */
public class ConcatAlignmentReader extends AbstractConcatAlignmentReader {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(ConcatAlignmentReader.class);

    protected final AlignmentReader[] readers;
    protected final IntSet readersWithMoreEntries;

    /**
     * One element per reader.
     */
    private final int[] numQueriesPerReader;

    /**
     * One element per reader.
     */
    private final int[] queryIndexOffset;

    protected int activeIndex;
    protected boolean adjustQueryIndices = true;
    private int numberOfAlignedReads;
    /**
     * Permutations for read origin indices. The first index is the index of the input reader.
     * The second index is the original read origin index in the input reader. The value is the
     * permuted read origin index for the concatenated entry.
     */
    protected int[][] readOriginPermutations;
    private boolean needsPermutation;
    private String[] basenames;
    // indicates whether a reader has read a origin information:
    protected boolean[] hasReadOrigin;


    /**
     * Construct an alignment reader over a RepositionableInputStreamset of alignments.
     * Please note that the constructor access the header of each individual alignment to
     * check reference sequence identity and obtain the number of queries in each input alignment.
     * This version uses adjustQueryIndices as the default true.
     *
     * @param basenames Basenames of the individual alignemnts to combine.
     * @throws IOException If an error occurs reading the header of the alignments.
     */
    public ConcatAlignmentReader(final String... basenames) throws IOException {
        this(new DefaultAlignmentReaderFactory(), true, basenames);
    }

    /**
     * Construct an alignment reader over a set of alignments.
     * Please note that the constructor access the header of each individual alignment to
     * check reference sequence identity and obtain the number of queries in each input alignment.
     * This version uses adjustQueryIndices as the default true.
     *
     * @param basenames          Basenames of the individual alignemnts to combine.
     * @param adjustQueryIndices if we need to adjustQueryIndices
     * @throws IOException If an error occurs reading the header of the alignments.
     */
    public ConcatAlignmentReader(boolean adjustQueryIndices, final String... basenames) throws IOException {
        this(new DefaultAlignmentReaderFactory(), adjustQueryIndices, basenames);
    }

    /**
     * Construct an alignment reader over a set of alignments.
     * Please note that the constructor access the header of each individual alignment to
     * check reference sequence identity and obtain the number of queries in each input alignment.
     *
     * @param alignmentReaderFactory Factory to create new alignmentReaders.
     * @param adjustQueryIndices     if we need to adjustQueryIndices
     * @param basenames              Basenames of the individual alignemnts to combine.
     * @throws IOException If an error occurs reading the header of the alignments.
     */
    public ConcatAlignmentReader(final AlignmentReaderFactory alignmentReaderFactory,
                                 final boolean adjustQueryIndices, final String... basenames) throws IOException {
        super(true, null);
        this.adjustQueryIndices = adjustQueryIndices;
        readers = alignmentReaderFactory.createReaderArray(basenames.length);
        hasReadOrigin = new boolean[basenames.length];
        readersWithMoreEntries = new IntArraySet();

        for (int readerIndex = 0; readerIndex < basenames.length; readerIndex++) {
            readers[readerIndex] = alignmentReaderFactory.createReader(basenames[readerIndex]);
            readersWithMoreEntries.add(readerIndex);
            sampleBasenames.add(basenames[readerIndex]);
        }
        numQueriesPerReader = new int[basenames.length];
        queryIndexOffset = new int[basenames.length];
        concatenatePerms = new ConcatenatePermutations(basenames);
        this.basenames = basenames;
        readHeader();
    }

    /**
     * Obtain the concatenate permutation helper. This helper indicates if a permutation had to be created to maintain
     * query index mapping and let you move such a temporary permutation file to a final destination.
     *
     * @return ConcatenatePermutations helper, which may have created a temporary global permutation file for the
     * concatenated input alignments.
     */
    public ConcatenatePermutations getConcatPerm() {
        return concatenatePerms;
    }

    /**
     * Construct an alignment reader over a set of alignments.
     * Please note that the constructor access the header of each individual alignment to
     * check reference sequence identity and obtain the number of queries in each input alignment.
     *
     * @param alignmentReaderFactory Factory to create new alignmentReaders.
     * @param adjustQueryIndices     if we need to adjustQueryIndices
     * @param startReferenceIndex    Index of the reference for the start position.
     * @param startPosition          Position on the reference for the start position.
     * @param endReferenceIndex      Index of the reference for the end position.
     * @param endPosition            Position on the reference for the end position.
     * @param basenames              Basenames of the individual alignemnts to combine.
     * @throws IOException If an error occurs reading the header of the alignments.
     */
    public ConcatAlignmentReader(final AlignmentReaderFactory alignmentReaderFactory,
                                 final boolean adjustQueryIndices,
                                 final int startReferenceIndex,
                                 final int startPosition,
                                 final int endReferenceIndex,
                                 final int endPosition,
                                 final String... basenames) throws IOException {
        super(true, null);
        this.adjustQueryIndices = adjustQueryIndices;
        readers = alignmentReaderFactory.createReaderArray(basenames.length);
        hasReadOrigin = new boolean[basenames.length];
        readersWithMoreEntries = new IntArraySet();
        int readerIndex = 0;
        for (final String basename : basenames) {
            readers[readerIndex] = alignmentReaderFactory.createReader(basename,
                    startReferenceIndex, startPosition,
                    endReferenceIndex, endPosition);
            readersWithMoreEntries.add(readerIndex);
            sampleBasenames.add(basename);
            readerIndex++;
        }
        numQueriesPerReader = new int[basenames.length];
        queryIndexOffset = new int[basenames.length];
        concatenatePerms = new ConcatenatePermutations(basenames);
        this.basenames = basenames;
        readHeader();
    }

    private ConcatenatePermutations concatenatePerms;

    /**
     * Read the header of this alignment.
     *
     * @throws java.io.IOException If an error occurs.
     */
    @Override
    public final void readHeader() throws IOException {
        if (!isHeaderLoaded()) {

            adjustQueryIndices |= concatenatePerms.needsPermutation();
            needsPermutation = concatenatePerms.needsPermutation();
            final IntSet targetNumbers = new IntArraySet();
            int readerIndex = 0;
            ObjectList<String> alignerNames = new ObjectArrayList<String>();
            ObjectList<String> alignerVersions = new ObjectArrayList<String>();

            numberOfQueries = 0;
            smallestQueryIndex = Integer.MAX_VALUE;
            largestQueryIndex = adjustQueryIndices ? Integer.MIN_VALUE : 0;
            readOriginPermutations = new int[readers.length][];

            for (final AlignmentReader reader : readers) {
                reader.readHeader();
                String alignerName = reader.getAlignerName();
                String alignerVersion = reader.getAlignerVersion();
                if (!(alignerNames.contains(alignerName) && alignerVersions.contains(alignerVersion))) {
                    alignerNames.add(alignerName);
                    alignerVersions.add(alignerVersion);
                }

                smallestQueryIndex = Math.min(reader.getSmallestSplitQueryIndex(), smallestQueryIndex);
                largestQueryIndex = adjustQueryIndices ?
                        Math.max(largestQueryIndex, 0) + 1 + reader.getLargestSplitQueryIndex() :
                        Math.max(reader.getLargestSplitQueryIndex(), largestQueryIndex);

                targetNumbers.add(reader.getNumberOfTargets());
                final int numQueriesForReader = reader.getNumberOfQueries();
                numQueriesPerReader[readerIndex] = numQueriesForReader;
                if (adjustQueryIndices) {
                    numberOfQueries += numQueriesForReader;
                } else {

                    numberOfQueries = Math.max(numberOfQueries, numQueriesForReader);
                }
                numberOfAlignedReads += reader.getNumberOfAlignedReads();
                ReadOriginInfo readOriginInfo = reader.getReadOriginInfo();
                ReadGroupHelper readGroupHelper = getReadGroupHelper();
                if (readOriginInfo.size() > 0 && readGroupHelper.isOverrideReadGroups()) {
                    LOG.warn("Source contained read origin info, but overriding.");
                }
                if (readGroupHelper.isOverrideReadGroups()) {
                    readOriginInfo = makeDefaultReadOriginInfo(reader);
                }
                mergeReadOrigins(readerIndex, readOriginInfo.getPbList(), readers.length);

                readerIndex++;
            }
            alignerName = alignerNames.toString();
            alignerVersion = alignerVersions.toString();
            if (targetNumbers.size() != 1) {
                throw new IllegalArgumentException("The number of targets must match exactly across the input basenames. Found " + targetNumbers.toString());
            } else {
                this.numberOfTargets = targetNumbers.iterator().nextInt();
            }
            targetIdentifiers = new IndexedIdentifier();
            // target information may have more or less targets depending on the reader, but indices must match across
            // all readers:
            boolean error = false;
            for (final AlignmentReader reader : readers) {
                IndexedIdentifier targetIds = reader.getTargetIdentifiers();
                for (MutableString key : targetIds.keySet()) {
                    if (!targetIdentifiers.containsKey(key)) {
                        targetIdentifiers.put(key, targetIds.getInt(key));
                    } else {
                        final int globalValue = targetIdentifiers.getInt(key);
                        final int localValue = targetIds.getInt(key);
                        if (globalValue != localValue) {
                            error = true;
                            LOG.error(
                                    String.format("target indices must match across input alignments. Key %s was found with the distinct values global: %d local %d in alignment %s",
                                            key, globalValue, localValue, reader.basename()));
                        }
                    }
                }
            }
            if (error) {
                throw new RuntimeException("target indices must match across input alignments.");
            }
            targetLengths = new int[targetIdentifiers.size()];
            // keep the maximum length across all readers. We do this to retrieve targetLength over alignments merged
            // from pieces that do not have entries for all target.
            for (int targetIndex = 0; targetIndex < targetIdentifiers.size(); targetIndex++) {
                int maxLength = -1;
                for (final AlignmentReader reader : readers) {
                    final int[] readerLengths = reader.getTargetLength();
                    if (readerLengths != null && readerLengths.length > targetIndex) {
                        maxLength = Math.max(readerLengths[targetIndex], maxLength);
                        targetLengths[targetIndex] = maxLength;
                    }
                }

            }
            // calculate offsets needed to adjustQueryIndices
            for (int i = 0; i < queryIndexOffset.length; i++) {

                queryIndexOffset[i] = adjustQueryIndices ? i == 0 ? 0 : readers[i - 1].getLargestSplitQueryIndex() + 1 : 0;

            }

        }


        setHeaderLoaded(true);
    }

    private ReadOriginInfo makeDefaultReadOriginInfo(AlignmentReader reader) {

        List<Alignments.ReadOriginInfo> list = new ObjectArrayList<Alignments.ReadOriginInfo>();
        Alignments.ReadOriginInfo.Builder builder = Alignments.ReadOriginInfo.newBuilder();
        builder.setOriginIndex(0);
        String basename = reader.basename();
        ReadGroupHelper readGroupHelper = getReadGroupHelper();
        builder.setOriginId(readGroupHelper.getId(basename));
        builder.setSample(readGroupHelper.getSample(basename));
        // Assume only one barcode per sample:
        builder.setPlatformUnit(readGroupHelper.getSample(basename));
        builder.setPlatform(readGroupHelper.getPlatform(basename));
        list.add(builder.build());
        return new ReadOriginInfo(list);
    }

    public ReadGroupHelper getReadGroupHelper() {
        return new ReadGroupHelper();
    }

    private int nextAvailableReadOriginIndex = 0;

    private void mergeReadOrigins(final int readerIndex, final List<Alignments.ReadOriginInfo> readOriginInfo, final int numberOfReaders) {
        hasReadOrigin[readerIndex] = !readOriginInfo.isEmpty();
        for (final Alignments.ReadOriginInfo roi : readOriginInfo) {
            final int[] permutation = new int[readOriginInfo.size()];
            readOriginPermutations[readerIndex] = permutation;
            // for (int i = 0; i < numberOfReaders; i++) {
            final int newReadOriginIndex = nextAvailableReadOriginIndex++;
            permutation[roi.getOriginIndex()] = newReadOriginIndex;
            final Alignments.ReadOriginInfo.Builder newRoi = Alignments.ReadOriginInfo.newBuilder(roi);
            newRoi.setOriginIndex(newReadOriginIndex);
            mergedReadOriginInfoList.add(newRoi.build());

        }

    }


    protected int mergedQueryIndex(final int readerIndex, final int queryIndex) {
        if (needsPermutation) {
            try {
                return concatenatePerms.combine(readerIndex, queryIndex);
            } catch (IOException e) {
                LOG.error("Unable to retrieve original query index from permutation for reader " + readerIndex + " basename=" + basenames[readerIndex], e);
                return -1;
            }
        } else {
            return adjustQueryIndices ? queryIndexOffset[readerIndex] + queryIndex : queryIndex;
        }

    }

    /**
     * Iterator over alignment entries.
     *
     * @return an iterator over the alignment entries.
     */
    public final Iterator<Alignments.AlignmentEntry> iterator() {
        return this;
    }

    /**
     * Returns true if the input has more entries.
     *
     * @return true if the input has more entries, false otherwise.
     */
    public boolean hasNext() {
        while (!readersWithMoreEntries.isEmpty()) {
            activeIndex = readersWithMoreEntries.iterator().nextInt();
            final AlignmentReader reader = readers[activeIndex];
            final boolean hasNext = reader.hasNext();
            if (!hasNext) {
                readersWithMoreEntries.remove(activeIndex);
            } else {
                return true;
            }

        }
        return false;
    }


    /**
     * @return The list of aligner names with duplicates removed
     */
    @Override
    public String getAlignerName() {
        return super.getAlignerName();
    }

    /**
     * @return The list of aligner versions with duplicates removed
     */
    @Override
    public String getAlignerVersion() {
        return super.getAlignerVersion();
    }


    /**
     * Returns the next alignment entry from the input stream.
     *
     * @return the alignment read entry from the input stream.
     */
    public Alignments.AlignmentEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        } else {
            final Alignments.AlignmentEntry alignmentEntry = readers[activeIndex].next();
            final int queryIndex = alignmentEntry.getQueryIndex();
            final int newQueryIndex = mergedQueryIndex(activeIndex, queryIndex);

            Alignments.AlignmentEntry.Builder builder = alignmentEntry.newBuilderForType().mergeFrom(alignmentEntry);
            if (adjustQueryIndices && newQueryIndex != queryIndex) {

                builder = builder.setQueryIndex(newQueryIndex);
            }
            if (adjustSampleIndices) {
                builder = builder.setSampleIndex(activeIndex);
            }
            builder = processReadGroups(alignmentEntry, builder, activeIndex);
            return builder.build();
        }
    }

    protected Alignments.AlignmentEntry.Builder processReadGroups(Alignments.AlignmentEntry alignmentEntry,
                                                                  Alignments.AlignmentEntry.Builder builder, final int readerIndex) {
        if (alignmentEntry.hasReadOriginIndex() && hasReadOrigin[readerIndex]) {
            // remove conflicts by permuting read origin index to the concatenated read origin indices:
            builder = builder.setReadOriginIndex(readOriginPermutations[readerIndex][alignmentEntry.getReadOriginIndex()]);
        }
        return builder;
    }

    /**
     * This operation is not supported by this iterator.
     */
    public void remove() {
        throw new UnsupportedOperationException("Cannot remove from a reader.");
    }

    /**
     * @deprecated
     */

    @Deprecated
    public void setAdjustQueryIndices(final boolean adjustQueryIndices) {
        throw new UnsupportedOperationException("This operation is unsafe. Set flag through the constructor.");
    }

    /**
     * Obtain statistics about this alignment as a Java property instance.
     *
     * @return statistics about this alignment
     */
    public Properties getStatistics() {
        int index = 1;
        final Properties result = new Properties();
        for (final AlignmentReader reader : this.readers) {
            final Properties localProps = reader.getStatistics();
            for (final Map.Entry<Object, Object> localProp : localProps.entrySet()) {
                result.put("part" + index + "." + localProp.getKey().toString(), localProp.getValue());
            }
            index++;
        }
        return result;
    }

    public int getNumberOfAlignedReads() {
        return numberOfAlignedReads;
    }

    /**
     * Close the underlying readers.
     *
     * @throws IOException if an I/O error occurs
     */
    public void close() throws IOException {
        for (final AlignmentReader reader : readers) {
            reader.close();
        }
    }

    @Override
    public ReferenceLocation getMinLocation() throws IOException {
        ReferenceLocation minLocation = readers[0].getMinLocation();

        for (AlignmentReader reader : this.readers) {

            ReferenceLocation loc = reader.getMinLocation();
            if (loc.compareTo(minLocation) < 0) {
                minLocation = loc;
            }

        }
        return minLocation;
    }

    @Override
    public ReferenceLocation getMaxLocation() throws IOException {
        ReferenceLocation maxLocation = readers[0].getMaxLocation();

        for (AlignmentReader reader : this.readers) {

            ReferenceLocation loc = reader.getMaxLocation();
            if (loc.compareTo(maxLocation) > 0) {
                maxLocation = loc;
            }

        }
        return maxLocation;
    }

    public ObjectList<ReferenceLocation> getLocationsByBytes(int numBytesPerSlice) throws IOException {
        readHeader();
        ObjectSet<ReferenceLocation> result = new ObjectOpenHashSet<ReferenceLocation>();
        int numReaders = this.readers.length;
        long byteAccumulation = 0;
        int readerIndex = 0;
        ObjectList<ReferenceLocation> locations[] = new ObjectList[numReaders];
        int[] locationIndices = new int[numReaders];
        int maxLocationIndices = -1;
        for (AlignmentReader reader : this.readers) {

            locations[readerIndex] = reader.getLocationsByBytes(numBytesPerSlice / numReaders);
            maxLocationIndices = Math.max(maxLocationIndices, locations[readerIndex].size());
            readerIndex++;
        }

        long sizeSinceLastSlice = 0;
        // explicitly put start and end locations into the result:
        ReferenceLocation startLocation = new ReferenceLocation(Integer.MAX_VALUE, Integer.MAX_VALUE);
        ReferenceLocation endLocation = new ReferenceLocation(0, 0);
        for (readerIndex = 0; readerIndex < numReaders; readerIndex++) {
            ReferenceLocation readerFirst = locations[readerIndex].get(0);
            ReferenceLocation readerLast = locations[readerIndex].get(locations[readerIndex].size() - 1);
            if (readerFirst.compareTo(startLocation) < 0) {
                startLocation = readerFirst;
            }
            if (readerLast.compareTo(endLocation) > 0) {
                endLocation = readerLast;
            }
        }
        startLocation = getMinLocation();
        endLocation = getMaxLocation();
        for (int i = 0; i < maxLocationIndices; i++) {

            for (readerIndex = 0; readerIndex < numReaders; readerIndex++) {
                if (i < locations[readerIndex].size()) {
                    assert readerIndex < locations.length : "readerIndex must be smaller than locations length";
                    assert readerIndex < locationIndices.length : "i must be smaller than locationIndices length";

                    ReferenceLocation readerLocation = locations[readerIndex]
                            .get(locationIndices[readerIndex]);
                    sizeSinceLastSlice += readerLocation.compressedByteAmountSincePreviousLocation;
                }
                locationIndices[readerIndex]++;
            }
            if (sizeSinceLastSlice > numBytesPerSlice) {
                ObjectList<ReferenceLocation> currentLocations = new ObjectArrayList<ReferenceLocation>();
                for (readerIndex = 0; readerIndex < numReaders; readerIndex++) {

                    if (locationIndices[readerIndex] == 0 || locationIndices[readerIndex] < locations[readerIndex].size()) {
                        ReferenceLocation readerLocation = locations[readerIndex].get(locationIndices[readerIndex]);
                        currentLocations.add(readerLocation);
                    }
                }
                Collections.sort(currentLocations);
                int medianIndex = currentLocations.size() / 2;
                if (medianIndex < currentLocations.size()) {
                    ReferenceLocation medianLocation = currentLocations.get(medianIndex);
                    if (!result.contains(medianLocation)) {
                        result.add(medianLocation);
                    }
                }
                sizeSinceLastSlice = 0;
            }
        }
        ObjectList<ReferenceLocation> list = new ObjectArrayList<ReferenceLocation>();
        result.add(getMinLocation());
        result.add(getMaxLocation());
        list.addAll(result);
        Collections.sort(list);
        return list;
    }

    public ObjectList<ReferenceLocation> getLocations(int modulo) throws IOException {
        readHeader();
        ObjectSet<ReferenceLocation> result = new ObjectOpenHashSet<ReferenceLocation>();

        for (AlignmentReader reader : this.readers) {
            result.addAll(reader.getLocations(modulo));
        }
        ObjectList<ReferenceLocation> list = new ObjectArrayList<ReferenceLocation>();
        list.addAll(result);
        Collections.sort(list);
        return list;
    }

    ObjectArrayList<Alignments.ReadOriginInfo> mergedReadOriginInfoList = new ObjectArrayList<Alignments.ReadOriginInfo>();

    /**
     * Return the read origin infos for the concatenated alignment.
     *
     * @return A list of read origin info messages, adjusted to remove conflicts.
     */
    public ReadOriginInfo getReadOriginInfo() {
        return new ReadOriginInfo(mergedReadOriginInfoList);
    }

    private String startOffsetArgument;
    private String endOffsetArgument;

    /**
     * Restrict concatenation to a slice of the inputs. Offset arguments are either long byte offsets
     * in a string, or strings in the format ref,genomic-pos, where ref is the identifier of the reference
     * sequence where the slice stats/ends and genomic-pos is the corresponding genomic position on that reference.
     *
     * @param startOffsetArgument
     * @param endOffsetArgument
     */
    public void setStartEndOffsets(String startOffsetArgument, String endOffsetArgument) {
        this.startOffsetArgument = startOffsetArgument;
        this.endOffsetArgument = endOffsetArgument;
    }


}
