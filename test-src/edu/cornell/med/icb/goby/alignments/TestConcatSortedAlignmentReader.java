/*
 * Copyright (C) 2010 Institute for Computational Biomedicine,
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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Fabien Campagne
 *         Date: Jun 22, 2010
 *         Time: 11:26:20 AM
 */
public class TestConcatSortedAlignmentReader {

    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(TestConcatSortedAlignmentReader.class);
    private static final String BASE_TEST_DIR = "test-results/sort-concat";

    @BeforeClass
    public static void initializeTestDirectory() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating base test directory: " + BASE_TEST_DIR);
        }
        FileUtils.forceMkdir(new File(BASE_TEST_DIR));
    }

    @AfterClass
    public static void cleanupTestDirectory() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting base test directory: " + BASE_TEST_DIR);
        }
        FileUtils.forceDeleteOnExit(new File(BASE_TEST_DIR));
    }


    @Test
    public void testSortConcatWithGenomicRange() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        GenomicRange genomicRange = new GenomicRange(1, 3,
                1, 10);
        concat.setGenomicRange(genomicRange);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {/* 3 is not included because the range start is non inclusive. */ 5, 6, 7, 8, 9, 10, 10,};
        for (final Alignments.AlignmentEntry entry : concat) {
            System.out.println("entry.position(): " + entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }

    @Test
    public void testSortConcatWithGenomicRange3() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        GenomicRange genomicRange = new GenomicRange(0, 0,
                1, 100000);
        concat.setGenomicRange(genomicRange);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {1, 2, 3, 5, 6, 7, 8, 9, 10, 10, 12, 99};
        for (final Alignments.AlignmentEntry entry : concat) {
            System.out.println("entry.position(): " + entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }


    @Test
    public void testSortConcatWithGenomicRangeSmaller() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        GenomicRange genomicRange = new GenomicRange(1, 7,
                1, 9);
        concat.setGenomicRange(genomicRange);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {/* 7 is not included because the range start is non inclusive. */  8, 9,};
        for (final Alignments.AlignmentEntry entry : concat) {
            System.out.println("entry.position(): " + entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }

    @Test
    public void testSortConcatWithGenomicRangeSmaller2() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        GenomicRange genomicRange = new GenomicRange(1, 7,
                1, 8);
        concat.setGenomicRange(genomicRange);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {/* 7 is not included because the range start is non inclusive. */  8,};
        for (final Alignments.AlignmentEntry entry : concat) {
            System.out.println("entry.position(): " + entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }

    @Test
    public void testSortConcatNotInGenomicRange() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        GenomicRange genomicRange = new GenomicRange(0, 3,     // wrong targetIndex, alignment only has refIndex=1
                0, 10);
        concat.setGenomicRange(genomicRange);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {3, 5, 6, 7, 8, 9, 10, 10,};
        for (final Alignments.AlignmentEntry entry : concat) {
            // System.out.println("entry.position(): "+entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(0, sortedPositions.size());

    }


    @Test
    public void testSortConcat() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {1, 2, 3, 5, 6, 7, 8, 9, 10, 10, 12, 99};
        for (final Alignments.AlignmentEntry entry : concat) {
            // System.out.println("entry.position(): "+entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }


    @Test
       public void testSortConcatWithReadGroupOverride() throws IOException {
        final ReadGroupHelper readGroupHelper=new ReadGroupHelper();
        readGroupHelper.setOverrideReadGroups(true);

        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3) {
               @Override
               public ReadGroupHelper getReadGroupHelper() {

                   return readGroupHelper;
               }
           };
           final IntList sortedPositions = new IntArrayList();
           final int[] expectedPositions = {1, 2, 3, 5, 6, 7, 8, 9, 10, 10, 12, 99};
           for (final Alignments.AlignmentEntry entry : concat) {
               // System.out.println("entry.position(): "+entry.getPosition());
               sortedPositions.add(entry.getPosition());
           }
           assertEquals(expectedPositions.length, sortedPositions.size());
           for (int i = 0; i < expectedPositions.length; i++) {
               assertEquals(expectedPositions[i], sortedPositions.getInt(i));
           }
       }

    @Test
    public void testSortConcatNonAmbiguous() throws IOException {
        final ConcatSortedAlignmentReader concat = new ConcatSortedAlignmentReader(basename1, basename2, basename3);
        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {1, 2, 3, 5, 6, 7, 8, 9, 10, 10, 12, 99};
        for (final Alignments.AlignmentEntry entry : concat) {
            // System.out.println("entry.position(): "+entry.getPosition());
            sortedPositions.add(entry.getPosition());
        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }
    }

    @Test
    public void testLoadNonAmbiguousOnlySkipTo() throws IOException {

        // we now install a factory that removes entries whose reads match ambiguously:
        final ConcatSortedAlignmentReader concatReader = new ConcatSortedAlignmentReader(
                new NonAmbiguousAlignmentReaderFactory(),
                false, basename1, basename2, basename3);
        // exclude all entries from reader 2 by position:

        final IntList sortedPositions = new IntArrayList();
        final int[] expectedPositions = {6, 7, 8, 9, 10, 10, 12, 99};
        Alignments.AlignmentEntry entry = null;
        while ((entry = concatReader.skipTo(1, 6)) != null) {

            // System.out.println("entry.position(): "+entry.getPosition());
            sortedPositions.add(entry.getPosition());

        }
        assertEquals(expectedPositions.length, sortedPositions.size());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], sortedPositions.getInt(i));
        }

    }

    static final String basename1 = FilenameUtils.concat(BASE_TEST_DIR, "sort-concat-1");
    static final String basename2 = FilenameUtils.concat(BASE_TEST_DIR, "sort-concat-2");
    static final String basename3 = FilenameUtils.concat(BASE_TEST_DIR, "sort-concat-3");

    @Before
    public void setUp() throws IOException {

        final AlignmentWriterImpl writer1 =
                new AlignmentWriterImpl(basename1);
        writer1.setNumAlignmentEntriesPerChunk(1000);
        writer1.setTargetLengths(new int[]{10000, 10000});
        // we write this alignment sorted:
        writer1.setSorted(true);

        append(writer1, 1, 1);
        append(writer1, 1, 2);
        append(writer1, 1, 3);
        append(writer1, 1, 10);
        append(writer1, 1, 99);

        writer1.close();

        final AlignmentWriterImpl writer2 =
                new AlignmentWriterImpl(basename2);
        writer2.setNumAlignmentEntriesPerChunk(1000);
        writer2.setTargetLengths(new int[]{10000, 10000});
        // we write this alignment sorted:
        writer2.setSorted(true);

        append(writer2, 1, 5);
        append(writer2, 1, 8);
        append(writer2, 1, 9);
        append(writer2, 1, 12);

        writer2.close();

        final AlignmentWriterImpl writer3 =
                new AlignmentWriterImpl(basename3);
        writer3.setNumAlignmentEntriesPerChunk(1000);
        writer3.setTargetLengths(new int[]{10000, 10000});

        // we write this alignment sorted:
        writer3.setSorted(true);

        append(writer3, 1, 6);
        append(writer3, 1, 7);
        append(writer3, 1, 10);

        writer3.close();


    }


    private void append(final AlignmentWriterImpl writer, final int referenceIndex, final int position) throws IOException {
        writer.setAlignmentEntry(0, referenceIndex, position, 1, false, 50);

        writer.appendEntry();
    }


}
