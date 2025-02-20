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

package edu.cornell.med.icb.goby.modes;

import com.google.protobuf.TextFormat;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import edu.cornell.med.icb.goby.alignments.AlignmentReader;
import edu.cornell.med.icb.goby.alignments.AlignmentReaderImpl;
import edu.cornell.med.icb.goby.alignments.Alignments;
import edu.cornell.med.icb.goby.alignments.EntryFlagHelper;
import edu.cornell.med.icb.goby.alignments.FileSlice;
import edu.cornell.med.icb.goby.alignments.IterateAlignments;
import edu.cornell.med.icb.identifier.DoubleIndexedIdentifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Converts a compact alignment to plain text.
 *
 * @author Fabien Campagne
 */
public class AlignmentToTextMode extends AbstractGobyMode {
    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(AlignmentToTextMode.class);

    /**
     * The mode name.
     */
    private static final String MODE_NAME = "alignment-to-text";

    /**
     * The mode description help text.
     */
    private static final String MODE_DESCRIPTION = "Converts a compact alignment to text formats.";

    /**
     * If the table should be scrolling (or paging). As the entire dataset is loaded into a Javascript array,
     * one COULD ask DataTables to scroll the data. This works with about 500 entries but for greater number
     * of entries, paging seems to work MUCH better, so we are defaulting to scrolling off (paging on) which
     * seems to work just fine with even 10,000 or possibly more entries.
     */
    private static final boolean SCROLLING = false;

    /**
     * The output file.
     */
    private String outputFilename;

    /**
     * The basename of the compact alignment.
     */
    private String[] basenames;
    private AlignmentToTextIterateAlignments alignmentIterator;

    /**
     * If header is written, used in PLAIN output (not SAM).
     */
    private boolean headerWritten;

    @Override
    public String getModeName() {
        return MODE_NAME;
    }

    @Override
    public String getModeDescription() {
        return MODE_DESCRIPTION;
    }

    enum OutputFormat {
        PLAIN,
        HTML,
        PROTOTEXT
    }

    private OutputFormat outputFormat;

    /**
     * An "unset" value for startPosition and endPosition.
     */
    private boolean hasStartOrEndPosition;

    /**
     * The start position for the reformat.
     */
    private long startPosition;

    /**
     * The end position for the reformat.
     */
    private long endPosition = Long.MAX_VALUE;

    /**
     * The maximum number of entries to output, set to -1 for all entries.
     */
    private long maxToOutput = -1;

    /**
     * Configure.
     *
     * @param args command line arguments
     * @return this object for chaining
     * @throws IOException   error parsing
     * @throws JSAPException error parsing
     */
    @Override
    public AbstractCommandLineMode configure(final String[] args)
            throws IOException, JSAPException {
        final JSAPResult jsapResult = parseJsapArguments(args);

        final String[] inputFiles = jsapResult.getStringArray("input");
        basenames = AlignmentReaderImpl.getBasenames(inputFiles);
        outputFilename = jsapResult.getString("output");
        outputFormat = OutputFormat.valueOf(jsapResult.getString("format").toUpperCase());
        /*
        if (outputFormat == OutputFormat.SAM) {
            // No output header for SAM format
            headerWritten = true;
        }
        */
        alignmentIterator = new AlignmentToTextIterateAlignments();
        alignmentIterator.parseIncludeReferenceArgument(jsapResult);
        maxToOutput = jsapResult.getLong("max-to-output");
        if (maxToOutput == -1L) {
            maxToOutput = Long.MAX_VALUE;
        }
        if (jsapResult.contains("start-position") || jsapResult.contains("end-position")) {
            hasStartOrEndPosition = true;
            startPosition = jsapResult.getLong("start-position", 0L);
            endPosition = jsapResult.getLong("end-position", Long.MAX_VALUE);
            if (startPosition < 0) {
                startPosition = 0;
            }
            if (endPosition < 0) {
                endPosition = Long.MAX_VALUE;
            }
            if (startPosition == 0 && endPosition == 0) {
                endPosition = Long.MAX_VALUE;
            }
        }

        if (startPosition < 0L) {
            throw new JSAPException("Start position must not be less than zero");
        }
        if (endPosition < 0L) {
            throw new JSAPException("End position must not be less than zero");
        }
        if (startPosition > endPosition) {
            throw new JSAPException("Start position must not be greater than the end position");
        }

        return this;
    }

