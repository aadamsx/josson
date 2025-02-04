/*
 * Copyright 2020-2022 Octomix Software Technology Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.octomix.josson;

/**
 * Stores an array filter details.
 */
class ArrayFilter {

    /**
     * Filter modes.
     */
    enum FilterMode {

        /**
         * Query first matching element.
         */
        FILTRATE_FIND_FIRST(' '),

        /**
         * Query all matching elements and output them inside an array node.
         */
        FILTRATE_COLLECT_ALL('*'),

        /**
         * Query all matching elements and divert each element to separate branch for upcoming manipulation.
         */
        FILTRATE_DIVERT_ALL('@');

        private final char symbol;

        FilterMode(final char symbol) {
            this.symbol = symbol;
        }

        static FilterMode fromSymbol(final char symbol) {
            for (FilterMode mode : values()) {
                if (mode.symbol == symbol) {
                    return mode;
                }
            }
            return null;
        }

        char getSymbol() {
            return symbol;
        }
    }

    private final String nodeName;

    private final String filter;

    private final FilterMode mode;

    ArrayFilter(final String nodeName, final String filter, final FilterMode mode) {
        this.nodeName = nodeName;
        this.filter = filter;
        this.mode = mode;
    }

    String getNodeName() {
        return nodeName;
    }

    String getFilter() {
        return filter;
    }

    FilterMode getMode() {
        return mode;
    }
}
