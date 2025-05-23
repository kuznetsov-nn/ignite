/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.internal.processors.query.calcite.exec;

import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.cache.query.index.sorted.IndexRow;
import org.apache.ignite.internal.cache.query.index.sorted.inline.IndexQueryContext;
import org.apache.ignite.internal.cache.query.index.sorted.inline.InlineIndex;
import org.apache.ignite.internal.cache.query.index.sorted.inline.InlineIndexImpl;
import org.apache.ignite.internal.processors.query.calcite.schema.CacheTableDescriptor;
import org.apache.ignite.internal.util.lang.GridCursor;
import org.jetbrains.annotations.Nullable;

/**
 * Takes only first or last index value excluding nulls.
 */
public class IndexFirstLastScan<Row> extends IndexScan<Row> {
    /** First or last field value. */
    private final boolean first;

    /**
     * @param first {@code True} to take first index value. {@code False} to take last value.
     * @param ectx Execution context.
     * @param desc Table descriptor.
     * @param idx Physical index.
     * @param idxFieldMapping Mapping from index keys to row fields.
     * @param parts Mapping from index keys to row fields.
     * @param requiredColumns Required columns.
     */
    public IndexFirstLastScan(
        boolean first,
        ExecutionContext<Row> ectx,
        CacheTableDescriptor desc,
        InlineIndexImpl idx,
        ImmutableIntList idxFieldMapping,
        int[] parts,
        @Nullable ImmutableBitSet requiredColumns
    ) {
        super(ectx, desc, idx, idxFieldMapping, parts, null, requiredColumns);

        this.first = first;
    }

    /** {@inheritDoc} */
    @Override protected TreeIndex<IndexRow> treeIndex() {
        return new FirstLastIndexWrapper(idx, indexQueryContext(), first);
    }

    /** {@inheritDoc} */
    @Override protected IndexQueryContext indexQueryContext() {
        IndexQueryContext res = super.indexQueryContext();

        boolean checkExpired = !cctx.config().isEagerTtl();

        return new IndexQueryContext(
            res.cacheFilter(),
            createNotNullRowFilter(idx, checkExpired)
        );
    }

    /** */
    private static class FirstLastIndexWrapper extends IndexScan.TreeIndexWrapper {
        /** */
        private final boolean first;

        /**
         * @param idx   Index
         * @param qctx  Query context.
         * @param first {@code True} to take first index value. {@code False} to take last value.
         */
        protected FirstLastIndexWrapper(InlineIndex idx, IndexQueryContext qctx, boolean first) {
            super(idx, qctx);

            this.first = first;
        }

        /** {@inheritDoc} */
        @Override public GridCursor<IndexRow> find(
            IndexRow lower,
            IndexRow upper,
            boolean lowerInclude,
            boolean upperInclude
        ) {
            assert lower == null && upper == null;
            assert lowerInclude && upperInclude;

            try {
                return idx.findFirstOrLast(qctx, first);
            }
            catch (IgniteCheckedException e) {
                throw new IgniteException("Failed to take " + (first ? "first" : "last") + " not-null index value.", e);
            }
        }
    }
}
