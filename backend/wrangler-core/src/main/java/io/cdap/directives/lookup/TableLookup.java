/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */
package io.cdap.directives.lookup;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.common.Bytes;
import io.cdap.cdap.api.data.DatasetInstantiationException;
import io.cdap.cdap.etl.api.Lookup;
import io.cdap.cdap.etl.api.lookup.TableLookup;
import io.cdap.wrangler.api.*;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Plugin(type = Directive.TYPE)
@Name(TableLookup.NAME)
@Categories(categories = {"lookup"})
@Description("Uses the given column as a key to perform a lookup into the specified table.")
public class TableLookup implements Directive, Lineage {

  public static final String NAME = "table-lookup";

  private String column;
  private String table;
  private boolean initialized;
  private TableLookup tableLookup;

  @Override
  public UsageDefinition define() {
    return UsageDefinition.builder(NAME)
      .define("column", TokenType.COLUMN_NAME)
      .define("table", TokenType.TEXT)
      .build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    column = ((ColumnName) args.value("column")).value();
    table = ((Text) args.value("table")).value();
    initialized = false;
  }

  @Override
  public void destroy() {
    // no cleanup needed
  }

  private void ensureInitialized(ExecutorContext context) throws DirectiveExecutionException {
    if (initialized) {
      return;
    }

    Lookup lookup;
    try {
      lookup = context.provide(table, Collections.emptyMap());
    } catch (DatasetInstantiationException e) {
      throw new DirectiveExecutionException(NAME, String.format(
        "Dataset '%s' could not be instantiated. Ensure a Table dataset named '%s' exists.", table, table), e);
    }

    if (!(lookup instanceof TableLookup)) {
      throw new DirectiveExecutionException(NAME,
        "Lookup is not being performed on a Table. Only Table datasets are supported.");
    }

    tableLookup = (TableLookup) lookup;
    initialized = true;
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    ensureInitialized(context);

    for (Row row : rows) {
      int idx = row.find(column);
      if (idx == -1) {
        continue;
      }

      Object value = row.getValue(idx);
      if (value == null) {
        throw new DirectiveExecutionException(NAME,
          String.format("Column '%s' contains a null value. A non-null String is required.", column));
      }

      if (!(value instanceof String)) {
        throw new DirectiveExecutionException(NAME,
          String.format("Column '%s' has type '%s'. Expected type: String.",
            column, value.getClass().getSimpleName()));
      }

      io.cdap.cdap.api.dataset.table.Row resultRow = tableLookup.lookup((String) value);
      for (Map.Entry<byte[], byte[]> entry : resultRow.getColumns().entrySet()) {
        String key = column + "_" + Bytes.toString(entry.getKey());
        String val = Bytes.toString(entry.getValue());
        row.add(key, val);
      }
    }

    return rows;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Looked up rows from table '%s' using key from column '%s'", table, column)
      .all(Many.of(column))
      .build();
  }
}
