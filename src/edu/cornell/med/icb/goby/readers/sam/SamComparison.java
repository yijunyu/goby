/*
 * Copyright (C) 2009-2012 Institute for Computational Biomedicine,
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

package edu.cornell.med.icb.goby.readers.sam;

import edu.cornell.med.icb.goby.alignments.Alignments;
import edu.cornell.med.icb.goby.modes.SamHelper;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.lang.MutableString;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;
import org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for comparing two SAMRecords. Used with both tests and
 * with SAMComparisonMode.
 */
public class SamComparison implements SamComparisonInterface {

    /**
     * Configuration values.
     */
    private boolean mappedQualitiesPreserved;
    private boolean softClipsPreserved;
    private boolean checkMate;
    private boolean canonicalMdzForComparison;
    private boolean readNamesPreserved;
    /** If the source can contain an N but the destination be something else. */
    private boolean allowSourceNs;

    private int currentComparisonScore;

    /**
     * Running totals, etc.
     */
    protected int readNum;
    protected int comparisonFailureCount;
    protected boolean outputFailedComparisons;
    protected boolean countComparisonFailures;

    /**
     * State.
     */
    private final MutableString comparisonErrorDump;
    private final List<String> comparisonFailures;
    private boolean initialized;

    /**
     * Create a SamComparison.
     */
    public SamComparison() {
        comparisonErrorDump = new MutableString();
        comparisonFailures = new ArrayList<String>();
        initialized = false;
        mappedQualitiesPreserved = false;
        softClipsPreserved = false;
        checkMate = false;
        canonicalMdzForComparison = true;
        outputFailedComparisons = true;
    }

    /**
     * Call this one time before at the start of a large number of comparisons (such as before comparing
     * one entire file with another). Calling the first time is optional as it will be called if a comparison
     * is done and it has never been called.
     */
    @Override
    public void reset() {
        readNum = 0;
        comparisonFailureCount = 0;
        countComparisonFailures = true;
    }

    /**
     * Get if it is assumed that the compact file created from the BAM/SAM
     * file preserved mapped qualities.
     * @return if it is assumed ...
     */
    @Override
    public boolean isMappedQualitiesPreserved() {
        return mappedQualitiesPreserved;
    }

    /**
     * Set if it is assumed that the compact file created from the BAM/SAM
     * file preserved mapped qualities.
     * @param mappedQualitiesPreserved if it is assumed...
     */
    @Override
    public void setMappedQualitiesPreserved(final boolean mappedQualitiesPreserved) {
        this.mappedQualitiesPreserved = mappedQualitiesPreserved;
    }

    /**
     * Get if it is assumed that the compact file created from the BAM/SAM
     * file preserved soft clips.
     * @return if it is assumed ...
     */
    @Override
    public boolean isSoftClipsPreserved() {
        return softClipsPreserved;
    }

    /**
     * Set if it is assumed that the compact file created from the BAM/SAM
     * file preserved soft clips.
     * @param softClipsPreserved if it is assumed ...
     */
    @Override
    public void setSoftClipsPreserved(final boolean softClipsPreserved) {
        this.softClipsPreserved = softClipsPreserved;
    }

    /**
     * Get if the details about mate reads will be checked.
     * If the source SAM/BAM file is a complete file you can set this to true,
     * if you are using an incomplete source SAM/BAM file, this should be
     * set to false. Default is false.
     * @return if mates will be checked
     */
    @Override
    public boolean isCheckMate() {
        return checkMate;
    }

    /**
     * Set if the details about mate reads will be checked.
     * If the source SAM/BAM file is a complete file you can set this to true,
     * if you are using an incomplete source SAM/BAM file, this should be
     * set to false. Default is false.
     * @param checkMate if mates will be checked
     */
    @Override
    public void setCheckMate(final boolean checkMate) {
        this.checkMate = checkMate;
    }

