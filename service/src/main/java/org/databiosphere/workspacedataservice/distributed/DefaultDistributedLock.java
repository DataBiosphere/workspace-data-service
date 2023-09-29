package org.databiosphere.workspacedataservice.distributed;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Component;

@Component
public class DefaultDistributedLock implements DistributedLock {

  private final LockRegistry lockRegistry;

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDistributedLock.class);

  // A wrapper around a Lock for easier testing.
  public DefaultDistributedLock(LockRegistry lockRegistry) {
    this.lockRegistry = lockRegistry;
  }

  @Override
  public Lock obtainLock(String lockId) {
    return lockRegistry.obtain(lockId);
  }

  @Override
  public Boolean tryLock(Lock lock) {
    if (lock == null) {
      return false;
    }
    try {
      return lock.tryLock(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.error("Error with aquiring cloning/schema initialization Lock: {}", e.getMessage());
      lock.unlock();
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public void unlock(Lock lock) {
    if (lock == null) {
      return;
    }
    lock.unlock();
  }
}
