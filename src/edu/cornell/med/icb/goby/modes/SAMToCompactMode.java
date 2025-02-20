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

package edu.cornell.med.icb.goby.modes;

import com.google.protobuf.ByteString;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import edu.cornell.med.icb.goby.alignments.*;
import edu.cornell.med.icb.goby.alignments.perms.QueryIndexPermutation;
import edu.cornell.med.icb.goby.alignments.perms.ReadNameToIndex;
import edu.cornell.med.icb.goby.compression.MessageChunksWriter;
import edu.cornell.med.icb.goby.readers.sam.*;
import edu.cornell.med.icb.goby.reads.DualRandomAccessSequenceCache;
import edu.cornell.med.icb.goby.reads.QualityEncoding;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceInterface;
import edu.cornell.med.icb.goby.util.dynoptions.DynamicOptionClient;
import edu.cornell.med.icb.goby.util.dynoptions.DynamicOptionRegistry;
import edu.cornell.med.icb.goby.util.dynoptions.RegisterThis;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import net.sf.samtools.*;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Converts alignments in the SAM or BAM format to the compact alignment format.
 *
 * @author Kevin Dorff
 * @author Fabien Campagne
 */
public class SAMToCompactMode extends AbstractGobyMode {

    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = Logger.getLogger(SAMToCompactMode.class);

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "sam-to-compact";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Converts alignments in the BAM or SAM "
            + "format to the compact alignment format (new version that uses SamRecordParser).";

    /**
     * Native reads output from aligner.
     */
    protected String samBinaryFilename;

    private int dummyQueryIndex;

    private QualityEncoding qualityEncoding = QualityEncoding.SANGER;
    /**
     * Flag to indicate if log4j was configured.
     */
    private boolean debug;
    private boolean runningFromCommandLine = false;
    private RandomAccessSequenceInterface genome;
    private boolean preserveAllTags;
    private boolean preserveAllMappedQuals;
    private boolean storeReadOrigin;
    private boolean preserveReadName;

    @RegisterThis
    public static DynamicOptionClient doc = new DynamicOptionClient(SAMToCompactMode.class,
            "ignore-read-origin:boolean, When this flag is true do not import read groups.:false"
    );
    private boolean preserveSoftClips;
    private int numberOfReads;
    private int numberOfReadsFromCommandLine;
    private int largestQueryIndex;
    private int smallestQueryIndex;
    private String inputFile;
    private boolean thirdPartyInput = true;
    private int mParameter = 1;
    private String outputFile;
    private boolean readNamesAreQueryIndices;


    public static DynamicOptionClient doc() {
        return doc;
    }

    public String getSamBinaryFilename() {
        return samBinaryFilename;
    }

