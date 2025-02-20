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

#if HAVE_CONFIG_H
#include <config.h>
#endif

#include <fcntl.h>
#include <iostream>
#include <string>
#include <vector>

#ifdef _MSC_VER
#include <io.h>
#else
#include <unistd.h>
#endif

#include <google/protobuf/io/gzip_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>

#include "Alignments.pb.h"
#include "TooManyHits.h"
#include "hash.h"

#ifdef _MSC_VER
// Disable Microsoft deprecation warnings for POSIX functions called from this class (open, close)
#pragma warning(push)
#pragma warning(disable:4996)
#endif

using namespace std;

namespace goby {
  /*
   * TooManyHits
   */
  TooManyHits::TooManyHits(const string& basename) : basename(basename), pbTmh(AlignmentTooManyHits::default_instance()) {
  }
  
  vector<unsigned> TooManyHits::getQueryIndicies() const {
    // copy the keys from the queryIndex2NumHits and return as a new vector 
    vector<unsigned> queryIndicies(queryIndex2NumHits.size());
    unsigned i = 0;
    for (LIBGOBY_HASH_MAP<unsigned, unsigned>::const_iterator it = queryIndex2NumHits.begin(); it != queryIndex2NumHits.end(); it++) {
      queryIndicies[i++] = it->first;
    }

    return queryIndicies;
  }
  
  unsigned TooManyHits::getNumberOfHits(unsigned queryIndex) const {
    unsigned numberOfHits;
    LIBGOBY_HASH_MAP<unsigned, unsigned>::const_iterator it = queryIndex2NumHits.find(queryIndex);
    if (it != queryIndex2NumHits.end()) {
      numberOfHits = it->second;
    } else {
      numberOfHits = -1;
    }
    return numberOfHits;
  }

  unsigned TooManyHits::getLengthOfMatch(unsigned queryIndex) const {
    unsigned lengthOfMatch;
    LIBGOBY_HASH_MAP<unsigned, unsigned>::const_iterator it = queryIndex2Depth.find(queryIndex);
    if (it != queryIndex2NumHits.end()) {
      lengthOfMatch = it->second;
    } else {
      lengthOfMatch = -1;
    }
    return lengthOfMatch;
  }
  
  // Returns true if the query was considered ambiguous by the alignment tool
  bool TooManyHits::isQueryAmbiguous(unsigned queryIndex) const {
    return queryIndex2NumHits.find(queryIndex) != queryIndex2NumHits.end();
  }

  bool TooManyHits::isQueryAmbiguous(unsigned queryIndex, unsigned k) const {
    LIBGOBY_HASH_MAP<unsigned, unsigned>::const_iterator it = queryIndex2Depth.find(queryIndex);
    if (it == queryIndex2NumHits.end()) {
      return false;
    } if (k >= getAlignerThreshold()) {
      // since k is larger than the aligner threshold, we have to assume the query is
      // ambiguous at k, this is the safe choice.
      return true;
    } else {
      return it->second >= k;
    }
  }

  bool TooManyHits::isQueryAmbiguous(unsigned queryIndex, unsigned k, unsigned matchLength) const {
    if (matchLength < getLengthOfMatch(queryIndex)) {
      return true;
    } else {
      return isQueryAmbiguous(queryIndex, k);
    }
  }

  TooManyHits::~TooManyHits(void) {
  }

  /*
   * TooManyHitsReader
   */
  TooManyHitsReader::TooManyHitsReader(const string& basename) : TooManyHits(basename) {
    // open the "tmh" file
    const string tmhFilename = basename + ".tmh";
    const int fd = ::open(tmhFilename.c_str(), O_RDONLY | O_BINARY);
    if (fd < 0) {
      pbTmh = AlignmentTooManyHits::default_instance();
      cerr << "Failed to open too many hits file: " << tmhFilename << endl;
    } else {
      // uncompress file into memory so that it can be parsed
      google::protobuf::io::FileInputStream tmh_file_stream(fd);
      google::protobuf::io::GzipInputStream gzip_tmh_stream(&tmh_file_stream);

      if (!pbTmh.ParseFromZeroCopyStream(&gzip_tmh_stream)) {
        cerr << "Failed to parse too many hits file: " << tmhFilename << endl;
      }

      // close the streams and files
      tmh_file_stream.Close();  // this call closes the file descriptor as well
    }

    // populate the query index to number of hits and depth maps
    google::protobuf::RepeatedPtrField<const AmbiguousLocation>::const_iterator hitsIterator;
    for (hitsIterator = pbTmh.hits().begin(); hitsIterator != pbTmh.hits().end(); hitsIterator++) {
      unsigned queryIndex = hitsIterator->query_index();
      queryIndex2NumHits[queryIndex] = hitsIterator->at_least_number_of_hits();
      if (hitsIterator->has_length_of_match()) {
        queryIndex2Depth[queryIndex] = hitsIterator->length_of_match();
      }
    }
  }

  TooManyHitsReader::~TooManyHitsReader(void) {
  }

  /*
   * TooManyHitsWriter
   */
  TooManyHitsWriter::TooManyHitsWriter(const string& basename) : TooManyHits(basename), written(false) {
  }

  TooManyHitsWriter::TooManyHitsWriter(const string& basename, unsigned threshold) : TooManyHits(basename), written(false) {
    pbTmh.set_aligner_threshold(threshold);
  }

  TooManyHitsWriter::~TooManyHitsWriter(void) {
    if (!written) {
      write();
    }
  }

  void TooManyHitsWriter::append(unsigned queryIndex, unsigned howManyHits, unsigned lengthOfMatch) {
    if (howManyHits > pbTmh.aligner_threshold()) {
      AmbiguousLocation *ambiguousLocation = pbTmh.add_hits();
      ambiguousLocation->set_query_index(queryIndex);
      ambiguousLocation->set_at_least_number_of_hits(howManyHits);
      ambiguousLocation->set_length_of_match(lengthOfMatch);
      written = false;
    }
  }

  void TooManyHitsWriter::write() {
    // Write to the "tmh" file
    const string tmhFilename = basename + ".tmh";
    cout << "Writing file: " << tmhFilename << endl;
    const int fd = ::open(tmhFilename.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_BINARY, 0644);

    // set up a gzip output stream to compress the data
    google::protobuf::io::FileOutputStream tmhFileStream(fd);
    google::protobuf::io::GzipOutputStream gzipTmhStream(&tmhFileStream);

    if (!pbTmh.SerializeToZeroCopyStream(&gzipTmhStream)) {
      cerr << "Failed to write too many hits file: " << tmhFilename << endl;
    }

    // close the streams and files
    gzipTmhStream.Close();
    tmhFileStream.Close();    // this call closes the file descriptor as well

    written = true;
  }
}

#ifdef _MSC_VER
#pragma warning(pop)  // Restores the warning state.
#endif
