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

package edu.cornell.med.icb.goby.stats;

import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Abstract class for all implementations of FDR correction methods.
 *
 * @author Fabien Campagne
 *         Date: Jan 12, 2010
 *         Time: 6:59:01 PM
 */
public abstract class FDRAdjustment {
    private static final Log LOG = LogFactory.getLog(FDRAdjustment.class);
    protected int ignoredElementsAboveThreshold;

    /**
     * Set the number of elements that were not stored in list, because their P-value was already above threshold.
     *
     * @param ignoredElementsAboveThreshold the number of elements not in list.
     */
    public void setNumberAboveThreshold(final int ignoredElementsAboveThreshold) {
        this.ignoredElementsAboveThreshold = ignoredElementsAboveThreshold;
    }

    public DifferentialExpressionResults adjust(final DifferentialExpressionResults list, final NormalizationMethod method, final String... statisticIds) {
        for (final String statisticId : statisticIds) {
            LOG.info("Trying to perform FDR adjustment for statistic " + statisticId);
            boolean adjusted = false;
            final IntList statisticIndexes = list.statisticsIndexesFor(statisticId, method);
            for (final int statisticIndex : statisticIndexes) {
                final String currentStatisticId = list.getStatisticIdForIndex(statisticIndex).toString();
                adjust(list, currentStatisticId);
                LOG.info("... statistic " + currentStatisticId + " was found, FDR adjustment executed.");
                adjusted = true;
            }
            if (!adjusted) {
                LOG.warn("... statistic for " + statisticId + " was not found, it will be ignored by FDR adjustment.");
            }
        }
        return list;
    }

    public double getListSize(final DifferentialExpressionResults list) {
        int listSize = 0;
        // exclude NaN p-values from the number of comparisons:
        for (final DifferentialExpressionInfo info : list) {
            if (info.informative()) {
                listSize++;
            }
        }
        return listSize;
    }

    public abstract DifferentialExpressionResults adjust(DifferentialExpressionResults list, String statisticId);
}
