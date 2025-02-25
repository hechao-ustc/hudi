/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.client.transaction;

import org.apache.hudi.client.transaction.lock.LockManager;
import org.apache.hudi.common.table.timeline.HoodieInstant;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.config.HoodieWriteConfig;

import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * This class allows clients to start and end transactions. Anything done between a start and end transaction is
 * guaranteed to be atomic.
 */
public class TransactionManager implements Serializable {

  protected static final Logger LOG = LoggerFactory.getLogger(TransactionManager.class);
  protected final LockManager lockManager;
  protected final boolean needsLockGuard;
  protected Option<HoodieInstant> currentTxnOwnerInstant = Option.empty();
  private Option<HoodieInstant> lastCompletedTxnOwnerInstant = Option.empty();

  public TransactionManager(HoodieWriteConfig config, FileSystem fs) {
    this(new LockManager(config, fs), config.needsLockGuard());
  }

  protected TransactionManager(LockManager lockManager, boolean needsLockGuard) {
    this.lockManager = lockManager;
    this.needsLockGuard = needsLockGuard;
  }

  public void beginTransaction(Option<HoodieInstant> newTxnOwnerInstant,
                               Option<HoodieInstant> lastCompletedTxnOwnerInstant) {
    if (needsLockGuard) {
      LOG.info("Transaction starting for " + newTxnOwnerInstant
          + " with latest completed transaction instant " + lastCompletedTxnOwnerInstant);
      lockManager.lock();
      reset(currentTxnOwnerInstant, newTxnOwnerInstant, lastCompletedTxnOwnerInstant);
      LOG.info("Transaction started for " + newTxnOwnerInstant
          + " with latest completed transaction instant " + lastCompletedTxnOwnerInstant);
    }
  }

  public void endTransaction(Option<HoodieInstant> currentTxnOwnerInstant) {
    if (needsLockGuard) {
      LOG.info("Transaction ending with transaction owner " + currentTxnOwnerInstant);
      if (reset(currentTxnOwnerInstant, Option.empty(), Option.empty())) {
        lockManager.unlock();
        LOG.info("Transaction ended with transaction owner " + currentTxnOwnerInstant);
      }
    }
  }

  protected synchronized boolean reset(Option<HoodieInstant> callerInstant,
                                       Option<HoodieInstant> newTxnOwnerInstant,
                                       Option<HoodieInstant> lastCompletedTxnOwnerInstant) {
    if (!this.currentTxnOwnerInstant.isPresent() || this.currentTxnOwnerInstant.get().equals(callerInstant.get())) {
      this.currentTxnOwnerInstant = newTxnOwnerInstant;
      this.lastCompletedTxnOwnerInstant = lastCompletedTxnOwnerInstant;
      return true;
    }
    return false;
  }

  public void close() {
    if (needsLockGuard) {
      lockManager.close();
      LOG.info("Transaction manager closed");
    }
  }

  public LockManager getLockManager() {
    return lockManager;
  }

  public Option<HoodieInstant> getLastCompletedTransactionOwner() {
    return lastCompletedTxnOwnerInstant;
  }

  public Option<HoodieInstant> getCurrentTransactionOwner() {
    return currentTxnOwnerInstant;
  }

  public boolean isNeedsLockGuard() {
    return needsLockGuard;
  }
}
