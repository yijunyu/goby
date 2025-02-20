//
// Copyright (C) 2009-2012 Institute for Computational Biomedicine,
//                         Weill Medical College of Cornell University
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation; either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//

#include <string>
#include <iostream>
#include <queue>

#include "Reads.h"
#include "C_Reads.h"

using namespace std;

#undef C_READ_API_WRITE_ALIGNMENT_DEBUG
#ifdef C_READ_API_WRITE_ALIGNMENT_DEBUG
#define debug(x) x
#else
#define debug(x)
#endif

/**
 * C API to enable reading Goby compact-reads in C.
 */
extern "C" {

    void gobyReads_openReadsReader(
            char **unopenedFiles, int numUnopenedFiles, unsigned char circular, CReadsHelper **readsHelperpp) {
        gobyReads_openReadsReaderWindowed(unopenedFiles, numUnopenedFiles, circular, 0, 0, readsHelperpp);
    }

    void gobyReads_openReadsReaderSingleWindowed(
            char *filename,  unsigned long startOffset, unsigned long endOffset, CReadsHelper **readsHelperpp) {
        gobyReads_openReadsReaderWindowed(&filename, 1, 0, startOffset, endOffset, readsHelperpp);
    }

    /**
     * Open the .compact-reads file to read from.
     */
    void gobyReads_openReadsReaderWindowed(
            char **unopenedFiles, int numUnopenedFiles, unsigned char circular,
            unsigned long startOffset, unsigned long endOffset, CReadsHelper **readsHelperpp) {

        if (numUnopenedFiles == 0) {
            fprintf(stderr,"No input files to process.\n");
            exit(9);
        }
        *readsHelperpp = new CReadsHelper;
        CReadsHelper *readsHelper = *readsHelperpp;
        readsHelper->numberOfReads = 0;
        readsHelper->circular = circular;
        readsHelper->unopenedFiles = new queue<string>;
        for (int i = 0; i < numUnopenedFiles; i++) {
            string unopenedFile(unopenedFiles[0]);
            readsHelper->unopenedFiles->push(unopenedFile);
            unopenedFiles++;
        }
        string filename = readsHelper->unopenedFiles->front();
        readsHelper->unopenedFiles->pop();
        readsHelper->readsReader = new goby::ReadsReader(filename);
        readsHelper->it = readsHelper->readsReader->beginPointer(startOffset, endOffset);
        readsHelper->end = readsHelper->readsReader->endPointer();
        readsHelper->numberOfReads = 0;

        int defaultSize = 50;
        readsHelper->lastReadIdentifier_m = defaultSize;
        readsHelper->lastReadIdentifier = (char *) malloc(defaultSize);

        readsHelper->lastDescription_m = defaultSize;
        readsHelper->lastDescription = (char *) malloc(defaultSize);

        readsHelper->lastSequence_m = defaultSize;
        readsHelper->lastSequence = (char *) malloc(defaultSize);

        readsHelper->lastQuality_m = defaultSize;;
        readsHelper->lastQuality = (char *) malloc(defaultSize);

        readsHelper->lastPairSequence_m = defaultSize;;
        readsHelper->lastPairSequence = (char *) malloc(defaultSize);

        readsHelper->lastPairQuality_m = defaultSize;
        readsHelper->lastPairQuality = (char *) malloc(defaultSize);

        // By default C_Reads will return quality scores as Phred. Adjust
        // this value to get Sanger (33) or Illumina (64).
        readsHelper->qualityAdjustment = 0;
        
        readsHelper->avoidZeroQuals = 0;
    }

    void gobyReads_avoidZeroQuals(CReadsHelper *readsHelper, int value) {
        readsHelper->avoidZeroQuals = value;
    }

    int gobyReads_getQualityAdjustment(CReadsHelper *readsHelper) {
        return readsHelper->qualityAdjustment;
    }
    
    void gobyReads_setQualityAdjustment(CReadsHelper *readsHelper, int value) {
        readsHelper->qualityAdjustment = value;
    }

    /**
     * This should be called ONCE per read.
     * Return values, 0 == false,  1 == true
     *
     * TODO: * We have a queue of unopenedFile but it isn't currently
     * TODO:   switching to the next file when we've finished reading
     * TODO:   the first.
     */
    int gobyReads_hasNext(CReadsHelper *readsHelper) {
        if (*((*readsHelper).it) != *(readsHelper->end)) {
            return 1;
        } else {
            return 0;
        }
    }

    void transferString(string src, char **dest, int *prevMallocSize) {
        int size = src.size();
        if (size + 1 > *prevMallocSize) {
            // we must realloc the memory we are using over and over to accomodate a new size
            *dest = (char *) realloc(*dest, size + 1);
            *prevMallocSize = size + 1;
        }
        memcpy(*dest, src.c_str(), size);
        (*dest)[size] = '\0';
    }

    void initializeHelperLastFields(CReadsHelper *readsHelper) {
        // Initialize non-NULL incoming strings to empty string in case the value isn't set this time around.
        (readsHelper->lastReadIdentifier)[0] = '\0';
        (readsHelper->lastDescription)[0] = '\0';
        (readsHelper->lastSequence)[0] = '\0';
        (readsHelper->lastQuality)[0] = '\0';
        (readsHelper->lastPairSequence)[0] = '\0';
        (readsHelper->lastPairQuality)[0] = '\0';
    }

    void clearHelperLastField(char **field, int *length) {
        free(*field);
        *field = (char *) NULL;
        *length = 0;
    }

    void clearHelperLastFields(CReadsHelper *readsHelper) {
        clearHelperLastField(&(readsHelper->lastReadIdentifier), &(readsHelper->lastReadIdentifier_m));
        clearHelperLastField(&(readsHelper->lastDescription), &(readsHelper->lastDescription_m));
        clearHelperLastField(&(readsHelper->lastSequence), &(readsHelper->lastSequence_m));
        clearHelperLastField(&(readsHelper->lastQuality), &(readsHelper->lastQuality_m));
        clearHelperLastField(&(readsHelper->lastPairSequence), &(readsHelper->lastPairSequence_m));
        clearHelperLastField(&(readsHelper->lastPairQuality), &(readsHelper->lastPairQuality_m));
    }

    void adjustQuality(CReadsHelper *readsHelper, char *qual, int length, int delta) {
        if (qual != NULL && length > 0) {
            int i;
            for (i = 0; i < length; i++) {
                qual[i] += delta;
                if (readsHelper->avoidZeroQuals && qual[i] == 0) {
                    // For gsnap, at least for now, change 0 qual scores 1
                    // because Bisulfate alignment (gsnap -C) doesn't like 0 values
                    qual[i] += 1;
                }
            }
        }
    }

    void dumpReadAndQual(char *type, char *read, bool hasQuality, int qualityLength, char *quality, int qualityAdjustment) {
        fprintf(stderr, ":: READ SEQUENCE (%s)\n:: %s\n", type, read);
        if (hasQuality) {
            fprintf(stderr, ":: no adjust ");
            for(int i = 0; i < qualityLength; i++) {
                if (i != 0) {
                    fprintf(stderr, ":");
                }
                fprintf(stderr, "%d", (quality[i] - qualityAdjustment));
            }
            fprintf(stderr, "\n:: adjusted  ");
            for(int i = 0; i < qualityLength; i++) {
                if (i != 0) {
                    fprintf(stderr, ":");
                }
                fprintf(stderr, "%d", quality[i]);
            }
            fprintf(stderr, "\n");
        }
    }

    /**
     * Read the sequence but ignore the pair even if it exists.
     * Do NOT free the char *'s you send to this method. If you need to keep a copy, MAKE A COPY.
     * @return the Goby read index
     */
    unsigned int gobyReads_nextSequence(
        CReadsHelper *readsHelper,
        char **readIdentifierpp, char **descriptionpp,
        char **sequencepp, int *sequenceLength,
        char **qualitypp, int *qualityLength) {

        /** Default is nothing populated. */
        *sequenceLength = 0;
        *qualityLength = 0;
        initializeHelperLastFields(readsHelper);

        goby::ReadEntry entry = *(*(*readsHelper).it);
        (*readsHelper).numberOfReads++;

        if (entry.has_read_identifier()) {
            transferString(entry.read_identifier(), &(readsHelper->lastReadIdentifier), &(readsHelper->lastReadIdentifier_m));
        }

        if (entry.has_description()) {
            transferString(entry.description(), &(readsHelper->lastDescription), &(readsHelper->lastDescription_m));
        }

        if (entry.has_sequence()) {
            transferString(entry.sequence(), &(readsHelper->lastSequence), &(readsHelper->lastSequence_m));
            *sequenceLength = entry.sequence().size();
            if (entry.has_quality_scores()) {
                transferString(entry.quality_scores(), &(readsHelper->lastQuality), &(readsHelper->lastQuality_m));
                *qualityLength = entry.quality_scores().size();
                adjustQuality(readsHelper, readsHelper->lastQuality, *qualityLength, readsHelper->qualityAdjustment);
            } else {
                *qualityLength = 0;
            }
            debug(dumpReadAndQual("primary", readsHelper->lastSequence, entry.has_quality_scores(),
                *qualityLength, readsHelper->lastQuality, readsHelper->qualityAdjustment);)
        }

        *readIdentifierpp = readsHelper->lastReadIdentifier;
        *descriptionpp = readsHelper->lastDescription;
        *sequencepp = readsHelper->lastSequence;
        *qualitypp = readsHelper->lastQuality;

        // Increment to the next ReadsEntry
        (*(*readsHelper).it)++;

        return entry.read_index();
    }

    /**
     * Read the sequence WITH pair if applicable.
     * Do NOT free the char *'s you send to this method. If you need to keep a copy, MAKE A COPY.
     * @return the Goby read index
     */
    unsigned int gobyReads_nextSequencePair(
        CReadsHelper *readsHelper,
        char **readIdentifierpp, char **descriptionpp,
        char **sequencepp, int *sequenceLength,
        char **qualitypp, int *qualityLength,
        char **pairSequencepp, int *pairSequenceLength,
        char **pairQualitypp, int *pairQualityLength) {

        /** Default is nothing populated. */
        *sequenceLength = 0;
        *qualityLength = 0;
        *pairSequenceLength = 0;
        *pairQualityLength = 0;
        initializeHelperLastFields(readsHelper);

        goby::ReadEntry entry = *(*(*readsHelper).it);
        (*readsHelper).numberOfReads++;

        if (entry.has_read_identifier()) {
            transferString(entry.read_identifier(), &(readsHelper->lastReadIdentifier), &(readsHelper->lastReadIdentifier_m));
        }

        if (entry.has_description()) {
            transferString(entry.description(), &(readsHelper->lastDescription), &(readsHelper->lastDescription_m));
        }

        if (entry.has_sequence()) {
            transferString(entry.sequence(), &(readsHelper->lastSequence), &(readsHelper->lastSequence_m));
            *sequenceLength = entry.sequence().size();
            if (entry.has_quality_scores()) {
                transferString(entry.quality_scores(), &(readsHelper->lastQuality), &(readsHelper->lastQuality_m));
                *qualityLength = entry.quality_scores().size();
                adjustQuality(readsHelper, readsHelper->lastQuality, *qualityLength, readsHelper->qualityAdjustment);
            } else {
                *qualityLength = 0;
            }
            debug(dumpReadAndQual("primary", readsHelper->lastSequence, entry.has_quality_scores(),
                *qualityLength, readsHelper->lastQuality, readsHelper->qualityAdjustment);)
        }

        if (entry.has_sequence_pair()) {
            transferString(entry.sequence_pair(), &(readsHelper->lastPairSequence), &(readsHelper->lastPairSequence_m));
            *pairSequenceLength = entry.sequence_pair().size();
            if (entry.has_quality_scores_pair()) {
                transferString(entry.quality_scores_pair(), &(readsHelper->lastPairQuality), &(readsHelper->lastPairQuality_m));
                *pairQualityLength = entry.quality_scores_pair().size();
                adjustQuality(readsHelper, readsHelper->lastPairQuality, *pairQualityLength, readsHelper->qualityAdjustment);
            } else {
                *pairQualityLength = 0;
            }
            debug(dumpReadAndQual("mate", readsHelper->lastPairSequence, entry.has_quality_scores_pair(),
                *pairQualityLength, readsHelper->lastPairQuality, readsHelper->qualityAdjustment);)
        }

        *readIdentifierpp = readsHelper->lastReadIdentifier;
        *descriptionpp = readsHelper->lastDescription;
        *sequencepp = readsHelper->lastSequence;
        *qualitypp = readsHelper->lastQuality;
        *pairSequencepp = readsHelper->lastPairSequence;
        *pairQualitypp = readsHelper->lastPairQuality;

        // Increment to the next ReadsEntry
        (*(*readsHelper).it)++;

        return entry.read_index();
    }

    /**
     * Call after you are _completely_ done reading Goby Reads.
     */
    void gobyReads_finished(CReadsHelper *readsHelper) {
        if (readsHelper != NULL) {
            while (!readsHelper->unopenedFiles->empty()) {
                string unopenedFile = readsHelper->unopenedFiles->front();
                readsHelper->unopenedFiles->pop();
            }
            clearHelperLastFields(readsHelper);
            delete readsHelper->unopenedFiles;
            delete readsHelper->readsReader;
            delete readsHelper->it;
            delete readsHelper;
        }
    }

    void goby_shutdownProtobuf() {
        google::protobuf::ShutdownProtobufLibrary();
    }
}
