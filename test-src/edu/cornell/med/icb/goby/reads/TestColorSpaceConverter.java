/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                         Weill Medical College of Cornell University
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

package edu.cornell.med.icb.goby.reads;

import it.unimi.dsi.lang.MutableString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * Validate the functionality of the {@link edu.cornell.med.icb.goby.reads.ColorSpaceConverter}.
 * @author Fabien Campagne
 *         Date: May 18, 2009
 *         Time: 5:53:21 PM
 */
public class TestColorSpaceConverter {
    /**
     * Test that our color space converter implementation behaves the same way as
     * corona-lite convert_color.pl.
     */
    @Test
    public void testConvert() {
        final MutableString destination = new MutableString();

        ColorSpaceConverter.convert(null, destination);
        assertNotNull(destination);
        assertEquals(0, destination.length());

        ColorSpaceConverter.convert("", destination);
        assertNotNull(destination);
        assertEquals(0, destination.length());

        ColorSpaceConverter.convert("ACTG", destination);
        assertEquals("A121", destination.toString());

        ColorSpaceConverter.convert("AAAAAAAAAA", destination);
        assertEquals("A000000000", destination.toString());


        ColorSpaceConverter.convert("AAA.AAAAAA", destination);
        assertEquals("A007700000", destination.toString());

        ColorSpaceConverter.convert("AAA..AAAAAA", destination);
        assertEquals("A0077700000", destination.toString());

        ColorSpaceConverter.convert("ACTGAACTGGGCTACATGT", destination);
        assertEquals("A121201210032311311", destination.toString());

        ColorSpaceConverter.convert("GCGCAGAGGGGATCGGCCTGCCGCTTGCCAGCCGCCCCAAGGCTGACAGAGAGGCCCTCG" +
                "GGAGTCCGCATCTCAGCCCCCAGGAAGAGGAAGACGATCGCCCACTCTTCCAGCCCTTGC" +
                "TTGGTCACAGGTTACACAGATGCCAAGAGAACCCGGGTGGCCAGCAGCAGCCAACGCTCC" +
                "CGTGGCTCCAAGGTCGGCAGACAGCCAGGGAAGACGCACAACAGGTCAGGGATGGCATGC" +
                "AAGACCACCGCCACCACCAGCTCTAAGCGAATCGTCCGTCGTGCATCCTTACCGAGTTTG" +
                "AGTTTGAAGAAACCCATTATCCTCCGAAGCTCTGGGTGCCAAGTCCCCACCGTCCTCCGC" +
                "CGAGGCTATCTCCAACTGTTCACCGAAGAGTGTCTCAAGTTCTGCGCCTCCAAGCAGGAG" +
                "GCCGAGGAGAAGGCGCTGAACGAGGAGAAGGTGGCCTACGACTGCAGCCCCAACAAGAAC" +
                "AGGTACCTGAACGTGGTCCTGAACACCCTCAAGAGACTGAAGGGCCTGACCCCCAGCTCC" +
                "ATGCCCGGCCTCAGCAGGGCCGCCCTGTACAGCCACCTCCAGGAGTTCCTGCTCACCCAG" +
                "GACCAGCTCAAGGAGAACGGCTACCCCTTCCCGCACCCCGAGCGGCCCGGAGGCGCCGTC" +
                "CTCTTCACTGGCCAGGGGAAGGGGCCCGGCGACTCCTCCTGCAGGGTCTGCTGCCGTTGT" +
                "GGCACCGAGTACCTGGTATCCTCCTCGGGCCGCTGTGTACGCGACCAGTTGTGTTATTAT" +
                "CACTGGGGGCGGGTCCGCTCGAGCCAGGTGGCTGGAGGCCGGGTTAGCCAGTACACCTGC" +
                "TGTGCAGCTGCTCCTGGCTCTGTGGGCTGCCAGGTGGCAAAGCAGCACGTGCGGGACGGC" +
                "CGCAAGGAGAGCCTCGATGGCTTCGTGGAGACCTTCAAGAAAGAGTTGTCCAGAGACGCT" +
                "TATCCAGGAATCTACGCCTTGGACTGTGAGATGTGCTACACCACGCATGGCCTAGAGCTG" +
                "ACCCGCGTCACCGTGGTGGACGCCGACATGCGAGTGGTGTACGACACCTTCGTCAAGCCC" +
                "GACAACGAGATCGTGGACTACAACACCAGGTTTTCCGGAGTCACCGAGGCCGACGTCGCC" +
                "AAGACGAGCATCACGTTGCCCCAAGTCCAAGCCATCCTGCTGAGCTTTTTCAGCGCCCAA" +
                "ACCATCCTCATCGGGCACAGCCTGGAGAGCGACCTGCTGGCCCTGAAGCTCATCCACAGC" +
                "ACCGTGGTGGACACGGCCGTGCTCTTCCCGCACTACCTGGGTTTCCCCTACAAGCGCTCC" +
                "CTCAGGAATCTCGCGGCTGACTACCTGGCACAGATCATCCAGGACAGCCAGGACGGCCAC" +
                "AACTCCAGCGAGGACGCAAGCGCCTGCCTGCAGCTGGTGATGTGGAAGGTCCGACAGCGC" +
                "GCCCAGATCCAGCCA", destination);
        assertEquals("G3331222000232303021303320130123033000102032121122222030022300221203313" +
                "2221230000120202220202213232330011222020123002013201012111201031111223" +
                "1301022220100300110301231231230101332200311032201020123031221123012002" +
                "0221331110112012120023103131310221011033011011012322230233203231203123" +
                "1131320203103221001221001202200100130332022032023222100113010212000110" +
                "3120220330322032332220101211021103202221112221021022133302201023120220" +
                "3032202220203332120132202220201103023132121312300010110220112013102120" +
                "1311012021201110022102222121202003021210000123220131300303022123120030" +
                "3300211311230110220120221020213221100120210123221020222013032310002020" +
                "0331100032233030030220333031202220211210301200020200030030332122022021" +
                "3120012213213031011103110322131021013320220223003033211113133321012101" +
                "1110330332112100003300120332232230120110321022030300103230121311102132" +
                "1113123213220210322211100321301201103100231231131133002130303310202222" +
                "3022323103202311022210202102200222101120122221332033201202032231330201" +
                "0212111222311132311101133131030232223212100333121103110110213303211313" +
                "3221101113132111020231210230032110132223231102123110111012010002030221" +
                "2110322030321312330102213223132113101300010212010230132021321223200002" +
                "1233300100101320221323003111230210222233210213210300212023221320111231" +
                "1031101102111303031132220200331123102100100200023110233322002212020322" +
                "2333032121231021031112232132012021123012021303011101220123322021331023" +
                "330213021312321011231110202012032112333330012232012301", destination.toString());
    }

    /**
     * Validates that the color space converter implementation behaves the same way as
     * corona-lite convert_color.pl.
     */
    @Test
    public void testNConvert() {
        final MutableString destination = new MutableString();
        ColorSpaceConverter.convert("ACTGANCNTNGNNGNANCNTNNNNN", destination, true);
        assertEquals("A1212NNNNNNNNNNNNNNNNNNNN", destination.toString());
    }


}
