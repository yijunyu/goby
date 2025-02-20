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

package edu.cornell.med.icb.goby.compression;

import com.google.protobuf.Message;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * The original Goby Gzip Chunk codec. Simply GZips the protocol buffer collection.
 *
 * @author Fabien Campagne
 *         Date: 3/3/12
 *         Time: 10:30 AM
 */
public class GZipChunkCodec implements ChunkCodec {

    private ProtobuffCollectionHandler parser;

    @Override
    public void setHandler(final ProtobuffCollectionHandler parser) {
        this.parser = parser;
    }

   private final byte[] bytes=new byte[7];

    @Override
    public boolean validate(byte c, DataInputStream input) {
        try {
            final int length = 4 + 3;    // size 4 bytes + magic number 1F 8B 08
            if (input.read(bytes, 0, length) != length) {
                return false;
            } else {
                return bytes[3] == (byte)0x1F && bytes[4] == (byte)0x8B  && bytes[5] == (byte)0x8;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String name() {
        return "gzip";
    }

    @Override
    public byte registrationCode() {
        return REGISTRATION_CODE;
    }

    public static final byte REGISTRATION_CODE = (byte) 0xFF;

    @Override
    public ByteArrayOutputStream encode(final Message readCollection) throws IOException {
        final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream(10000);

        final OutputStream gzipOutputStream = new GzipOutputStreamWithCustomLevel(Deflater.DEFAULT_COMPRESSION,
                byteBuffer);
        readCollection.writeTo(gzipOutputStream);
        gzipOutputStream.flush();
        gzipOutputStream.close();
        return byteBuffer;

    }

    @Override
    public Message decode(final byte[] bytes) throws IOException {
        final GZIPInputStream uncompressStream = new GZIPInputStream(new FastByteArrayInputStream(bytes));
        try {
            return parser.parse(uncompressStream);
        } finally {
            uncompressStream.close();
        }


    }

    @Override
    public int getSuggestedChunkSize() {
        return 10000;
    }

}