    private class AlignmentToTextIterateAlignments extends IterateAlignments {
        private PrintStream outputStream;
        private OutputFormat outputFormat;
        private AlignmentReader cachedReader;
        private boolean hasReadIds;
        private DoubleIndexedIdentifier readIds;
        private int[] referenceLengths;
        StringBuilder htmlBuilder;
        long numWritten;
        List<Map<String, String>> fieldAttributes;

        public void setOutputWriter(final PrintStream outputStream, final OutputFormat outputFormat) {
            this.outputStream = outputStream;
            this.outputFormat = outputFormat;
        }

        @Override
        public void processAlignmentEntry(final AlignmentReader alignmentReader, final Alignments.AlignmentEntry alignmentEntry) {
            final int referenceIndex = alignmentEntry.getTargetIndex();

            if (cachedReader != alignmentReader) {
                hasReadIds = !alignmentReader.getQueryIdentifiers().isEmpty();
                referenceLengths = alignmentReader.getTargetLength();
            }

            final int startPosition = alignmentEntry.getPosition();
            final int queryAlignmentLength = alignmentEntry.getQueryAlignedLength();
            final int targetAlignmentLength = alignmentEntry.getTargetAlignedLength();

            final String leftClipBases = alignmentEntry.getSoftClippedBasesLeft();
            final String leftClipQuals =
                    ArrayUtils.toString(alignmentEntry.getSoftClippedQualityLeft().toByteArray());
            final String rightClipBases = alignmentEntry.getSoftClippedBasesRight();
            final String rightClipQuals =
                    ArrayUtils.toString(alignmentEntry.getSoftClippedQualityRight().toByteArray());
            final String placedUnmappedBases = alignmentEntry.getPlacedUnmappedSequence();
            final String placedUnmappedQuals =
                    ArrayUtils.toString(alignmentEntry.getPlacedUnmappedQuality().toByteArray());

            if (!headerWritten && outputFormat == OutputFormat.PLAIN || outputFormat == OutputFormat.HTML) {
                printHeader(outputStream);
            }

            final StringBuilder output = new StringBuilder();

            final int multiplicity = alignmentEntry.getMultiplicity();
            for (int i = 0; i < multiplicity; ++i) {
                output.setLength(0);
                final int queryIndex = alignmentEntry.getQueryIndex();

                // Get the length of the reference (if available)
                final int referenceLength;
                if (referenceLengths != null && ArrayUtils.getLength(referenceLengths) >= referenceIndex) {
                    referenceLength = referenceLengths[referenceIndex];
                } else {
                    referenceLength = -1;
                }
                switch (outputFormat) {
                    case HTML:
                        if (outputHtml(outputStream, false,
                                hasReadIds ? readIds.getId(queryIndex) : queryIndex,
                                getReferenceId(alignmentEntry.getTargetIndex()),
                                startPosition,
                                alignmentEntry.getQueryLength(),
                                queryAlignmentLength,
                                targetAlignmentLength,
                                leftClipBases,
                                leftClipQuals,
                                rightClipBases,
                                rightClipQuals,
                                placedUnmappedBases,
                                placedUnmappedQuals,
                                alignmentEntry.getMatchingReverseStrand() ? "-" : "+",
                                alignmentEntry.getScore(),
                                alignmentEntry.hasMappingQuality() ? alignmentEntry.getMappingQuality() : 255,
                                alignmentEntry.hasPairFlags() ? EntryFlagHelper.pairToString(alignmentEntry) : "",
                                alignmentEntry.hasAmbiguity() ? alignmentEntry.getAmbiguity() : 0,
                                alignmentEntry.hasQueryIndexOccurrences() ? alignmentEntry.getQueryIndexOccurrences() : 0,
                                alignmentEntry.hasFragmentIndex() ? alignmentEntry.getFragmentIndex() : 0,
                                alignmentEntry.hasPairAlignmentLink() ? alignmentEntry.getPairAlignmentLink().getFragmentIndex() : "",
                                alignmentEntry.hasPairAlignmentLink() ? getReferenceId(alignmentEntry.getPairAlignmentLink().getTargetIndex()) : "",
                                alignmentEntry.hasPairAlignmentLink() ? alignmentEntry.getPairAlignmentLink().getPosition() : "",
                                alignmentEntry.hasSplicedFlags() ? EntryFlagHelper.spliceToString(alignmentEntry) : "",
                                alignmentEntry.hasSplicedForwardAlignmentLink() ? alignmentEntry.getSplicedForwardAlignmentLink().getFragmentIndex() : "",
                                alignmentEntry.hasSplicedForwardAlignmentLink() ? getReferenceId(alignmentEntry.getSplicedForwardAlignmentLink().getTargetIndex()) : "",
                                alignmentEntry.hasSplicedForwardAlignmentLink() ? alignmentEntry.getSplicedForwardAlignmentLink().getPosition() : "",
                                alignmentEntry.hasSplicedBackwardAlignmentLink() ? alignmentEntry.getSplicedBackwardAlignmentLink().getFragmentIndex() : "",
                                alignmentEntry.hasSplicedBackwardAlignmentLink() ? getReferenceId(alignmentEntry.getSplicedBackwardAlignmentLink().getTargetIndex()) : "",
                                alignmentEntry.hasSplicedBackwardAlignmentLink() ? alignmentEntry.getSplicedBackwardAlignmentLink().getPosition() : "",
                                referenceLength,
                                alignmentEntry.getNumberOfIndels(),
                                alignmentEntry.getNumberOfMismatches())) {
                            numWritten++;
                        }
                        break;
                    case PLAIN:
                        if (numWritten < maxToOutput) {
                            outputStream.printf("%s\t" +  // Name,
                                    "%d\t%d\t%d\t" +  // Ambiguity, QueryIndexOccurrences, fragmentIndex
                                    "%s\t%s\t%s\t%s\t" +   // PairFlags...
                                    "%s\t%s\t%s\t%s\t" +   // Splice Forward
                                    "%s\t%s\t%s\t" +   // Splice Backward
                                    "%s\t%d\t%d\t%d\t%g\t%d\t%d\t%d\t%s\t%d%n",
                                    hasReadIds ? readIds.getId(queryIndex) : queryIndex,
                                    alignmentEntry.hasAmbiguity() ? alignmentEntry.getAmbiguity() : 0,
                                    alignmentEntry.hasQueryIndexOccurrences() ? alignmentEntry.getQueryIndexOccurrences() : 0,
                                    alignmentEntry.hasFragmentIndex() ? alignmentEntry.getFragmentIndex() : 0,
                                    alignmentEntry.hasPairFlags() ? zeroPad(Integer.toBinaryString(alignmentEntry.getPairFlags()), 9) : "",
                                    alignmentEntry.hasPairAlignmentLink() ? alignmentEntry.getPairAlignmentLink().getFragmentIndex() : "",
                                    alignmentEntry.hasPairAlignmentLink() ? getReferenceId(alignmentEntry.getPairAlignmentLink().getTargetIndex()) : "",
                                    alignmentEntry.hasPairAlignmentLink() ? alignmentEntry.getPairAlignmentLink().getPosition() : "",
                                    alignmentEntry.hasSplicedFlags() ? zeroPad(Integer.toBinaryString(alignmentEntry.getSplicedFlags()), 9) : "",
                                    alignmentEntry.hasSplicedForwardAlignmentLink() ? alignmentEntry.getSplicedForwardAlignmentLink().getFragmentIndex() : "",
                                    alignmentEntry.hasSplicedForwardAlignmentLink() ? getReferenceId(alignmentEntry.getSplicedForwardAlignmentLink().getTargetIndex()) : "",
                                    alignmentEntry.hasSplicedForwardAlignmentLink() ? alignmentEntry.getSplicedForwardAlignmentLink().getPosition() : "",
                                    alignmentEntry.hasSplicedBackwardAlignmentLink() ? alignmentEntry.getSplicedBackwardAlignmentLink().getFragmentIndex() : "",
                                    alignmentEntry.hasSplicedBackwardAlignmentLink() ? getReferenceId(alignmentEntry.getSplicedBackwardAlignmentLink().getTargetIndex()) : "",
                                    alignmentEntry.hasSplicedBackwardAlignmentLink() ? alignmentEntry.getSplicedBackwardAlignmentLink().getPosition() : "",                                                    
                                    getReferenceId(alignmentEntry.getTargetIndex()),
                                    referenceLength,
                                    alignmentEntry.getNumberOfIndels(),
                                    alignmentEntry.getNumberOfMismatches(),
                                    alignmentEntry.getScore(),
                                    startPosition,
                                    queryAlignmentLength,
                                    targetAlignmentLength,
                                    alignmentEntry.getMatchingReverseStrand() ? "-" : "+",
                                    alignmentEntry.hasMappingQuality() ? alignmentEntry.getMappingQuality() : 255);
                            numWritten++;
                        }
                        break;
                    case PROTOTEXT:
                        if (numWritten < maxToOutput) {
                            try {
                                outputStream.println("{");
                                TextFormat.print(alignmentEntry, outputStream);
                                outputStream.println("}");
                            } catch (IOException e) {
                                LOG.error("cannot format protobuff to text", e);
                            }
                            numWritten++;
                        }
                        break;
                }
            }
        }

