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

package edu.cornell.med.icb.goby.counts;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * @author Fabien Campagne
 *         Date: May 29, 2009
 *         Time: 1:08:55 PM
 */
public class TestPeakAggregator {
    private static final Log LOG = LogFactory.getLog(TestPeakAggregator.class);
    private static final String BASE_TEST_DIR = "test-results/peaks";

    private final int lengthA = 5;
    private final int lengthB = 100000;
    private final int lengthC = 10;
    private final int lengthD = 1;

    @Test
    public void testPeakFinding() throws IOException {
        final String basename = FilenameUtils.concat(BASE_TEST_DIR, "counts-101.bin");
        writeSomeCounts(basename);
        CountsReader countReader = new CountsReader(new FileInputStream(basename));
        PeakAggregator peakIterator = new PeakAggregator(countReader);
        int count = 0;
        while (peakIterator.hasNext()) {
            final Peak peak = peakIterator.next();
            System.out.println(peak);
            count++;
        }
        assertEquals(2, count);

        // rewind and start again:
        countReader = new CountsReader(new FileInputStream(basename));
        peakIterator = new PeakAggregator(countReader);

        assertTrue(peakIterator.hasNext());
        Peak peak = peakIterator.next();
        assertEquals(5, peak.start);

        assertEquals(lengthB + lengthC, peak.length);
        assertEquals(13, peak.count);

        peak = peakIterator.next();
        assertEquals(lengthA + lengthB + lengthC + lengthA, peak.start);
        assertEquals(lengthD, peak.length);
        assertEquals(10, peak.count);
    }

    @Test
    public void testHasNext() throws IOException {
        final String basename = FilenameUtils.concat(BASE_TEST_DIR, "counts-101.bin");
        writeSomeCounts(basename);
        final CountsReader countReader = new CountsReader(new FileInputStream(basename));
        final PeakAggregator peakIterator = new PeakAggregator(countReader);
        assertTrue(peakIterator.hasNext());
        peakIterator.next();
        assertTrue(peakIterator.hasNext());
        peakIterator.next();
        assertFalse(peakIterator.hasNext());
        try {
            peakIterator.next();
        } catch (NoSuchElementException e) {
            // we expect this
            return;
        }
        fail("Expected an exception when calling next too many times");
    }

    private void writeSomeCounts(final String basename) throws IOException {
        final CountsWriterI writerI = new CountsWriter(new FileOutputStream(basename));
        writerI.appendCount(0, lengthA);
        writerI.appendCount(1, lengthB);
        writerI.appendCount(12, lengthC);
        writerI.appendCount(0, lengthA);
        writerI.appendCount(10, lengthD);
        writerI.appendCount(0, lengthD);
        writerI.close();
    }

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
}
