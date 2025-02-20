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

package edu.cornell.med.icb.goby.algorithmic.algorithm.dmr;

/**
 * @author Fabien Campagne
 *         Date: 2/24/12
 *         Time: 4:37 PM
 */
public class PassThroughStatisticAdaptor extends AbstractMethylationAdapter{


    private static final long serialVersionUID = 8506302569020149425L;
    private int maxValue;
    private double fixedValue;

    public PassThroughStatisticAdaptor(int maxValue, double fixedValue) {
        this.maxValue=maxValue;
        this.fixedValue=fixedValue;
    }

    @Override
    public String statName() {
        return "pass-through";
    }
    public double calculate(Object dataProvider, int sampleIndexA, int sampleIndexB, int... covariate) {
     return fixedValue;
    }
    @Override
    public double calculateNoCovariate(final int... a) {
        return a[0];
    }

    @Override
    public double calculateWithCovariate(int covariate, int... a) {
        return calculateNoCovariate(a);
    }

    @Override
    public double getMaximumStatistic() {
        return maxValue;
    }

    @Override
    public double getRange() {
        return maxValue;
    }

}