    /**
     * Get if canonical MD:Z comparisons will be made.
     * When true, the source and destination MD:Z values will be passed through an algorithm
     * to make them canonical (place 0's in places where 0's should exist but might not).
     * By default this is enabled.
     * @return if ...
     */
    @Override
    public boolean isCanonicalMdzForComparison() {
        return canonicalMdzForComparison;
    }

    /**
     * Set if canonical MD:Z comparisons will be made.
     * When true, the source and destination MD:Z values will be passed through an algorithm
     * to make them canonical (place 0's in places where 0's should exist but might not).
     * By default this is enabled.
     * @param canonicalMdzForComparison if ...
     */
    @Override
    public void setCanonicalMdzForComparison(final boolean canonicalMdzForComparison) {
        this.canonicalMdzForComparison = canonicalMdzForComparison;
    }

    /**
     * Return how many reads have been compared since reset() was last called.
     * @return how many...
     */
    @Override
    public int getReadNum() {
        return readNum;
    }

    /**
     * Return how many comparison failures have been found since reset() was last called.
     * @return how many...
     */
    @Override
    public int getComparisonFailureCount() {
        return comparisonFailureCount;
    }

    public List<String> getComparisonFailures() {
        return comparisonFailures;
    }

    /**
     * Get if details of failed comparisons should be written to stdout automatically.
     * Default is true.
     * @return if ...
     */
    public boolean isOutputFailedComparisons() {
        return outputFailedComparisons;
    }

    /**
     * Set if details of failed comparisons should be written to stdout automatically.
     * Default is true.
     * @param outputFailedComparisons if ...
     */
    public void setOutputFailedComparisons(final boolean outputFailedComparisons) {
        this.outputFailedComparisons = outputFailedComparisons;
    }

    /**
     * Get if a failure should be counted.
     * @return if...
     */
    public boolean isCountComparisonFailures() {
        return countComparisonFailures;
    }

    /**
     * Set if a failure should be counted.
     * @return if...
     */
    public void setCountComparisonFailures(final boolean countComparisonFailures) {
        this.countComparisonFailures = countComparisonFailures;
    }

    /**
     * The comparison failure score of the last comparison.
     * @return comparison failure score of the last comparison
     */
    public int getCurrentComparisonScore() {
        return currentComparisonScore;
    }

    /**
     * Get if read names were preserved and thus we can use them to aid with comparison.
     * @return if...
     */
    @Override
    public boolean isReadNamesPreserved() {
        return readNamesPreserved;
    }

    /**
     * Set if read names were preserved and thus we can use them to aid with comparison.
     * @param readNamesPreserved if...
     */
    @Override
    public void setReadNamesPreserved(final boolean readNamesPreserved) {
        this.readNamesPreserved = readNamesPreserved;
    }

    /**
     * Get if the source base can be and and thd destination base be a non-N.
     * @return if...
     */
    public boolean isAllowSourceNs() {
        return allowSourceNs;
    }

    /**
     * Set if the source base can be and and thd destination base be a non-N.
     * @return if...
     */
    public void setAllowSourceNs(boolean allowSourceNs) {
        this.allowSourceNs = allowSourceNs;
    }