    public void setSamBinaryFilename(final String samBinaryFilename) {
        this.samBinaryFilename = samBinaryFilename;
    }


    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }

    /**
     * Get the quality encoding scale used for the input fastq file.
     *
     * @return the quality encoding scale used for the input fastq file
     */
    public QualityEncoding getQualityEncoding() {
        return qualityEncoding;
    }

    /**
     * Set the quality encoding scale to be used for the input fastq file.
     * Acceptable values are "Illumina", "Sanger", and "Solexa".
     *
     * @param qualityEncoding the quality encoding scale to be used for the input fastq file
     */
    public void setQualityEncoding(final QualityEncoding qualityEncoding) {
        this.qualityEncoding = qualityEncoding;
    }

    /**
     * Get if the SAM/BAM readName should be preserved into the compact-alignment file.
     * Generally this is not recommended but for testing this can be useful.
     *
     * @return if..
     */
    public boolean isPreserveReadName() {
        return preserveReadName;
    }

    /**
     * Set if the SAM/BAM readName should be preserved into the compact-alignment file.
     * Generally this is not recommended but for testing this can be useful.
     *
     * @param preserveReadName if..
     */
    public void setPreserveReadName(final boolean preserveReadName) {
        this.preserveReadName = preserveReadName;
    }

    /**
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws java.io.IOException error parsing
     * @throws com.martiansoftware.jsap.JSAPException
     *                             error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args) throws IOException, JSAPException {
        // configure baseclass

        final JSAPResult jsapResult = parseJsapArguments(args);
        inputFile = jsapResult.getString("input");
        outputFile = jsapResult.getString("output");

        readNamesAreQueryIndices = jsapResult.getBoolean("read-names-are-query-indices");
        preserveSoftClips = jsapResult.getBoolean("preserve-soft-clips");
        preserveAllTags = jsapResult.getBoolean("preserve-all-tags");
        preserveAllMappedQuals = jsapResult.getBoolean("preserve-all-mapped-qualities");
        storeReadOrigin = !doc().getBoolean("ignore-read-origin");
        preserveReadName = jsapResult.getBoolean("preserve-read-name");
        System.out.printf("Store read origin: %b%n", storeReadOrigin);
        final String genomeFilename = jsapResult.getString("input-genome");
        if (genomeFilename != null) {
            System.err.println("Loading genome " + genomeFilename);
            final DualRandomAccessSequenceCache aGenome = new DualRandomAccessSequenceCache();
            try {
                aGenome.load(genomeFilename);
                genome = aGenome;
            } catch (ClassNotFoundException e) {
                System.err.println("Unable to load genome.");
                System.exit(1);
            }
            System.err.println("Done loading genome ");
        }
        mParameter = jsapResult.getInt("ambiguity-threshold");
        numberOfReadsFromCommandLine = jsapResult.getInt("number-of-reads");
        qualityEncoding = QualityEncoding.valueOf(jsapResult.getString("quality-encoding").toUpperCase());
        sortedInput = jsapResult.getBoolean("sorted");
        largestQueryIndex = numberOfReadsFromCommandLine;
        smallestQueryIndex = 0;
        // don't even dare go through the debugging code if log4j was not configured. The debug code
        // is way too slow to run unintentionally in production!
        debug = Util.log4JIsConfigured();
        DynamicOptionRegistry.register(MessageChunksWriter.doc());
        DynamicOptionRegistry.register(AlignmentWriterImpl.doc());
        DynamicOptionRegistry.register(QueryIndexPermutation.doc());
        runningFromCommandLine = true;
        return this;
    }

    @Override
    public void execute() throws IOException {
        // read target/query identifier lookup table, and initialize output alignment
        // file with this information

        // initialize too-many-hits output file
        final AlignmentTooManyHitsWriter tmhWriter =
                new AlignmentTooManyHitsWriter(outputFile, mParameter);

        try {
            scan(tmhWriter);


        } finally {

            tmhWriter.close();
        }
    }

    private boolean sortedInput;

    private int scan(final AlignmentTooManyHitsWriter tmhWriter)
            throws IOException {
        int numAligns = 0;
        final IndexedIdentifier targetIds = new IndexedIdentifier();
        final AlignmentWriter destinationWriter = new AlignmentWriterImpl(outputFile);
        final AlignmentWriter writer = sortedInput ? new BufferedSortingAlignmentWriter(destinationWriter, 10000) : destinationWriter;
        final ProgressLogger progress = new ProgressLogger(LOG);
        progress.displayFreeMemory = true;
        // the following is required to set validation to SILENT before loading the header (done in the SAMFileReader constructor)
        SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.SILENT);

        final InputStream stream = "-".equals(inputFile) ? System.in : new FileInputStream(inputFile);
        final SAMFileReader parser = new SAMFileReader(stream);
        // transfer read groups to Goby header:
        final SAMFileHeader samHeader = parser.getFileHeader();
        final IndexedIdentifier readGroups = new IndexedIdentifier();

        importReadGroups(samHeader, readGroups);
        boolean hasPaired = false;
        progress.start();

        numberOfReads = 0;

        // int stopEarly = 0;
        SAMRecord prevRecord = null;


        if (samHeader.getSequenceDictionary().isEmpty()) {
            System.err.println("SAM/BAM file/input appear to have no target sequences. If reading from stdin, please check you are feeding this mode actual SAM/BAM content and that the header of the SAM file is included.");
            if (runningFromCommandLine) {
                System.exit(0);
            }
        } else {
            // register all targets ids:
            if (genome != null) {
                // register target indices in the order they appear in the genome. This makes alignment target indices compatible
                // with the genome indices.
                for (int genomeTargetIndex = 0; genomeTargetIndex < genome.size(); genomeTargetIndex++) {
                    getTargetIndex(targetIds, genome.getReferenceName(genomeTargetIndex), thirdPartyInput);
                }
            }
            final int numTargets = samHeader.getSequenceDictionary().size();
            for (int i = 0; i < numTargets; i++) {
                final SAMSequenceRecord seq = samHeader.getSequence(i);
                getTargetIndex(targetIds, seq.getSequenceName(), thirdPartyInput);
            }
        }

        if (!targetIds.isEmpty() && targetIds.size() != samHeader.getSequenceDictionary().size()) {
            LOG.warn("targets: " + targetIds.size() + ", records: " + samHeader.getSequenceDictionary().size());
        }

        final int numTargets = Math.max(samHeader.getSequenceDictionary().size(), targetIds.size());
        final int[] targetLengths = new int[numTargets];
        for (int i = 0; i < numTargets; i++) {
            final SAMSequenceRecord seq = samHeader.getSequence(i);
            if (seq != null) {
                final int targetIndex = getTargetIndex(targetIds, seq.getSequenceName(), thirdPartyInput);
                if (targetIndex < targetLengths.length) {
                    targetLengths[targetIndex] = seq.getSequenceLength();
                }
            }
        }

        writer.setTargetLengths(targetLengths);
        if (sortedInput) {
            // if the input is sorted, request creation of the index when writing the alignment.
            writer.setSorted(true);
        }
        final Int2ByteMap queryIndex2NextFragmentIndex = new Int2ByteOpenHashMap();


        final ObjectArrayList<Alignments.AlignmentEntry.Builder> builders = new ObjectArrayList<Alignments.AlignmentEntry.Builder>();

        final SamRecordParser samRecordParser = new SamRecordParser();
        samRecordParser.setQualityEncoding(qualityEncoding);
        for (final SAMRecord samRecord : new SAMRecordIterable(parser.iterator())) {
            builders.clear();
            numberOfReads++;
            final GobySamRecord gobySamRecord = samRecordParser.processRead(samRecord);
            if (gobySamRecord == null) {
                if (debug && LOG.isDebugEnabled()) {
                    LOG.debug(String.format("NOT keeping unmapped read %s", samRecord.getReadName()));
                }
                continue;
            }
            if (gobySamRecord.getTargetAlignedLength() + gobySamRecord.getNumInserts() !=
                    gobySamRecord.getQueryAlignedLength() + gobySamRecord.getNumDeletes()) {
                LOG.error(String.format("targetAlignedLength+inserts != queryAlignedLength+deletes for read %s",
                        samRecord.getReadName()));
                continue;
            }
            final int targetIndex = getTargetIndex(targetIds, samRecord.getReferenceName(), thirdPartyInput);

            if (sortedInput) {
                // check that input entries are indeed in sort order. Abort otherwise.
                if (prevRecord != null && prevRecord.getReferenceIndex() == targetIndex) {
                    final int compare = prevRecord.getAlignmentStart() - samRecord.getAlignmentStart();//  samComparator.compare(prevRecord, samRecord);
                    if (compare > 0) {
                        final String message = String.format("record %s has position before previous record: %s",
                                samRecord.toString(), prevRecord.toString());
                        System.err.println("You cannot specify --sorted when the input file is not sorted. For instance: " + message);

                        LOG.warn(message);
                        // we continue because it is possible BufferedSortingAlignmentWriter will succeed in sorting the file
                        // nevertheless. It will set sorted to false if the file cannot be locally sorted.
                    }
                }
            }

            prevRecord = samRecord;

            // try to determine readMaxOccurence, the maximum number of times a read name occurs in a complete alignment.
            int readMaxOccurence = 1;
            final boolean readIsPaired = samRecord.getReadPairedFlag();
            final boolean anotherPair = readIsPaired && !samRecord.getMateUnmappedFlag();
            if (anotherPair) {
                hasPaired = true;
                // if the reads are paired, we expect to see the read name  at least twice.
                readMaxOccurence++;
                // Unfortunately the SAM/BAM format does not provide the exact number of times
                // a read matched the reference sequence. We could find this number in an non sorted BAM file
                // by counting how many times the same read name appears in a continuous block of constant read name.
                // However, for sorted input, we need this number to know when to stop
                // keep a given read name in memory with its associated query index.
                // Since we can't keep all the read names in memory continuously (these are strings and
                // consume much memory), it is unclear how to determine  query-index-occurrences in a sorted
                // SAM/BAM file that contains multiple best hits for read or mate. We can handle these cases
                // correctly when working directly in the aligner and writing Goby format because the information
                // is available at the time of alignment, but discarded afterwards.
            }

            final Object xoString = samRecord.getAttribute("X0");

            // in the following, we consider paired end alignment to always map a single time. This may
            // not be true, but there is no way to tell from the SAM format (the X0 field indicates how
            // many times the segment occurs in the genome, not the pair of read placed by the aligner).
            // Single reads typically have the field X0 set to the number of times the read appears in the
            // genome, there is no problem there, so we use X0 to initialize TMH.
            final int numTotalHits = xoString == null ? 1 : hasPaired ? 1 : (Integer) xoString;

            // Q: samHelper hasn't been set to anything since .reset(). This will always be 1. ??
            // Q: Also, readMaxOccurence is *2 for paired and *2 for splice, but splices can be N pieces, not just 2.
            //    so readMaxOccurence isn't always correct it seems.
            final int numEntries = gobySamRecord.getNumSegments();
            final boolean readIsSpliced = numEntries > 1;
            if (hasPaired) {
                // file has paired end reads, check if this read is paired to use 1 occurrence:

                readMaxOccurence = readIsPaired ? 2 : 1;
            } else {
                // single end, use numTotalHits to remember read name and initialize TMH
                readMaxOccurence = numTotalHits;
            }
            readMaxOccurence *= readIsSpliced ? 2 : 1;
            /* While STAR uses NH to store readMaxOccurence, GSNAP seems to put some other values in that field so we
             can't really trust these values in general. Disable for now on the stable branch.
            final Integer nh = samRecord.getIntegerAttribute("NH");
            // NH:i indicates: NH i Number of reported alignments that contains the query in the current record
            if (nh != null) {
                // used by STAR, for instance, to encode readMaxOccurence
                readMaxOccurence = nh;
            } */
            final String readName = samRecord.getReadName();

            final int queryIndex = getQueryIndex(readMaxOccurence, readName);
            assert queryIndex >= 0 : " Query index must never be negative.";

            // positions reported by BWA appear to start at 1. We convert to start at zero.
            final int multiplicity = 1;

            largestQueryIndex = Math.max(queryIndex, largestQueryIndex);
            smallestQueryIndex = Math.min(queryIndex, smallestQueryIndex);
            final int genomeTargetIndex = genome == null ? -1 : genome.getReferenceIndex(chromosomeNameMapping(genome, samRecord.getReferenceName()));
            if (genome != null && genomeTargetIndex == -1) {
                System.out.println("genomeTargetIndex==-1, name=" + samRecord.getReferenceName());
                System.out.println("mapping=" + chromosomeNameMapping(genome, samRecord.getReferenceName()));
                System.exit(10);
            }
            int segmentIndex = 0;
            for (final GobySamSegment gobySamSegment : gobySamRecord.getSegments()) {
                // the record represents a mapped read..
                final Alignments.AlignmentEntry.Builder currentEntry = Alignments.AlignmentEntry.newBuilder();

                if (multiplicity > 1) {
                    currentEntry.setMultiplicity(multiplicity);
                }
                if (preserveReadName) {
                    currentEntry.setReadName(gobySamRecord.getReadName().toString());
                }
                currentEntry.setQueryIndex(queryIndex);
                currentEntry.setTargetIndex(targetIndex);
                currentEntry.setPosition(gobySamSegment.getPosition());     // samhelper returns zero-based positions compatible with Goby.
                currentEntry.setQueryPosition(gobySamSegment.getQueryPosition());

                currentEntry.setQueryLength(gobySamRecord.getQueryLength());
                //currentEntry.setScore(samHelper.getScore());  BAM does not have the concept of a score.
                currentEntry.setMatchingReverseStrand(gobySamRecord.isReverseStrand());
                currentEntry.setQueryAlignedLength(gobySamSegment.getQueryAlignedLength());
                currentEntry.setTargetAlignedLength(gobySamSegment.getTargetAlignedLength());
                currentEntry.setMappingQuality(samRecord.getMappingQuality());
                if (preserveSoftClips) {
                    final int leftTrim = gobySamSegment.getSoftClippedBasesLeft().length();
                    if (leftTrim > 0) {
                        currentEntry.setSoftClippedBasesLeft(convertBases(
                                genomeTargetIndex, gobySamSegment.getPosition() - leftTrim, samRecord.getReadBases(), 0, leftTrim));
                        currentEntry.setSoftClippedQualityLeft(gobySamSegment.getSoftClippedQualityLeft());
                    }
                    final int queryAlignedLength = gobySamSegment.getQueryAlignedLength();
                    final int rightTrim = gobySamSegment.getSoftClippedBasesRight().length();
                    final int queryPosition = gobySamSegment.getQueryPosition();
                    if (rightTrim > 0) {
                        final int startIndex = queryPosition + queryAlignedLength;
                        final int endIndex = startIndex + rightTrim;
                        currentEntry.setSoftClippedBasesRight(convertBases(genomeTargetIndex,
                                gobySamSegment.getPosition() + gobySamSegment.getTargetAlignedLength(),
                                samRecord.getReadBases(), startIndex, endIndex));
                        currentEntry.setSoftClippedQualityRight(gobySamSegment.getSoftClippedQualityRight());
                    }
                }

                if (preserveAllMappedQuals && segmentIndex == 0) {
                    final byte[] sourceQualAsBytes = gobySamRecord.getReadQualitiesAsBytes();
                    // we only store the full quality score on the first entry with a given query index:
                    if (sourceQualAsBytes != null) {
                        currentEntry.setReadQualityScores(ByteString.copyFrom(sourceQualAsBytes));
                    }
                }
                addSamAttributes(samRecord, currentEntry);

                // Always store the sam flags when converting from sam/bam
                currentEntry.setPairFlags(samRecord.getFlags());
                if (hasPaired) {
                    final int inferredInsertSize = samRecord.getInferredInsertSize();
                    if (inferredInsertSize != 0) {   // SAM specification indicates that zero means no insert size.
                        currentEntry.setInsertSize(inferredInsertSize);
                    }
                }

                for (final GobyQuickSeqvar variation : gobySamSegment.getSequenceVariations()) {
                    appendNewSequenceVariation(currentEntry, variation, gobySamRecord.getQueryLength());
                    if (debug && LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Added seqvar=%s for queryIndex=%d to alignment", variation.toString(), queryIndex));
                    }
                }
                final String readGroup = samRecord.getStringAttribute("RG");
                if (readGroup != null && storeReadOrigin) {
                    final int readOriginIndex = readGroups.getInt(new MutableString(readGroup).compact());
                    if (readOriginIndex == -1) {
                        System.err.printf("Read group identifier %s is used in alignment record (read-name=%s), " +
                                "but was not found in the header. Ignoring this read group.%n", readGroup, samRecord.getReadName());
                    } else {
                        currentEntry.setReadOriginIndex(readOriginIndex);
                    }
                }
                builders.add(currentEntry);
                segmentIndex++;
            }
            final int numFragments = builders.size();
            for (final Alignments.AlignmentEntry.Builder builder : builders) {

                builder.setFragmentIndex(nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex));
            }
            if (numFragments > 1) {
                for (int j = 0; j < numFragments + 1; j++) {

                    linkSplicedEntries(j - 1 >= 0 ? builders.get(j - 1) : null, j < numFragments ? builders.get(j) : null);
                }
            }
            final int fragmentIndex;
            final int firstFragmentIndex = builders.get(0).getFragmentIndex();
            final int mateFragmentIndex;
            if (readIsPaired) {
                if (samRecord.getFirstOfPairFlag()) {
                    fragmentIndex = firstFragmentIndex;
                    if (pairBefore(samRecord)) {
                        mateFragmentIndex = firstFragmentIndex - 1;
                    } else {
                        mateFragmentIndex = nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
                        // fragment index is used as reference, but not own by this entry, we uncomsume it:
                        uncomsumeFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
                    }

                } else {
                    fragmentIndex = firstFragmentIndex;
                    mateFragmentIndex = pairBefore(samRecord) ? firstFragmentIndex - 1 : firstFragmentIndex + 1;
                }
            } else {
                fragmentIndex = firstFragmentIndex;
                mateFragmentIndex = nextFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
                // fragment index is used as reference, but not own by this entry, we uncomsume it:
                uncomsumeFragmentIndex(queryIndex, queryIndex2NextFragmentIndex);
            }

            for (final Alignments.AlignmentEntry.Builder builder : builders) {
                if (numTotalHits <= mParameter) {
                    if (readIsPaired) {

                        if (!samRecord.getMateUnmappedFlag()) {
                            assert firstFragmentIndex >= 0 : " firstFragmentIndex cannot be negative";
                            // some BAM files indicate pair is in the p
                            if (mateFragmentIndex >= 0) {
                                final Alignments.RelatedAlignmentEntry.Builder relatedBuilder =
                                        Alignments.RelatedAlignmentEntry.newBuilder();

                                final int mateTargetIndex = getTargetIndex(targetIds, samRecord.getMateReferenceName(), thirdPartyInput);
                                final int mateAlignmentStart = samRecord.getMateAlignmentStart() - 1; // samhelper returns zero-based positions compatible with Goby.
                                relatedBuilder.setFragmentIndex(mateFragmentIndex);
                                relatedBuilder.setPosition(mateAlignmentStart);
                                relatedBuilder.setTargetIndex(mateTargetIndex);
                                builder.setPairAlignmentLink(relatedBuilder);
                            }
                        } else {
                            // mate is unmapped.

                        }
                    }
                    writer.appendEntry(builder.build());
                    numAligns += multiplicity;
                    if (debug && LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Added queryIdndex=%d to alignment", queryIndex));
                    }
                } else {
                    // TMH writer adds the alignment entry only if hits > thresh
                    tmhWriter.append(queryIndex, numTotalHits, gobySamRecord.getQueryLength());
                    if (debug && LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Added queryIndex=%d to TMH", queryIndex));
                    }
                    // remove the query name from memory since we are not writing these entries anyway
                    while (queryIndex == getQueryIndex(0, samRecord.getReadName())) {
                        //do nothing
                    }
                }
            }
            progress.lightUpdate();
        }

        if (!targetIds.isEmpty()) {
            // we collected target ids, let's write them to the header:
            writer.setTargetIdentifiers(targetIds);
        }
        writer.putStatistic("number-of-entries-written", numAligns);
        writer.setNumQueries(Math.max(numberOfReads, numberOfReadsFromCommandLine));
        writer.printStats(System.out);

        // write information from SAM file header
        final SAMSequenceDictionary samSequenceDictionary = samHeader.getSequenceDictionary();
        final List<SAMSequenceRecord> samSequenceRecords = samSequenceDictionary.getSequences();

        writer.setReadOriginInfo(readOriginInfoBuilderList);
        progress.stop();
        writer.close();
        return numAligns;
    }

    private int getQueryIndex(final int readMaxOccurence, final String readName) {
        return readNamesAreQueryIndices ? Integer.parseInt(readName) :
                nameToQueryIndices.getQueryIndex(readName, readMaxOccurence);
    }

    private int getTargetIndex(final IndexedIdentifier targetIds, final String sequenceName, final boolean thirdPartyInput) {
        int targetIndex = -1;

        targetIndex = targetIds.registerIdentifier(new MutableString(sequenceName));
        return targetIndex;
    }

    MutableString convertBasesBuffer = new MutableString();
    private final MutableString bases = new MutableString();

    public String convertBases(
            final int referenceIndex, final int positionStartOfRead,
            final byte[] readBases, final int startIndex, final int endIndex) {
        if (genome != null) {
            int actualPositionStartOfRead = positionStartOfRead;
            int numPrepend = 0;
            int numAppend = 0;
            int actualLength = endIndex - startIndex;
            if (actualPositionStartOfRead < 0) {
                numPrepend = -actualPositionStartOfRead;
                actualPositionStartOfRead = 0;
                actualLength -= numPrepend;
            }
            final int referenceLength = genome.getLength(referenceIndex);
            if (actualPositionStartOfRead + actualLength > referenceLength) {
                numAppend = actualPositionStartOfRead + actualLength - referenceLength;
                actualLength -= numAppend;
            }
            genome.getRange(referenceIndex, actualPositionStartOfRead, actualLength, bases);
            for (int i = 0; i < numPrepend; i++) {
                bases.insert(0, "N");
            }
            for (int i = 0; i < numAppend; i++) {
                bases.append("N");
            }
        }
        convertBasesBuffer.setLength(endIndex - startIndex);
        int j = 0;
        for (int i = startIndex; i < endIndex; i++) {
            final char readBase = (char) readBases[i];
            final char refBase = genome != null ? bases.charAt(i - startIndex) : '!';
            convertBasesBuffer.setCharAt(j, refBase == readBase ? '=' : readBase);
            j++;
        }

        return convertBasesBuffer.toString();
    }

    private final ObjectArrayList<Alignments.ReadOriginInfo.Builder> readOriginInfoBuilderList = new ObjectArrayList<Alignments.ReadOriginInfo.Builder>();
    DateFormat dateFormatter = new SimpleDateFormat("dd:MMM:yyyy");

    private void importReadGroups(final SAMFileHeader samHeader, final IndexedIdentifier readGroups) {
        if (!samHeader.getReadGroups().isEmpty() && storeReadOrigin) {
            for (final SAMReadGroupRecord rg : samHeader.getReadGroups()) {
                final String sample = rg.getSample();
                final String library = rg.getLibrary();
                final String platform = rg.getPlatform();
                final String platformUnit = rg.getPlatformUnit();
                final Date date = rg.getRunDate();
                final String id = rg.getId();
                final int readGroupIndex = readGroups.registerIdentifier(new MutableString(id));
                final Alignments.ReadOriginInfo.Builder roi = Alignments.ReadOriginInfo.newBuilder();
                roi.setOriginIndex(readGroupIndex);
                roi.setOriginId(id);
                if (library != null) {
                    roi.setLibrary(library);
                }
                if (platform != null) {
                    roi.setPlatform(platform);
                }
                if (platformUnit != null) {
                    roi.setPlatformUnit(platformUnit);
                }
                if (sample != null) {
                    roi.setSample(sample);
                }
                if (date != null) {
                    roi.setRunDate(dateFormatter.format(date));
                }
                readOriginInfoBuilderList.add(roi);
            }
        }
    }

    private void addSamAttributes(final SAMRecord samRecord, final Alignments.AlignmentEntry.Builder currentEntry) {
        if (preserveAllTags) {
            final String[] tokens = samRecord.getSAMString().split("\t");
            final int size = tokens.length;
            for (int i = 11; i < size; i++) {
                final String token = tokens[i];
                if (!token.startsWith("MD:Z") && !token.startsWith("RG:Z")) {
                    // ignore MD and RG since we store them natively..
                    //    System.out.printf("Preserving token=%s%n", token);
                    currentEntry.addBamAttributes(token.replaceAll("\n", ""));
                }
            }
        }

    }


    /**
     * Adjust reference names to match genome.
     *
     * @param genome        reference genome
     * @param referenceName reference name
     * @return the possibly re-mapped reference name
     */

    public static String chromosomeNameMapping(final RandomAccessSequenceInterface genome, final String referenceName) {
        if (genome.getReferenceIndex(referenceName) == -1) {
            if (referenceName.contentEquals("chrM")) {
                return "MT";
            }
            if (referenceName.startsWith("chr")) {
                return referenceName.substring(3);
            } else {
                return referenceName;
            }
        } else {
            return referenceName;
        }
    }

    // determine if the pair occurs before the primary in genomic position:
    private boolean pairBefore(final SAMRecord samRecord) {
        final int pairOrder = samRecord.getMateReferenceName().compareTo(samRecord.getReferenceName());
        if (pairOrder > 0) {
            return false;
        }
        if (pairOrder == 0) {
            //same reference, check positions:
            return samRecord.getMateAlignmentStart() < samRecord.getAlignmentStart();
        }
        return true;
    }

    /**
     * Unconsume one fragment index.
     *
     * @param queryIndex                   the query index
     * @param queryIndex2NextFragmentIndex the map query index to next fragment indexes
     */
    private void uncomsumeFragmentIndex(final int queryIndex, final Int2ByteMap queryIndex2NextFragmentIndex) {
        final int fragmentIndex = queryIndex2NextFragmentIndex.get(queryIndex);
        queryIndex2NextFragmentIndex.put(queryIndex, (byte) (fragmentIndex - 1));
        //    System.out.printf("unconsumed fragmentIndex=%d for queryIndex=%d %n", fragmentIndex - 1, queryIndex);

    }

    private int nextFragmentIndex(final int queryIndex, final Int2ByteMap queryIndex2NextFragmentIndex) {
        final int fragmentIndex = queryIndex2NextFragmentIndex.get(queryIndex);
        queryIndex2NextFragmentIndex.put(queryIndex, (byte) (fragmentIndex + 1));
        //       System.out.printf("queryIndex=%d returning fragmentIndex=%d %n", queryIndex, fragmentIndex);
        return fragmentIndex;
    }

    private void linkSplicedEntries(final Alignments.AlignmentEntry.Builder a, final Alignments.AlignmentEntry.Builder b) {
        if (a == null || b == null) {
            return;
        }
        // System.out.printf("Adding splice links between a=%s b=%s %n", a.build().toString(), b.build().toString());
        final Alignments.RelatedAlignmentEntry.Builder forwardSpliceLink = Alignments.RelatedAlignmentEntry.newBuilder();
        forwardSpliceLink.setFragmentIndex(b.getFragmentIndex());
        forwardSpliceLink.setPosition(b.getPosition());
        forwardSpliceLink.setTargetIndex(b.getTargetIndex());

        a.setSplicedForwardAlignmentLink(forwardSpliceLink);

        final Alignments.RelatedAlignmentEntry.Builder backwardSpliceLink = Alignments.RelatedAlignmentEntry.newBuilder();
        backwardSpliceLink.setFragmentIndex(a.getFragmentIndex());
        backwardSpliceLink.setPosition(a.getPosition());
        backwardSpliceLink.setTargetIndex(a.getTargetIndex());

        b.setSplicedBackwardAlignmentLink(backwardSpliceLink);
        //   System.out.printf("Linked queryIndex=%d forward: %d>%d %n", a.getQueryIndex(), a.getFragmentIndex(), forwardSpliceLink.getFragmentIndex());
        //  System.out.printf("Linked queryIndex=%d backward: %d<%d %n", a.getQueryIndex(), backwardSpliceLink.getFragmentIndex(), b.getFragmentIndex());
    }

    static void appendNewSequenceVariation(
            final Alignments.AlignmentEntry.Builder currentEntry,
            final GobyQuickSeqvar variation, final int queryLength) {

        final int readIndex = variation.getReadIndex();
        if (readIndex > queryLength) {
            if (readIndex > queryLength) {
                System.out.println("STOP6");
            }
            assert readIndex <= queryLength : String.format(" readIndex %d must be smaller than read length %d .",
                    readIndex, queryLength);
            LOG.warn(String.format(
                    "Ignoring sequence variations for a read since readIndex %d must be smaller than read length %d. query index=%d reference index=%d%n",
                    readIndex, queryLength, currentEntry.getQueryIndex(), currentEntry.getTargetIndex()));
            return;
        }

        final Alignments.SequenceVariation.Builder sequenceVariation =
                Alignments.SequenceVariation.newBuilder();

        sequenceVariation.setFrom(variation.getFrom());
        sequenceVariation.setTo(variation.getTo());
        sequenceVariation.setPosition(variation.getPosition());
        sequenceVariation.setReadIndex(readIndex);  // readIndex starts at 1
        final byte[] toQuality = variation.getToQualitiesAsBytes();
        if (toQuality != null && toQuality.length > 0) {
            sequenceVariation.setToQuality(ByteString.copyFrom(toQuality));
        }
        currentEntry.addSequenceVariations(sequenceVariation);
    }

    ReadNameToIndex nameToQueryIndices = new ReadNameToIndex("ignore-this-for-now");

    /**
     * Main method.
     *
     * @param args command line args.
     * @throws com.martiansoftware.jsap.JSAPException
     *                             error parsing
     * @throws java.io.IOException error parsing or executing.
     */
    public static void main(final String[] args) throws JSAPException, IOException {

        final SAMToCompactMode processor = new SAMToCompactMode();
        processor.configure(args);
        processor.runningFromCommandLine = true;
        processor.execute();
    }

    public void setGenome(final RandomAccessSequenceInterface genome) {
        this.genome = genome;
    }

    public void setPreserveSoftClips(final boolean flag) {
        preserveSoftClips = flag;
    }

    public void setPreserveReadQualityScores(final boolean flag) {
        preserveAllMappedQuals = flag;
    }

    public void setPreserveAllTags(final boolean flag) {
        preserveAllTags = flag;
    }

    public void setInputFile(final String s) {
        inputFile = s;
    }

    public void setOutputFile(final String outputFilename) {
        outputFile = outputFilename;
    }


}
