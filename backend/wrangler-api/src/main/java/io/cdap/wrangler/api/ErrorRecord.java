/*
 * Copyright © 2017-2019 Cask Data, Inc.
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

 package io.cdap.wrangler.api;

 import io.cdap.wrangler.api.annotations.Public;
 import java.util.Collections;
 import java.util.List;
 
 /**
  * This class is used to represent an error that occurred while processing a row of data.
  * It holds details like the actual row that caused the error, an error message, and an error code.
  */
 @Public
 public final class ErrorRecord extends ErrorRecordBase {
   // This is the row of data that caused the error.
   private final Row row;
 
   /**
    * Creates an ErrorRecord with full details including whether to show the error in the Wrangler UI.
    *
    * @param row The row of data that caused the error.
    * @param message A message describing what went wrong.
    * @param code An integer error code representing the type of error.
    * @param showInWrangler Whether or not to display this error in the Wrangler UI.
    */
   public ErrorRecord(Row row, String message, int code, boolean showInWrangler) {
     super(message, code, showInWrangler);  // Pass message, code, and UI flag to the base class.
     this.row = row;  // Store the actual row that caused the error.
   }
 
   /**
    * Creates an ErrorRecord with a message and code.
    * By default, this error will not be shown in the Wrangler UI.
    *
    * @param row The row of data that caused the error.
    * @param message A message describing what went wrong.
    * @param code An integer error code representing the type of error.
    */
   public ErrorRecord(Row row, String message, int code) {
     this(row, message, code, false);
   }
 
   /**
    * Gets the row that caused the error.
    *
    * @return The original Row object that had the issue.
    */
   public Row getRow() {
     return row;
   }
 }
 
