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

package edu.cornell.med.icb.goby.modes;

import edu.cornell.med.icb.goby.reads.Reads;
import edu.cornell.med.icb.goby.reads.ReadsReader;
import edu.cornell.med.icb.goby.reads.ReadsToTextWriter;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Validates the functionality of {@link edu.cornell.med.icb.goby.modes.ReformatCompactReadsMode}.
 */
public class TestReformatCompactReadsMode {
    /**
     * Validates that the reformat compact reads mode is capable of writing the same contents.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void noChange() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);

        final String outputFilename = "test-results/reformat-test.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));
    }

    /**
     * Validates that the reformat compact reads mode is capable of reformatting when
     * given positions at the extreme minimum and maximum values.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void startAndEndAtExtremes() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setStartPosition(0L);
        reformat.setEndPosition(Long.MAX_VALUE);
        final String outputFilename = "test-results/reformat-test-extremes.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));
    }

    /**
     * Validates that the reformat compact reads mode is capable of reformatting when
     * given positions that exactly match the length of the file.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void startAndEndAtExactLength() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setStartPosition(0L);
        reformat.setEndPosition(new File(inputFilename).length() - 1);
        final String outputFilename = "test-results/reformat-test-start-end.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));
    }

    /**
     * Validates that the reformat compact reads mode is capable of writing the same contents.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void zeroEntries() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setStartPosition(0L);
        reformat.setEndPosition(0L);
        final String outputFilename = "test-results/reformat-test-zero-zero.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertFalse("There should be no reads in this file", reader.hasNext());
    }

    /**
     * Validates that a file can be reformatted to change the chunk size.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void reformatChunkSize() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setSequencePerChunk(1);
        final String outputFilename = "test-results/reformat-test-chunk-size.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader inputReader = new ReadsReader(FileUtils.openInputStream(inputFile));
        assertTrue("There should be reads in this file", inputReader.hasNext());

        final List<Reads.ReadEntry> inputEntries = new ArrayList<Reads.ReadEntry>(73);
        for (final Reads.ReadEntry entry : inputReader) {
            inputEntries.add(entry);
        }

        final ReadsReader outputReader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertTrue("There should be reads in this file", outputReader.hasNext());

        final List<Reads.ReadEntry> outputEntries = new ArrayList<Reads.ReadEntry>(73);
        for (final Reads.ReadEntry entry : outputReader) {
            outputEntries.add(entry);
        }

        assertEquals("The entries of both files should be equal", inputEntries, outputEntries);
    }

    /**
     * Validates that a subset of a compact reads file can be written.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void reformatStartOfCompactFile() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/s_1_sequence_short_1_per_chunk.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setStartPosition(0);
        reformat.setEndPosition(10);
        final String outputFilename = "test-results/reformat-test-start.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertTrue("There should be reads in this file", reader.hasNext());
        final Reads.ReadEntry entry = reader.next();
        assertNotNull("Entry should not be null", entry);
        assertEquals("Reader returned the wrong sequence string",
                "CTCATGTTCATACACCTNTCCCCCATTCTCCTCCT",
                entry.getSequence().toString(Charset.defaultCharset().name()));
        assertFalse("There should be no other reads in this file", reader.hasNext());
    }

    /**
     * Validates that a subset of a compact reads file can be written.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void reformatSubsetOfCompactFile() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/s_1_sequence_short_1_per_chunk.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setStartPosition(11);
        final String outputFilename = "test-results/reformat-test-subset.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertTrue("There should be reads in this file", reader.hasNext());

        int numberOfEntries = 0;
        for (final Reads.ReadEntry entry : reader) {
            assertNotNull("Entry should not be null: " + numberOfEntries, entry);
            numberOfEntries++;
        }

        // we should have skipped the first entry
        assertEquals("There should be 72 entries in the test file", 72, numberOfEntries);
    }

    /**
     * Validates that setting a maximum read length will propertly exclude reads from
     * being written to the output.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void excludeReadLengthsOf23() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/s_1_sequence_short_1_per_chunk.compact-reads";
        reformat.setInputFilenames(inputFilename);

        // there are no reads in the input file longer than 23
        reformat.setMaxReadLength(23);
        final String outputFilename =
                "test-results/reformat-test-exclude-read-lengths.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertFalse("There should be no reads in this file", reader.hasNext());
    }


    /**
     * Validate read trimming
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void trimPairedReadsAt10() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/small-paired.compact-reads";
        reformat.setInputFilenames(inputFilename);

        // there are no reads in the input file longer than 23
        reformat.setTrimReadLength(10);
        final String outputFilename =
                "test-results/reformat-test-trim-paired.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        ReadsReader reader = new ReadsReader(outputFilename);
        for (Reads.ReadEntry it : reader) {
            assertEquals(10, it.getSequence().size());
            assertEquals(10, it.getSequencePair().size());
            assertEquals(10, it.getQualityScores().size());
            assertEquals(10, it.getQualityScoresPair().size());
        }
    }

    /**
     * Validates that setting a maximum read length will not exclude reads that are
     * within the limit from being written to the output.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void excludeReadLengthsAt35() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setMaxReadLength(35);

        final String outputFilename =
                "test-results/reformat-test-exclude-read-lengths-at-extrreme.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));
    }

    /**
     * Validates that setting a read length trim value will not exclude reads that are
     * within the limit from being written to the output.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void trimReadLengthsAt35() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setTrimReadLength(35);

        final String outputFilename =
                "test-results/reformat-test-exclude-read-lengths-at-extrreme.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));
    }

    /**
     * Validates that setting a read length trim value will write the trimmed reads to
     * the output properly.
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    public void trimReadLengthsAt23() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename = "test-data/compact-reads/s_1_sequence_short.compact-reads";
        reformat.setInputFilenames(inputFilename);
        final String outputFilename = "test-results/reformat-test-start.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();
        reformat.setTrimReadLength(23);

        final File inputFile = new File(inputFilename);
        final File outputFile = new File(outputFilename);
        assertFalse("The reformatted file should not be the same as the original",
                FileUtils.contentEquals(inputFile, outputFile));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(outputFile));
        assertTrue("There should be reads in this file", reader.hasNext());

        int readCount = 0;
        for (final Reads.ReadEntry entry : reader) {
            if (readCount == 0) {
                assertEquals("Reader returned the wrong sequence string",
                        "CTCATGTTCATACACCTNTCCCCCATTCTCCTCCT".subSequence(0, 22),
                        entry.getSequence().toString(Charset.defaultCharset().name()));
            }
            readCount++;
            assertEquals("Entry ", readCount + " was not trimmed", entry.getReadLength());
        }
        assertEquals("There should have been 73 entries in the reformatted file", 73, readCount);
    }

    /**
     * Validates that meta data are transfered by reformat.
     * The input file to this test was created with the following command:
     * goby 1g fasta-to-compact  -k key1 -v value1 -k key2 -v value2 test-data/fastx-test-data/test-fastq-1.fq -o test-data/compact-reads/with-meta-data-input.compact-reads
     *
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void reformatTransferMetaData() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/with-meta-data-input.compact-reads";
        reformat.setInputFilenames(inputFilename);

        final String outputFilename = "test-results/with-meta-data-output.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        ReadsReader reader1 = new ReadsReader(inputFilename);
        for (Reads.ReadEntry it : reader1) {
            System.out.println(it.toString());
            System.out.println();
        }

        ReadsReader reader2 = new ReadsReader(outputFilename);
        for (Reads.ReadEntry it : reader2) {
            System.out.println(it.toString());
            System.out.println();
        }

        assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(new File(outputFilename)));
        assertTrue("There should be reads in this file", reader.hasNext());
        final Reads.ReadEntry entry = reader.next();
        assertNotNull("Entry should not be null", entry);
        Properties keyValuePairs = reader.getMetaData();

        assertEquals("key/value pairs must match",
                "value1",
                keyValuePairs.get("key1"));
        assertEquals("key/value pairs must match",
                "value2",
                keyValuePairs.get("key2"));

    }


     /**
     * Validates that reformating does not change quality score length by default.
     * @throws IOException if there is a problem reading or writing to the files
     */
    @Test
    public void reformatQualityScoreLength() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/with-meta-data-input.compact-reads";
        reformat.setInputFilenames(inputFilename);

