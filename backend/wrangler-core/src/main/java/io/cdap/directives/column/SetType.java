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
package io.cdap.directives.column;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.data.schema.Schema.LogicalType;
import io.cdap.wrangler.api.*;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.*;
import io.cdap.wrangler.utils.ColumnConverter;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Plugin(type = "directives")
@Name(SetType.NAME)
@Categories(categories = {"column"})
@Description("Converting data type of a column. Optional arguments scale, precision and "
    + "rounding-mode are used only when type is decimal.")
public final class SetType implements Directive, Lineage {
  public static final String NAME = "set-type";

  private String col;
  private String type;
  private Integer scale;
  private Integer precision;
  private RoundingMode roundingMode;

  @Override
  public UsageDefinition define() {
    return UsageDefinition.builder(NAME)
      .define("column", TokenType.COLUMN_NAME)
      .define("type", TokenType.IDENTIFIER)
      .define("scale", TokenType.NUMERIC, Optional.TRUE)
      .define("rounding-mode", TokenType.TEXT, Optional.TRUE)
      .define("precision", TokenType.PROPERTIES, "prop:{precision=<precision>}", Optional.TRUE)
      .build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    col = ((ColumnName) args.value("column")).value();
    type = ((Identifier) args.value("type")).value();

    if ("decimal".equalsIgnoreCase(type)) {
      if (args.contains("precision")) {
        @SuppressWarnings("unchecked")
        HashMap<String, Numeric> precisionMap = (HashMap<String, Numeric>) args.value("precision").value();
        precision = precisionMap.get("precision").value().intValue();
        if (precision < 1) {
          throw new DirectiveParseException("precision cannot be less than 1");
        }
      }

      scale = args.contains("scale") ? ((Numeric) args.value("scale")).value().intValue() : null;

      if ((scale == null && precision == null) && args.contains("rounding-mode")) {
        throw new DirectiveParseException("'rounding-mode' can only be specified when a 'scale' or 'precision' is set");
      }

      if (args.contains("rounding-mode")) {
        String mode = ((Text) args.value("rounding-mode")).value();
        try {
          roundingMode = RoundingMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
          throw new DirectiveParseException(String.format(
            "Specified rounding-mode '%s' is not a valid Java rounding mode", mode), e);
        }
      } else {
        roundingMode = (scale == null && precision == null)
          ? RoundingMode.UNNECESSARY
          : RoundingMode.HALF_EVEN;
      }
    }
  }

  @Override
  public void destroy() {
    // No resources to clean up
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    for (Row row : rows) {
      ColumnConverter.convertType(NAME, row, col, type, scale, precision, roundingMode);
    }
    return rows;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Changed the column '%s' to type '%s'", col, type)
      .relation(col, col)
      .build();
  }

  @Override
  public Schema getOutputSchema(SchemaResolutionContext context) {
    Schema inputSchema = context.getInputSchema();

    return Schema.recordOf(
      "outputSchema",
      inputSchema.getFields().stream()
        .map(field -> {
          try {
            if (!field.getName().equals(col)) {
              return field;
            }

            Integer outScale = scale;
            Integer outPrecision = precision;
            Schema fieldSchema = field.getSchema().getNonNullable();
            Pair<Integer, Integer> current = getPrecisionAndScale(fieldSchema);
            Integer inputPrecision = current.getFirst();
            Integer inputScale = current.getSecond();

            if (scale == null && precision == null) {
              outPrecision = inputPrecision;
              outScale = inputScale;
            } else if (scale == null && inputScale != null) {
              if (precision - inputScale < 1) {
                throw new DirectiveParseException(String.format(
                  "Cannot set scale as '%s' and precision as '%s' when precision - scale is less than 1",
                  inputScale, precision));
              }
              outPrecision = precision;
              outScale = inputScale;
            } else if (precision == null && inputPrecision != null) {
              if (inputPrecision - scale < 1) {
                throw new DirectiveParseException(String.format(
                  "Cannot set scale as '%s' and precision as '%s' when precision - scale is less than 1",
                  scale, inputPrecision));
              }
              outScale = scale;
              outPrecision = inputPrecision;
            }

            Schema newSchema = ColumnConverter.getSchemaForType(type, outScale, outPrecision);
            return Schema.Field.of(col, newSchema);

          } catch (DirectiveParseException e) {
            throw new RuntimeException(e);
          }
        })
        .collect(Collectors.toList())
    );
  }

  public static Pair<Integer, Integer> getPrecisionAndScale(Schema fieldSchema) {
    Integer precision = null;
    Integer scale = null;
    if (fieldSchema.getLogicalType() == LogicalType.DECIMAL) {
      precision = fieldSchema.getPrecision();
      scale = fieldSchema.getScale();
    }
    return new Pair<>(precision, scale);
  }
}
