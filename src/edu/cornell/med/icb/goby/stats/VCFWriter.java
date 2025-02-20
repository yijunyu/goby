/*
 * Copyright (C) 2009-2011 Institute for Computational Biomedicine,
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

import edu.cornell.med.icb.goby.modes.GobyDriver;
import edu.cornell.med.icb.goby.readers.vcf.*;
import edu.cornell.med.icb.util.VersionUtils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import net.sf.samtools.util.BlockCompressedOutputStream;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;

/**
 * Helper class to write VCF statistic result files.
 *
 * @author Fabien Campagne
 *         Date: Sep 23, 2010
 *         Time: 1:26:56 PM
 */
public class VCFWriter {
    PrintWriter outWriter;
    private Int2ObjectMap<ColumnType> indexTypes;
    private boolean VCFmode;
    private Int2ObjectMap<String> indexDescriptions;
    private Int2IntMap indexNumValues;
    private Int2ObjectMap<String> indexShortIds;

    public int chromosomeColumnIndex;
    public int positionColumnIndex;
    private CharSequence chrom;
    private int position = -1;
    private String id;
    private CharSequence[] formatFieldIds;
    private MutableString ref;
    private MutableString alt;
    private CharSequence qual = ".";
    private CharSequence filter;

    private boolean[] formatFieldActive;
    private CharSequence[][] formatValues;
    private CharSequence[] infoIds;
    ObjectArrayList<String> refAlleles;
    private ObjectArrayList<String> altAlleles;
    private char genotypeDelimiterCharacter;
    /**
     * flag is true if info field at index is of type Flag.
     */
    private boolean[] infoFlag;
    protected Object2IntMap<String> formatTypeToFormatFieldIndex = new Object2IntArrayMap<String>();
    private int numFormatFields;
    private static final String[] NO_GROUPS = new String[0];

    /**
     * When this flag is true, writeHeader writes associations between columns and groups.
     *
     * @param writeFieldGroupAssociations True or False.
     */
    public void setWriteFieldGroupAssociations(final boolean writeFieldGroupAssociations) {
        this.writeFieldGroupAssociations = writeFieldGroupAssociations;
    }

    private boolean writeFieldGroupAssociations;


    protected CharSequence getChromosome() {
        return this.chrom;
    }

    public VCFWriter(Writer writer) {
        this(new PrintWriter(writer));
    }

    public VCFWriter(final BlockCompressedOutputStream stream) {
        this(new PrintWriter(new OutputStreamWriter(stream)));
    }

