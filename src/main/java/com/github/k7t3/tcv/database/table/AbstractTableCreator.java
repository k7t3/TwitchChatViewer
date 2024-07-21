/*
 * Copyright 2024 k7t3
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

package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.TableCreator;

public abstract class AbstractTableCreator implements TableCreator {

    protected final DatabaseVersion current;
    protected final DatabaseVersion preferred;

    public AbstractTableCreator(DatabaseVersion current, DatabaseVersion preferred) {
        this.current = current;
        this.preferred = preferred;
    }

    protected abstract void modifyTables(DBConnector connector);

    @Override
    public void create(DBConnector connector) {
        modifyTables(connector);
    }

}