    /**
     * Compare expectedSamRecord vs actualSamRecord. Output details if differences are found.
     * @param expectedSamRecord the expected sam record
     * @param actualSamRecord the actual sam record
     * @param gobyAlignment the actual goby alignment record
     * @return the comparison score, 0 means perfect-ish comparison.
     */
    @Override
    public int compare(final SAMRecord expectedSamRecord, final SAMRecord actualSamRecord,
                       final Alignments.AlignmentEntry gobyAlignment) {
        currentComparisonScore = 0;
        if (!initialized) {
            initialized = true;
            reset();
        }
        comparisonFailures.clear();
        compareField("Positions don't match", expectedSamRecord.getAlignmentStart(), actualSamRecord.getAlignmentStart(), 2);
        compareField("Ref Index don't match", expectedSamRecord.getReferenceName(), expectedSamRecord.getReferenceName(), 2);
        if (readNamesPreserved) {
            compareField("Read name doesn't match", expectedSamRecord.getReadName(), actualSamRecord.getReadName(), 2);
        }
        compareField("Flags don't match", expectedSamRecord.getFlags(), actualSamRecord.getFlags(), 1);
        compareField("Mapping quality doesn't match", expectedSamRecord.getMappingQuality(), actualSamRecord.getMappingQuality(), 2);
        compareField("Read paired flag doesn't match", expectedSamRecord.getReadPairedFlag(), actualSamRecord.getReadPairedFlag(), 1);
        compareField("Read length doesn't match", expectedSamRecord.getReadLength(), actualSamRecord.getReadLength(), 2);
        if (checkMate) {
            if (expectedSamRecord.getReadPairedFlag()) {
                compareField("Read mate unmapped doesn't match", expectedSamRecord.getMateUnmappedFlag(), actualSamRecord.getMateUnmappedFlag(), 1);
                if (!expectedSamRecord.getMateUnmappedFlag()) {
                    compareField("Mate alignment start doesn't match", expectedSamRecord.getMateAlignmentStart(), actualSamRecord.getMateAlignmentStart(), 2);
                    compareField("Mate alignment index doesn't match", expectedSamRecord.getMateReferenceIndex(), actualSamRecord.getMateReferenceIndex(), 2);
                    compareField("Inferred insert size doesn't match", expectedSamRecord.getInferredInsertSize(), actualSamRecord.getInferredInsertSize(), 2);
                }
            }
        }
        compareField("Positive/negative strand doesn't match", expectedSamRecord.getReadNegativeStrandFlag(), actualSamRecord.getReadNegativeStrandFlag(), 2);
        compareField("Cigars don't match", expectedSamRecord.getCigarString(), actualSamRecord.getCigarString(), 2);

        final String eMdz;
        final String aMdz;
        if (canonicalMdzForComparison) {
            eMdz = SamHelper.canonicalMdz(expectedSamRecord.getStringAttribute("MD"));
            aMdz = SamHelper.canonicalMdz(actualSamRecord.getStringAttribute("MD"));
        } else {
            eMdz = expectedSamRecord.getStringAttribute("MD");
            aMdz = actualSamRecord.getStringAttribute("MD");
        }
        compareField("MD:Z doesn't match", eMdz, aMdz, 3);

        final String eRead = usableReadOf(expectedSamRecord);
        final String aRead = usableReadOf(actualSamRecord);

        compareBasesField("Reads didn't match", eRead, aRead, 3);
        if (mappedQualitiesPreserved) {
            compareField("Quality didn't match", expectedSamRecord.getBaseQualityString(), actualSamRecord.getBaseQualityString(), 3);
        } else {
            if (gobyAlignment != null) {
                final int readLength = expectedSamRecord.getReadLength();
                for (final Alignments.SequenceVariation seqvar : gobyAlignment.getSequenceVariationsList()) {
                    final String to = seqvar.getTo();
                    int i = 0;
                    for (final char toChar : to.toCharArray()) {
                        if (toChar != '-') {
                            final int checkPosition;
                            if (expectedSamRecord.getReadNegativeStrandFlag()) {
                                checkPosition = readLength - seqvar.getReadIndex();
                            } else {
                                checkPosition = seqvar.getReadIndex() + i - 1;
                            }
                            try {
                                final String eQual = expectedSamRecord.getBaseQualityString();
                                final String aQual = actualSamRecord.getBaseQualityString();
                                compareField("Quality at location specified by seqvar (" + checkPosition +
                                        ") doesn't match " +
                                        "e=" + eQual.substring(checkPosition, checkPosition + 1) + " " +
                                        "a=" + aQual.substring(checkPosition, checkPosition + 1),
                                        eQual.substring(checkPosition, checkPosition + 1),
                                        aQual.substring(checkPosition, checkPosition + 1), 2);
                            } catch (IndexOutOfBoundsException e) {
                                comparisonFailures.add("Quality at " + checkPosition + " index out of bounds.");
                            }
                            i++;
                        }
                    }
                }
            }
        }
        readNum++;
        if (!comparisonFailures.isEmpty()) {
            if (outputFailedComparisons) {
                dumpComparison(expectedSamRecord, actualSamRecord, gobyAlignment);
            }
            if (countComparisonFailures) {
                comparisonFailureCount++;
            }
        }
        return currentComparisonScore;
    }

