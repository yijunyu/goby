/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                         Weill Medical College of Cornell University
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

import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Fabien Campagne
 *         Date: Apr 30, 2009
 *         Time: 6:15:52 PM
 */
public class TestReadWriteAlignments {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(TestReadWriteAlignments.class);
    private static final String BASE_TEST_DIR = "test-results/alignments";
    private int constantQueryLength=40;

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
    public void readWriteEntries101() throws IOException {
        final AlignmentWriterImpl writer =
                new AlignmentWriterImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-101"));
        writer.setNumAlignmentEntriesPerChunk(1000);
        final int numReads = 2000;
        final int numTargets = 10;
        int numExpected = 0;
        int position = 1;
        for (int referenceIndex = 0; referenceIndex < numTargets; referenceIndex++) {
            for (int queryIndex = 0; queryIndex < numReads; queryIndex++) {
                writer.setAlignmentEntry(queryIndex, referenceIndex, position++, 30, false, constantQueryLength);
                writer.appendEntry();
                numExpected++;
            }
        }

        writer.close();
        writer.printStats(System.out);

        int count = 0;
        final AlignmentReader reader =
                new AlignmentReaderImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-101"));
        int maxQueryIndex = -1;
        int maxTargetIndex = -1;
        reader.readHeader();
        while (reader.hasNext()) {
            final Alignments.AlignmentEntry alignmentEntry = reader.next();
            assert alignmentEntry.hasPosition();
            maxQueryIndex = Math.max(maxQueryIndex, alignmentEntry.getQueryIndex());
            maxTargetIndex = Math.max(maxTargetIndex, alignmentEntry.getTargetIndex());
            count++;
        }
        assertEquals(numExpected, count);
        assertEquals("number of queries must match", numReads - 1, maxQueryIndex);
        assertEquals("number of targets must match", numTargets - 1, maxTargetIndex);


    }



    @Test
    public void writeEmptyIds() throws IOException {
        final AlignmentWriter writer =
                new AlignmentWriterImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-emptyids"));
        final IndexedIdentifier queryIds = new IndexedIdentifier();
        assertNotNull(queryIds.keySet());
        writer.setQueryIdentifiers(queryIds);
        writer.setTargetIdentifiers(queryIds);
        writer.close();

        // TODO: assert something here
    }

    @Test
    public void readWriteHeader102() throws IOException {
        final IndexedIdentifier queryIds = new IndexedIdentifier();
        queryIds.put(new MutableString("query:1"), 1);
        queryIds.put(new MutableString("query:2"), 2);

        final IndexedIdentifier targetIds = new IndexedIdentifier();
        targetIds.put(new MutableString("target:1"), 1);

        final AlignmentWriter writer =
                new AlignmentWriterImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-102"));

        assertNotNull(queryIds.keySet());
        writer.setQueryIdentifiers(queryIds);
        writer.setTargetIdentifiers(targetIds);
        writer.close();

        final AlignmentReaderImpl reader =
                new AlignmentReaderImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-102"));
        reader.readHeader();
        assertEquals(1, reader.getQueryIdentifiers().getInt(new MutableString("query:1")));
        assertEquals(-1, reader.getTargetIdentifiers().getInt(new MutableString("query:1")));
        assertEquals(1, reader.getTargetIdentifiers().getInt(new MutableString("target:1")));
        assertEquals(-1, reader.getTargetIdentifiers().getInt(new MutableString("target:2")));
    }

    @Test
    public void readWriteHeader103() throws IOException {
        final IndexedIdentifier queryIds = new IndexedIdentifier();
        // NOTE: there is no id for entry 0, this is ok
        queryIds.put(new MutableString("query:1"), 1);
        queryIds.put(new MutableString("query:2"), 2);

        final IndexedIdentifier targetIds = new IndexedIdentifier();
        targetIds.put(new MutableString("target:0"), 0);
        targetIds.put(new MutableString("target:1"), 1);

        final AlignmentWriter writer =
                new AlignmentWriterImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-103"));

        assertNotNull("Query ids should not be null", queryIds.keySet());
        writer.setQueryIdentifiers(queryIds);
        final int[] queryLengths = {0, 34, 84};


        assertNotNull("Target ids should not be null", targetIds.keySet());
        writer.setTargetIdentifiers(targetIds);
        final int[] targetLengths = {0, 42};
        writer.setTargetLengths(targetLengths);
        writer.close();

        final AlignmentReaderImpl reader =
                new AlignmentReaderImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-103"));
        reader.readHeader();
        assertEquals("Number of queries do not match", 2, reader.getNumberOfQueries());

        assertArrayEquals("Target lengths do not match", targetLengths, reader.getTargetLength());
        assertEquals("Number of targets do not match", 2, reader.getNumberOfTargets());


    }

