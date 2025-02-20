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

import edu.cornell.med.icb.goby.exception.GobyRuntimeException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Aggregate counts for peaks. Peaks are defined as a contiguous set of bases such that
 * no base has zero count.  This class defines the peaks and aggregate counts for the peak.
 *
 * @author Fabien Campagne
 *         Date: May 27, 2009
 *         Time: 6:41:18 PM
 */
public class PeakAggregator implements Iterator<Peak>, Iterable<Peak> {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(PeakAggregator.class);

    private final CountsReaderI reader;
    private boolean nextLoaded;
    private int peakDetectionThreshold;

    private final Peak currentPeak;

    /**
     * Will detect peaks in the counts information provided by reader.
     * @param reader The counts reader to exttract peaks from
     */
    public PeakAggregator(final CountsReaderI reader) {
        this.reader = reader;
        currentPeak = new Peak();
    }

    /**
     * Returns true if another peak is detected above the detection threshold.
     *
     * @return true if there are more peaks
     */
    public boolean hasNext() {
        if (nextLoaded) {
            return true;
        }
        try {
            currentPeak.start = -1;
            currentPeak.length = 0;
            currentPeak.count = 0;
            // find start of peak:

            while (reader.hasNextTransition()) {
                reader.nextTransition();

                final int baseCount = reader.getCount();
                if (baseCount > peakDetectionThreshold) {
                    nextLoaded = true;
                    //start of a new peak.
                    currentPeak.count += baseCount;
                    currentPeak.length += reader.getLength();
                    break;
                }

            }
            currentPeak.start = reader.getPosition();

            // find end of peak:
            while (reader.hasNextTransition()) {
                reader.nextTransition();

                final int baseCount = reader.getCount();
                if (baseCount <= peakDetectionThreshold) {
                    // past end of the peak.
                    break;
                }
                currentPeak.length += reader.getLength();
                currentPeak.count += baseCount;
            }
        } catch (IOException e) {
            LOG.error("", e);
            throw new GobyRuntimeException(e);
        }

        return nextLoaded;
    }

    /**
     * Return the next detected peak. The same instance of Peak is reused to provide information
     * about the peak. Make a copy if you need to save the peak for some purpose.
     * @return the next detected peak
     */
    public Peak next() {
        if (hasNext()) {
            nextLoaded = false;
            return currentPeak;
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Not supported.
     */
    public void remove() {
        throw new UnsupportedOperationException("This operation is not supported. "
                + "Peaks are read-only.");
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Peak> iterator() {
        return this;
    }

    /**
     * Set the threshold for peak detection. Zero by default.
     * @param peakDetectionThreshold The value of count that triggers the detection of a peak.
     */
    public void setPeakDetectionThreshold(final int peakDetectionThreshold) {
        this.peakDetectionThreshold = peakDetectionThreshold;
    }
}