    /**
     * Indicate whether the genotypes should be recorded as phased (true) or unphased (false).
     * Default value at construction of the writer is unphased.
     *
     * @param state True when the genotypes are phased.
     */
    public void setGenotypesPhased(boolean state) {
        genotypeDelimiterCharacter = state ? '|' : '/';
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPosition(int position) {

        this.position = position;
    }

    public void setChromosome(CharSequence chromosome) {
        this.chrom = chromosome;
    }

    public void setReferenceAllele(String allele) {
        refAlleles.clear();
        refAlleles.add(allele);
    }

    public void clearAlternateAlleles() {
        altAlleles.clear();
    }

    public void addAlternateAllele(String allele) {

        if (!altAlleles.contains(allele)) {
            altAlleles.add(allele);
        }
    }

    /**
     * Determine if an allele is included in a set of reference alleles. An allele is included if any of the alleles
     * of refAlleles start with the string allele, possibly followed by bases.
     *
     * @param allele     the allele that may be in refAlleles
     * @param refAlleles a list of reference alleles to consider
     * @return True when allele is included in refAlleles
     */
    private boolean includedIn(final String allele, final ObjectArrayList<String> refAlleles) {
        for (final String testAllele : refAlleles) {
            if (testAllele.startsWith(allele)) {
                return true;
            }
        }
        return false;
    }

    private Columns columns = new Columns();
    private ObjectArrayList<ColumnInfo> columnList = new ObjectArrayList<ColumnInfo>();

    /**
     * Contruct a VCFWriter.
     *
     * @param outWriter Where the output will be written.
     */
    public VCFWriter(PrintWriter outWriter) {
        this.outWriter = outWriter;
        this.sampleIds = new String[0];
        columns.addAll(Arrays.asList(VCFParser.fixedColumn()));
        for (ColumnInfo c : columns) {
            for (ColumnField field : c.fields) {
                if (field.id.equals("VALUE")) {
                    c.fields.remove(field);
                }
            }
        }
        columns.add(new ColumnInfo("FORMAT"));
        refAlleles = new ObjectArrayList<String>();
        altAlleles = new ObjectArrayList<String>();
        ref = new MutableString();
        alt = new MutableString();

        setGenotypesPhased(false);
    }

    /**
     * Write the VCF header.
     */
    public void writeHeader() {

        outWriter.printf("##fileformat=VCFv4.1%n" +
                "##Goby=%s%n", VersionUtils.getImplementationVersion(GobyDriver.class));
        if (writeFieldGroupAssociations) {
            outWriter.printf("##FieldGroupAssociations=%s%n", buildGroupDeclaration());
        }
        columnList.addAll(columns);
        Collections.sort(columnList, VCFParser.COLUMN_SORT);


        int index = 0;

        MutableString tsvHeaderLine = new MutableString();

        for (ColumnInfo c : columnList) {
            if (!c.useFormat) {
                for (ColumnField field : c.fields) {
                    if ("VALUE".equals(field.id)) continue;
                    outWriter.printf("##%s=<ID=%s,Number=%d,Type=%s,Description=\"%s\">%n",
                            c.getColumnName(),
                            field.id,
                            field.numberOfValues,
                            field.type,
                            field.description);
                }
                tsvHeaderLine.append(c.getColumnName());
                tsvHeaderLine.append("\t");
            }

            ++index;
        }


        for (String sampleId : sampleIds) {

            tsvHeaderLine.append(sampleId);
            tsvHeaderLine.append("\t");
            ++index;

        }
        // trim last \t
        tsvHeaderLine.setLength(tsvHeaderLine.length() - 1);
        tsvHeaderLine.append("\n");
        outWriter.print("#");
        outWriter.print(tsvHeaderLine);
        outWriter.flush();

        infoValues = new CharSequence[columns.find("INFO").fields.size()];
        final int numFormatTypes = columns.find("FORMAT").fields.size();
        formatFieldIds = new CharSequence[numFormatTypes];
        formatFieldActive = new boolean[numFormatTypes];
        index = 0;
        for (ColumnField formatField : columns.find("FORMAT").fields) {
            formatFieldIds[index] = formatField.id;
            formatTypeToFormatFieldIndex.put(formatField.id, index);
            index += 1;
            numFormatFields++;
        }
        formatValues = new CharSequence[formatFieldActive.length][sampleIds.length];
        ref.setLength(0);
        alt.setLength(0);
        filter = ".";
        id = ".";
        chrom = "";
        qual = ".";
        final ColumnInfo info = columns.find("INFO");

        // constructs an array of infoIds to keep the INFO field keys (use to write key= in front of each field)
        infoIds = new CharSequence[info.fields.size()];
        infoFlag = new boolean[info.fields.size()];
        ObjectArrayList<ColumnField> fieldList = new ObjectArrayList<ColumnField>();
        fieldList.addAll(info.fields);
        Collections.sort(fieldList, VCFParser.FIELD_SORT);

        for (int infoFieldIndex = 0; infoFieldIndex < infoIds.length; infoFieldIndex++) {
            final ColumnField infoField = fieldList.get(infoFieldIndex);
            int globalFieldIndex = infoField.globalFieldIndex;
            final ColumnField columnField = info.fields.find(globalFieldIndex);

            infoIds[infoFieldIndex] =
                    columnField.id;
            infoFlag[infoFieldIndex] = infoField.type == ColumnType.Flag;
        }
    }

    /**
     * Build a text encoding of field group associations, in the format globalFieldIndex=group,...
     * This format supports associating multiple groups to one field.
     *
     * @return text encoding of field group associations.
     */
    private String buildGroupDeclaration() {
        MutableString sb = new MutableString();
        for (ColumnInfo column : columns) {

            for (String columnGroup : column.getGroups()) {
                sb.append(column.getColumnName());
                sb.append('=');
                sb.append(columnGroup);
                sb.append(',');
            }
            for (ColumnField field : column.fields) {
                if (field.getGroups() != null) {
                    for (String group : field.getGroups()) {
                        sb.append(column.getColumnName());
                        sb.append('/');
                        sb.append(field.id);
                        sb.append('=');
                        sb.append(group);
                        sb.append(',');
                    }
                }
            }
        }
        ColumnInfo formatColumn = columns.find("FORMAT");
        for (String sample : sampleIds) {
            for (String group : formatColumn.getGroups()) {
                sb.append("FORMAT");
                sb.append('/');
                sb.append(sample);
                sb.append('=');
                sb.append(group);
                sb.append(',');
            }

        }
        return sb.toString();
    }

    /**
     * Append a record. Call the various setter before writing a record.
     */

    public void writeRecord() {
        outWriter.append(chrom);
        outWriter.append('\t');
        outWriter.append(position == -1 ? "" : Integer.toString(position));
        outWriter.append('\t');
        outWriter.append(id);
        outWriter.append('\t');
        outWriter.append(constructAlleleString(refAlleles));
        outWriter.append('\t');
        outWriter.append(constructAlleleString(altAlleles));
        outWriter.append('\t');
        outWriter.append(qual);
        outWriter.append('\t');
        outWriter.append(filter);
        outWriter.append('\t');
        int max;
        int index = 0;
        max = infoValues.length;

        MutableString infoString = new MutableString();
        for (CharSequence infoValue : infoValues) {
            assert infoValue != null : "value cannot be null for INFO field " + infoIds[index];
            if (infoValue.length() != 0) {
                if (infoFlag[index]) {
                    infoString.append(infoIds[index]);
                } else {


                    infoString.append(infoIds[index]);
                    infoString.append('=');
                    infoString.append(infoValue);
                }
                infoString.append(';');

            }

            index += 1;

        }
        if (infoString.length() > 0) outWriter.append(infoString.subSequence(0, infoString.length() - 1));
        outWriter.append('\t');

        max = formatFieldIds.length;
        index = 0;
        MutableString formatString = new MutableString();
        for (int formatIndex = 0; formatIndex < numFormatFields; formatIndex++) {
            if (formatFieldActive[formatIndex]) {
                formatString.append(formatFieldIds[formatIndex]);
                formatString.append(':');
            }
        }
        if (formatString.length() > 0) outWriter.append(formatString.subSequence(0, formatString.length() - 1));

        outWriter.append('\t');
        int sampleIndex = 0;

        max = sampleIds.length;

        for (CharSequence value : sampleIds) {

            MutableString sampleString = new MutableString();
            for (int formatIndex = 0; formatIndex < numFormatFields; formatIndex++) {
                //  for (CharSequence formatType : formatFieldIds) {
                if (formatFieldActive[formatIndex]) {
                    final CharSequence v = formatValues[formatIndex][sampleIndex];
                    if (v != null) {
                        sampleString.append(v);
                    }
                    sampleString.append(':');
                }
            }

            sampleIndex++;
            if (sampleString.length() > 0) outWriter.append(sampleString.subSequence(0, sampleString.length() - 1));
            if (sampleIndex != max) outWriter.append('\t');
        }
        outWriter.println();
        clear();
    }

    /**
     * Clear the record without writing to the output and prepare for the next record.
     */
    public void clear() {
        Arrays.fill(formatFieldActive, false);
        for (int i = 0; i < formatFieldActive.length; i++) Arrays.fill(formatValues[i], "");
        Arrays.fill(infoValues, "");

        filter = ".";
        id = ".";
        chrom = "";
        altAlleles.clear();
        refAlleles.clear();
        position = -1;
        qual = ".";
    }

    MutableString buffer = new MutableString();

    private MutableString constructAlleleString(ObjectArrayList<String> refAlleles) {
        buffer.setLength(0);
        int max = refAlleles.size();
        int index = 0;
        for (String allele : refAlleles) {
            buffer.append(allele);
            if (++index != max) buffer.append(',');
        }
        if (buffer.length() == 0) {
            // set REF or ALT to the VCF missing value if there are no alleles to write:
            buffer.append('.');
        }
        return buffer;
    }

    /**
     * Set a flag to true.
     *
     * @param infoFlagIndex index of the INFO field corresponding to the flag.
     */
    public void setFlag(final int infoFlagIndex) {
        setFlag(infoFlagIndex, true);
    }

    /**
     * Set a flag to state.
     *
     * @param infoFlagIndex index of the INFO field corresponding to the flag.
     * @param state         state to set the flag to.
     */
    public void setFlag(final int infoFlagIndex, final boolean state) {

        infoValues[infoFlagIndex] = state ? infoIds[infoFlagIndex] : "";

    }

    private final MutableString codedGenotypeBuffer = new MutableString();
    private final IntSortedSet genotypeIndexList = new IntAVLTreeSet();

    /**
     * Encode the list of alleles as a VCF genotype.
     *
     * @param alleles String of the form A/B/C where A,B C are allele that are part of a genotype.
     * @return coded VCF genotype.
     * @see #codeGenotype(String[] alleles)
     */
    public MutableString codeGenotype(String alleles) {
        return codeGenotype(alleles.split("/"));
    }

    /**
     * Encode the list of alleles as a VCF genotype. VCF genotypes are coded against the REF and ALT alleles.
     * A VCF genotype has the form 0/2/3 where each int encodes the index of the allele that participates to the
     * genotype. REF ALT aleleles are considered in the order they appear and given increasing indices (starting at
     * zero with the REF allele). The coded genotype 0/0 represents a genotype with two reference alleles.
     *
     * @param alleles List of alleles included in the genotype.
     * @return coded VCF genotype.
     */
    protected MutableString codeGenotype(String[] alleles) {
        return codeGenotype(alleles, this.refAlleles, this.altAlleles);
    }

    /**
     * Encode the list of alleles as a VCF genotype. VCF genotypes are coded against the REF and ALT alleles.
     * A VCF genotype has the form 0/2/3 where each int encodes the index of the allele that participates to the
     * genotype. REF ALT aleleles are considered in the order they appear and given increasing indices (starting at
     * zero with the REF allele). The coded genotype 0/0 represents a genotype with two reference alleles.
     *
     * @param alleles    List of alleles included in the genotype.
     * @param altAlleles alternate alleles
     * @param refAlleles reference alleles
     * @return coded VCF genotype.
     */
    protected MutableString codeGenotype(final String[] alleles,
                                         final ObjectArrayList<String> refAlleles,
                                         final ObjectArrayList<String> altAlleles) {

        codedGenotypeBuffer.setLength(0);
        boolean alleleFound;
        genotypeIndexList.clear();
        for (String allele : alleles) {
            alleleFound = false;
            int alleleIndex = 0;
            for (String ref : refAlleles) {
                // the second part of the next clause checks if the one base allele is included in the ref allele (i.e., C is included in CC
                // since the next base matches the reference in both C and CC when CC is a ref allele.
                if (includedIn(allele, ref)) {
                    genotypeIndexList.add(alleleIndex);

                    alleleFound = true;

                    break;
                }
                alleleIndex++;
            }
            if (alleleFound) continue;
            for (String alt : altAlleles) {
                if (includedIn(allele, alt)) {
                    genotypeIndexList.add(alleleIndex);
                    alleleFound = true;

                    break;
                }
                alleleIndex++;
            }

            if (!alleleFound) {
                System.out.printf("allele: %s ref: %s alt: %s", allele, refAlleles.toString(), altAlleles.toString());
                throw new IllegalArgumentException(String.format("Allele %s was not found in REF or ALT", allele));
            }
        }
        //    Collections.sort(genotypeIndexList);

        for (final int alleleIndex : genotypeIndexList) {
            codedGenotypeBuffer.append(Integer.toString(alleleIndex));
            codedGenotypeBuffer.append(genotypeDelimiterCharacter);
        }
        if (genotypeIndexList.size() == 1) {
            // write n/n rather than n
            codedGenotypeBuffer.append(codedGenotypeBuffer);
        }
        final int length = codedGenotypeBuffer.length();
        if (length > 0) {
            codedGenotypeBuffer.setLength(length - 1);
        }
        return codedGenotypeBuffer.copy();
    }

    private boolean includedIn(final String allele, final String referenceAllele) {
        return referenceAllele.startsWith(allele);
    }

    public static int COLUMN_NOT_DEFINED = -1;

    /**
     * Close the writer.
     */
    public void close() {
        outWriter.close();
    }

    CharSequence[] infoValues;


    ColumnInfo infoColumn = new ColumnInfo("INFO");

    /**
     * Define a VCF field for a column. No groups associated with the field.
     *
     * @param columnName  Name of an existing column.
     * @param fieldName   Name of the new field.
     * @param numValues   Number of values in this field.
     * @param type        Type of data for individual values of the field.
     * @param description Description of data represented by the field.
     * @return index of the field in the column.
     */
    public int defineField(String columnName, String fieldName, int numValues, ColumnType type, String description) {

        return defineField(columnName, fieldName, numValues, type, description, NO_GROUPS);
    }

    /**
     * Define a VCF field for a column.
     *
     * @param columnName  Name of an existing column.
     * @param fieldName   Name of the new field.
     * @param numValues   Number of values in this field.
     * @param type        Type of data for individual values of the field.
     * @param description Description of data represented by the field.
     * @param groups      Groups associated with the new field.
     * @return index of the field in the column.
     */
    public int defineField(String columnName, String fieldName, int numValues, ColumnType type, String description, String... groups) {
        ColumnInfo c = columns.find(columnName);
        if (c == null) {
            throw new IllegalArgumentException("Could not find column " + columnName);
        }
        int maxIndex = -1;
        for (ColumnField f : c.fields) {
            maxIndex = Math.max(f.globalFieldIndex, maxIndex);
        }
        final ColumnField columnField = new ColumnField(fieldName, numValues, type, description);
        columnField.globalFieldIndex = maxIndex + 1;
        c.addField(columnField);
        columnField.addGroup(groups);
        fieldGroups.add(new ObjectArrayList<String>());
        return columnField.globalFieldIndex;
    }

    /**
     * Set the value of an INFO column field for the current record. The value will be written when writeRecord is executed.
     *
     * @param infoFieldIndex Index returned by definedField("INFO",...)
     * @param value          Value of the field.
     */
    public void setInfo(int infoFieldIndex, CharSequence value) {
        infoValues[infoFieldIndex] = value;
    }

    /**
     * Set the value of an INFO column field for the current record. The value will be written when writeRecord is executed.
     *
     * @param infoFieldIndex Index returned by definedField("INFO",...)
     * @param value          Value of the field.
     */
    public void setInfo(int infoFieldIndex, double value) {
        infoValues[infoFieldIndex] = Double.toString(value);
    }

    /**
     * Set the value of an INFO column field for the current record. The value will be written when writeRecord is executed.
     *
     * @param infoFieldIndex Index returned by definedField("INFO",...)
     * @param value          Value of the field.
     */
    public void setInfo(int infoFieldIndex, float value) {
        infoValues[infoFieldIndex] = Float.toString(value);
    }

    /**
     * Set the value of an INFO column field for the current record. The value will be written when writeRecord is executed.
     *
     * @param infoFieldIndex Index returned by definedField("INFO",...)
     * @param value          Value of the field.
     */
    public void setInfo(int infoFieldIndex, int value) {
        infoValues[infoFieldIndex] = Integer.toString(value);
    }

    private String[] sampleIds;

    /**
     * Get the list of sample column identifiers.
     *
     * @return list of sample column identifiers.
     */
    public String[] getSamples() {
        return sampleIds;
    }

    /**
     * Define sample identifiers. VCF stores information for each sample according to information stored in the
     * FORMAT column in each line.
     * se
     *
     * @param samples Identifiers of the samples
     */
    public void defineSamples(String[] samples) {
        sampleIds = samples;
        formatValues = new CharSequence[getNumFormatFields()][samples.length];
    }

    /**
     * Set a value of a sample column. The sampleIndex identifies the sample in the getSampleIds()  array.
     *
     * @param formatFieldIndex Index of a FORMAT field created with defineField("FORMAT,...)
     * @param sampleIndex      Index of the sample
     * @param value            Value to set the field to for the current record.
     */
    public void setSampleValue(final int formatFieldIndex, final int sampleIndex, final CharSequence value) {
        formatFieldActive[formatFieldIndex] = true;
        formatValues[formatFieldIndex][sampleIndex] = value;
    }

    protected CharSequence getSampleValue(final int formatFieldIndex, final int sampleIndex) {
        return formatValues[formatFieldIndex][sampleIndex];
    }

    /**
     * Set a value of a sample column. The sampleIndex identifies the sample in the getSampleIds()  array.
     *
     * @param formatFieldIndex Index of a FORMAT field created with defineField("FORMAT,...)
     * @param sampleIndex      Index of the sample
     * @param value            Value to set the field to for the current record.
     */
    public void setSampleValue(int formatFieldIndex, int sampleIndex, double value) {
        setSampleValue(formatFieldIndex, sampleIndex, Double.toString(value));
    }

    /**
     * Move a sample value to a new column, replace the previous value of one of the columns with some new value.
     *
     * @param formatFieldIndex      FORMAT field to modify.
     * @param sampleIndex           Index of the first column.
     * @param newSampleIndex        Index of the second column
     * @param newValueAtSampleIndex New value to fill one of the columns.
     */
    public void switchSampleValue(final int formatFieldIndex, final int sampleIndex, final int newSampleIndex,
                                  final String newValueAtSampleIndex) {

        int minIndex = Math.min(sampleIndex, newSampleIndex);
        int maxIndex = Math.max(sampleIndex, newSampleIndex);
        if (maxIndex == maxIndex) {
            setSampleValue(formatFieldIndex, newSampleIndex, formatValues[formatFieldIndex][minIndex]);
        }
        // move value from sampleIndex to newSampleIndex, put newValueAtSampleIndex at sampleIndex
        setSampleValue(formatFieldIndex, newSampleIndex, formatValues[formatFieldIndex][minIndex]);
        setSampleValue(formatFieldIndex, sampleIndex, newValueAtSampleIndex);


    }

    /**
     * Set a value of a sample column. The sampleIndex identifies the sample in the getSampleIds()  array.
     *
     * @param formatFieldIndex Index of a FORMAT field created with defineField("FORMAT,...)
     * @param sampleIndex      Index of the sample
     * @param value            Value to set the field to for the current record.
     */
    public void setSampleValue(int formatFieldIndex, int sampleIndex, int value) {
        setSampleValue(formatFieldIndex, sampleIndex, Integer.toString(value));
    }

    /**
     * Set a value of a sample column. The sampleIndex identifies the sample in the getSampleIds()  array.
     *
     * @param formatFieldIndex Index of a FORMAT field created with defineField("FORMAT,...)
     * @param sampleIndex      Index of the sample
     * @param value            Value to set the field to for the current record.
     */
    public void setSampleValue(int formatFieldIndex, int sampleIndex, float value) {
        setSampleValue(formatFieldIndex, sampleIndex, Float.toString(value));
    }

    public void setSampleValue(String formatToken, int sampleIndex, String value) {
        int formatFieldIndex = formatTypeToFormatFieldIndex.getInt(formatToken);
        setSampleValue(formatFieldIndex, sampleIndex, value);
    }

    /**
     * Get the total number of possible format fields in the FORMAT column.
     *
     * @return number of FORMAT fields.
     */
    public int getNumFormatFields() {
        return columns.find("FORMAT").fields.size();
    }

    /**
     * Define the VCF schema from Columns assembled externally.
     *
     * @param columns
     */
    public void defineSchema(Columns columns) {
        assignColumnIndices(columns);
        columnList.clear();
        this.columns = columns;
    }


    // assign column order according to the CVF format.
    private void assignColumnIndices(Columns columns) {
        int colIndex = 9;
        final Object2IntMap<String> colIndices = new Object2IntArrayMap<String>();
        colIndices.put("CHROM", 0);
        colIndices.put("POS", 1);
        colIndices.put("ID", 2);
        colIndices.put("REF", 3);
        colIndices.put("ALT", 4);
        colIndices.put("QUAL", 5);
        colIndices.put("FILTER", 6);
        colIndices.put("INFO", 7);
        colIndices.put("FORMAT", 8);
        //CHROM  POS     ID      REF     ALT     QUAL    FILTER  INFO    FORMAT
        for (final ColumnInfo col : columns) {
            final String columnName = col.getColumnName();
            if (colIndices.containsKey(columnName)) {
                col.columnIndex = colIndices.getInt(columnName);
            } else {
                col.columnIndex = colIndex++;
            }
        }
    }

    public void setAlternateAllele(String value) {
        String[] alleles = value.split(",");
        for (String allele : alleles) {
            addAlternateAllele(allele);
        }
    }

    public void setFilter(CharSequence value) {
        this.filter = value;
    }

    public void setQual(CharSequence value) {
        this.qual = value;
    }

    /**
     * Returns the number of INFO fields currently defined.
     *
     * @return the number of INFO fields currently defined.
     */
    public int getNumInfoFields() {
        return columns.find("INFO").fields.size();
    }

    public int getPosition() {
        return position;
    }

    /**
     * Return true if the writer has at least one alternate allele defined at the current record.
     *
     * @return True or False.
     */
    public boolean hasAlternateAllele() {
        return !altAlleles.isEmpty();
    }

    private ObjectArrayList<ObjectArrayList<String>> fieldGroups = new ObjectArrayList<ObjectArrayList<String>>();


}
