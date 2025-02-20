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

package edu.cornell.med.icb.goby.modes;

import edu.cornell.med.icb.goby.alignments.AlignmentReader;
import edu.cornell.med.icb.goby.alignments.AlignmentReaderImpl;
import edu.cornell.med.icb.goby.alignments.Alignments;
import edu.cornell.med.icb.goby.reads.PicardFastaIndexedSequence;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceInterface;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceTestSupport;
import edu.cornell.med.icb.goby.util.TestFiles;
import it.unimi.dsi.lang.MutableString;
import junit.framework.Assert;
import junitx.framework.FileAssert;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.*;

/**
 * Test creation of CompactAlignment from SAM.
 * This used to be done using SamHelper/SplicedSamHelper so this class was named TestSplicedSamHelper but
 * this is now done with SamRecordParser. Some of the tests in TestSamRecordParser are duplicates of these
 * tests. Only a few (two?) of these tests actually test SplicedSamHelper, the rest use directly use
 * SamToCompact() which no longer uses SplicedSamHelper (the mode that uses that was renamed, pre-elimiation,
 * to SamToCompactSamHelperMode).
 * TODO: Merge this with TestSamRecordParser.
 *
 * @author Fabien Campagne
 */
public class TestSplicedSamHelper {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(TestSplicedSamHelper.class);
    private static final String BASE_TEST_DIR = "test-results/splicedsamhelper";

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
     //   FileUtils.forceDeleteOnExit(new File(BASE_TEST_DIR));
    }


    /*
9068319	0	1	18339	2	28M6371N7M	*	0	0	CCTGCACCTGGCTCCGGCTCTGCTCTACCTGCTGA	aaa^`a``a^^aaaa``aaa_V__`_X]`a`aa_[	MD:Z:35	NH:i:3	NM:i:0	SM:i:2	XQ:i:40	X2:i:40	XS:A:-
11090122	0	1	18345	1	22M6371N13M	*	0	0	CCTGGCTCCGGCTCTGCTCTACCTGCTGAAGATGT	Xa^`U\``]]Y`ZZ\ZYZ\\\Z`ZQ\XJO\VGOQQ	MD:Z:35	NH:i:4	NM:i:0	SM:i:1	XQ:i:40	X2:i:40	XS:A:-
12986491	0	1	18345	2	22M6371N13M	*	0	0	CCTGGCTCCGGCTCTGCTCTACCTGCTGCAGATGT	__a`a_^__^`^`^^^^^^]V[``^\YTFV\XXYS	MD:Z:28A6	NH:i:3	NM:i:1	SM:i:2	XQ:i:40	X2:i:40	XS:A:-

     */

    @Test
    public void testLimits() {
        final SplicedSamHelper samHelper = new SplicedSamHelper();

        SplicedSamHelper.Limits[] limits = samHelper.getLimits(18339, "28M6371N7M", "28A6");
        assertEquals(2, limits.length);
        assertEquals(18339, limits[0].position);
        assertEquals(18339 + 28 + 6371, limits[1].position);

        assertEquals(0, limits[0].cigarStart);
        assertEquals(3, limits[0].cigarEnd);
        assertEquals(8, limits[1].cigarStart);
        assertEquals(10, limits[1].cigarEnd);

        assertEquals(0, limits[0].readStart);
        assertEquals(28, limits[0].readEnd);
        assertEquals(28, limits[1].readStart);
        assertEquals(35, limits[1].readEnd);

        assertEquals("28", limits[0].md);
        assertEquals("A6", limits[1].md);


    }

    @Test
    public void testSpliced() throws IOException {
        final SplicedSamHelper samHelper = new SplicedSamHelper();
        String bases_0_28 = "CCTGCACCTGGCTCCGGCTCTGCTCTAC";
        String quals_0_28 = "aaa^`a``a^^aaaa``aaa_V__`_X]";
        final String bases_28_35 = "CTGCTGA";
        final String sourceRead = bases_0_28 + bases_28_35;
        final String quals_28_35 = "`a`aa_[";
        final String sourceQual = quals_0_28 + quals_28_35;
        samHelper.setSource(0, sourceRead, sourceQual, "28M6371N7M", "35", 18339, false, 35);
        assertEquals(2, samHelper.getNumEntries());
        samHelper.setEntryCursor(0);

        assertEquals(0, samHelper.getNumLeftClipped());
        assertEquals(0, samHelper.getNumRightClipped());
        assertEquals(bases_0_28, samHelper.getQuery().toString());
        assertEquals(bases_0_28, samHelper.getRef().toString());
        assertEquals(quals_0_28, samHelper.getQual().toString());
        assertEquals(18339 - 1, samHelper.getPosition());
        assertEquals(0, samHelper.getQueryPosition());
        assertEquals(28, samHelper.getScore());
        assertEquals(28, samHelper.getAlignedLength());
        assertEquals(28, samHelper.getQueryAlignedLength());
        assertEquals(28, samHelper.getTargetAlignedLength());
        assertEquals(0, samHelper.getNumMisMatches());
        assertEquals(0, samHelper.getNumInsertions());
        assertEquals(0, samHelper.getNumDeletions());
        assertEquals(0, samHelper.getQueryIndex());
        // note that read length is 35, since this is the length of the complete read, not just the spliced piece we are looking at
        assertEquals(35, samHelper.getQueryLength());
        assertFalse(samHelper.isReverseStrand());
        List<SamSequenceVariation> vars = samHelper.getSequenceVariations();
        assertEquals(0, vars.size());

        samHelper.setEntryCursor(1);

        assertEquals(0, samHelper.getNumLeftClipped());
        assertEquals(0, samHelper.getNumRightClipped());
        assertEquals(28, samHelper.getQueryPosition());
        assertEquals(bases_28_35, samHelper.getQuery().toString());
        assertEquals(bases_28_35, samHelper.getRef().toString());
        assertEquals(quals_28_35, samHelper.getQual().toString());

        // full quality scores are returned on the first cursor only
        assertEquals(null, samHelper.getSourceQual());
        assertEquals(null, samHelper.getSourceQualAsBytes());
        assertEquals(18339 - 1 + 6371 + bases_0_28.length(), samHelper.getPosition());
    }

    //@Test  NOTE: THIS DEST CANNOT BE RUN BECAUSE THERE IS NO MD:Z IN THE EXAMPLE!!
    public void testSamToCompactTrickCase1() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-1");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertTrue(first.hasPairAlignmentLink());
        assertTrue(first.hasSplicedForwardAlignmentLink());
        Assert.assertEquals(1, first.getSplicedForwardAlignmentLink().getFragmentIndex());
        Assert.assertEquals(2, first.getPairAlignmentLink().getFragmentIndex());
        assertFalse(first.hasSplicedBackwardAlignmentLink());

        Alignments.AlignmentEntry second = reader.next();
        assertEquals(0, second.getQueryIndex());
        assertEquals(1, second.getFragmentIndex());
        assertTrue(second.hasPairAlignmentLink());

        Assert.assertEquals(2, second.getPairAlignmentLink().getFragmentIndex());
        assertFalse(second.hasSplicedForwardAlignmentLink());
        assertTrue(second.hasSplicedBackwardAlignmentLink());
        Assert.assertEquals(0, second.getSplicedBackwardAlignmentLink().getFragmentIndex());

        Alignments.AlignmentEntry third = reader.next();
        assertEquals(0, third.getQueryIndex());
        assertEquals(2, third.getFragmentIndex());
        assertTrue(third.hasPairAlignmentLink());

        Assert.assertEquals(1, third.getPairAlignmentLink().getFragmentIndex());
        assertFalse(third.hasSplicedBackwardAlignmentLink());
        assertTrue(third.hasSplicedForwardAlignmentLink());
        Assert.assertEquals(3, third.getSplicedForwardAlignmentLink().getFragmentIndex());

        Alignments.AlignmentEntry fourth = reader.next();
        assertEquals(0, fourth.getQueryIndex());
        assertEquals(3, fourth.getFragmentIndex());
        assertTrue(fourth.hasPairAlignmentLink());

        Assert.assertEquals(1, fourth.getPairAlignmentLink().getFragmentIndex());
        assertTrue(fourth.hasSplicedBackwardAlignmentLink());
        Assert.assertEquals(2, fourth.getSplicedBackwardAlignmentLink().getFragmentIndex());

        assertFalse(fourth.hasSplicedForwardAlignmentLink());
    }

    //@Test  NOTE: THIS DEST CANNOT BE RUN BECAUSE THERE IS NO MD:Z IN THE EXAMPLE!!
    public void testSamToCompactTrickCase2() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-2.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-2");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertTrue(first.hasPairAlignmentLink());
        assertFalse(first.hasSplicedForwardAlignmentLink());
        Assert.assertEquals(1, first.getPairAlignmentLink().getFragmentIndex());
        assertFalse(first.hasSplicedBackwardAlignmentLink());

        Alignments.AlignmentEntry second = reader.next();
        assertEquals(0, second.getQueryIndex());
        assertEquals(1, second.getFragmentIndex());
        assertTrue(second.hasPairAlignmentLink());

        Assert.assertEquals(0, second.getPairAlignmentLink().getFragmentIndex());
        assertFalse(second.hasSplicedForwardAlignmentLink());
        assertFalse(second.hasSplicedBackwardAlignmentLink());


    }

    //@Test  NOTE: THIS DEST CANNOT BE RUN BECAUSE THERE IS NO MD:Z IN THE EXAMPLE!!
    public void testSamToCompactTrickCase3() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-3.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-3");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(170769 - 1, first.getPosition());
        assertTrue(first.hasPairAlignmentLink());
        assertFalse(first.hasSplicedForwardAlignmentLink());
        Assert.assertEquals(1, first.getPairAlignmentLink().getFragmentIndex());
        Assert.assertEquals(216048 - 1, first.getPairAlignmentLink().getPosition());
        assertFalse(first.hasSplicedBackwardAlignmentLink());

        Alignments.AlignmentEntry second = reader.next();
        assertEquals(0, second.getQueryIndex());
        assertEquals(1, second.getFragmentIndex());
        assertTrue(second.hasPairAlignmentLink());

        Assert.assertEquals(0, second.getPairAlignmentLink().getFragmentIndex());
        assertTrue("second must have spliced forward link", second.hasSplicedForwardAlignmentLink());
        assertFalse("second must have spliced backward link", second.hasSplicedBackwardAlignmentLink());


    }

    //@Test  NOTE: THIS DEST CANNOT BE RUN BECAUSE THERE IS NO MD:Z IN THE EXAMPLE!!
    // primary is mapped, but mate is unmapped. Primary must be imported.
    public void testSamToCompactTrickCase4() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-4.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-4");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(188966 - 1, first.getPosition());
        assertFalse(first.hasPairAlignmentLink());
        assertFalse(first.hasSplicedForwardAlignmentLink());
        assertFalse(first.hasSplicedBackwardAlignmentLink());


    }

    @Test
    // Test  right soft clip:
    public void testSamToCompactTrickCase5() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-5.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-5");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(71428 - 1, first.getPosition());


    }

    @Test
    // Test deletion in the read:
    public void testSamToCompactTrickCase6() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-6.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-6");
        importer.setOutputFile(outputFilename);
        String[] refs = {"NNNNNNTTAGAAAAACAGAGAGAGAAGGAGAGTAAAGGGAGGAGGCGGAGGAGGAGAAAAGAAGAAAGCAGAGANNNNNN"};

        RandomAccessSequenceTestSupport genomeTestSupport = new RandomAccessSequenceTestSupport(refs);
        importer.setGenome(genomeTestSupport);

        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(7 - 1, first.getPosition());


    }

    @Test
    public void testSamToCompactTrickCase7() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-7.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-7");
        importer.setOutputFile(outputFilename);
        String[] refs = {"NNNNNNTTAGAAAAACAGAGAGAGAAGGAGAGTAAAGGGAGGAGGCGGAGGAGGAGAAAAGAAGAAAGCAGAGANNNNNN"};

        RandomAccessSequenceTestSupport genomeTestSupport = new RandomAccessSequenceTestSupport(refs);
        importer.setGenome(genomeTestSupport);

        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(8 - 1, first.getPosition());


    }

    @Test
    public void testSamToCompactTrickCase8() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-8.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-8");
        importer.setOutputFile(outputFilename);
        MutableString seq = new MutableString();
        for (int i = 0; i < 194407 - 7; i++) {
            seq.append('N');
        }
        seq.append("NNNNNNTTAGAAAAACAGAGAGAGAAGGAGAGTAAAGGGAGGAGGCGGAGGAGGAGAAAAGAAGAAAGCAGAGANNNNNN");
        String[] refs = {seq.toString()};

        RandomAccessSequenceTestSupport genomeTestSupport = new RandomAccessSequenceTestSupport(refs);
        importer.setGenome(genomeTestSupport);

        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(194301 - 1, first.getPosition());


    }

    @Test
    public void testSamToCompactTrickCase9() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-9.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-9");
        importer.setOutputFile(outputFilename);
        MutableString seq = new MutableString();

        seq.append("NNNTTAGAAAAACAGAGAGAGAAGGAGAGTAAAGGGAGGAGGCGGAGGAGGAGAAAAGAAGAAAGCAGAGANNNNNN");
        for (int i = 0; i < 573; i++) {
            seq.insert(25, '-');
        }
        String[] refs = {seq.toString()};

        RandomAccessSequenceTestSupport genomeTestSupport = new RandomAccessSequenceTestSupport(refs);
        importer.setGenome(genomeTestSupport);

        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(3 - 1, first.getPosition());


    }

    @Test
    // like 9 no genome
    public void testSamToCompactTrickCase9NoGenome() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-9.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-9");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(3 - 1, first.getPosition());
    }

    @Test
    // variation after splice
    public void testSamToCompactTrickCase10NoGenome() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-10.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-10");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(3 - 1, first.getPosition());
    }

    @Test
    public void testSamToCompactTrickCase11() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-11.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-11");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        assertEquals(0, first.getQueryIndex());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(26800015 - 1, first.getPosition());
    }

    @Test
    // like 9 no genome
    public void testSamToCompactTrickCase12NoGenome() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-12.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-12");
        importer.setOutputFile(outputFilename);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry second = reader.next();

        assertEquals(15013, first.getPosition());
        assertEquals(3, first.getQueryPosition());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(25, first.getQueryAlignedLength());

        assertEquals(15795, second.getPosition());
        assertEquals(28, second.getQueryPosition());
        assertEquals(1, second.getFragmentIndex());
        assertEquals(7, second.getQueryAlignedLength());
    }

    @Test
    // To test import of soft clips:
    public void testSamToCompactTrickCase13NoGenomeSoftClips() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-13.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-13");
        importer.setOutputFile(outputFilename);
        importer.setPreserveSoftClips(true);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry second = reader.next();

        assertEquals(15013, first.getPosition());
        assertEquals(3, first.getQueryPosition());
        assertEquals("AAT", first.getSoftClippedBasesLeft());
        assertEquals("", first.getSoftClippedBasesRight());
        assertEquals(25, first.getQueryAlignedLength());

        assertEquals(15795, second.getPosition());
        assertEquals(28, second.getQueryPosition());
        assertEquals(1, second.getFragmentIndex());
        assertEquals(5, second.getQueryAlignedLength());
        assertEquals("", second.getSoftClippedBasesLeft());
        assertEquals("TC", second.getSoftClippedBasesRight());
    }

    @Test
    // To test import of soft clips:
    public void testSamToCompactTrickCase13SoftClipsWithGenome() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-14.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-14");

        MutableString seq = new MutableString();

        seq.append("NCAGTGCCCACCTTGGCTCGTGGCTCTCANNNNNNNNNNCTTGCCTNNN");
        String[] refs = {seq.toString()};

        RandomAccessSequenceTestSupport genomeTestSupport = new RandomAccessSequenceTestSupport(refs);
        importer.setGenome(genomeTestSupport);
        importer.setOutputFile(outputFilename);
        importer.setPreserveSoftClips(true);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry second = reader.next();

        assertEquals(4, first.getPosition());
        assertEquals(3, first.getQueryPosition());
        assertEquals("A=T", first.getSoftClippedBasesLeft());
        assertEquals("", first.getSoftClippedBasesRight());
        assertEquals(25, first.getQueryAlignedLength());

        assertEquals(40 - 1, second.getPosition());
        assertEquals(28, second.getQueryPosition());
        assertEquals(1, second.getFragmentIndex());
        assertEquals(5, second.getQueryAlignedLength());
        assertEquals("TC", second.getSoftClippedBasesRight());
        assertEquals("", second.getSoftClippedBasesLeft());

    }
    /*
PATHBIO-SOLEXA2:2:37:931:1658#0	97	chr10	97392943	255	11M10083N29M	=	64636105	0	CTGGATACAATGAGATCTGAAGACGGTTGTACACTTGACC	BBB@BA???BBCBA@>AA=6>A@?B?<<B<>;=@ABA@@<	NM:i:0	XS:A:-	NS:i:0
PATHBIO-SOLEXA2:2:37:931:1658#0	145	chr11	64636105	255	11M447N29M	=	97392943	0	AGGGCCCCTGGGCGCCGCGGCTCTGCTACTGCTGCTGCCC	A?@AB@?@?=A=AAA@<A<BBBBBBBBBA@<@<BBB@BAA	NM:i:0	XS:A:+	NS:i:0

    */

    @Test
    // To test import of three splice with initial 2-clip.
    public void testSamToCompactTrickCase15NoGenomeThreeSplice() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-15.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-15");
        importer.setOutputFile(outputFilename);
        importer.setPreserveSoftClips(true);
        //  importer.setPropagateTargetIds(true);
        importer.execute();

        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry second = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry third = reader.next();

        assertEquals(32485524 - 1, first.getPosition());
        assertEquals(2, first.getQueryPosition());
        assertEquals("CG", first.getSoftClippedBasesLeft());
        assertEquals("", first.getSoftClippedBasesRight());
        assertEquals(6, first.getQueryAlignedLength());

        assertEquals(32485524 + 6 + 301 - 1, second.getPosition());
        assertEquals(8, second.getQueryPosition());
        assertEquals("", second.getSoftClippedBasesLeft());
        assertEquals("", second.getSoftClippedBasesRight());
        assertEquals(24, second.getQueryAlignedLength());

        assertEquals(32485524 + 6 + 301 + 24 + 478 - 1, third.getPosition());
        assertEquals(32, third.getQueryPosition());
        assertEquals("", third.getSoftClippedBasesLeft());
        assertEquals("", third.getSoftClippedBasesRight());
        assertEquals(3, third.getQueryAlignedLength());
    }

    @Test
    // Failed on server when converting back to BAM because seqVar.position would be set to zero:

    public void testSamToCompactTrickCase19() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-19.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-19");
        importer.setOutputFile(outputFilename);
        importer.setPreserveSoftClips(true);
        //  importer.setPropagateTargetIds(true);
        importer.execute();
        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertFalse(reader.hasNext());
        assertEquals("seqVar.position must be zero", 0, first.getSequenceVariations(0).getPosition());
    }

    @Test
    // Failed on server when converting back to BAM because seqVar.position would be set to zero:

    public void testSamToCompactTrickCase20() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/sam/test.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "test-sam-1");
        importer.setOutputFile(outputFilename);

        importer.setPreserveAllTags(true);
        importer.setPreserveReadName(true);
        //  importer.setPropagateTargetIds(true);
        importer.execute();
        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();

        CompactToSAMMode exporter = new CompactToSAMMode();
        final String samOutputFilename = FilenameUtils.concat(BASE_TEST_DIR, "test-sam-out.sam");

        exporter.setInputBasename(outputFilename);
        exporter.setOutput(samOutputFilename);
        exporter.setGenome("test-data/sam/test.fa");
        exporter.execute();
        FileAssert.assertEquals(new File("test-data/sam/test.sam"),
                new File(samOutputFilename));
    }

    @Test
    // Failed on server when converting back to BAM because query_aligned_length and target_aligned_length differ
    // without an apparent indel
    public void testSamToCompactTrickCase16() throws IOException {

        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile("test-data/splicedsamhelper/tricky-spliced-16.sam");
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "spliced-output-alignment-16");
        importer.setOutputFile(outputFilename);
        importer.setPreserveSoftClips(true);
        //  importer.setPropagateTargetIds(true);
        importer.execute();
        // cigar is  13S21M1I29M4S   13 21 -1 29 4
        AlignmentReader reader = new AlignmentReaderImpl(outputFilename);
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry first = reader.next();
        assertTrue(reader.hasNext());
        Alignments.AlignmentEntry second = reader.next();
        assertFalse(reader.hasNext());


        assertEquals(190077 - 1, first.getPosition());
        assertEquals(13, first.getQueryPosition());
        assertEquals("AGTGGCAGCACGA", first.getSoftClippedBasesLeft());
        assertEquals("TGCT", first.getSoftClippedBasesRight());
        assertEquals(51, first.getQueryAlignedLength());
        assertEquals(50, first.getTargetAlignedLength());

        assertEquals(5, first.getSequenceVariationsCount());

        Alignments.SequenceVariation seqvar = first.getSequenceVariations(0);
        assertEquals("T", seqvar.getFrom());
        assertEquals("C", seqvar.getTo());
        assertEquals(22, seqvar.getReadIndex());
        assertEquals(9, seqvar.getPosition());
        assertArrayEquals(byteArray(3), seqvar.getToQuality().toByteArray());

        seqvar = first.getSequenceVariations(1);
        assertEquals("T", seqvar.getFrom());
        assertEquals("G", seqvar.getTo());
        assertEquals(26, seqvar.getReadIndex());
        assertEquals(13, seqvar.getPosition());
        assertArrayEquals(byteArray(34), seqvar.getToQuality().toByteArray());


        seqvar = first.getSequenceVariations(2);
        assertEquals("C", seqvar.getFrom());
        assertEquals("A", seqvar.getTo());
        assertEquals(33, seqvar.getReadIndex());
        assertEquals(20, seqvar.getPosition());
        assertArrayEquals(byteArray(28), seqvar.getToQuality().toByteArray());

        seqvar = first.getSequenceVariations(3);
        assertEquals("-", seqvar.getFrom());
        assertEquals("C", seqvar.getTo());
        assertEquals(35, seqvar.getReadIndex());
        assertEquals(21, seqvar.getPosition());
        assertArrayEquals(byteArray(27), seqvar.getToQuality().toByteArray());

        seqvar = first.getSequenceVariations(4);
        assertEquals("T", seqvar.getFrom());
        assertEquals("A", seqvar.getTo());
        assertEquals(36, seqvar.getReadIndex());
        assertEquals(22, seqvar.getPosition());
        assertArrayEquals(byteArray(35), seqvar.getToQuality().toByteArray());

        //second's CIGAR is 20S48M
        assertEquals(190246 - 1, second.getPosition());
        assertEquals(20, second.getQueryPosition());
        assertEquals("CAGTGTCGTGGCTGCACGCC", second.getSoftClippedBasesLeft());
        assertEquals("", second.getSoftClippedBasesRight());
        assertEquals(48, second.getQueryAlignedLength());
        assertEquals(48, second.getTargetAlignedLength());


    }

    private byte[] byteArray(final int... bytes) {
        final byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) bytes[i];
        }
        return result;
    }

    //@Test
    // We cannot really test without a genome corresponding to each sam file.
    public void testRoundTrips() throws IOException {
        File dir = new File("test-data/splicedsamhelper/");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith("tricky-spliced-") && s.endsWith(".sam")) return true;
                else return false;
            }
        });

        for (File inputFile : files) {
            String filename = inputFile.getAbsolutePath();
            boolean preserveSoftClips = true;
            boolean preserveAllReadQuals = true;
            String importedBasename = importFile(filename, preserveSoftClips, preserveAllReadQuals);
            String exportedSamFilename = exportFile(importedBasename, null);

            TestFiles tester = new TestFiles();
            tester.assertEquals(inputFile, new File(exportedSamFilename));
        }

    }

    @Test
    // We cannot really test without a genome corresponding to each sam file.
    public void testGenomeRoundTrip() throws IOException {
        File dir = new File("test-data/sam/");
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                if (s.startsWith("test") && s.endsWith(".sam")) return true;
                else return false;
            }
        });

        for (File inputFile : files) {
            String filename = inputFile.getAbsolutePath();
            boolean preserveSoftClips = true;
            boolean preserveAllReadQuals = false;
            String importedBasename = importFile(filename, preserveSoftClips, preserveAllReadQuals);
            String exportedSamFilename = exportFile(importedBasename, "test-data/faidx/file3.fasta");

            TestFiles tester = new TestFiles();
            tester.assertEquals(inputFile, new File(exportedSamFilename));
        }

    }

    private String exportFile(String importedBasename, String genomePath) throws IOException {
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "round-trip-output" + counterSam++ + ".sam");

        CompactToSAMMode exporter = new CompactToSAMMode();
        exporter.setInputBasename(importedBasename);
        exporter.setOutput(outputFilename);
        if (genomePath != null) {
            exporter.setGenome(new PicardFastaIndexedSequence(genomePath));

        } else {


            RandomAccessSequenceInterface genomeTestSupport = new RandomAccessSequenceInterface() {

                @Override
                public char get(int referenceIndex, int position) {
                    return 'A';
                }

                @Override
                public int getLength(int targetIndex) {
                    return Integer.MAX_VALUE;
                }

                @Override
                public void getRange(int referenceIndex, int position, int length, MutableString bases) {
                    bases.setLength(0);
                    for (int i = 0; i < length; i++) {
                        bases.append('A');
                    }
                }

                @Override
                public int getReferenceIndex(String referenceId) {
                    return 0;
                }

                @Override
                public String getReferenceName(int index) {
                    return "ref-id";
                }

                @Override
                public int size() {
                    return 5;
                }
            };

            exporter.setGenome(genomeTestSupport);
        }
        exporter.execute();
        return outputFilename;
    }

    private int counterGoby = 1;
    private int counterSam = 1;

    private String importFile(String filename, boolean preserveSoftClips, boolean preserveAllReadQuals) throws IOException {
        SAMToCompactMode importer = new SAMToCompactMode();
        importer.setInputFile(filename);
        final String outputFilename = FilenameUtils.concat(BASE_TEST_DIR, "round-trip-input-alignment-" + counterGoby++);
        importer.setOutputFile(outputFilename);
        importer.setPreserveReadName(true);
        importer.setPreserveSoftClips(preserveSoftClips);
        //  importer.setPropagateTargetIds(true);
        importer.setPreserveReadQualityScores(true);
        importer.setPreserveAllTags(true);

        importer.execute();
        return outputFilename;
    }

}
