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

package edu.cornell.med.icb.goby.reads;

import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.lang.MutableString;
import net.sf.picard.reference.FastaSequenceIndex;
import net.sf.picard.reference.IndexedFastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * Implementation of RandomAccessSequenceInterface backed by a 'samtools faidx' fasta indexed file.
 *
 * @author Fabien Campagne
 *         Date: 2/25/12
 *         Time: 2:16 PM
 */
public class PicardFastaIndexedSequence implements RandomAccessSequenceInterface {
    IndexedFastaSequenceFile delegate;
    final int[] lengths;
    final Object2IntMap<String> namesToIndices = new Object2IntAVLTreeMap<String>();
    final String[] names;
    private ReferenceSequence cachedSeq;
    private final MutableString baseBuffer = new MutableString();
    private FastaSequenceIndex indexDelegate;
    private long[] basesPerLine;


    public PicardFastaIndexedSequence(final String filename) throws FileNotFoundException {
        delegate = new IndexedFastaSequenceFile(new File(filename));
        indexDelegate = new FastaSequenceIndex(new File(filename + ".fai"));
        final int numContigs = indexDelegate.size();
        if (!delegate.isIndexed())
            throw new FileNotFoundException("An fasta idx index must be found for filename " + filename);


        lengths = new int[numContigs];
        names = new String[numContigs];
        basesPerLine = new long[numContigs];

        final LineIterator lineIt = new LineIterator(new FileReader(filename + ".fai"));

        // collect the contig names by parsing the text fai file. For some bizarre reason neither the
        // IndexedFastaSequenceFile class nor the FastaSequenceIndex class expose the contig names, yet
        // contig name is the parameter expected to get data from the sequences!
        int index = 0;
        while (lineIt.hasNext()) {
            final String line = lineIt.nextLine();
            final String[] tokens = line.split("[\\s]");
            names[index] = tokens[0];
            namesToIndices.put(tokens[0], index);
            lengths[index] = Integer.parseInt(tokens[1]);
            basesPerLine[index] = Long.parseLong(tokens[2]);
            index++;
        }


    }

    @Override
    public char get(final int referenceIndex, final int position) {
        if (position >= cachedStart && position < cachedStop && referenceIndex == cachedReferenceIndex) {
            return baseBuffer.charAt(position - cachedStart);
        } else {
            getRange(referenceIndex, position, Math.min((int) lengths[referenceIndex] - position, 10000), baseBuffer);
            return baseBuffer.charAt(position - cachedStart);
        }

    }

    @Override
    public int getLength(final int targetIndex) {
        return (int) lengths[targetIndex];
    }

    private int cachedReferenceIndex = -1;
    int cachedStart = -1;
    int cachedStop = -1;

    @Override
    public void getRange(final int referenceIndex, final int position, final int length, final MutableString bases) {
        bases.setLength(0);
        final int stop = Math.max(position + length, lengths[referenceIndex] - position);
        final int oneBasedPosition = position + 1;
        final ReferenceSequence seq = delegate.getSubsequenceAt(names[referenceIndex], oneBasedPosition, stop);
        assert seq.getContigIndex() == referenceIndex : " contig index and reference index must match.";
        final int i=0;
        for (final byte b : seq.getBases()) {
            final char c = (char) b;

            if (c != '\n') {
                bases.append((char) b);
            }
        }
        cachedStart = position;
        cachedStop = stop;
        cachedSeq = seq;
        cachedReferenceIndex = referenceIndex;
     //   System.out.println("got: " + bases);
    }

    @Override
    public int getReferenceIndex(final String referenceId) {
        return (int) namesToIndices.getInt(referenceId);
    }

    @Override
    public String getReferenceName(final int index) {
        return names[index];
    }

    @Override
    public int size() {
        return names.length;
    }

    public void print(final int referenceIndex) {
        for (int i=0;i<getLength(referenceIndex);i++ ){
           if (i % basesPerLine[referenceIndex]==0) {
                System.out.println(" "+i+ " ");
            }
            System.out.print(get(referenceIndex, i));

        }
    }
}
