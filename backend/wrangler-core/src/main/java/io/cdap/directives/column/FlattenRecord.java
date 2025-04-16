/*
 * Copyright © 2021 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.directives.column;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.*;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.parser.ColumnNameList;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A directive that expands a Row (record-type) column into multiple flat columns.
 *
 * Example:
 * Input:
 *   Column "A" → {B: 1, C: 2}
 * Directive:
 *   flatten-record :A
 * Output:
 *   A_B → 1, A_C → 2
 */
@Plugin(type = Directive.TYPE)
@Name(FlattenRecord.NAME)
@Categories(categories = {"column"})
@Description("Flattens a record (Row type) column into multiple top-level columns.")
public class FlattenRecord implements Directive {

  public static final String NAME = "flatten-record";
  private String[] recordColumns;

  @Override
  public UsageDefinition define() {
    return UsageDefinition.builder(NAME)
      .define("columns", TokenType.COLUMN_NAME_LIST)
      .build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    List<String> columnList = ((ColumnNameList) args.value("columns")).value();
    recordColumns = columnList.toArray(new String[0]);
  }

  @Override
  public void destroy() {
    // No cleanup necessary
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    List<Row> processedRows = new ArrayList<>();

    for (Row row : rows) {
      for (String columnName : recordColumns) {
        int columnIndex = row.find(columnName);

        if (columnIndex == -1) {
          continue; // Column not found in this row
        }

        Object value = row.getValue(columnIndex);

        if (value == null) {
          continue; // Column exists but is null
        }

        if (!(value instanceof Row)) {
          throw new DirectiveExecutionException(
            NAME,
            String.format("Column '%s' has unsupported type '%s'. Expected type: 'Record'.",
                          columnName, value.getClass().getSimpleName()));
        }

        Row nestedRow = (Row) value;

        for (Pair<String, Object> field : nestedRow.getFields()) {
          String flatColumnName = String.format("%s_%s", columnName, field.getFirst());
          row.addOrSet(flatColumnName, field.getSecond());
        }
      }

      processedRows.add(row);
    }

    return processedRows;
  }
}
