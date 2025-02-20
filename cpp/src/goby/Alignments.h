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

#pragma once

#ifndef GOBY_ALIGNMENTS_H
#define GOBY_ALIGNMENTS_H

#if HAVE_CONFIG_H
#include <config.h>
#endif

#include <string>
#include "common.h"
#include "hash.h"
#include "Alignments.pb.h"
#include "MessageChunks.h"

namespace goby {
  class LIBGOBY_EXPORT AlignmentEntryIterator : public std::iterator<std::input_iterator_tag, AlignmentEntry> {
    // the file descriptor for the reads file
    int fd;

    // iterator over AlignmentCollections in the compact file
    MessageChunksIterator<AlignmentCollection> message_chunks_iterator;

    // the "end" iterator for the collections
    MessageChunksIterator<AlignmentCollection> message_chunks_iterator_end;

    // current chunk of alignement entries
    AlignmentCollection *alignment_collection;

    // index of the current alignment entry in the collection
    int current_alignment_entry_index;

  public:
    AlignmentEntryIterator(const int fd, std::streamoff off, std::ios_base::seekdir dir);
    AlignmentEntryIterator(const AlignmentEntryIterator& that);

    virtual ~AlignmentEntryIterator();

    // Prefix increment operator
    AlignmentEntryIterator& operator++();

    // Postfix increment operator
    AlignmentEntryIterator& operator++(int);

    bool operator==(const AlignmentEntryIterator& rhs) const;
    bool operator!=(const AlignmentEntryIterator& rhs) const;

    const AlignmentEntry& operator*();
    const AlignmentEntry* const operator->();
  };

  class LIBGOBY_EXPORT Alignment {
  protected:
    std::string basename;
    AlignmentHeader header;

    // A map of target identifiers (name to index)
    LIBGOBY_HASH_MAP<std::string, unsigned> target_identifiers;
    
    // A map of target identifiers (name to index)
    LIBGOBY_HASH_MAP<std::string, unsigned> query_identifiers;

    std::vector<unsigned> target_lengths;
    std::vector<unsigned> query_lengths;   

    LIBGOBY_HASH_MAP<std::string, std::string> stats;

  public:
    Alignment(const std::string& basename);
    virtual ~Alignment(void);

    static std::string getBasename(const char* filename);
    static std::string getBasename(const std::string& filename);

    inline const std::string& getBasename() const { return basename; };
    inline unsigned getNumberOfQueries() const { return header.number_of_queries(); };
    inline unsigned getNumberOfTargets() const { return header.number_of_targets(); };
    inline unsigned getNumberOfAlignedReads() const { return header.number_of_aligned_reads(); };
    inline bool hasConstantQueryLength() const { return header.has_constant_query_length(); };
    inline unsigned getConstantQuerylength() const { return header.constant_query_length(); };
    inline unsigned getSmallestSplitQueryIndex() const { return header.smallest_split_query_index(); };
    inline unsigned getLargestSplitQueryIndex() const { return  header.largest_split_query_index(); };
    
    inline bool isSorted() const { return header.sorted(); };
    inline bool isIndexed() const { return header.indexed(); };

    inline const std::vector<unsigned>& getTargetLengths() const { return target_lengths; };
    inline const std::vector<unsigned>& getQueryLengths() const { return query_lengths; };
    
    inline const LIBGOBY_HASH_MAP<std::string, unsigned>& getTargetIdentifiers() const { return target_identifiers; };
    inline const LIBGOBY_HASH_MAP<std::string, unsigned>& getQueryIdentifiers() const { return query_identifiers; };

    inline const LIBGOBY_HASH_MAP<std::string, std::string> getStatistics() const { return stats; };
  };

  class LIBGOBY_EXPORT AlignmentReader : public Alignment {
    // the file descriptor for the alignment entries file
    int entries_fd;

    // the "end" iterator for the alignment entries
    AlignmentEntryIterator *alignment_entry_iterator_end;

  public:
    AlignmentReader(const std::string& basename);
    AlignmentReader(const Alignment& alignment);
    ~AlignmentReader(void);

    AlignmentEntryIterator begin() const;
    AlignmentEntryIterator end() const;
  };

  class LIBGOBY_EXPORT AlignmentWriter : public Alignment {
    // the underlying message chunk writer
    MessageChunksWriter<AlignmentCollection> *entries_chunks_writer;

    // current chunk of alignment entries
    AlignmentCollection alignment_collection;

    // Vector containing indexes we've already seen for target_name_mapping
    std::vector<google::protobuf::uint32> target_name_indexes;

  public:
    AlignmentWriter(const std::string& basename, unsigned number_of_entries_per_chunk = GOBY_DEFAULT_NUMBER_OF_ENTRIES_PER_CHUNK);
    AlignmentWriter(const Alignment& alignment);
    ~AlignmentWriter(void);

    inline void setNumberOfQueries(unsigned number_of_queries) { header.set_number_of_queries(number_of_queries); };
    inline void setNumberOfTargets(unsigned number_of_targets) { header.set_number_of_targets(number_of_targets); };
    inline void setNumberOfAlignedReads(unsigned number_of_aligned_reads) { header.set_number_of_aligned_reads(number_of_aligned_reads); };
    inline void setConstantQuerylength(unsigned constant_query_length) { header.set_constant_query_length(constant_query_length); };
    inline void setSmallestSplitQueryIndex(unsigned smallest_split_query_index) { header.set_smallest_split_query_index(smallest_split_query_index); };
    inline void setLargestSplitQueryIndex(unsigned largest_split_query_index) { header.set_largest_split_query_index(largest_split_query_index); };
    inline void setAlignerName(const std::string& aligner_name) { header.set_aligner_name(aligner_name); };
    inline void setAlignerVersion(const std::string& aligner_version) { header.set_aligner_version(aligner_version); };

    inline void setSorted(bool sorted) { header.set_sorted(sorted); };
    inline void setIndexed(bool indexed) { header.set_indexed(indexed); };

    void setTargetLengths(const std::vector<unsigned>& target_lengths);
    void setTargetLengths(const unsigned* target_lengths);
    void addTargetLength(const google::protobuf::uint32 targetLength);
    // NOTE: Query Length setters are not provided - this information is no longer in the header

    void setQueryIndexOccurrencesStoredInEntries(bool value);
    void setQueryLengthsStoredInEntries(bool value);

    void addTargetIdentifier(const std::string& targetName, const google::protobuf::uint32 targetIndex);
    int isTargetIdentifierRegistered(const std::string& targetName);
    unsigned targetIndexForIdentifier(const std::string& targetName);
    unsigned addQueryIdentifier(const std::string& queryIdentifier);
    void addQueryIdentifierWithIndex(const std::string& queryIdentifier, unsigned newQueryIndex);

    // add the description/value to the overall alignment stats
    inline void addStatistic(const std::string& description, const std::string& value) { stats[description] = value; };
    inline void addStatistic(const std::string& description, const int value) { stats[description] = t_to_string(value); };
    inline void addStatistic(const std::string& description, const double value) { stats[description] = t_to_string(value); };

    // add/replace statistics for the alignment
    inline void addStatistics(LIBGOBY_HASH_MAP<std::string, std::string>& statistics) { stats.insert(statistics.begin(), statistics.end()); };

    // get an empty alignment entry to populate
    AlignmentEntry* appendEntry();
    void close();
  };
}

#endif // GOBY_ALIGNMENTS_H
