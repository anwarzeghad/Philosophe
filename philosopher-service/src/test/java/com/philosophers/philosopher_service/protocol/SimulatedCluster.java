package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Banc d'essai : N philosophes ForkProtocol reliés par un réseau simulé en mémoire,
 * avec injection de défaillances (délais, pertes, nœuds hors service, partition).
 * Un détecteur vérifie en continu qu'une fourchette n'a jamais deux utilisateurs.
 */
class SimulatedCluster implements PeerSender {

    final int n;
    final ForkProtocol[] nodes;
    final ExecutorService netPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private final Random random = new Random();

    // --- réglages d'injection de pannes (modifiables pendant le test) ---
    volatile int maxDelayMs = 3;
    volatile double requestLossRate = 0;
    volatile double finishedLossRate = 0;
    volatile boolean partitioned = false;
    volatile boolean closed = false;
    final AtomicInteger tokensSent = new AtomicInteger();
    final boolean[] down;

    // --- observation ---
    final AtomicInteger[] usersOfFork;
    final AtomicInteger violations = new AtomicInteger();
    final AtomicInteger[] meals;
    final AtomicInteger concurrentEaters = new AtomicInteger();
    final AtomicInteger maxConcurrentEaters = new AtomicInteger();

    private static final java.util.List<SimulatedCluster> LIVE = new java.util.concurrent.CopyOnWriteArrayList<>();

    static void closeAll() {
        LIVE.forEach(SimulatedCluster::close);
        LIVE.clear();
    }

    SimulatedCluster(int n, long retransmitMs) {
        LIVE.add(this);
        this.n = n;
        this.nodes = new ForkProtocol[n];
        this.down = new boolean[n];
        this.usersOfFork = new AtomicInteger[n];
        this.meals = new AtomicInteger[n];
        for (int i = 0; i < n; i++) {
            usersOfFork[i] = new AtomicInteger();
            meals[i] = new AtomicInteger();
            nodes[i] = new ForkProtocol(i, n, this, netPool, retransmitMs);
        }
    }

    private void delay() {
        int max = maxDelayMs;
        if (max <= 0) {
            return;
        }
        try {
            Thread.sleep(random.nextInt(max + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean unreachable(int peerId) {
        return closed || partitioned || down[peerId];
    }

    @Override
    public void sendRequest(int peerId, RequestMessage m) {
        if (unreachable(peerId) || random.nextDouble() < requestLossRate) {
            return;
        }
        delay();
        nodes[peerId].onRequest(m);
    }

    @Override
    public void sendFinished(int peerId, FinishedMessage m) {
        if (unreachable(peerId) || random.nextDouble() < finishedLossRate) {
            return;
        }
        delay();
        nodes[peerId].onFinished(m);
    }

    @Override
    public boolean sendToken(int peerId, TokenMessage m) {
        if (closed) {
            return true;   // cluster arrêté : le message disparaît
        }
        tokensSent.incrementAndGet();
        if (unreachable(peerId)) {
            return false;   // comme PeerClient : échec visible → l'émetteur reprend son jeton
        }
        delay();
        nodes[peerId].onToken(m);
        return true;
    }

    /** Un repas complet du philosophe p, avec contrôle de l'exclusion mutuelle. */
    void eatOnce(int p, long eatMs) throws InterruptedException {
        int left = p;
        int right = (p + 1) % n;
        nodes[p].acquireForks();
        if (usersOfFork[left].incrementAndGet() > 1) violations.incrementAndGet();
        if (usersOfFork[right].incrementAndGet() > 1) violations.incrementAndGet();
        int now = concurrentEaters.incrementAndGet();
        maxConcurrentEaters.accumulateAndGet(now, Math::max);
        try {
            Thread.sleep(eatMs);
        } finally {
            meals[p].incrementAndGet();
            concurrentEaters.decrementAndGet();
            usersOfFork[right].decrementAndGet();
            usersOfFork[left].decrementAndGet();
            nodes[p].releaseForks();
        }
    }

    /**
     * Lance les philosophes listés ; chacun mange "mealsEach" fois, sans pause de réflexion
     * (contention maximale). Retourne true si tous ont fini avant le délai.
     */
    boolean runAll(int[] philosophers, int mealsEach, long eatMs, long timeoutSec) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(philosophers.length);
        CountDownLatch start = new CountDownLatch(1);   // départ simultané
        CountDownLatch done = new CountDownLatch(philosophers.length);
        for (int p : philosophers) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int m = 0; m < mealsEach; m++) {
                        eatOnce(p, eatMs);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        boolean finished = done.await(timeoutSec, TimeUnit.SECONDS);
        pool.shutdownNow();
        return finished;
    }

    /** Arrête le cluster : plus aucun message ne circule (évite les fuites entre tests). */
    void close() {
        closed = true;
        netPool.shutdownNow();
    }

    static int[] all(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }
}
