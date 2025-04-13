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
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnNameList;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A directive that creates a new column of type Record by combining selected columns into a single structured field.
 */
@Plugin(type = Directive.TYPE)
@Name(CreateRecord.NAME)
@Categories(categories = {"column"})
@Description("Creates a column of type Record by combining values from specified columns.")
public class CreateRecord implements Directive, Lineage {

  public static final String NAME = "create-record";

  private String recordColumnName;
  private List<String> sourceColumns;

  @Override
  public UsageDefinition define() {
    return UsageDefinition.builder(NAME)
      .define("target_column", TokenType.COLUMN_NAME)
      .define("columns", TokenType.COLUMN_NAME_LIST)
      .build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    recordColumnName = args.value("target_column").value().toString();
    sourceColumns = ((ColumnNameList) args.value("columns")).value();
  }

  @Override
  public void destroy() {
    // No cleanup necessary
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    List<Row> outputRows = new ArrayList<>();

    for (Row originalRow : rows) {
      // Create a new row object to store the record from selected columns
      Row record = new Row();
      for (String column : sourceColumns) {
        Object value = originalRow.getValue(column);
        if (value != null) {
          record.addOrSet(column, value);
        }
      }

      // Add the record as a new column in a copy of the original row
      Row updatedRow = new Row(originalRow);
      updatedRow.addOrSet(recordColumnName, record);
      outputRows.add(updatedRow);
    }

    return outputRows;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Created column '%s' using values from columns %s", recordColumnName, sourceColumns)
      .relation(Many.columns(sourceColumns.toArray(new String[0])), recordColumnName)
      .build();
  }
}
