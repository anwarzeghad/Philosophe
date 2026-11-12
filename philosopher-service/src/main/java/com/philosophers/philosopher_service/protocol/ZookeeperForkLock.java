package com.philosophers.philosopher_service.protocol;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMultiLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verrou distribué de la partie 3.4.
 *
 * Les deux verrous sont toujours construits dans l'ordre des identifiants de
 * fourchettes. Curator libère les verrous déjà acquis si l'acquisition complète
 * échoue, ce qui évite de conserver une seule fourchette.
 */
@Component
@ConditionalOnProperty(name = "philosopher.mode", havingValue = "zookeeper")
public class ZookeeperForkLock {

    private static final String LOCK_ROOT = "/philosophers/forks";

    private final InterProcessMultiLock lock;
    private final long acquireTimeoutMs;

    public ZookeeperForkLock(
            CuratorFramework client,
            @Value("${philosopher.id:0}") int id,
            @Value("${philosophers.total:5}") int total,
            @Value("${zookeeper.acquire-timeout-ms:5000}") long acquireTimeoutMs) {
        if (id < 0 || id >= total || total < 2) {
            throw new IllegalArgumentException("philosopher.id doit être compris entre 0 et total-1");
        }

        int left = id;
        int right = (id + 1) % total;
        List<Integer> forkIds = List.of(left, right).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<InterProcessLock> locks = new ArrayList<>();
        for (int forkId : forkIds) {
            locks.add(new InterProcessMutex(client, LOCK_ROOT + "/" + forkId));
        }
        this.lock = new InterProcessMultiLock(locks);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public boolean acquire() throws Exception {
        return lock.acquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
    }

    public void release() throws Exception {
        lock.release();
    }
}
