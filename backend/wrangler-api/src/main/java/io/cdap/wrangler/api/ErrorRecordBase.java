/*
 * Copyright © 2019 Cask Data, Inc.
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

import java.util.List;

/**
 * This is the base class for error records.
 * It contains the essential information about the error.
 */
public class ErrorRecordBase {

  // Describes what went wrong with the row.
  protected final String message;

  // A numeric identifier for the type of error.
  protected final int code;

  // Tells whether this error should be displayed in the Wrangler UI.
  protected final boolean showInWrangler;

  /**
   * Constructs an ErrorRecordBase with a message, an error code, 
   * and a flag indicating if it should be shown in the UI.
   *
   * @param message A description of the error.
   * @param code A number representing the error type.
   * @param showInWrangler Whether or not to show this error in the Wrangler interface.
   */
  public ErrorRecordBase(String message, int code, boolean showInWrangler) {
    this.message = message;
    this.code = code;
    this.showInWrangler = showInWrangler;
  }

  /**
   * Gets the description of the error that occurred for a row.
   *
   * @return The error message.
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the numeric error code that represents the type of issue.
   *
   * @return The error code.
   */
  public int getCode() {
    return code;
  }

  /**
   * Checks whether this error should be shown in the Wrangler UI.
   *
   * @return true if it should be shown, false otherwise.
   */
  public boolean isShownInWrangler() {
    return showInWrangler;
  }
}
