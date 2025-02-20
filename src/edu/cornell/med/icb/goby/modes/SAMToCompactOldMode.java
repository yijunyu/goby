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

import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import edu.cornell.med.icb.goby.alignments.AlignmentTooManyHitsWriter;
import edu.cornell.med.icb.goby.alignments.AlignmentWriter;
import edu.cornell.med.icb.goby.alignments.AlignmentWriterImpl;
import edu.cornell.med.icb.goby.alignments.Alignments;
import edu.cornell.med.icb.goby.reads.ReadSet;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import net.sf.samtools.AlignmentBlock;
import net.sf.samtools.CigarElement;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;
import net.sf.samtools.util.CloseableIterator;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts binary BWA alignments in the SAM format to the compact alignment format.
 *
 * @author Fabien Campagne
 */
public class SAMToCompactOldMode extends AbstractAlignmentToCompactMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Logger LOG = Logger.getLogger(SAMToCompactOldMode.class);

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "sam-to-compact-old";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Converts binary BWA alignments in the SAM "
            + "format to the compact alignment format.";

    /**
     * Native reads output from aligner.
     */
    protected String samBinaryFilename;

    private boolean skipMissingMdAttribute = true;
    private int dummyQueryIndex;
    private ObjectSet<String> parseAttributeNames = new ObjectArraySet<String>();


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
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws IOException   error parsing
     * @throws JSAPException error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args) throws IOException, JSAPException {
        // configure baseclass
        super.configure(args);
        final JSAPResult jsapResult = parseJsapArguments(args);

        skipMissingMdAttribute = jsapResult.getBoolean("allow-missing-attributes");
        numberOfReadsFromCommandLine = jsapResult.getInt("number-of-reads");
        final String[] strings = jsapResult.getStringArray("parse");
        parseAttributeNames = new ObjectArraySet<String>();
        for (String name : strings) {
            parseAttributeNames.add(name.intern());
        }

        this.largestQueryIndex = numberOfReadsFromCommandLine;
        this.smallestQueryIndex = 0;
        return this;
    }

    @Override
    protected int scan(final ReadSet readIndexFilter, final IndexedIdentifier targetIds,
                       final AlignmentWriter writer, final AlignmentTooManyHitsWriter tmhWriter)
            throws IOException {
        int numAligns = 0;
        final int[] readLengths = createReadLengthArray();

        final ProgressLogger progress = new ProgressLogger(LOG);
        final SAMFileReader parser = new SAMFileReader(new File(inputFile));
        parser.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);

        progress.start();

        final CloseableIterator<SAMRecord> recordCloseableIterator = parser.iterator();

        final MutableString readSequence = new MutableString();
        // shared buffer for extract sequence variation work. We allocate here to avoid repetitive memory allocations.
        final MutableString referenceSequence = new MutableString();
        // shared buffer for extract sequence variation work. We allocate here to avoid repetitive memory allocations.
        final MutableString readPostInsertions = new MutableString();
        numberOfReads = 0;

        // int stopEarly = 0;
        while (recordCloseableIterator.hasNext()) {
            numberOfReads++;
            final SAMRecord samRecord = recordCloseableIterator.next();
            final int queryIndex = getQueryIndex(samRecord);

            //    stopEarly++;
            //   if (stopEarly > 10000) break;
            final int readLength = samRecord.getReadLength();

            // if SAM reports read is unmapped (we don't know how or why), skip record
            if (samRecord.getReadUnmappedFlag()) {
                continue;
            }

            // SAM mismatch info must be available, o.w. skip record
            final String mismatches = (String) samRecord.getAttribute("MD");
            // System.out.println("mismatches: " + mismatches);
            if (mismatches == null && !skipMissingMdAttribute) {

                continue;
            }
            final int targetIndex = getTargetIndex(
                    targetIds, samRecord.getReferenceName(), thirdPartyInput);

            // positions reported by BWA appear to start at 1. We convert to start at zero.
            final int position = samRecord.getAlignmentStart() - 1;
            final boolean reverseStrand = samRecord.getReadNegativeStrandFlag();
            final List<AlignmentBlock> blocks = samRecord.getAlignmentBlocks();

            float score = 0;
            int targetAlignedLength = 0;
            int numMismatches = 0;
            int numIndels = 0;
            int queryAlignedLength = 0;
            int multiplicity = 1;
            if (!skipMissingMdAttribute) {
                // count the number of mismatches:
                for (final char c : mismatches.toCharArray()) {
                    if (!Character.isDigit(c)) {
                        numMismatches++;
                    }
                }
            }
            for (final CigarElement cigar : samRecord.getCigar().getCigarElements()) {
                final int length = cigar.getLength();
                switch (cigar.getOperator()) {
                    case M:
                        // match or mismatch?!   CIGAR cannot differentiate.
                        // This means here that the score rewards mutations as much as matches.  We would
                        // have to parse the sequence to determine what is what.
                        score += length;
                        break;
                    case I:
                        // insertion to the reference :
                        score -= length;
                        numIndels += length;
                        break;
                    case P:
                        //padding, no score impact:
                        break;
                    case D:
                        // deletion from the reference
                        score -= length;
                        numIndels += length;
                        break;
                }
            }
            score -= numMismatches;
            for (final AlignmentBlock block : blocks) {
                targetAlignedLength += block.getLength();
                queryAlignedLength += block.getLength();
            }

            // we have a multiplicity filter. Use it to determine multiplicity.
            if (readIndexFilter != null) {
                /* Multiplicity of a read is the number of times the (exact) sequence
                  of the read is identically repeated across a sample file.  The filter
                  removes duplicates to avoid repeating the same alignments.  Once
                  aligned, these are recorded multiplicity times. */
                multiplicity = readIndexFilter.getMultiplicity(queryIndex);
            }
            largestQueryIndex = Math.max(queryIndex, largestQueryIndex);

            // the record represents a mapped read..
            final Alignments.AlignmentEntry.Builder currentEntry = Alignments.AlignmentEntry.newBuilder();

            //      System.out.println("score: " + score);
            currentEntry.setNumberOfIndels(numIndels);
            currentEntry.setNumberOfMismatches(numMismatches);
            currentEntry.setMatchingReverseStrand(reverseStrand);
            currentEntry.setMultiplicity(multiplicity);
            currentEntry.setPosition(position);
            currentEntry.setQueryAlignedLength(queryAlignedLength);
            currentEntry.setQueryIndex(queryIndex);
            currentEntry.setScore(score);
            currentEntry.setTargetAlignedLength(targetAlignedLength);
            currentEntry.setTargetIndex(targetIndex);
            currentEntry.setQueryLength(readLength);
            final String cigar = samRecord.getCigarString();
            final String attributeMD = (String) samRecord.getAttribute("MD");
            final String sequence = samRecord.getReadString();
            readSequence.setLength(0);
            readSequence.append(sequence);

            final int queryLength = samRecord.getReadLength();
            if (parseAttribute("BSMAP:XR")) {

                // reference is provided in attribute XR
                final String attributeXR_Z = (String) samRecord.getAttribute("XR");
                System.out.println(attributeXR_Z);
                referenceSequence.setLength(0);
                referenceSequence.append(attributeXR_Z);
                final int alignmentLength = readSequence.length();
                interpretBisulfiteConversion(readSequence, referenceSequence);
                extractSequenceVariations(currentEntry, alignmentLength,
                        referenceSequence, readSequence, 0, queryLength, reverseStrand, samRecord.getBaseQualities());

            } else {
                // variations are encoded in attribute MD
                extractSequenceVariations(cigar, attributeMD, readSequence, readPostInsertions,
                        referenceSequence, currentEntry, queryLength, reverseStrand, samRecord.getBaseQualities());
            }
            final Alignments.AlignmentEntry alignmentEntry = currentEntry.build();

            final Object xoString = samRecord.getAttribute("X0");
            if (xoString == null && !skipMissingMdAttribute) {
                System.err.println("The XO attribute is required. Use --allow-missing-attributes to ignore.");
                System.exit(1);
            }
            final int numTotalHits = skipMissingMdAttribute && xoString == null ?
                    1 : (Integer) xoString;

            if (qualityFilter.keepEntry(readLength, alignmentEntry)) {
                // only write the entry if it is not ambiguous. i.e. less than or equal to mParameter
                if (numTotalHits <= mParameter) {
                    writer.appendEntry(alignmentEntry);
                    numAligns += multiplicity;
                }
                // TMH writer adds the alignment entry only if hits > thresh
                tmhWriter.append(queryIndex, numTotalHits, readLength);
            }

            progress.lightUpdate();

        }
        recordCloseableIterator.close();

        // TODO write statistics to writer.
        // stu 090817 - mimicking LastToCompactMode.scan() statistics
        if (readIndexFilter != null) {
            writer.putStatistic("keep-filter-filename", readIndexFilterFile.getName());
        }
        writer.putStatistic("number-of-entries-written", numAligns);
        writer.setNumQueries(numberOfReads);
        writer.printStats(System.out);

        // write information from SAM file header
        final SAMFileHeader samHeader = parser.getFileHeader();
        final SAMSequenceDictionary samSequenceDictionary = samHeader.getSequenceDictionary();
        final List<SAMSequenceRecord> samSequenceRecords = samSequenceDictionary.getSequences();
        int targetCount = targetIds.size();
        if (targetIds.size() != 0 && (targetIds.size() != samSequenceRecords.size())) {

            LOG.warn("targets: " + targetIds.size() + ", records: " + samSequenceRecords.size());

        }
        targetCount = Math.max(samSequenceRecords.size(), targetCount);
        final int[] targetLengths = new int[targetCount];
        for (final SAMSequenceRecord samSequenceRecord : samSequenceRecords) {
            final int index = samSequenceRecord.getSequenceIndex();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sam record: " + samSequenceRecord.getSequenceName() + " at " + index);
            }
            targetLengths[index] = samSequenceRecord.getSequenceLength();
        }

        writer.setTargetLengths(targetLengths);
        progress.stop();
        return numAligns;
    }

    private void interpretBisulfiteConversion(MutableString readSequence, MutableString referenceSequence) {
        int length = Math.min(readSequence.length(), referenceSequence.length());
        for (int i = 0; i < length; i++) {
            final char readBase = readSequence.charAt(i);
            final char referenceBase = referenceSequence.charAt(i);
            if (readBase == 'C' && referenceBase == 'C') readSequence.charAt(i, 'm');
            if (readBase == 'T' && referenceBase == 'C') readSequence.charAt(i, 'C');
        }
    }

    private boolean parseAttribute(String attributeId) {
        return parseAttributeNames.contains(attributeId);
    }


    private int getQueryIndex(final SAMRecord samRecord) {
        final String readName = samRecord.getReadName();
        try {
            return Integer.parseInt(readName);
        } catch (NumberFormatException e) {

            if (!propagateQueryIds) {
                return dummyQueryIndex++;
            } else {  // read name is not the integer Goby relies on, make an int from the id:
                return queryIds.registerIdentifier(new MutableString(readName));

            }

        }
    }

    private static final Pattern attributeMD_pattern;
    private static final Pattern attributeCIGAR_insertions_pattern;

    static {
        attributeMD_pattern = Pattern.compile("([0-9]+)(([ACGTN]|\\^[ACGTN])+)?");
        attributeCIGAR_insertions_pattern = Pattern.compile("([0-9]+)([MID])");
    }

    /*
   @param baseQualities are ascii encoded, remove 33 to get Phred quality score.
    */
    private void extractSequenceVariations(final String cigar, final String attributeMD, final MutableString readSequence,
                                           final MutableString readPostInsertions,
                                           final MutableString referenceSequence,
                                           final Alignments.AlignmentEntry.Builder currentEntry,
                                           final int queryLength,
                                           final boolean reverseStrand, byte[] baseQualities) {
        /* From the SAM specification document, see http://samtools.sourceforge.net/SAM1.pdf
         MD Z String for mismatching positions in the format of [0-9]+(([ACGTN]|\^[ACGTN]+)[0-9]+)* 2,3

       The MD field aims to achieve SNP/indel calling without looking at the reference. SOAP and Eland SNP callers prefer
   such information. For example, a string "10A5^AC6" means from the leftmost reference base in the alignment, there
   are 10 matches followed by an A on the reference which is different from the aligned read base; the next 5 reference
   bases are matches followed by a 2bp deletion from the reference; the deleted sequence is AC; the last 6 bases are
   matches. The MD field should match the CIGAR string, although an SAM parser may not check this optional field.
        */
        if (attributeMD == null && skipMissingMdAttribute) {
            return;
        }

        final int readStartPosition = produceReferenceSequence(cigar, attributeMD, readSequence, readPostInsertions,
                referenceSequence);

        final int alignmentLength = readSequence.length();
        extractSequenceVariations(currentEntry, alignmentLength,
                referenceSequence, readSequence, readStartPosition, queryLength, reverseStrand, baseQualities);

    }

    /**
     * Interpret the CIGAR string and MD SAM attribute and reconstruct the reference sequence given the read sequence.
     * The reference passed as a argument is cleared before appending bases.
     *
     * @param CIGAR             The CIGAR string.
     * @param mdAttribute       The SAM MD attribute
     * @param readSequence      The sequence of the read.
     * @param referenceSequence The sequence of the reference that will be reconstructed.
     * @return alignedReadStartPosition The position on the read that starts to align.
     */
    public static int produceReferenceSequence(final String CIGAR, final String mdAttribute, final MutableString readSequence, final MutableString readPostInsertions,
                                               final MutableString referenceSequence) {
        final Pattern matchPattern = attributeMD_pattern;
        referenceSequence.setLength(0);
        readPostInsertions.setLength(0);
        int end = 0;
        int positionInReadSequence = 0;
        final IntList positionAdjustment = new IntArrayList();
        positionAdjustment.size(readSequence.length());

        final int readStartAlignedPosition = processInsertionsOnly(CIGAR, mdAttribute, readSequence, readPostInsertions, positionAdjustment);

        final Matcher matchMatcher = matchPattern.matcher(mdAttribute);

        final int currentAdjustment = 0;
        while (matchMatcher.find(end)) {

            final String matchChars = matchMatcher.group(1);
            final String variationChars = matchMatcher.group(2);
            if (matchChars != null) {
                final int matchLength = Integer.parseInt(matchChars);

                /*    System.out.println(String.format("subsequence(%d,%d),",
                          positionInReadSequence,
                          positionInReadSequence + matchLength));
                */
                // calculate by the read index position adjustment, given the number of insertions seen so far.
                int regionAdjustment = currentAdjustment;
                for (int i = positionInReadSequence; i < matchLength; i++) {
                    regionAdjustment += positionAdjustment.getInt(i);
                }
                final CharSequence matchingSequence = readPostInsertions.subSequence(positionInReadSequence,
                        positionInReadSequence + matchLength + regionAdjustment);

                //  System.out.println("match " + matchChars + " appending " + matchingSequence);
                referenceSequence.append(matchingSequence);
                positionInReadSequence += matchLength;
            }
            if (variationChars != null) {

                if (variationChars.charAt(0) == '^') {
                    // deletion in the reference, to reconstitute the reference, we append the deleted characters.
                    final int mutationLength = variationChars.length() - 1;   // -1 is for the indicator character '^'
                    final String toAppend = variationChars.substring(1);
                    referenceSequence.append(toAppend);

                    //        System.out.println("var " + variationChars + " appending " + toAppend);

                    //  positionInReadSequence += mutationLength;
                    for (int i = 0; i < mutationLength; ++i) {
                        readSequence.insert(positionInReadSequence, '-');
                        readPostInsertions.insert(positionInReadSequence, '-');
                    }
                    positionInReadSequence += mutationLength;
                } else {
                    // base mutations:
                    final int mutationLength = variationChars.length();

                    referenceSequence.append(variationChars);
                    //   System.out.println("var " + variationChars + " appending " + variationChars);
                    //   referenceSequence.setLength(referenceSequence.length() -
                    //           mutationLength);
                    positionInReadSequence += mutationLength;


                }
            }
            //  System.out.println("groupChars: " + matchChars + " variation chars: " + variationChars);
            end = matchMatcher.end();

            // System.out.println("mdAttribute: " + mdAttribute);
            //matchMatcher.reset(mdAttribute);

        }
        return readStartAlignedPosition;
    }

    private static int processInsertionsOnly(final String cigar, final String mdAttribute, final MutableString readSequence,
                                             final MutableString transformedSequence, final IntList positionAdjustment) {
        final Pattern matchPattern = attributeCIGAR_insertions_pattern;
        final Matcher matchMatcher = matchPattern.matcher(cigar);
        int end = 0;
        int positionInReadSequence = 0;
        int readStartAlignedPosition = 0;
        //   System.out.println("cigar: "+cigar +" mdAttribute: "+mdAttribute);

        while (matchMatcher.find(end)) {
            final String matchLenthAsString = matchMatcher.group(1);
            final String variationType = matchMatcher.group(2);
            final int matchLength = Integer.parseInt(matchLenthAsString);

            //      System.out.println(String.format("length: %s type: %s", matchLenthAsString, variationType));
            assert variationType.length() == 1 : " CIGAR mutation type must be one character only";
            switch (variationType.charAt(0)) {
                case 'M':
                    // The read and reference match.

                    final CharSequence matchingSequence = readSequence.subSequence(positionInReadSequence,
                            positionInReadSequence + matchLength);
                    //     System.out.println("match appending " + matchingSequence);
                    transformedSequence.append(matchingSequence);

                    positionInReadSequence += matchLength;
                    break;

                case 'I':
                    // The read has an insertion relative to the reference. Delete these bases when reconstituting the reference:
                    for (int i = 0; i < matchLength; i++) {
                        positionAdjustment.set(positionInReadSequence + i, +1);
                    }
                    positionInReadSequence += matchLength;
                    for (int i = 0; i < matchLength; ++i) {
                        transformedSequence.append('-');
                    }
                    break;
                case 'D':
                    // the reference had extra bases.
                    for (int i = 0; i < matchLength; i++) {
                        positionAdjustment.size(Math.max(positionInReadSequence + i + 1, positionAdjustment.size()));
                        positionAdjustment.set(positionInReadSequence + i, -1);
                    }
                    break;
                case 'P':
                    readStartAlignedPosition += matchLength;
            }
            //       System.out.println("current ref: " + transformedSequence);
            end = matchMatcher.end();

        }
        return readStartAlignedPosition;
    }

    /**
     * Main method.
     *
     * @param args command line args.
     * @throws JSAPException error parsing
     * @throws IOException   error parsing or executing.
     */
    public static void main(final String[] args) throws JSAPException, IOException {
        new SAMToCompactMode().configure(args).execute();
    }

    public int getSmallestSplitQueryIndex() {
        return smallestQueryIndex;
    }
}