    @Override
    public int finished() {
        return 0;
    }

    /**
     * Dump the details of expectedSamRecord and actualSamRecord (and gobyAlignment if available). This is
     * called when there are differences between expected and actual to help debug the conversion process.
     * The differences are written to stdout.
     * @param expectedSamRecord the expected sam record
     * @param actualSamRecord the actual sam record
     * @param gobyAlignment the actual goby alignment record
     */
    public void dumpComparison(final SAMRecord expectedSamRecord, final SAMRecord actualSamRecord,
                               final Alignments.AlignmentEntry gobyAlignment) {
        comparisonErrorDump.setLength(0);
        comparisonErrorDump.append("Read Num         : ").append(readNum).append('\n');
        comparisonErrorDump.append("     ERROR(s)    : ").append(ArrayUtils.toString(comparisonFailures)).append('\n');
        if (gobyAlignment != null) {
            comparisonErrorDump.append("     g.index     : ").append(gobyAlignment.getQueryIndex()).append('\n');
            comparisonErrorDump.append("     g.position  : ").append(gobyAlignment.getPosition()).append('\n');
            comparisonErrorDump.append("     g.leftClip  : ").append(gobyAlignment.getSoftClippedBasesLeft()).append('\n');
            comparisonErrorDump.append("     g.leftClipQual  : ").append(ByteArrayList.wrap(gobyAlignment.getSoftClippedQualityLeft().toByteArray())).append('\n');
            comparisonErrorDump.append("     g.rightClip : ").append(gobyAlignment.getSoftClippedBasesRight()).append('\n');
            comparisonErrorDump.append("     g.rightClipQual : ").append(ByteArrayList.wrap(gobyAlignment.getSoftClippedQualityRight().toByteArray())).append('\n');
            comparisonErrorDump.append("     g.qAlignLen : ").append(gobyAlignment.getQueryAlignedLength()).append('\n');
            comparisonErrorDump.append("     g.tAlignLen : ").append(gobyAlignment.getTargetAlignedLength()).append('\n');
        }
        comparisonErrorDump.append("     readName (S): ").append(expectedSamRecord.getReadName()).append('\n');
        comparisonErrorDump.append("     readName (D): ").append(actualSamRecord.getReadName()).append('\n');
        if (gobyAlignment != null && gobyAlignment.hasReadName()) {
            comparisonErrorDump.append("     readName (G): ").append(gobyAlignment.getReadName()).append('\n');
        }
        comparisonErrorDump.append("     position (S): ").append(expectedSamRecord.getAlignmentStart()).append('\n');
        comparisonErrorDump.append("     position (D): ").append(actualSamRecord.getAlignmentStart()).append('\n');
        comparisonErrorDump.append("     refName  (S): ").append(expectedSamRecord.getReferenceName()).append('\n');
        comparisonErrorDump.append("     refName  (D): ").append(actualSamRecord.getReferenceName()).append('\n');
        comparisonErrorDump.append("     flags    (S): ").append(expectedSamRecord.getFlags()).append('\n');
        comparisonErrorDump.append("     flags    (D): ").append(actualSamRecord.getFlags()).append('\n');
        comparisonErrorDump.append("     mapQual  (S): ").append(expectedSamRecord.getMappingQuality()).append('\n');
        comparisonErrorDump.append("     mapQual  (D): ").append(actualSamRecord.getMappingQuality()).append('\n');
        comparisonErrorDump.append("     negStrand(S): ").append(expectedSamRecord.getReadNegativeStrandFlag()).append('\n');
        comparisonErrorDump.append("     negStrand(D): ").append(actualSamRecord.getReadNegativeStrandFlag()).append('\n');
        comparisonErrorDump.append("     cigar    (S): ").append(expectedSamRecord.getCigar()).append('\n');
        comparisonErrorDump.append("     cigar    (D): ").append(actualSamRecord.getCigar()).append('\n');
        comparisonErrorDump.append("     mdz      (S): ").append(expectedSamRecord.getStringAttribute("MD")).append('\n');
        comparisonErrorDump.append("     mdz      (D): ").append(actualSamRecord.getStringAttribute("MD")).append('\n');
        comparisonErrorDump.append("     mdz-c    (S): ").append(SamHelper.canonicalMdz(expectedSamRecord.getStringAttribute("MD"))).append('\n');
        comparisonErrorDump.append("     mdz-c    (D): ").append(SamHelper.canonicalMdz(actualSamRecord.getStringAttribute("MD"))).append('\n');
        comparisonErrorDump.append("     read     (S): ").append(expectedSamRecord.getReadString()).append('\n');
        comparisonErrorDump.append("     read     (D): ").append(actualSamRecord.getReadString()).append('\n');
        comparisonErrorDump.append("     qual     (S): ").append(expectedSamRecord.getBaseQualityString()).append('\n');
        comparisonErrorDump.append("     qual     (D): ").append(actualSamRecord.getBaseQualityString()).append('\n');
        System.out.println(comparisonErrorDump.toString());
    }

