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

package edu.cornell.med.icb.goby.algorithmic.algorithm;

import edu.cornell.med.icb.goby.algorithmic.data.Annotation;
import edu.cornell.med.icb.goby.algorithmic.data.Read;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Collections;

public class AnnotationCount implements AnnotationCountInterface {
    private final ObjectList<Read> reads;
    private final IntList readStart;
    private final ComputeCount baseCounter;

    public AnnotationCount() {
        super();
        baseCounter = new ComputeCount();
        readStart = new IntArrayList();
        reads = new ObjectArrayList<Read>();
    }

    public ComputeCountInterface getBaseCounter() {
        return baseCounter;
    }

    public void startPopulating() {
        baseCounter.startPopulating();
    }

    /**
     * Populate the data structures with start and end positions.
     * This implementation ignores queryIndex.
     *
     * @param startPosition
     * @param endPosition
     * @param queryIndex
     */
    public void populate(final int startPosition, final int endPosition, final int queryIndex) {
        this.populate(startPosition, endPosition);
    }

    public final void populate(final int startPosition, final int endPosition) {
        final Read read = new Read(startPosition, endPosition);
        reads.add(read);
        readStart.add(startPosition);
        baseCounter.populate(startPosition, endPosition);
    }

    public void sortReads() {
        Collections.sort(reads, new Read.ReadSortByStart());
        Collections.sort(readStart);
    }

    /**
     * For hasmap map with keylist key, find the key for index.
     * if index is in map, return the key, otherwise return the key immediately less than index
     *
     * @param searchKey the key to look for
     * @param keyList   the list to search
     * @return the index on the keyList for the searchKey postion or immediate previous one
     */
    public final int getIndex(final int searchKey, final IntList keyList) {
        //  if (map.containsKey(index)) return index;
        final int x = Collections.binarySearch(keyList, searchKey); //fails to find index returns position+1
        int index = x < 0 ? -x - 2 : x; //the true index to
        index = (index < 0) ? 0 : index;
        return index;
    }

    /**
     * Returns the value at index at the map either starts or ends.
     *
     * @param searchKey the position on chromosome to get value
     * @param keyList   e.g. startKeys or endKeys
     * @param map       e.g. starts or ends
     * @return the count on position index on chromosome
     */
    public final int getValue(final int searchKey, final IntList keyList, final Int2IntMap map) {
        if (map.containsKey(searchKey)) {
            return map.get(searchKey);
        }
        final int index = getIndex(searchKey, keyList);
        return map.get(keyList.get(index));
    }

    /**
     * Returns the average read coverage per base for a segment, or the sequencing depth.
     * requires baseRun to generate the countPerBase hashmap
     *
     * @param start of a segment
     * @param end   of a segment
     * @return average count per base on this segment
     */
    public final float averageReadsPerPosition(final int start, final int end) {
        long sum = 0;

        final int startIndex = getIndex(start, baseCounter.countKeys);
        final int startKey = baseCounter.countKeys.get(startIndex);
        final int startCount = baseCounter.countPerBase.get(startKey);

        //    System.out.println("start   "+start +"  start index   "+startIndex +"   start Key   "+startKey);
        final long startOverCountArea = (start - startKey) * startCount;

        final int maxIndex = baseCounter.countKeys.size() - 1;
        int endIndex = maxIndex;
        int index = startIndex;
        while (index < maxIndex) {
            final int key = baseCounter.countKeys.get(index);
            final int count = baseCounter.countPerBase.get(key);
            final int nextKey = baseCounter.countKeys.get(index + 1);
            //      System.out.println(key+"    nextkey "+nextKey+" count   "+count);
            if (nextKey > end) {
                endIndex = index;
                break;
            }
            final long recArea = count * (nextKey - key);
            sum += recArea;
            index++;
        }

        final int endKey = baseCounter.countKeys.get(endIndex);
        final int endCount = baseCounter.countPerBase.get(endKey);
        final long endUnderCountArea = (end - endKey + 1) * endCount;
        //    System.out.println("sum "+sum+" startOver   "+startOverCountArea+"  endUnder    "+endUnderCountArea);
        sum = sum - startOverCountArea + endUnderCountArea;

        final int segmentSize = end - start + 1;
        //     System.out.println("new sum "+sum+" segsize "+segmentSize);
        if (end < start) {
            return 0;
        } else {
            return ((float) sum) / ((float) segmentSize);
        }
    }

    /**
     * Return the number of reads that map within exons of an annotation, excluding intron counts.
     *
     * @param annot an annotation
     * @return number of reads covered on the genes except all reads exclusively in introns
     */
    public double geneExpressionCount(final Annotation annot) {
        double sum = countReadsPartiallyOverlappingWithInterval(annot.getStart(), annot.getEnd());
        final int numIntrons = annot.getSegments().size() - 1;
        for (int k = 0; k < numIntrons; k++) {
            sum -= countReadsStriclyWithinInterval(annot.getSegments().get(k).getEnd() + 1, annot.getSegments().get(k + 1).getStart() - 1);
        }
        return sum;
    }

    public void accumulate() {
        baseCounter.accumulate();
    }

    public void baseCount() {
        baseCounter.baseCount();
    }


    /**
     * Returns the number of reads completely contained within an interval of the reference sequence.
     *
     * @param start position
     * @param end   position
     * @return the number of reads
     */
    public double countReadsStriclyWithinInterval(final int start, final int end) {
        final int n = reads.size();
        int i = getIndex(start, readStart);
        int count = 0;

        while (i < n) {
            final Read read = reads.get(i);
            if (read.start >= start && read.end <= end) {
                count++;
            } else if (read.start > end) {
                break;
            }
            i++;
        }
        return count;
    }


    /**
     * Returns the number of reads that partially overlap  with the given annotation interval.
     *
     * @param start position
     * @param end   position
     * @return the number of reads
     */
    public double countReadsPartiallyOverlappingWithInterval(final int start, final int end) {
        return getValue(end, baseCounter.startKeys, baseCounter.starts)
                - getValue(start, baseCounter.endKeys, baseCounter.ends);
    }


}
