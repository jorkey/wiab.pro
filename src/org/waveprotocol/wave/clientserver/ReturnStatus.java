/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.clientserver;

/**
 * Carries information about an error.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ReturnStatus {
  private final ReturnCode code;
  private String message;
  private Throwable exception;

  public ReturnStatus(ReturnCode errorCode) {
    this.code = errorCode;
  }

  public ReturnStatus(ReturnCode code, String message) {
    this.code = code;
    this.message = message;
  }

  public ReturnStatus(ReturnCode code, String message, Throwable exception) {
    this.code = code;
    this.message = message;
    this.exception = exception;
  }

  public ReturnCode getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public Throwable getException() {
    return exception;
  }

  @Override
  public String toString() {
    return code + " : " + message;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ReturnStatus other = (ReturnStatus) obj;
    if (this.code != other.code) {
      return false;
    }
    if (this.message != null) {
      if (!this.message.equals(other.message)) {
        return false;
      }
    } else if (other.message != null) {
      return false;
    }
    if (this.exception != null) {
      if (!this.exception.equals(other.exception)) {
        return false;
      }
    } else if (other.exception != null) {
      return false;
    }
    return true;
  } 
}
