/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.fielddata.plain;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.index.fielddata.AtomicFieldData;
import org.elasticsearch.index.fielddata.BytesValues;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.index.fielddata.ScriptDocValues.Strings;

import java.io.IOException;

/** {@link AtomicFieldData} impl on top of Lucene's binary doc values. */
public class BinaryDVAtomicFieldData implements AtomicFieldData<ScriptDocValues.Strings> {

    private final AtomicReader reader;
    private final String field;

    public BinaryDVAtomicFieldData(AtomicReader reader, String field) {
        this.reader = reader;
        this.field = field;
    }

    @Override
    public boolean isMultiValued() {
        return false;
    }

    @Override
    public boolean isValuesOrdered() {
        return true; // single-valued
    }

    @Override
    public int getNumDocs() {
        return reader.maxDoc();
    }

    @Override
    public long getNumberUniqueValues() {
        // probably not accurate, but a good upper limit
        return reader.maxDoc();
    }

    @Override
    public long getMemorySizeInBytes() {
        // TODO: Lucene doesn't expose it right now
        return -1;
    }

    @Override
    public BytesValues getBytesValues() {
        final BinaryDocValues values;
        final Bits docsWithField;
        try {
            final BinaryDocValues v = reader.getBinaryDocValues(field);
            if (v == null) {
                // segment has no value
                values = BinaryDocValues.EMPTY;
                docsWithField = new Bits.MatchNoBits(reader.maxDoc());
            } else {
                values = v;
                final Bits b = reader.getDocsWithField(field);
                docsWithField = b == null ? new Bits.MatchAllBits(reader.maxDoc()) : b;
            }
        } catch (IOException e) {
            throw new ElasticSearchIllegalStateException("Cannot load doc values", e);
        }

        return new BytesValues(false) {

            final BytesValues.Iter.Single iter = new BytesValues.Iter.Single();
            final BytesRef spare = new BytesRef();

            @Override
            public boolean hasValue(int docId) {
                return docsWithField.get(docId);
            }

            @Override
            public BytesRef getValueScratch(int docId, BytesRef ret) {
                values.get(docId, ret);
                return ret;
            }

            @Override
            public Iter getIter(int docId) {
                if (!docsWithField.get(docId)) {
                    return BytesValues.Iter.Empty.INSTANCE;
                }
                values.get(docId, spare);
                return iter.reset(spare, -1L);
            }

        };
    }

    @Override
    public BytesValues getHashedBytesValues() {
        // if you want hashes to be cached, you should rather store them on disk alongside the values rather than loading them into memory
        // here - not supported for now, and probably not useful since this field data only applies to _id and _uid?
        return getBytesValues();
    }

    @Override
    public Strings getScriptValues() {
        return new ScriptDocValues.Strings(getBytesValues());
    }

    @Override
    public void close() {
        // no-op
    }

}
