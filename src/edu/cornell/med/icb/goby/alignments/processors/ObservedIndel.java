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

package edu.cornell.med.icb.goby.alignments.processors;


/**
 * @author Fabien Campagne
 *         Date: May 14, 2011
 *         Time: 10:50:34 AM
 */
public class ObservedIndel implements Comparable<ObservedIndel> {
    /**
     * Start position is zero-based.
     */
    final int startPosition;
    /**
     * End position is zero-based.
     */
    final int endPosition;
    final String from;
    final String to;
    private int length;
    /**
     * The index of the first base in the read where the indel was observed.
     */
    public int readIndex;

    public ObservedIndel(final int startPosition, final String from, final String to, final int readIndex) {
        this(startPosition, startPosition + Math.max(from.length(), to.length()), from, to);
        this.readIndex = readIndex;
    }

    public ObservedIndel(final int startPosition, final int endPosition, final String from, final String to) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.from = from;
        this.to = to;
        this.length = Math.max(from.length(), to.length());
    }

    public boolean isHasQualityScores() {
        return hasQualityScores;
    }

    /**
     * Return the quality scores for bases of this indel.
     *
     * @return
     */
    public byte[] getQualityScores() {

        return qualityScores;
    }

    private byte[] qualityScores;
    private boolean hasQualityScores;

    public void setQualityScores(byte[] qualityScores) {
        this.qualityScores = qualityScores;
    }

    /**
     * Return the span of the indel, in bases. This is the difference between endPosition and startPosition, measured on
     * the reference.
     *
     * @return the indel reference span.
     */
    public int positionSpan() {
        return endPosition - startPosition;
    }

    /**
     * Return the length of the indel. (e.g., --- has a length of 3)
     *
     * @return length.
     */
    public int getLength() {
        return length;
    }

    /**
     * Construct an indel observation.
     *
     * @param startPosition The position where the indel starts, zero-based, position of the base at the left of the first gap.
     * @param endPosition   The position where the indel ends, zero-based, position of the base at the right of the first gap.
     * @param from          Bases in the reference
     * @param to            Bases in the read
     * @param readIndex     Index of the base in the read at the left of where the indel is observed.
     */
    public ObservedIndel(final int startPosition, final int endPosition, final String from, final String to,
                         final int readIndex) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.from = from;
        this.to = to;
        this.readIndex = readIndex;
    }

    public int getStart() {
        return startPosition;
    }

    public int getEnd() {
        return endPosition;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public final boolean isReadInsertion() {
        return to.contains("-");
    }

    @Override
    public int hashCode() {
        return startPosition ^ endPosition ^ from.hashCode() ^ to.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ObservedIndel)) {
            return false;
        }
        final ObservedIndel other = (ObservedIndel) o;
        return other.startPosition == startPosition &&
                other.endPosition == endPosition &&
                other.from().equals(from) && other.to.equals(to);
    }

    @Override
    public String toString() {
        return String.format("%s/%s %d-%d", from, to, startPosition, endPosition);
    }

    final int BEFORE = -1;
    final int EQUAL = 0;
    final int AFTER = 1;

    @Override
    public int compareTo(final ObservedIndel other) {


        if (this == other) return EQUAL;
        /*if (!(o instanceof ObservedIndel)) {
            return BEFORE;
        }*/

        //primitive numbers follow this form
        if (this.startPosition < other.startPosition) return BEFORE;
        if (this.startPosition > other.startPosition) return AFTER;
        if (this.endPosition < other.endPosition) return BEFORE;
        if (this.endPosition > other.endPosition) return AFTER;
        int a = this.from.compareTo(other.to);
        if (a != 0) return a;
        a = this.from.compareTo(other.to);
        return a;

    }
}