    @Test
    public void readWriteHeader104() throws IOException {
        final IndexedIdentifier queryIds = new IndexedIdentifier();
        // NOTE: there is no id for entry 0, this is ok
        queryIds.put(new MutableString("query:1"), 1);
        queryIds.put(new MutableString("query:2"), 2);

        final IndexedIdentifier targetIds = new IndexedIdentifier();
        targetIds.put(new MutableString("target:0"), 0);
        targetIds.put(new MutableString("target:1"), 1);

        final AlignmentWriter writer =
                new AlignmentWriterImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-104"));

        assertNotNull("Query ids should not be null", queryIds.keySet());
        writer.setQueryIdentifiers(queryIds);


        assertNotNull("Target ids should not be null", targetIds.keySet());
        writer.setTargetIdentifiers(targetIds);
        final int[] targetLengths = {42, 42};
        writer.setTargetLengths(targetLengths);
        writer.close();

        final AlignmentReaderImpl reader =
                new AlignmentReaderImpl(FilenameUtils.concat(BASE_TEST_DIR, "align-104"));
        reader.readHeader();


        assertEquals("Number of queries do not match", 2, reader.getNumberOfQueries());


    }

    /**
     * Validate that the the alignment header constant query length
     * ({@link edu.cornell.med.icb.goby.alignments.Alignments.AlignmentHeader#hasConstantQueryLength()})
     * is set to true when queries of the same length get appended.
     * @throws IOException if there is a problem reading or writing the alignment
     */
    @Test
    public void constantQueryLengths() throws IOException {
        final AlignmentWriter writer = new AlignmentWriterImpl(FilenameUtils.concat(
                BASE_TEST_DIR, "constant-query-lengths"));

        for (int queryIndex = 0; queryIndex < 4; queryIndex++) {
            final Alignments.AlignmentEntry.Builder builder = Alignments.AlignmentEntry.newBuilder();
            builder.setQueryLength(11);
            builder.setQueryIndex(queryIndex);
            builder.setTargetIndex(42);
            builder.setPosition(0);
            builder.setMatchingReverseStrand(true);
            final Alignments.AlignmentEntry entry = builder.build();
            writer.appendEntry(entry);
        }
        writer.close();

        final AlignmentReaderImpl reader = new AlignmentReaderImpl(
                FilenameUtils.concat(BASE_TEST_DIR, "constant-query-lengths"));
        for (final Alignments.AlignmentEntry entry : reader) {
            assertEquals(11, entry.getQueryLength());
        }
        reader.readHeader();
        assertTrue("query length should be constant", reader.isConstantQueryLengths());
        assertEquals("Number of queries do not match", 4, reader.getNumberOfQueries());

    }

    /**
     * Validate that the the alignment header constant query length
     * ({@link edu.cornell.med.icb.goby.alignments.Alignments.AlignmentHeader#hasConstantQueryLength()})
     * is set to false when queries of the same length get appended.
     * @throws IOException if there is a problem reading or writing the alignment
     */
    @Test
    public void nonConstantQueryLengths() throws IOException {
        final int[] queryLengths = {11, 21, 12, 42};
        final AlignmentWriter writer = new AlignmentWriterImpl(FilenameUtils.concat(
                BASE_TEST_DIR, "non-constant-query-lengths"));

        int queryIndex = 0;
        for (final int queryLength : queryLengths) {
            final Alignments.AlignmentEntry.Builder builder = Alignments.AlignmentEntry.newBuilder();
            builder.setQueryLength(queryLength);
            builder.setQueryIndex(queryIndex++);
            builder.setTargetIndex(42);
            builder.setPosition(0);
            builder.setMatchingReverseStrand(true);
            final Alignments.AlignmentEntry entry = builder.build();
            writer.appendEntry(entry);
        }
        writer.close();

        final AlignmentReaderImpl reader = new AlignmentReaderImpl(
                FilenameUtils.concat(BASE_TEST_DIR, "non-constant-query-lengths"));
        for (final Alignments.AlignmentEntry entry : reader) {
            assertEquals(queryLengths[entry.getQueryIndex()], entry.getQueryLength());
        }
        reader.readHeader();
        assertFalse("query length must not be constant", reader.isConstantQueryLengths());
        assertEquals("Number of queries do not match", 4, reader.getNumberOfQueries());
      
    }

}
