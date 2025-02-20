/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
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

package edu.cornell.med.icb.goby.counts;

import java.io.IOException;

/**
 * A facade over a countsReader that returns positions summed with an offset.
 * The position of count transitions returned is the position of the transition
 * returned by the delegate reader, plus the offset.
 *
 * @author Fabien Campagne
 *         Date: Jun 15, 2009
 *         Time: 6:41:52 PM
 */
public class OffsetCountsReader implements CountsReaderI {
    private final CountsReader delegate;
    private int offset;

    /**
     * {@inheritDoc}
     */
    public int getCount() {
        return delegate.getCount();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * {@inheritDoc}
     */
    public void skipTo(final int position) throws IOException {
        delegate.skipTo(position - offset);
    }

    @Override
    public void reposition(final int position) throws IOException {
        delegate.reposition(position-offset);
    }

    public int getLength() {
        return delegate.getLength();
    }

    /**
     * {@inheritDoc}
     */
    public int getPosition() {
        return delegate.getPosition() + offset;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNextTransition() throws IOException {
        return delegate.hasNextTransition();
    }

    /**
     * {@inheritDoc}
     */
    public void nextTransition() throws IOException {
        delegate.nextTransition();
    }

    public OffsetCountsReader(final CountsReader delegate, final int offset) {
        this.delegate = delegate;
        this.offset = offset;
    }

    /**
     * Change the offset.
     *
     * @param offset New value that is being added to count transition positions.
     */
    public void setOffset(final int offset) {
        this.offset = offset;
    }
}
