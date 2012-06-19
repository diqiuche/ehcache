/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.transaction;

import net.sf.ehcache.Element;
import net.sf.ehcache.store.Store;
import net.sf.ehcache.transaction.SoftLock;
import net.sf.ehcache.transaction.SoftLockFactory;
import net.sf.ehcache.transaction.SoftLockID;
import net.sf.ehcache.transaction.TransactionID;
import net.sf.ehcache.transaction.local.LocalTransactionContext;
import org.terracotta.collections.ConcurrentDistributedMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Ludovic Orban
 */
public class ReadCommittedClusteredSoftLockFactory implements SoftLockFactory {

  private final static Object MARKER = new Object();

  private final String cacheName;

  // actually all we need would be a ConcurrentSet...
  private final ConcurrentMap<ReadCommittedClusteredSoftLock, Object> newKeyLocks = new ConcurrentDistributedMap<ReadCommittedClusteredSoftLock, Object>();

  // locks must be inserted in a clustered collection b/c they must be managed by the L1 before they are returned
  private final ConcurrentMap<ClusteredSoftLockID, ReadCommittedClusteredSoftLock> allLocks = new ConcurrentDistributedMap<ClusteredSoftLockID, ReadCommittedClusteredSoftLock>();


  public ReadCommittedClusteredSoftLockFactory(String cacheName) {
    this.cacheName = cacheName;
  }

  public SoftLock createSoftLock(SoftLockID softLockId) {
    ReadCommittedClusteredSoftLock softLock = new ReadCommittedClusteredSoftLock(this, softLockId.getTransactionID(), softLockId.getKey());

    allLocks.put(new ClusteredSoftLockID(softLockId), softLock);

    if (softLockId.getOldElement() == null) {
      newKeyLocks.put(softLock, MARKER);
    }
    return softLock;
  }

  public SoftLockID createSoftLockID(TransactionID transactionID, Object key, Element newElement, Element oldElement, boolean pinned) {
    if (newElement != null && newElement.getObjectValue() instanceof SoftLockID) { throw new AssertionError("newElement must not contain a soft lock ID"); }
    if (oldElement != null && oldElement.getObjectValue() instanceof SoftLockID) { throw new AssertionError("oldElement must not contain a soft lock ID"); }

    return new SoftLockID(transactionID, key, newElement, oldElement, pinned);
  }

  public SoftLock findSoftLockById(SoftLockID softLockId) {
    return allLocks.get(new ClusteredSoftLockID(softLockId));
  }

  ReadCommittedClusteredSoftLock getLock(TransactionID transactionId, Object key) {
    for (Map.Entry<ClusteredSoftLockID, ReadCommittedClusteredSoftLock> entry : allLocks.entrySet()) {
      ReadCommittedClusteredSoftLock readCommittedSoftLock = allLocks.get(entry.getKey()); //workaround for DEV-5390
      if (readCommittedSoftLock.getTransactionID().equals(transactionId) &&
          readCommittedSoftLock.getKey().equals(key)) {
        return readCommittedSoftLock;
      }
    }
    return null;
  }

  public Set<Object> getKeysInvisibleInContext(LocalTransactionContext currentTransactionContext, Store underlyingStore) {
    Set<Object> invisibleKeys = new HashSet<Object>();

    // all new keys added into the store are invisible
    invisibleKeys.addAll(getNewKeys());

    List<SoftLock> currentTransactionContextSoftLocks = currentTransactionContext.getSoftLocksForCache(cacheName);
    for (SoftLock softLock : currentTransactionContextSoftLocks) {
      SoftLockID softLockId = (SoftLockID)underlyingStore.getQuiet(softLock.getKey()).getObjectValue();

      if (softLock.getElement(currentTransactionContext.getTransactionId(), softLockId) == null) {
        // if the soft lock's element is null in the current transaction then the key is invisible
        invisibleKeys.add(softLock.getKey());
      } else {
        // if the soft lock's element is not null in the current transaction then the key is visible
        invisibleKeys.remove(softLock.getKey());
      }
    }

    return invisibleKeys;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized Set<TransactionID> collectExpiredTransactionIDs() {
    Set<TransactionID> result = new HashSet<TransactionID>();

    for (Map.Entry<ClusteredSoftLockID, ReadCommittedClusteredSoftLock> entry : allLocks.entrySet()) {
      ReadCommittedClusteredSoftLock softLock = allLocks.get(entry.getKey()); //workaround for DEV-5390
      if (softLock.isExpired()) {
        result.add(softLock.getTransactionID());
      }
    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  public Set<SoftLock> collectAllSoftLocksForTransactionID(TransactionID transactionID) {
    Set<SoftLock> result = new HashSet<SoftLock>();

    for (Map.Entry<ClusteredSoftLockID, ReadCommittedClusteredSoftLock> entry : allLocks.entrySet()) {
      ReadCommittedClusteredSoftLock softLock = allLocks.get(entry.getKey()); //workaround for DEV-5390
      if (softLock.getTransactionID().equals(transactionID)) {
        result.add(softLock);
      }
    }

    return result;
  }

  void clearSoftLock(ReadCommittedClusteredSoftLock softLock) {
    newKeyLocks.remove(softLock);

    for (Map.Entry<ClusteredSoftLockID, ReadCommittedClusteredSoftLock> entry : allLocks.entrySet()) {
      if (entry.getValue().equals(softLock)) {
        allLocks.remove(entry.getKey());
        break;
      }
    }
  }

  private Set<Object> getNewKeys() {
    Set<Object> result = new HashSet<Object>();

    for (ReadCommittedClusteredSoftLock softLock : newKeyLocks.keySet()) {
      newKeyLocks.get(softLock); //workaround for DEV-5390
      result.add(softLock.getKey());
    }

    return result;
  }
}