        final String outputFilename = "test-results/with-meta-data-output-same.compact-reads";
        reformat.setOutputFile(outputFilename);
        reformat.execute();

        ReadsReader reader1 = new ReadsReader(inputFilename);
        for (Reads.ReadEntry it : reader1) {
            System.out.println(it.toString());
            System.out.println();
        }

        ReadsReader reader2 = new ReadsReader(outputFilename);
        for (Reads.ReadEntry it : reader2) {
            System.out.println(it.toString());
            System.out.println();
        }

    //    assertTrue(FileUtils.contentEquals(new File(inputFilename), new File(outputFilename)));

        final ReadsReader reader = new ReadsReader(FileUtils.openInputStream(new File(outputFilename)));
        assertTrue("There should be reads in this file", reader.hasNext());
        final Reads.ReadEntry entry = reader.next();
        assertEquals(12, entry.getQualityScores().size());

    }

    @Test
    public void reformatPairedEndToText() throws IOException {
        final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

        final String inputFilename =
                "test-data/compact-reads/with-meta-data-input.compact-reads";
        reformat.setInputFilenames(inputFilename);
        reformat.setOutputFile("will-not-write-here");
        final ReadsToTextWriter writer = new ReadsToTextWriter();
        reformat.setWriter(writer);
        reformat.execute();
        assertEquals("numEntriesPerChunk: 10000\n" +
                "appendMetaData: key2=value2\n" +
                "appendMetaData: key1=value1\n" +
                "sequence: GATACCACATCA\n" +
                "setQualityScores: [-15, -14, -13, -12, -11, -10, -9, -8, -7, -16, -15, -14]\n" +
                "append: readIndex: 0\n" +
                "\n" +
                "sequence: TAGACCATAGG\n" +
                "setQualityScores: [-15, -14, -13, -12, -11, -10, -9, -8, -7, -16, -14]\n" +
                "append: readIndex: 1\n" +
                "\n" +
                "close()\n" +
                "printStats\n",
                writer.getTextOutput());
    }

    @Test
    public void reformatTrimStartBy2() throws IOException {
            final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

            final String inputFilename =
                    "test-data/compact-reads/with-meta-data-input.compact-reads";
            reformat.setInputFilenames(inputFilename);
            reformat.setOutputFile("will-not-write-here");
            final ReadsToTextWriter writer = new ReadsToTextWriter();
            reformat.setWriter(writer);
        reformat.setTrimStart(2);
            reformat.execute();
            assertEquals("numEntriesPerChunk: 10000\n" +
                    "appendMetaData: key2=value2\n" +
                    "appendMetaData: key1=value1\n" +
                    "sequence: TACCACATCA\n" +
                    "setQualityScores: [-13, -12, -11, -10, -9, -8, -7, -16, -15, -14]\n" +
                    "append: readIndex: 0\n" +
                    "\n" +
                    "sequence: GACCATAGG\n" +
                    "setQualityScores: [-13, -12, -11, -10, -9, -8, -7, -16, -14]\n" +
                    "append: readIndex: 1\n" +
                    "\n" +
                    "close()\n" +
                    "printStats\n",
                    writer.getTextOutput());
        }

    @Test
        public void reformatTrimLengthBy2() throws IOException {
                final ReformatCompactReadsMode reformat = new ReformatCompactReadsMode();

                final String inputFilename =
                        "test-data/compact-reads/with-meta-data-input.compact-reads";
                reformat.setInputFilenames(inputFilename);
                reformat.setOutputFile("will-not-write-here");
                final ReadsToTextWriter writer = new ReadsToTextWriter();
                reformat.setWriter(writer);
            reformat.setTrimReadLength(10);
                reformat.execute();
                assertEquals("numEntriesPerChunk: 10000\n" +
                                "appendMetaData: key2=value2\n" +
                                "appendMetaData: key1=value1\n" +
                                "sequence: GATACCACAT\n" +
                                "setQualityScores: [-15, -14, -13, -12, -11, -10, -9, -8, -7, -16]\n" +
                                "append: readIndex: 0\n" +
                                "\n" +
                                "sequence: TAGACCATAG\n" +
                                "setQualityScores: [-15, -14, -13, -12, -11, -10, -9, -8, -7, -16]\n" +
                                "append: readIndex: 1\n" +
                                "\n" +
                                "close()\n" +
                                "printStats\n",
                        writer.getTextOutput());
            }
}