        Map<String, String> stringToMap(final String attributes) {
            final Map<String, String> result = new HashMap<String, String>();
            final String[] parts = attributes.trim().split(",");
            for (final String part : parts) {
                final String[] subParts = part.trim().split(":");
                result.put(subParts[0].trim(), subParts[1].trim());
            }
            return result;
        }

        boolean outputHtml(final PrintStream outputStream, final boolean header, final Object... fields) {
            if (htmlBuilder == null) {
                htmlBuilder = new StringBuilder();
                fieldAttributes = new ArrayList<Map<String, String>>(fields.length / 2);
            } else {
                htmlBuilder.setLength(0);
            }
            if (header) {
                htmlBuilder.append("<body><head>").append("\n");
                htmlBuilder.append("<style type='text/css'>").append("\n");
                htmlBuilder.append("    body{ font-family: Arial, Helvetica, sans-serif; }").append("\n");
                htmlBuilder.append("    .odd { background-color: #def; }").append("\n");
                htmlBuilder.append("    .right {text-align: right;}").append("\n");
                htmlBuilder.append("    .center {text-align: center;}").append("\n");
                htmlBuilder.append("    table td {font-size:80%; }").append("\n");
                htmlBuilder.append("    tfoot input { width:60px; }").append("\n");
                htmlBuilder.append("    tfoot input.search_init { color:#777777; font-style:italic; }").append("\n");
                htmlBuilder.append("</style>").append("\n");
                htmlBuilder.append("<link rel='stylesheet' type='text/css' href='http://yui.yahooapis.com/3.3.0/build/cssreset/reset-min.css' />").append("\n");
                htmlBuilder.append("<link rel='stylesheet' type='text/css' href='http://yui.yahooapis.com/3.3.0/build/cssreset/reset-context-min.css' />").append("\n");
                htmlBuilder.append("<link rel='stylesheet' type='text/css' href='http://ajax.aspnetcdn.com/ajax/jquery.ui/1.8.17/themes/ui-lightness/jquery-ui.css' />").append("\n");
                htmlBuilder.append("<link rel='stylesheet' type='text/css' href='http://campagnelab.org/files/cdn/datatables/media/css/jquery.dataTables.css'>").append("\n");
                htmlBuilder.append("<script type='text/javascript' charset='utf8' src='http://campagnelab.org/files/cdn/datatables/media/js/jquery.js'></script>").append("\n");
                htmlBuilder.append("<script type='text/javascript' charset='utf8' src='http://campagnelab.org/files/cdn/datatables/media/js/jquery.dataTables.min.js'></script>").append("\n");
                htmlBuilder.append("<script type='text/javascript' charset='utf8' src='http://campagnelab.org/files/cdn/datatables-plugins/js/fourButtonNavigation.js'></script>").append("\n");
                htmlBuilder.append("<script type='text/javascript'>").append("\n");
                htmlBuilder.append("    var dt;").append("\n");
                htmlBuilder.append("    jQuery(document).ready(function() {").append("\n");
                htmlBuilder.append("        dt = jQuery('#alignment').dataTable({").append("\n");
                htmlBuilder.append("            'aaData': data,").append("\n");
                htmlBuilder.append("            'aoColumns': columns,").append("\n");
                htmlBuilder.append("            'bJQueryUI': true,").append("\n");
                htmlBuilder.append("            'oLanguage': {").append("\n");
                htmlBuilder.append("                'sSearch': 'Search all columns:'").append("\n");
                htmlBuilder.append("            },").append("\n");
                if (SCROLLING) {
                    htmlBuilder.append("            'iDisplayLength': -1,").append("\n");
                    htmlBuilder.append("            'aLengthMenu': [[-1], ['All']],").append("\n");
                    htmlBuilder.append("            'sScrollY': 400,").append("\n");
                    htmlBuilder.append("            'sScrollX': 600,").append("\n");
                    htmlBuilder.append("            'bPaginate': false,").append("\n");
                    htmlBuilder.append("            'bProcessing': true,").append("\n");
                    htmlBuilder.append("            'bSort' : false").append("\n");
                } else {
                    htmlBuilder.append("            'aLengthMenu': [[10, 15, 20, 25, 30, 35, 40, 45, 50, -1], [10, 15, 20, 25, 30, 35, 40, 45, 50, 'All']],").append("\n");
                    htmlBuilder.append("            'sPaginationType': 'four_button',").append("\n");
                    htmlBuilder.append("            'sScrollX': '200px',").append("\n");
                }
                htmlBuilder.append("            'bSort' : false").append("\n");
                htmlBuilder.append("        });").append("\n");
                htmlBuilder.append("        var asInitVals = new Array();").append("\n");
                htmlBuilder.append("        jQuery('tfoot input').keyup( function () {").append("\n");
                htmlBuilder.append("            dt.fnFilter( this.value, jQuery('tfoot input').index(this) );").append("\n");
                htmlBuilder.append("        });").append("\n");
                htmlBuilder.append("        jQuery('tfoot input').each( function (i) {").append("\n");
                htmlBuilder.append("            asInitVals[i] = this.value;").append("\n");
                htmlBuilder.append("        });").append("\n");
                htmlBuilder.append("        jQuery('tfoot input').focus( function () {").append("\n");
                htmlBuilder.append("            if (this.className == 'search_init') {").append("\n");
                htmlBuilder.append("                this.className = '';").append("\n");
                htmlBuilder.append("                this.value = '';").append("\n");
                htmlBuilder.append("            }").append("\n");
                htmlBuilder.append("        });").append("\n");
                htmlBuilder.append("        jQuery('tfoot input').blur( function (i) {").append("\n");
                htmlBuilder.append("            if ( this.value == '' ) {").append("\n");
                htmlBuilder.append("                this.className = 'search_init';").append("\n");
                htmlBuilder.append("                this.value = asInitVals[jQuery('tfoot input').index(this)];").append("\n");
                htmlBuilder.append("            }").append("\n");
                htmlBuilder.append("        });").append("\n");
                htmlBuilder.append("    });").append("\n");
                htmlBuilder.append("    jQuery(window).bind('resize', function() {").append("\n");
                htmlBuilder.append("        dt.fnAdjustColumnSizing();").append("\n");
                htmlBuilder.append("    });").append("\n");
                htmlBuilder.append("</script>").append("\n");
                htmlBuilder.append("</head>").append("\n");
                htmlBuilder.append("<html>").append("\n");
                htmlBuilder.append("<script type='text/javascript'>").append("\n");
                htmlBuilder.append("   function formatNumber(o, val) {").append("\n");
                htmlBuilder.append("        x1 = val + '';").append("\n");
                htmlBuilder.append("        var rgx = /(\\d+)(\\d{3})/;").append("\n");
                htmlBuilder.append("        while (rgx.test(x1)) {").append("\n");
                htmlBuilder.append("            x1 = x1.replace(rgx, '$1' + ',' + '$2');").append("\n");
                htmlBuilder.append("        }").append("\n");
                htmlBuilder.append("        return x1;").append("\n");
                htmlBuilder.append("   }").append("\n");
                htmlBuilder.append("   var columns = [").append("\n");
                int fieldNum = 0;
                final List<String> columnNames = new LinkedList<String>();
                while (fieldNum < fields.length) {
                    if (fieldNum > 0) {
                        htmlBuilder.append(",");
                    }
                    final String fieldName = (String) fields[fieldNum++];
                    columnNames.add(fieldName);
                    final Map<String, String> attributes = stringToMap((String) fields[fieldNum++]);
                    fieldAttributes.add(attributes);
                    htmlBuilder.append("{'sTitle':'").append(fieldName).append("'");
                    final String renderFunction = attributes.get("renderFunction");
                    if (renderFunction != null) {
                        htmlBuilder.append(",'fnRender':").append(renderFunction);
                    }
                    final String align = attributes.get("align");
                    if (align != null) {
                        htmlBuilder.append(",'sClass':'").append(align).append("'");
                    }
                    htmlBuilder.append("}");
                }
                htmlBuilder.append("];\n");
                htmlBuilder.append("</script>\n");
                htmlBuilder.append("<table cellpadding='0' cellspacing='0' border='0' class='display' id='alignment'>").append("\n");
                htmlBuilder.append("<tfoot><tr>\n");
                for (final String columnName : columnNames) {
                    htmlBuilder.append("<td><input type='text' name='search_").append(columnName).append("' value='filter' class='search_init'/></td>");
                }
                htmlBuilder.append("</tr></tfoot>\n");
                htmlBuilder.append("</table>").append("\n");
                htmlBuilder.append("<script type='text/javascript'>").append("\n");
                htmlBuilder.append("   var data = [\n");
            } else {
                if (numWritten >= maxToOutput) {
                    return false;
                }
                if (numWritten > 0) {
                    htmlBuilder.append(",");
                }
                htmlBuilder.append("[");
                for (int i = 0; i < fields.length; i++) {
                    final Object field = fields[i];
                    if (i > 0) {
                        htmlBuilder.append(",");
                    }
                    final Map<String, String> attributes = fieldAttributes.get(i);
                    final String type = attributes.get("type");
                    if ("string".equals(type)) {
                        if ((field == null) || ((field instanceof String) && (((String) field).length() == 0))) {
                            htmlBuilder.append("''");
                        } else {
                            htmlBuilder.append("'").append(field).append("'");
                        }
                    } else if ("int".equals(type)) {
                        if ((field == null) || ((field instanceof String) && (((String) field).length() == 0))) {
                            htmlBuilder.append("''");
                        } else {
                            htmlBuilder.append(field);
                        }
                    }
                }
                htmlBuilder.append("]");
                if (numWritten > 0 && numWritten % 250000 == 0) {
                    System.err.printf("Written %d%n", numWritten);
                }
            }
            outputStream.print(htmlBuilder.toString());
            return true;
        }

