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

package edu.cornell.med.icb.goby.util;

import edu.cornell.med.icb.goby.reads.QualityEncoding;
import edu.cornell.med.icb.parsers.FastaParser;
import edu.mssm.crover.cli.CLI;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * Creates fastq files with simulated reads. Simulation can create bisulfite treated reads (or not treated)
 * with mutations on C bases at some rate.
 *
 * @author Fabien Campagne
 *         Date: Jan 20, 2011
 *         Time: 1:22:45 PM
 */
public class SimulateBisulfiteReads {
    private int readLength = 50;
    private Random random;
    private String refChoice;
    private String outputFilename;
    private Int2DoubleMap methylationForward;
    private Int2DoubleMap methylationReverse;
    private String regionTrueRates;
    private boolean bisulfiteTreatment;
    private long seed = 232424434L;
    private int numRepeats;
    private boolean nonDirectionalLibPrep;


    public void setBisulfiteTreatment(boolean bisulfiteTreatment) {
        this.bisulfiteTreatment = bisulfiteTreatment;
    }

    private boolean doForwardStrand = true;
    private boolean doReverseStrand = true;

    public SimulateBisulfiteReads() {
        random = new Random(seed);

    }

    public static void main(String[] args) throws IOException {

        String fastaReference = CLI.getOption(args, "-r", null);
        String outputFilename = CLI.getOption(args, "-o", "out.fq");
        String regionTrueRates = CLI.getOption(args, "-t", "true-methylation.tsv");
        // reference sequence to use
        String refChoice = CLI.getOption(args, "-c", "22");
        int from = CLI.getIntOption(args, "-s", 0);
        int to = CLI.getIntOption(args, "-e", 0);
        if (to < from) {
            System.err.println("argument to -e must be larger than argument to -s");
            System.exit(1);
        }
        int readLength = CLI.getIntOption(args, "-l", 50);
        String methylationRateFilename = CLI.getOption(args, "-m", "methylation-rates.tsv");
        SimulateBisulfiteReads processor = new SimulateBisulfiteReads();
        final boolean bisulfite = CLI.isKeywordGiven(args, "--bisulfite");
        String strandChoice = CLI.getOption(args, "--strand", "both");

        processor.configure(bisulfite, strandChoice);
        processor.bisulfiteTreatment = bisulfite;
        processor.readLength = readLength;
        processor.outputFilename = outputFilename;
        processor.regionTrueRates = regionTrueRates;
        processor.numRepeats = CLI.getIntOption(args, "-n", 10000);

        processor.process(refChoice, fastaReference, from, to, methylationRateFilename);

    }

    public void configure(boolean bisulfite, String strandChoice) {
        if ("both".equals(strandChoice)) {
            doForwardStrand = true;
            doReverseStrand = true;
        } else if ("+".equals(strandChoice) || "forward".equals(strandChoice)) {
            doForwardStrand = true;
            doReverseStrand = false;
        } else if ("-".equals(strandChoice) || "reverse".equals(strandChoice)) {
            doForwardStrand = false;
            doReverseStrand = true;
        }
        if (doForwardStrand) {
            System.err.println("generating reads on forward strand");
        }
        if (doReverseStrand) {
            System.err.println("generating reads on reserve strand");
        }
        if (bisulfite) {
            System.out.println("Bisulfite treatment activated.");
        }
        if (nonDirectionalLibPrep) {
            System.out.println("Non directional library mode activated.");
        }
    }

    public void process(String refChoice, String fastaReference, int from, int to, String methylationRateFilename) throws IOException {
        this.refChoice = refChoice;
        DoubleList methylationRates = load(methylationRateFilename);
        FastaParser parser = new FastaParser(fastaReference.endsWith(".gz") ?

                new InputStreamReader(new GZIPInputStream(new FileInputStream(fastaReference))) :
                new FileReader(fastaReference)
        );

        MutableString bases = new MutableString();
        MutableString description = new MutableString();
        MutableString accession = new MutableString();
        while (parser.hasNext()) {

            parser.next(description, bases);

            FastaParser.guessAccessionCode(description, accession);
            if (accession.equalsIgnoreCase(refChoice)) {
                PrintWriter writer = new PrintWriter(new FileWriter(outputFilename));
                // from and to are zero-based
                System.out.println("matching " + description);
                process(methylationRates, bases.substring(from, to), from, writer);
            }
        }
    }

