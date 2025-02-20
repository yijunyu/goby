/*
 * Copyright (C) 2009-2012 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This file is part of the Goby IO API.
 *
 *     The Goby IO API is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The Goby IO API is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with the Goby IO API.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.cornell.med.icb.goby.reads;

import com.google.protobuf.ByteString;
import edu.cornell.med.icb.goby.compression.MessageChunksWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Properties;

/**
 * Write reads to the compact format.
 *
 * @author Fabien Campagne
 *         Date: Apr 24, 2009
 *         Time: 4:32:35 PM
 */
public class ReadsWriterImpl implements ReadsWriter {
    private final Reads.ReadCollection.Builder collectionBuilder;

    private CharSequence sequence;
    private CharSequence description;
    private CharSequence identifier;
    private byte[] qualityScores;

    private int readIndex;
    private int previousReadLength;
    private long sequenceBasesWritten;
    private final MessageChunksWriter messageChunkWriter;

    private byte[] byteBuffer = new byte[100];
    private int barcodeIndex = -1;
    private CharSequence pairSequence;
    private byte[] qualityScoresPair;
    /**
     * An optional read codec.
     */
    private ReadCodec codec;


    public ReadsWriterImpl(final OutputStream output) {
        collectionBuilder = Reads.ReadCollection.newBuilder();
        messageChunkWriter = new MessageChunksWriter(output);
        messageChunkWriter.setParser(new ReadProtobuffCollectionHandler());
        readIndex = 0;
    }

    @Override
    public synchronized void setQualityScores(final byte[] qualityScores) {
        this.qualityScores = qualityScores;
    }

    @Override
    public synchronized void setDescription(final CharSequence description) {
        this.description = description;
    }

    @Override
    public synchronized void setSequence(final CharSequence sequence) {
        this.sequence = sequence;
    }

    @Override
    public synchronized void setPairSequence(final CharSequence sequence) {
        this.pairSequence = sequence;
    }

    @Override
    public synchronized void appendEntry(final CharSequence description,
                                         final CharSequence sequence,
                                         final byte[] qualityScores) throws IOException {
        this.description = description;
        this.sequence = sequence;
        this.qualityScores = qualityScores;
        appendEntry();
    }

    @Override
    public synchronized void appendEntry(final CharSequence description,
                                         final CharSequence sequence) throws IOException {
        this.description = description;
        this.sequence = sequence;
        appendEntry();
    }

    @Override
    public synchronized void appendEntry(final CharSequence sequence) throws IOException {
        this.sequence = sequence;
        appendEntry();
    }

    @Override
    public synchronized void appendEntry(final Reads.ReadEntry.Builder entryBuilder) throws IOException {
        collectionBuilder.addReads(entryBuilder.build());
        messageChunkWriter.writeAsNeeded(collectionBuilder);
        barcodeIndex = -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        messageChunkWriter.close(collectionBuilder);
    }

    /**
     * Append an entry with the next available readindex.
     *
     * @throws IOException If an error occurs while writing the file.
     */
    @Override
    public synchronized void appendEntry() throws IOException {
        appendEntry(readIndex);
        readIndex++;
    }

    boolean firstRead = true;