        private void printHeader(final PrintStream outputStream) {
            if (headerWritten) {
                return;
            }
            headerWritten = true;
            switch (outputFormat) {
                case HTML:
                    // Field name followed by if the field should be quoted.
                    outputHtml(outputStream, true,
                            "queryIndex", "type:int,align:right,renderFunction:formatNumber",
                            "targetIdentifier", "type:string",
                            "position", "type:int,align:right,renderFunction:formatNumber",
                            "queryLength", "type:int,align:right,renderFunction:formatNumber",
                            "queryAlignmentLength", "type:int,align:right,renderFunction:formatNumber",
                            "targetAlignmentLength", "type:int,align:right,renderFunction:formatNumber",
                            "leftClipBases", "type:string",
                            "leftClipQuals", "type:string",
                            "rightClipBases", "type:string",
                            "rightClipQuals", "type:string",
                            "placedUnmappedBases", "type:string",
                            "placedUnmappedQuals", "type:string",
                            "strand", "type:string",
                            "score", "type:int,align:right,renderFunction:formatNumber",
                            "mappingQuality", "type:int,align:right,renderFunction:formatNumber",
                            "pairFlags", "type:string,align:right",
                            "ambiguity", "type:int,align:right,renderFunction:formatNumber",
                            "queryIndexOccurrences", "type:int,align:right,renderFunction:formatNumber",
                            "readFragmentIndex", "type:int,align:center",
                            "pairFragmentIndex", "type:int,align:center",
                            "pairTarget", "type:string",
                            "pairPosition", "type:int,align:right,renderFunction:formatNumber",
                            "spliceFlags", "type:string,align:right",
                            "spliceForwardFragmentIndex", "type:int,align:center",
                            "spliceForwardTarget", "type:string",
                            "spliceForwardPosition", "type:int,align:right,renderFunction:formatNumber",
                            "spliceBackwardFragmentIndex", "type:int,align:center",
                            "spliceBackwardTarget", "type:string",
                            "spliceBackwardPosition", "type:int,align:right,renderFunction:formatNumber",
                            "referenceLength", "type:int,align:right,renderFunction:formatNumber",
                            "numIndels", "type:int,align:right,renderFunction:formatNumber",
                            "numMismatches", "type:int,align:right,renderFunction:formatNumber"
                    );
                    break;
                case PLAIN:
                    outputStream.printf(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n",
                            "queryIndex",
                            "ambiguity",
                            "queryIndexOccurrences",
                            "queryFragmentIndex",
                            "pairFlags",
                            "pairFragmentIndex",
                            "pairTarget",
                            "pairPosition",
                            "spliceFlags",
                            "spliceForwardFragmentIndex",
                            "spliceForwardTarget",
                            "spliceForwardPosition",
                            "spliceBackwardFragmentIndex",
                            "spliceBackwardTarget",
                            "spliceBackwardPosition",
                            "targetIdentifier",
                            "referenceLength",
                            "numIndels",
                            "numMismatches",
                            "score",
                            "position",
                            "queryAlignmentLength",
                            "targetAlignmentLength",
                            "strand",
                            "mappingQuality"));
                    break;
                case PROTOTEXT:
                    break;
            }
        }

    }

    void printFooter(final PrintStream outputStream) {
        outputStream.println("];");
        outputStream.println("</script></body></html>");
    }

    private String zeroPad(final String val, final int length) {
        final int addZeros = length - val.length();
        if (addZeros <= 0) {
            return val;
        }
        final String format = String.format("%%0%dd%%s", addZeros);
        return String.format(format, 0, val);
    }

    /**
     * Display the alignments as text files.
     *
     * @throws java.io.IOException error reading / writing
     */
    @Override
    public void execute() throws IOException {
        PrintStream stream = null;
        try {
            stream = outputFilename == null ? System.out
                    : new PrintStream(new FileOutputStream(outputFilename));

            alignmentIterator.setOutputWriter(stream, outputFormat);
            // Iterate through each alignment and write sequence variations to output file:
            if (hasStartOrEndPosition) {
                alignmentIterator.iterate(new FileSlice(startPosition, endPosition), basenames);
            } else {
                alignmentIterator.iterate(basenames);
            }

            if (outputFormat == OutputFormat.HTML) {
                printFooter(stream);
                System.err.printf("Wrote %d alignment entries%n", alignmentIterator.numWritten);
            }

        } finally {
            if (stream != System.out) {
                IOUtils.closeQuietly(stream);
            }
        }
    }

    /**
     * Main method.
     *
     * @param args command line args.
     * @throws JSAPException error parsing
     * @throws IOException   error parsing or executing.
     */

    public static void main(final String[] args) throws JSAPException, IOException {
        new AlignmentToTextMode().configure(args).execute();
    }
}