    protected void process(DoubleList methylationRates, CharSequence segmentBases, int from, Writer writer) throws IOException {
        readLength = Math.min(segmentBases.length(), readLength);
        final String trueRateFilename = FilenameUtils.removeExtension(outputFilename) + "-true-methylation.tsv";
        final PrintWriter trueRateWriter = new PrintWriter(new FileWriter(trueRateFilename));
        if (bisulfiteTreatment) {
            writeTrueRatesBisulfite(methylationRates, segmentBases, from, trueRateWriter);
        } else {
            writeTrueRatesMutations(methylationRates, segmentBases, from, trueRateWriter);
        }
        process(segmentBases, from, writer);
        writer.close();
    }

    protected void writeTrueRatesMutations(DoubleList methylationRates, CharSequence segmentBases,
                                           int from, Writer writer) throws IOException {

        PrintWriter trueRateWriter = new PrintWriter(writer);
        writeTrueRatesBisulfite(methylationRates, segmentBases, from, new StringWriter());
        trueRateWriter.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", "index", "methylationRate", "strand", "chromosome", "position", "fromBase", "toBase", "context");
        DoubleIterator it = methylationRates.iterator();

        for (int i = 0; i < segmentBases.length(); i++) {
            char fromBaseForward = segmentBases.charAt(i);

            if (fromBaseForward == 'C') {
                final int genomicPosition = i + from;
                if (!it.hasNext()) {
                    it = methylationRates.iterator();
                }
                char toBase = '.';
                final double value = it.nextDouble();
                if (value == 1) {
                    toBase = 'C';
                } else if (value == 0) {
                    toBase = 'G';
                }

                methylationForward.put(genomicPosition, value);
                final double getterValue = getMethylationRateAtPosition(false, genomicPosition);
                assert getterValue == value;
                trueRateWriter.printf("%d\t%g\t+1\t%s\t%d\t%c\t%c\t%s%n", i, value, refChoice, genomicPosition + 1,
                        fromBaseForward, toBase, context(i, segmentBases));
            }
        }
        trueRateWriter.close();
    }

    protected void writeTrueRatesBisulfite(DoubleList methylationRates, CharSequence segmentBases, int from,
                                           Writer writer) throws IOException {

        PrintWriter trueRateWriter = new PrintWriter(writer);
        // System.out.printf("segmentBases.length: %d %n", segmentBases.length());
        methylationForward = new Int2DoubleOpenHashMap();
        methylationReverse = new Int2DoubleOpenHashMap();
        trueRateWriter.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", "index", "methylationRate", "strand", "chromosome", "position", "fromBase", "toBase", "context");
        final int segmentLength = segmentBases.length();
        DoubleIterator it = methylationRates.iterator();
        if (doForwardStrand) {
            // prepare methylation rates for each C position of the segment, forward strand:
            for (int i = 0; i < segmentBases.length(); i++) {
                char fromBase = segmentBases.charAt(i);
                if (fromBase == 'C') {
                    char toBase = '.';
                    if (!it.hasNext()) {
                        it = methylationRates.iterator();
                    }
                    final double value = it.nextDouble();
                    if (value == 1) {
                        toBase = 'C';
                    } else if (value == 0) {
                        toBase = 'T';
                    }
                    // zero-based
                    final int genomicPosition = i + from;
                    methylationForward.put(genomicPosition, value);
                    final double getterValue = getMethylationRateAtPosition(false, genomicPosition);
                    assert getterValue == value;
                    // write 1-based position
                    trueRateWriter.printf("%d\t%g\t+1\t%s\t%d\t%c\t%c\t%s%n", i, getterValue, refChoice, genomicPosition + 1,
                            fromBase, toBase, context(i, segmentBases));
                }
            }
        }
        if (doReverseStrand) {
            it = methylationRates.iterator();
            CharSequence reverseStrandSegment = reverseComplement(segmentBases);
            // same for reverse strand:
            for (int i = 0; i < segmentLength; i++) {
                char fromBase = reverseStrandSegment.charAt(i);
                if (fromBase == 'C') {
                    char toBase = '.';
                    if (!it.hasNext()) {
                        it = methylationRates.iterator();
                    }
                    final double value = it.nextDouble();
                    if (value == 1) {
                        toBase = 'C';
                    } else if (value == 0) {
                        toBase = 'T';
                    }

                    // zero-based
                    final int genomicPosition = segmentLength - (i + 1) + from;
                    methylationReverse.put(genomicPosition, value);
                    final double getterValue = getMethylationRateAtPosition(true, genomicPosition);
                    assert getterValue == value : "getter must work for reverse strand";
                    trueRateWriter.printf("%d\t%g\t-1\t%s\t%d\t%c\t%c\t%s%n", i, getterValue,
                            refChoice, genomicPosition + 1, fromBase, toBase,
                            reverseComplement(context(i, reverseStrandSegment))); // write 1-based position
                }
            }
        }


        trueRateWriter.close();
    }