    /**
     * Append an entry with a specific read index.
     *
     * @param readIndex Index of the read that will be written
     * @throws IOException If an error occurs while writing the file.
     */
    @Override
    public synchronized void appendEntry(final int readIndex) throws IOException {

        Reads.ReadEntry.Builder entryBuilder = Reads.ReadEntry.newBuilder();

        entryBuilder.setReadIndex(readIndex);

        // set current read index to enable interleaving calls to appendEntry(readIndex)/appendEntry().
        this.readIndex = readIndex;
        if (barcodeIndex != -1) {
            entryBuilder.setBarcodeIndex(barcodeIndex);
        }
        if (description != null) {
            entryBuilder.setDescription(description.toString());
            description = null;
        }
        if (identifier != null) {
            entryBuilder.setReadIdentifier(identifier.toString());
            identifier = null;
        }
        if (sequence != null) {
            entryBuilder.setSequence(encodeSequence(sequence));
            sequence = null;

        }
        entryBuilder.setReadLength(previousReadLength);
        if (pairSequence != null) {
            entryBuilder.setSequencePair(encodeSequence(pairSequence));
            pairSequence = null;
            entryBuilder.setReadLengthPair(previousReadLength);
        }

        if (qualityScores != null) {
            entryBuilder.setQualityScores(ByteString.copyFrom(qualityScores));
            qualityScores = null;
        }

        if (qualityScoresPair != null) {
            entryBuilder.setQualityScoresPair(ByteString.copyFrom(qualityScoresPair));
            qualityScoresPair = null;
        }

        if (firstRead == true && keyValuePairs != null) {
            // Append meta data on the very first read of each file. This is used instead of a separate header file. 
            for (Object keyObject : keyValuePairs.keySet()) {
                String key = keyObject.toString();
                String value = keyValuePairs.get(key).toString();
                entryBuilder.addMetaData(Reads.MetaData.newBuilder().setKey(key).setValue(value));
            }
            firstRead = false;

        }
        if (codec != null) {
            if (collectionBuilder.getReadsCount()==0) {
                codec.newChunk();
            }
            Reads.ReadEntry.Builder result = codec.encode(entryBuilder);
            if (result != null) {
                entryBuilder = result;
            }
        }
        collectionBuilder.addReads(entryBuilder.build());

        messageChunkWriter.writeAsNeeded(collectionBuilder);
        barcodeIndex = -1;
    }

    public static ByteString encodeSequence(final CharSequence sequence, byte[] byteBuffer) {

        final int length = sequence.length();
        if (length > byteBuffer.length) {
            byteBuffer = new byte[length];
        }

        final byte[] bytes = byteBuffer;
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) sequence.charAt(i);

        }

        return ByteString.copyFrom(bytes, 0, length);
    }

    private synchronized ByteString encodeSequence(final CharSequence sequence) {

        final int length = sequence.length();
        if (length > byteBuffer.length) {
            byteBuffer = new byte[length];
        }

        final byte[] bytes = byteBuffer;
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) sequence.charAt(i);
            ++sequenceBasesWritten;
        }
        previousReadLength = length;
        return ByteString.copyFrom(bytes, 0, length);
    }

    @Override
    public void setNumEntriesPerChunk(final int numEntriesPerChunk) {
        messageChunkWriter.setNumEntriesPerChunk(numEntriesPerChunk);
    }

    @Override
    public synchronized void setIdentifier(final CharSequence identifier) {
        this.identifier = identifier;
    }

    @Override
    public synchronized long getSequenceBasesWritten() {
        return sequenceBasesWritten;
    }

    @Override
    public synchronized void printStats(final PrintStream out) {
        messageChunkWriter.printStats(out);
        out.println("Number of bits/base " +
                (messageChunkWriter.getTotalBytesWritten() * 8.0f) / (float) sequenceBasesWritten);
    }

    @Override
    public void setBarcodeIndex(final int barcodeIndex) {
        this.barcodeIndex = barcodeIndex;
    }

    /**
     * Set quality scores for the second sequence in a pair.
     *
     * @param qualityScores quality Scores in Phred scale.
     */
    @Override
    public void setQualityScoresPair(final byte[] qualityScores) {
        this.qualityScoresPair = qualityScores;
    }

    Properties keyValuePairs = new Properties();

    /**
     * Append meta data to this read.
     *
     * @param key
     * @param value
     */
    @Override
    public void appendMetaData(String key, String value) {
        keyValuePairs.put(key, value);
    }

    @Override
    public void setMetaData(Properties keyValuePairs) {
        this.keyValuePairs = keyValuePairs;
    }

    @Override
    public void setCodec(ReadCodec codec) {
        this.codec = codec;
    }
}
