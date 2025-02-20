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

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

/**
 * Calculates fold change magnitude from first group to second group (requires exactly two groups).
 * The fold change magnitude is defined as max(foldChange, 1d / foldChange).
 *
 * @author Fabien Campagne
 *         Date: Jan 11, 2010
 *         Time: 7:06:31 PM
 */
public class FoldChangeMagnitudeCalculator extends StatisticCalculator {

    public FoldChangeMagnitudeCalculator(final DifferentialExpressionResults results) {
        this();
        setResults(results);


    }

    public FoldChangeMagnitudeCalculator() {
        super();
    }


    @Override
    public boolean canDo(final String[] group) {
        return group.length == 2;
    }

    @Override
    public DifferentialExpressionInfo evaluate(final DifferentialExpressionCalculator differentialExpressionCalculator,
                                               final NormalizationMethod method, final DifferentialExpressionResults results,
                                               final DifferentialExpressionInfo info,
                                               final String... group) {
        final String groupA = group[0];
        final String groupB = group[1];
        final int statIndex = defineStatisticId(results, "fold-change-magnitude", method);
        final ObjectArraySet<String> samplesA = differentialExpressionCalculator.getSamples(groupA);
        final ObjectArraySet<String> samplesB = differentialExpressionCalculator.getSamples(groupB);
        double averageA = 0;
        double averageB = 0;


        for (final String sample : samplesA) {
            averageA += differentialExpressionCalculator.getNormalizedExpressionValue(sample, method, info.getElementId());
        }
        for (final String sample : samplesB) {
            averageB += differentialExpressionCalculator.getNormalizedExpressionValue(sample, method, info.getElementId());
        }
        averageA /= samplesA.size();
        averageB /= samplesB.size();
        final double shift=0.000001;
        final double foldChange = (averageA + shift ) / (averageB + shift);
        final double foldChangeMagnitudeStatistic = Math.max(foldChange, 1d / foldChange);

        info.statistics.size(results.getNumberOfStatistics());
        info.statistics.set(statIndex, foldChangeMagnitudeStatistic);

        return info;
    }


}