    private MutableString context(int i, CharSequence segmentBases) {
        MutableString context = new MutableString();
        int contextLength = 10;
        int start = Math.max(0, i - contextLength);
        int end = Math.min(segmentBases.length(), i + contextLength);

        final String bases = segmentBases.toString();
        context.append(bases.subSequence(start, i));
        context.append('>');
        context.append(bases.charAt(i));
        context.append('<');
        final int a = i + 1;
        if (a < end) {
            final CharSequence sequence = bases.subSequence(a, end);
            context.append(sequence);
        }
        return context;
    }

    public void setNumRepeats(int numRepeats) {
        this.numRepeats = numRepeats;
    }

    public void setReadLength(int readLength) {
        this.readLength = readLength;
    }

    public void setDoForwardStrand(boolean doForwardStrand) {
        this.doForwardStrand = doForwardStrand;
    }

    public void setDoReverseStrand(boolean doReverseStrand) {
        this.doReverseStrand = doReverseStrand;
    }

    protected void process(CharSequence segmentBases, int from, Writer writer) throws IOException {

        int segmentLength = segmentBases.length();
        for (int repeatCount = 0; repeatCount < numRepeats; repeatCount++) {
            int startReadPosition = choose(0, Math.max(0, segmentBases.length() - 1 - readLength));
            boolean matchedReverseStrand = doReverseStrand && doForwardStrand ?
                    random.nextBoolean() : doReverseStrand;
            if (matchedReverseStrand && !doReverseStrand) continue;
            if (!matchedReverseStrand && !doForwardStrand) continue;

            final CharSequence selectedReadRegion = segmentBases.subSequence(startReadPosition, startReadPosition + readLength);
            CharSequence readBases = matchedReverseStrand ? reverseComplement(selectedReadRegion) : selectedReadRegion;


            MutableString sequenceInitial = new MutableString();
            MutableString sequenceTreated = new MutableString();
            MutableString log = new MutableString();
            IntArrayList mutatedPositions = new IntArrayList();

            for (int i = 0; i < readLength; i++) {

                char base = readBases.charAt(i);
                // genomic position is zero-based
                int genomicPosition = matchedReverseStrand ?
                        readLength - (i + 1) + from + startReadPosition :
                        i + startReadPosition + from;
                sequenceInitial.append(base);

                if (base == 'C') {

                    boolean isBaseMethylated = random.nextDouble() <= getMethylationRateAtPosition(matchedReverseStrand, genomicPosition);

                    if (isBaseMethylated) {
                        // base is methylated, stays a C on forward or reverse strand
                        if (!bisulfiteTreatment) {
                            // mutate base to G
                            // introduce mutation C -> G
                            base = 'G';

                        }
                        // bases that are methylated are protected and stay C on the forward strand. They would also
                        // be seen as G on the opposite strand if the sequencing protocol did not respect strandness
                        log.append(bisulfiteTreatment ? "met: " : "mut: ");
                        log.append(genomicPosition + 1);   // write 1-based position
                        log.append(' ');

                        log.append("read-index: ");
                        log.append(i + 1);
                        log.append(' ');
                        mutatedPositions.add(genomicPosition);

                    } else {
                        // bases that are not methylated are changed to T through the bisulfite and PCR conversion steps
                        if (bisulfiteTreatment) {
                            base = 'T';

                        }

                    }
                }
                sequenceTreated.append(base);
            }
            MutableString coveredPositions = new MutableString();
            MutableString qualityScores = new MutableString();
            for (int i = 0; i < readLength; i++) {
                final char c = QualityEncoding.ILLUMINA.phredQualityScoreToAsciiEncoding((byte) 40);
                qualityScores.append(c);

            }
            // zero-based positions covered by the read:
            IntArrayList readCoveredPositions = new IntArrayList();

            for (int i = startReadPosition + from; i < startReadPosition + from + readLength; i++) {
                // positions are written 1-based
                coveredPositions.append(i + 1);
                coveredPositions.append(" ");
                readCoveredPositions.add(i);
            }

            readCoveredPositions.retainAll(mutatedPositions);
            assert readCoveredPositions.size() == mutatedPositions.size() : "positions mutated or changed must be covered by read.";
            //   System.out.printf("initial: %s%nbis:     %s%n", sequenceInitial, sequenceTreated);
            writer.write(String.format("@%d reference: %s startPosition: %d strand: %s %s %s%n%s%n+%n%s%n",
                    repeatCount,
                    refChoice,
                    startReadPosition, matchedReverseStrand ? "-1" : "+1", log,
                    coveredPositions, complement(sequenceTreated),
                    qualityScores));
        }
        writer.flush();

    }

