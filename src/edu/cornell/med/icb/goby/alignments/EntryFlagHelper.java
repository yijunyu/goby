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

import net.sf.samtools.SAMRecord;

/**
 * Class to decode paired end and splice flags from an alignment entry.
 *
 * @author Fabien Campagne
 *         Date: Jan 6, 2011
 *         Time: 5:02:53 PM
 */
public class EntryFlagHelper {
    /**
     * Provide a textual representation of pair flags.
     *
     * @param entry The entry that contains pair flags.
     * @return An informative string about pairing information stored in flags in the entry.
     */
    public static String pairToString(final SAMRecord entry) {
        final StringBuffer sb = new StringBuffer();
        if (entry.getReadPairedFlag()) {
            sb.append("PAIRED|");
        }
        if (entry.getProperPairFlag()) {
            sb.append("PROPERLY_PAIRED|");
        }
        if (entry.getNotPrimaryAlignmentFlag()) {
            sb.append("NOT_PRIMARY_ALIGNMENT|");
        }
        if (entry.getReadUnmappedFlag()) {
            sb.append("READ_UNMAPPED|");
        }
        if (entry.getMateUnmappedFlag()) {
            sb.append("MATE_UNMAPPED|");
        }
        if (entry.getReadNegativeStrandFlag()) {
            sb.append("READ_REVERSE_STRAND|");
        }
        if (entry.getMateNegativeStrandFlag()) {
            sb.append("MATE_REVERSE_STRAND|");
        }
        if (entry.getFirstOfPairFlag()) {
            sb.append("FIRST|");
        }
        if (entry.getSecondOfPairFlag()) {
            sb.append("SECOND|");
        }

        return sb.toString();
    }

    public static String pairToString(final Alignments.AlignmentEntry entry) {
        final StringBuffer sb = new StringBuffer();
        if (isPaired(entry)) {
            sb.append("PAIRED|");
        }
        if (isProperlyPaired(entry)) {
            sb.append("PROPERLY_PAIRED|");
        }
        if (isNotPrimaryAlignment(entry)) {
            sb.append("NOT_PRIMARY_ALIGNMENT|");
        }
        if (isReadUnmapped(entry)) {
            sb.append("READ_UNMAPPED|");
        }
        if (isMateUnmapped(entry)) {
            sb.append("MATE_UNMAPPED|");
        }
        if (isReadReverseStrand(entry)) {
            sb.append("READ_REVERSE_STRAND|");
        }
        if (isMateReverseStrand(entry)) {
            sb.append("MATE_REVERSE_STRAND|");
        }
        if (isFirstInPair(entry)) {
            sb.append("FIRST|");
        }
        if (isSecondInPair(entry)) {
            sb.append("SECOND|");
        }

        return sb.toString();
    }

    /**
     * Provide a textual representation of splice flags.
     *
     * @param entry The entry that contains splice flags.
     * @return An informative string about splicing information stored in flags in the entry.
     */
    public static String spliceToString(final Alignments.AlignmentEntry entry) {
        final StringBuffer sb = new StringBuffer();
        if (isNormalSplice(entry)) {
            sb.append("NORMAL_SPLICE|");
        }

        if (isNovelSplice(entry)) {
            sb.append("NOVEL_SPLICE|");
        }

        return sb.toString();
    }
    /*
       paired end flags (based on SAM):
         000000001    paired
         000000010    properly paired
         000000100    read unmapped
         000001000    mate unmapped
         000010000    read reverse strand
         000100000    mate reverse strand
         001000000    first in pair
         010000000    second in pair
         100000000    not primary alignment
     */

    /**
     * If this alignment entry is part of a pair.
     *
     * @param entry The entry.
     * @return True if the entry is part of a pair.
     */
    public static boolean isPaired(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & 0x1L) != 0;
    }

    public static int paired() {
        return 1;
    }

    public static int properlyPaired() {
        return 1 << 1;
    }

    public static int readUnmapped() {
        return 1 << 2;
    }

    public static int mateUnmapped() {
        return 1 << 3;
    }

    public static int readReverseStrand() {
        return 1 << 4;
    }

    public static int mateReverseStrand() {
        return 1 << 5;
    }

    public static int firstInPair() {
        return 1 << 6;
    }

    public static int secondInPair() {
        return 1 << 7;
    }

    public static int notPrimaryAlignment() {
        return 1 << 8;
    }

    /**
     * If this alignment entry is properly paired. (e.g., mapped in the correct orientation and
     * within reasonable distance on the same reference sequence).
     *
     * @param entry The entry.
     * @return True if the entry is properly paired.
     */
    public static boolean isProperlyPaired(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 1)) != 0;
    }

    /**
     * If the read was not mapped. This method will return true, if we are processing
     * an entry that is a mate, for which the read was not mapped.
     *
     * @param entry The entry.
     * @return True if the read of a pair was not mapped (and we are looking at the mate).
     */
    public static boolean isReadUnmapped(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 2)) != 0;
    }

    /**
     * If the mate was not mapped. This method will return true, if we are processing
     * an entry that is a read, for which the corresponding mate could not be mapped.
     *
     * @param entry The entry.
     * @return True if the mate of a pair was not mapped (and we are looking at the primary read).
     */
    public static boolean isMateUnmapped(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 3)) != 0;
    }

    /**
     * Returns whether the pair read was matching the reverse strand. Equivalent to looking
     * at matchingReverseStrand if we are processing entry for the primary read.
     *
     * @param entry The entry.
     * @return True or False.
     */
    public static boolean isReadReverseStrand(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 4)) != 0;
    }

    /**
     * Returns whether the pair mate was matching the reverse strand. Equivalent to looking
     * at matchingReverseStrand if we are processing entry for the mate.
     *
     * @param entry The entry.
     * @return True or False.
     */
    public static boolean isMateReverseStrand(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 5)) != 0;
    }

    /**
     * Returns whether the pair mate was matching the reverse strand. Equivalent to looking
     * at matchingReverseStrand if we are processing entry for the mate.
     *
     * @param pairFlags The entry pairFlags.
     * @return True or False.
     */
    public static boolean isMateReverseStrand(final int pairFlags) {
        return (pairFlags & (0x1L << 5)) != 0;
    }

    /**
     * Returns true if the entry corresponds to the first/primary read in a pair.
     *
     * @param entry The entry.
     * @return True or False.
     */
    public static boolean isFirstInPair(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 6)) != 0;
    }

    /**
     * Returns true if the entry corresponds to the mate/second read in a pair.
     *
     * @param entry The entry.
     * @return True or False.
     */
    public static boolean isSecondInPair(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 7)) != 0;
    }

    /**
     * Returns true if another entry had a better alignment score across the reference.
     *
     * @param entry The entry.
     * @return True or False.
     */
    public static boolean isNotPrimaryAlignment(final Alignments.AlignmentEntry entry) {
        return (entry.getPairFlags() & (0x1L << 8)) != 0;
    }

    /*
       spliced flags:
         000000001    normal
         000000010    novel
     */

    /**
     * If this alignment is a "normal" splice
     *
     * @param entry The entry.
     * @return True if the entry is a "normal" splice.
     */
    public static boolean isNormalSplice(final Alignments.AlignmentEntry entry) {
        return (entry.getSplicedFlags() & 0x1L) != 0;
    }

    /**
     * If this alignment is a "novel" splice
     *
     * @param entry The entry.
     * @return True if the entry is a "normal" splice.
     */
    public static boolean isNovelSplice(final Alignments.AlignmentEntry entry) {
        return (entry.getSplicedFlags() & (0x1L << 1)) != 0;
    }

}
