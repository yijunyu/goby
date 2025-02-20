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

import edu.cornell.med.icb.identifier.IndexedIdentifier;
import edu.cornell.med.icb.identifier.DoubleIndexedIdentifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.lang.MutableString;

import java.io.Serializable;

/**
 * @author Fabien Campagne
 *         Date: Oct 24, 2010
 *         Time: 11:39:23 AM
 */
public class MethylationData implements Serializable {
    public static final long serialVersionUID = 5664745795898488209L;

    IndexedIdentifier chromosomes;
    ObjectArrayList<MethylationSite> sites;
    private DoubleIndexedIdentifier chromosomeIndexToId;


    public MethylationData() {
        this.chromosomes = new IndexedIdentifier();
        sites = new ObjectArrayList<MethylationSite>(41741304);

    }

    public void append(String chromosomeId, char strand, int position, int methylatedReadCount, int totalCount) {

        int chromosome = chromosomes.registerIdentifier(new MutableString(chromosomeId));
        MethylationSite site = new MethylationSite();
        site.chromosome = chromosome;
        site.strand = strand;
        site.position = position;
        site.methylatedReadCount = methylatedReadCount;
        site.totalCount = totalCount;
        sites.add(site);
    }


    public MethylationSiteIterator iterator(char strandSelected) {
        return new MethylationSiteIterator(this, strandSelected);
    }


    public int getChromosomeIndex(String chromosomeId) {
        int chromosome = chromosomes.registerIdentifier(new MutableString(chromosomeId));

        return chromosome;
    }

    public int getChromosomeIndex(MutableString chromosomeId) {
        int chromosome = chromosomes.registerIdentifier(chromosomeId);

        return chromosome;
    }

    /**
     * Return the set of chromosome identifiers described in this data structure.
     *
     * @return
     */
    public ObjectSet<MutableString> getChromosomes() {

        return chromosomes.keySet();
    }

    public MutableString getChromosomeId(int chromosome) {
        prepareIds();
        return chromosomeIndexToId.getId(chromosome);
    }

    private void prepareIds() {
        if (chromosomeIndexToId == null) {
            chromosomeIndexToId = new DoubleIndexedIdentifier(chromosomes);
        }
    }

    public String[] getChromosomeStrings() {
        prepareIds();
        String[] result = new String[getChromosomes().size()];
        for (int chromosome = 0; chromosome < chromosomeIndexToId.size(); chromosome++) {
            result[chromosome] = chromosomeIndexToId.getId(chromosome).toString();
        }
        
        return result;
    }
}