    private MutableString complement(MutableString sequenceTreated) {
        if (this.nonDirectionalLibPrep && random.nextBoolean()) {
            // return the complement of sequenceTreated:
            for (int i = 0; i < sequenceTreated.length(); i++) {
                char base = sequenceTreated.charAt(i);
                switch (base) {
                    case 'A':
                        base = 'T';
                        break;
                    case 'C':
                        base = 'G';
                        break;
                    case 'G':
                        base = 'C';
                        break;
                    case 'T':
                        base = 'A';
                        break;
                }
                sequenceTreated.charAt(i, base);
            }

        }
        return sequenceTreated;
    }

    private double getMethylationRateAtPosition(boolean matchedReverseStrand, int genomicPosition) {
        if (bisulfiteTreatment) {
            return matchedReverseStrand ?
                    methylationReverse.get(genomicPosition) :
                    methylationForward.get(genomicPosition);
        } else {
            // mutate on both strands
            return Math.max(methylationReverse.get(genomicPosition),
                    methylationForward.get(genomicPosition));
        }
    }

    private CharSequence reverseComplement(CharSequence selectedReadRegion) {
        MutableString s = new MutableString();
        s.setLength(selectedReadRegion.length());
        int j = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char base = selectedReadRegion.charAt(i);
            switch (base) {
                case 'A':
                    base = 'T';
                    break;
                case 'C':
                    base = 'G';
                    break;
                case '>':
                    base = '<';
                    break;
                case '<':
                    base = '>';
                    break;
                case 'T':
                    base = 'A';
                    break;
                case 'G':
                    base = 'C';
                    break;
                default:
                    base = 'N';
                    break;
            }
            s.charAt(j++, base);

        }
        return s;
    }

    private DoubleList load(String methylationRateFilename) throws FileNotFoundException {

        DoubleList result = new DoubleArrayList();
        File toOpen = new File(methylationRateFilename);
        if (!toOpen.exists() || !toOpen.canRead()) {
            System.err.printf("methylation file cannot be read %s", methylationRateFilename);
            System.exit(10);
        }
        LineIterator it = new LineIterator(new FastBufferedReader(new FileReader(methylationRateFilename)));

        while (it.hasNext()) {
            MutableString mutableString = it.next();

            result.add(Double.valueOf(mutableString.toString()));
        }
        return result;
    }

    /**
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a random integer in the range <STRONG>lo</STRONG>,
     *         <STRONG>lo</STRONG>+1, ... ,<STRONG>hi</STRONG>
     */
    public int choose(final int lo, final int hi) {
        final int random = (int) ((long) lo + (long) ((1L + (long) hi - (long) lo) * this.random.nextDouble()));
        assert random <= hi;
        assert random >= lo;


        return random;
    }


    public void setOutputFilename(String outputFilename) {
        this.outputFilename = outputFilename;
    }


    public void setRegionTrueRates(String regionTrueRates) {
        this.regionTrueRates = regionTrueRates;
    }

    public void setNonDirectionalLibPrep(boolean nonDirectionalLibPrep) {
        this.nonDirectionalLibPrep = nonDirectionalLibPrep;
    }
}
