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
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * @author Fabien Campagne
 *         Date: May 6, 2009
 *         Time: 2:48:35 PM
 */
public class TestReadWriteCounts {
    private static final Log LOG = LogFactory.getLog(TestReadWriteCounts.class);
    private static final String BASE_TEST_DIR = "test-results/counts";

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
    public void testReadWrite() throws IOException {
        final String basename = FilenameUtils.concat(BASE_TEST_DIR, "counts-101.bin");
        final CountsWriterI writerI = new CountsWriter(new FileOutputStream(basename), 45);

        writerI.appendCount(10, 1);
        writerI.appendCount(15, 1);

        writerI.close();

        final CountsReader reader = new CountsReader(new FileInputStream(basename));

        assertTrue(reader.hasNextPosition());
        assertEquals(10, reader.nextCountAtPosition());
        assertEquals(15, reader.nextCountAtPosition());
        assertFalse(reader.hasNextPosition());
    }

    @Test
    public void testDeltaCount() {
        for (int x = -10000; x < 10000; x++) {
          assertEquals(x, CountsReader.decodeDeltaCount(CountsWriter.encodeDeltaCount(x)));
        }
    }

    @Test
    @SuppressWarnings("deprecation")  // We are intentionally testing a deprecated method here
    public void testReadTransitions() throws IOException {
        final String basename = FilenameUtils.concat(BASE_TEST_DIR, "counts-104.bin");
        final CountsWriterI writerI = new CountsWriter(new FileOutputStream(basename), 45);

        writerI.appendCount(10, 1);
        writerI.appendCount(15, 1);

        writerI.close();

        final CountsReader reader = new CountsReader(new FileInputStream(basename));

        assertTrue(reader.hasNextTransition());
        reader.nextTransition();

        assertEquals(0, reader.getPosition());
        assertEquals(1, reader.getLength());
        assertEquals(10, reader.getCount());
        assertEquals(-35, reader.getDeltaCount());

        assertTrue(reader.hasNextPosition());
        reader.nextTransition();

        assertEquals(1, reader.getPosition());
        assertEquals(1, reader.getLength());
        assertEquals(15, reader.getCount());
        assertEquals(5, reader.getDeltaCount());
        assertFalse(reader.hasNextTransition());
    }

    @Test
    @SuppressWarnings("deprecation")  // We are intentionally testing a deprecated method here
    public void testReadWrite2() throws IOException {
        final String basename =  FilenameUtils.concat(BASE_TEST_DIR, "counts-102.bin");
        final CountsWriterI writerI = new CountsWriter(new FileOutputStream(basename), 0);

        final int lengthA = 5;
        final int lengthB = 100000;
        final int lengthC = 10;
        final int lengthD = 1;

        writerI.appendCount(10, lengthA);
        writerI.appendCount(11, lengthB);
        writerI.appendCount(12, lengthC);

        writerI.appendCount(10, lengthD);
        writerI.close();

        final CountsReader reader = new CountsReader(new FileInputStream(basename));

        for (int index = 0; index < lengthA; index++) {
            assertTrue(reader.hasNextPosition());
            assertEquals(10, reader.nextCountAtPosition());
        }
        for (int index = 0; index < lengthB; index++) {
            assertTrue(reader.hasNextPosition());
            assertEquals(11, reader.nextCountAtPosition());
        }
        for (int index = 0; index < lengthC; index++) {
            assertTrue(reader.hasNextPosition());
            assertEquals(12, reader.nextCountAtPosition());
        }
        for (int index = 0; index < lengthD; index++) {
            assertTrue(reader.hasNextPosition());
            assertEquals(10, reader.nextCountAtPosition());
        }
        assertFalse(reader.hasNextPosition());
    }

    @Test
    @SuppressWarnings("deprecation")  // We are intentionally testing a deprecated method here
    public void testReadWrite3() throws IOException {
        final String basename =  FilenameUtils.concat(BASE_TEST_DIR, "counts-103.bin");
        final CountsWriterI writerI = new CountsWriter(new FileOutputStream(basename), 0);

        final Random random = new Random();
        for (int i = 0; i < 30; i++) {
            // increased the count to 50,000 to reduce the chance that two successive random values will be equal (which
            // correctly and intentionally will trigger an assertion)
            final int count = random.nextInt(50000);
            final int length = random.nextInt(10) + 1;
            System.out.println("Appending count: " + count + " length: " + length);
            writerI.appendCount(count, length);
        }

        writerI.close();
        final CountsReader reader = new CountsReader(new FileInputStream(basename));
        int position = 0;
        while (reader.hasNextPosition()) {
            final int count = reader.nextCountAtPosition();
            assertEquals(position, reader.getPosition());
            System.out.println(String.format(" position= %d count= %d", position++, count));
        }
    }
}
