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
package io.cdap.directives.parser;

import com.google.common.io.Closeables;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.functions.Types;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ErrorRowException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Optional;
import io.cdap.wrangler.api.Pair;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.*;

@Plugin(type = Directive.TYPE)
@Name("parse-as-excel")
@Categories(categories = { "parser", "excel"})
@Description("Parses column as Excel file.")
public class ParseExcel implements Directive, Lineage {
  public static final String NAME = "parse-as-excel";
  private static final Logger LOG = LoggerFactory.getLogger(ParseExcel.class);
  private String column;
  private String sheet;
  private boolean firstRowAsHeader;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("column", TokenType.COLUMN_NAME);
    builder.define("sheet", TokenType.TEXT, Optional.TRUE);
    builder.define("first-row-as-header", TokenType.BOOLEAN, Optional.TRUE);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    this.column = ((ColumnName) args.value("column")).value();
    this.sheet = args.contains("sheet") ? ((Text) args.value("sheet")).value() : "0";
    this.firstRowAsHeader = args.contains("first-row-as-header") && (Boolean) args.value("first-row-as-header").value();
  }

  @Override
  public void destroy() {
    // No cleanup necessary
  }

  @Override
  public List<Row> execute(List<Row> records, final ExecutorContext context)
      throws DirectiveExecutionException, ErrorRowException {
    List<Row> results = new ArrayList<>();
    ByteArrayInputStream input = null;
    DataFormatter formatter = new DataFormatter();

    try {
      for (Row record : records) {
        int idx = record.find(column);
        if (idx != -1) {
          Object object = record.getValue(idx);
          byte[] bytes = extractBytes(object);
          if (bytes != null) {
            input = new ByteArrayInputStream(bytes);
            try (XSSFWorkbook book = new XSSFWorkbook(input)) {
              XSSFSheet excelsheet = getExcelSheet(book);
              processExcelSheet(excelsheet, record, results, formatter);
            }
          }
        }
      }
    } catch (Exception e) {
      throw new ErrorRowException(NAME, e.getMessage(), 1);
    } finally {
      Closeables.closeQuietly(input);
    }
    return results;
  }

  private byte[] extractBytes(Object object) throws DirectiveExecutionException {
    if (object instanceof byte[]) {
      return (byte[]) object;
    } else if (object instanceof ByteBuffer) {
      ByteBuffer buffer = (ByteBuffer) object;
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    } else {
      throw new DirectiveExecutionException(NAME, String.format("Column '%s' should be of type 'byte array' or 'ByteBuffer'.", column));
    }
  }

  private XSSFSheet getExcelSheet(XSSFWorkbook book) throws DirectiveExecutionException {
    XSSFSheet excelsheet;
    if (Types.isInteger(sheet)) {
      excelsheet = book.getSheetAt(Integer.parseInt(sheet));
    } else {
      excelsheet = book.getSheet(sheet);
    }

    if (excelsheet == null) {
      throw new DirectiveExecutionException(NAME, String.format("Sheet '%s' does not exist.", sheet));
    }
    return excelsheet;
  }

  private void processExcelSheet(XSSFSheet excelsheet, Row record, List<Row> results, DataFormatter formatter)
      throws DirectiveExecutionException {
    Map<Integer, String> columnNames = new TreeMap<>();
    Iterator<org.apache.poi.ss.usermodel.Row> it = excelsheet.iterator();
    int rows = 0;

    while (it.hasNext()) {
      org.apache.poi.ss.usermodel.Row row = it.next();
      if (checkIfRowIsEmpty(row)) continue;

      Row newRow = new Row();
      newRow.add("fwd", rows);

      processCellsInRow(row, columnNames, rows, newRow, formatter);

      if (rows == 0 && firstRowAsHeader) {
        rows++;
        continue;
      }

      // Add old columns to the new row
      for (Pair<String, Object> field : record.getFields()) {
        String colName = field.getFirst();
        if (newRow.getValue(colName) == null && !colName.equals(column)) {
          newRow.add(colName, field.getSecond());
        }
      }
      results.add(newRow);
      rows++;
    }

    if (firstRowAsHeader) {
      rows--;
    }

    addRowIndexes(results, rows);
  }

  private void processCellsInRow(org.apache.poi.ss.usermodel.Row row, Map<Integer, String> columnNames, int rows, Row newRow, DataFormatter formatter) {
    Iterator<Cell> cellIterator = row.cellIterator();
    while (cellIterator.hasNext()) {
      Cell cell = cellIterator.next();
      String name = columnName(cell.getAddress().getColumn());

      if (firstRowAsHeader && rows > 0) {
        String value = columnNames.get(cell.getAddress().getColumn());
        if (value != null) {
          name = value;
        }
      }

      String value = getCellValue(cell, formatter);
      newRow.add(name, value);

      if (rows == 0 && firstRowAsHeader) {
        columnNames.put(cell.getAddress().getColumn(), value);
      }
    }
  }

  private String getCellValue(Cell cell, DataFormatter formatter) {
    switch (cell.getCellTypeEnum()) {
      case STRING: return cell.getStringCellValue();
      case NUMERIC: return HSSFDateUtil.isCellDateFormatted(cell) ? formatter.formatCellValue(cell) : String.valueOf(cell.getNumericCellValue());
      case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
      default: return "";
    }
  }

  private void addRowIndexes(List<Row> results, int rows) {
    for (int i = rows - 1; i >= 0; --i) {
      results.get(rows - i - 1).addOrSetAtIndex(1, "bkd", i); // fwd - 0, bkd - 1.
    }
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Parsed column '%s' as an Excel file", column)
      .all(Many.columns(column))
      .build();
  }

  private boolean checkIfRowIsEmpty(org.apache.poi.ss.usermodel.Row row) {
    if (row == null || row.getLastCellNum() <= 0) {
      return true;
    }
    for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
      Cell cell = row.getCell(cellNum);
      if (cell != null && cell.getCellTypeEnum() != CellType.BLANK && StringUtils.isNotBlank(cell.toString())) {
        return false;
      }
    }
    return true;
  }

  private String columnName(int number) {
    final StringBuilder sb = new StringBuilder();
    int num = number;
    while (num >= 0) {
      int numChar = (num % 26) + 65;
      sb.append((char) numChar);
      num = (num / 26) - 1;
    }
    return sb.reverse().toString();
  }
}
