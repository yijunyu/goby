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

package edu.cornell.med.icb.goby.methylation;

import edu.cornell.med.icb.goby.util.DoInParallel;
import edu.cornell.med.icb.goby.util.HitBoundedPriorityQueue;
import edu.cornell.med.icb.io.TSVReader;
import edu.mssm.crover.cli.CLI;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Fabien Campagne
 *         Date: Oct 21, 2010
 *         Time: 3:54:56 PM
 */
public class MethylSimilarityScan {


    private int maxBestHits;
    private String windowWidths;


    public static void main(String args[]) throws IOException {
        MethylSimilarityScan scanner = new MethylSimilarityScan();
        scanner.process(args);

    }

    private class Query {
        int positionStart;
        int positionEnd;
        int chromosomeIndex;
        char strand;


        public FloatArrayList methylationRates = new FloatArrayList();
        public ObjectArrayList<MethylationSite> sites = new ObjectArrayList<MethylationSite>();
    }

    private void process(String[] args) throws IOException {
        String inputFilename = CLI.getOption(args, "-i", "/data/lister/mc_h1.tsv");
        this.windowWidths = CLI.getOption(args, "-w", "10");
        this.maxBestHits = CLI.getIntOption(args, "-h", 100);
        String outputFilename = CLI.getOption(args, "-o", "out.tsv");
        final MethylationData data = load(inputFilename);
        File outputFile = new File(outputFilename);
        boolean outputFileExists = outputFile.exists();
// append:
        PrintWriter output = new PrintWriter(new FileWriter(outputFilename, true));


        if (!outputFileExists) {
            output.write("windowSize\tlocation\tchromosome\tforward strand start\tforward strand end\treverse strand start\treverse strand end\teffective window size\tstatistic\n");
        }
        for (String windowWidthString : windowWidths.split("[,]")) {
            final int windowWidth = Integer.parseInt(windowWidthString);
            System.out.println("Processing window size=" + windowWidth);
            final HitBoundedPriorityQueue hits = new HitBoundedPriorityQueue(maxBestHits);
            DoInParallel scan = new DoInParallel() {
                @Override
                public void action(DoInParallel forDataAccess, String chromosome, int loopIndex) {
                    compareStrands(hits, data, windowWidth, new MutableString(chromosome));
                }
            };

            try {
                scan.execute(true, data.getChromosomeStrings());
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            printResults(hits, windowWidth, data, output);
        }
        output.close();
    }


    private Query findQuery(MethylationData data, String chromosomeSelected, char strandSelected,
                            int windowWidth, int minSites) {

        MethylationSiteIterator iterator = data.iterator(strandSelected);
        iterator.skipTo(chromosomeSelected);


        Int2IntMap startTally = new Int2IntOpenHashMap();
        Int2IntMap endTally = new Int2IntOpenHashMap();
        int lastStartPosition = 0;
        int lastEndPosition = 0;

        while (iterator.hasNextSite()) {

            MethylationSite site = iterator.nextSite();

            startTally.put(site.position, startTally.get(lastStartPosition) + 1);
            endTally.put(site.position + 1, endTally.get(lastEndPosition) + 1);
            lastStartPosition = site.position;
            lastEndPosition = site.position + 1;
        }

        IntArrayList indices = new IntArrayList();
        indices.addAll(startTally.keySet());
        indices.addAll(endTally.keySet());
        Collections.sort(indices);
        Query query = null;
        for (int index : indices) {

            final int start = index;
            int end = start + windowWidth;
            while (!endTally.containsKey(end)) {
                // reduce window size until we find a position where a site ended.
                end--;
            }

            if (endTally.get(end) - startTally.get(start) >= minSites) {
                System.out.printf("queryStart=%d queryEnd=%d%n", start, end);
                // return the first window of size windowSize with at least minSites in the window
                query = new Query();
                query.positionEnd = end;
                query.positionStart = start;
                query.chromosomeIndex = data.getChromosomeIndex(chromosomeSelected);
                query.strand = strandSelected;
                break;
            }
        }
        if (query != null) {
            iterator = data.iterator(strandSelected);
            iterator.skipTo(chromosomeSelected);
            iterator.skipToPosition(query.positionStart);
            query.methylationRates = new FloatArrayList();
            int lastPosition = query.positionStart;
            while (iterator.hasNextSite()) {

                MethylationSite site = iterator.nextSite();

                query.methylationRates.add(site.getMethylationRate());
                for (int p = lastPosition; p < Math.min(site.position - 1, query.positionEnd); p++) {
                    query.methylationRates.add(Float.NaN);
                }
                lastPosition = site.position;
                if (site.position > query.positionEnd) break;
                query.sites.add(site);
            }
        }
        return query;
    }


    private void printResults(HitBoundedPriorityQueue results,
                              int windowWidth, MethylationData data, PrintWriter output) {
        ObjectArrayList<MethylationSimilarityMatch> sortedHits = new ObjectArrayList();
        sortedHits.size(results.size());

//        int queryLength = query.methylationRates.size();
        int i = sortedHits.size();
        while (!results.isEmpty()) {
            MethylationSimilarityMatch hit = results.dequeue();
            sortedHits.set(--i, hit);
        }
        System.out.println("windowWidth=" + windowWidth);
        for (MethylationSimilarityMatch hit : sortedHits) {
            output.printf("%d\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%f%n",
                    windowWidth,
                    data.getChromosomeId(hit.chromosome) + ":" + hit.startForward + ":" + hit.endForward + ":1",
                    data.getChromosomeId(hit.chromosome),
                    hit.startForward, hit.endForward,
                    hit.startReverse, hit.endReverse,
                    hit.windowLength, hit.score);
        }
        output.flush();


    }

    private HitBoundedPriorityQueue compareStrands(HitBoundedPriorityQueue results, MethylationData data,
                                                   int windowWidth, MutableString chromosome) {

        int chromosomeIndex = data.getChromosomeIndex(chromosome);
        MethylationSiteIterator itForward = data.iterator('+');
        itForward.skipTo(chromosome);

        MethylationSiteIterator itReverse = data.iterator('-');
        itReverse.skipTo(chromosome);

        ProgressLogger pg = new ProgressLogger();
        pg.expectedUpdates = data.sites.size();


        Int2FloatMap startTallyForward = new Int2FloatOpenHashMap();
        Int2FloatMap endTallyForward = new Int2FloatOpenHashMap();
        Int2FloatMap startTallyReverse = new Int2FloatOpenHashMap();
        Int2FloatMap endTallyReverse = new Int2FloatOpenHashMap();

        Int2IntMap forwardStrandSiteCount = new Int2IntOpenHashMap();
        Int2IntMap reverseStrandSiteCount = new Int2IntOpenHashMap();

        int lastStartPosition = 0;
        int lastEndPosition = 0;

        while (itForward.hasNextSite()) {

            MethylationSite site = itForward.nextSite();

            final float lastStartValue = startTallyForward.get(lastStartPosition);

            final float newStartValue = lastStartValue + site.getMethylationRate();

            startTallyForward.put(site.position, newStartValue);
            endTallyForward.put(site.position + 1, endTallyForward.get(lastEndPosition) + site.getMethylationRate());
            forwardStrandSiteCount.put(site.position, forwardStrandSiteCount.get(lastStartPosition) + 1);

            //      if (site.position >= 500 && site.position <= 1500000) {
            //        System.out.printf("site.position %d numberOfSiteUpToPosition %d lastStartValue: %f newStartValue %f delta: %f%n",
            //              site.position, forwardStrandSiteCount.get(site.position),
            //            lastStartValue, newStartValue, site.getMethylationRate());
            // }
            lastStartPosition = site.position;
            lastEndPosition = site.position + 1;
        }

        lastStartPosition = 0;
        lastEndPosition = 0;

        while (itReverse.hasNextSite()) {

            MethylationSite site = itReverse.nextSite();

            startTallyReverse.put(site.position, startTallyReverse.get(lastStartPosition) + site.getMethylationRate());
            endTallyReverse.put(site.position + 1, endTallyReverse.get(lastEndPosition) + site.getMethylationRate());
            reverseStrandSiteCount.put(site.position, reverseStrandSiteCount.get(lastStartPosition) + 1);
            lastStartPosition = site.position;
            lastEndPosition = site.position + 1;
        }

        IntSet indices = new IntOpenHashSet();
        indices.addAll(startTallyForward.keySet());
        indices.addAll(startTallyReverse.keySet());
        IntList uniqueIndices = new IntArrayList();
        uniqueIndices.addAll(indices);

        Collections.sort(uniqueIndices);


        pg.expectedUpdates = uniqueIndices.size();
        pg.start("comparing strands on chromosome " + chromosome);
        int skipToIndex = 0;
        for (int index : uniqueIndices) {
            if (index < skipToIndex) continue;

            int startForward = index;
            int startReverse = index;
            boolean forwardOutOfWindow = false;
            boolean reverseOutOfWindow = false;
            while (!startTallyForward.containsKey(startForward)) {
                // reduce window size until we find a position where a site ended.
                startForward++;
                if (startForward > index + windowWidth) {
                    forwardOutOfWindow = true;
                    break;
                }
            }
            while (!startTallyReverse.containsKey(startReverse)) {
                // reduce window size until we find a position where a site ended.
                startReverse++;
                if (startReverse > index + windowWidth) {
                    reverseOutOfWindow = true;
                    break;
                }
            }
            int endForward = startForward + windowWidth * 2 + 1;
            while (!endTallyForward.containsKey(endForward)) {
                // reduce window size until we find a position where a site ended.
                endForward--;
                if (endForward == startForward + windowWidth) {
                    forwardOutOfWindow = true;
                    break;
                }
            }
            int endReverse = startReverse + windowWidth * 2 + 1;
            while (!endTallyReverse.containsKey(endReverse)) {
                // reduce window size until we find a position where a site ended.
                endReverse--;
                if (endReverse == startReverse + windowWidth) {
                    reverseOutOfWindow = true;
                    break;
                }
            }
            if (endForward > startForward && endReverse > startReverse) {
                final int forwardSiteCountOverWindow = forwardStrandSiteCount.get(startForward) - forwardStrandSiteCount.get(endForward);
                final int reverseSiteCountOverWindow = reverseStrandSiteCount.get(startReverse) - reverseStrandSiteCount.get(endReverse);
                final float denominator = Math.max(forwardSiteCountOverWindow, reverseSiteCountOverWindow);
                final float sumForwardStrand = forwardOutOfWindow ? 0 : startTallyForward.get(startForward) - (endTallyForward.get(endForward));
                final float sumReverseStrand = reverseOutOfWindow ? 0 : startTallyReverse.get(startReverse) - (endTallyReverse.get(endReverse));
                final float score = Math.abs(sumForwardStrand - sumReverseStrand) / denominator;
                if (denominator != 0) {
                    if (score > 3) {
                        System.out.printf("index %d forward: %d-%d reverse: %d-%d %f%n ", index,
                                startForward, endForward, startReverse, endReverse, score);
                    }
                    boolean wasEnqueued = results.enqueue(chromosomeIndex, startForward, score, startForward, endForward, startReverse, endReverse,
                            Math.min(endForward - startForward, endReverse - startReverse), sumForwardStrand, sumReverseStrand);
                    if (wasEnqueued) {
                        skipToIndex = index + windowWidth;
                    }
                }
            }

            pg.lightUpdate();
        }

        pg.stop("done");
        return results;

    }


    private MethylationData load(String inputFilename) throws IOException {
        System.out.println("Loading..");
        final String cacheFilename = FilenameUtils.removeExtension(inputFilename) + ".cache";
        File cacheFile = new File(cacheFilename);
        if (cacheFile.canRead()) {
            try {
                System.out.println("Trying to load cache " + cacheFilename);
                System.out.flush();
                return (MethylationData) BinIO.loadObject(cacheFilename);
            } catch (ClassNotFoundException e) {
                System.err.println("Cannot load cache. Loading text file instead.");
                // continue loading as usual.
            }
        }
        MethylationData data = new MethylationData();
        TSVReader reader = new TSVReader(new FileReader(inputFilename), '\t');
        reader.setCommentPrefix("chromosome");
        int count = 0;

        while (reader.hasNext()) {
            if (reader.isCommentLine()) {
                reader.skip();
            } else {
                reader.next();
                String chr = reader.getString();
                int position = reader.getInt();
                char strand = reader.getString().charAt(0);
                // ignore type of site.
                reader.getString();
                int methylatedReadCount = reader.getInt();
                int totalReadCount = reader.getInt();

                // add element at index 'position'
                data.append(chr, strand, position, methylatedReadCount, totalReadCount);
                count++;
                if (count % 100000 == 1) {
                    System.out.print(".");
                }
            }
        }
        System.out.println("done");

        System.out.println("Sorting..");
        // sort the sites by position:
        Collections.sort(data.sites, new Comparator<MethylationSite>() {
            public int compare(MethylationSite site1, MethylationSite site2) {
                if (site1.chromosome != site2.chromosome) return site1.chromosome - site2.chromosome;
                else return site1.position - site2.position;
            }
        });
        System.out.println("Saving cache..");
        System.out.flush();

        BinIO.storeObject(data, cacheFilename);
        return data;

    }


}