    /**
     * Compare an int field.
     * @param error the error message if the comparison failes
     * @param expected the expected value
     * @param actual the actual value
     */
    private void compareField(final String error, final int expected, final int actual, final int failureScore) {
        if (expected != actual) {
            comparisonFailures.add(error);
            currentComparisonScore += failureScore;
        }
    }

    /**
     * Compare an field of bases.
     * @param error the error message if the comparison failes
     * @param expected the expected value
     * @param actual the actual value
     */
    private void compareBasesField(final String error, final String expected, final String actual, final int failureScore) {
        if (!allowSourceNs || expected.length() != actual.length()) {
            compareField(error, expected, actual, failureScore);
        } else {
            final int length = expected.length();
            boolean same = true;
            for (int i = 0; i < length; i++) {
                final char ebase = expected.charAt(i);
                final char abase = actual.charAt(i);
                if (ebase != 'N' && ebase != abase) {
                    same = false;
                    break;
                }
            }
            if (!same) {
                comparisonFailures.add(error);
                currentComparisonScore += failureScore;
            }
        }
    }

    /**
     * Compare a string field.
     * @param error the error message if the comparison failes
     * @param expected the expected value
     * @param actual the actual value
     */
    private void compareField(final String error, final String expected, final String actual, final int failureScore) {
        if (!expected.equals(actual)) {
            comparisonFailures.add(error);
            currentComparisonScore += failureScore;
        }
    }

    /**
     * Compare a boolean field.
     * @param error the error message if the comparison failes
     * @param expected the expected value
     * @param actual the actual value
     */
    private void compareField(final String error, final boolean expected, final boolean actual, final int failureScore) {
        if (expected != actual) {
            comparisonFailures.add(error);
            currentComparisonScore += failureScore;
        }
    }

    public String usableReadOf(final SAMRecord samRecord) {
        if (softClipsPreserved) {
            return samRecord.getReadString();
        } else {
            int clipLeft = 0;
            int clipRight = 0;
            final List<CigarElement> eCigarElements = samRecord.getCigar().getCigarElements();
            if (!eCigarElements.isEmpty()) {
                final CigarElement firstCigar = eCigarElements.get(0);
                if (firstCigar.getOperator() == CigarOperator.S) {
                    clipLeft = firstCigar.getLength();
                }
            }
            if (eCigarElements.size() > 1) {
                final CigarElement lastCigar = eCigarElements.get(eCigarElements.size() - 1);
                if (lastCigar.getOperator() == CigarOperator.S) {
                    clipRight = lastCigar.getLength();
                }
            }
            return samRecord.getReadString().substring(clipLeft, samRecord.getReadLength() - clipRight);
        }
    }
}
