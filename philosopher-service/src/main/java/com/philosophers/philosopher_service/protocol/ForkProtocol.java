package com.philosophers.philosopher_service.protocol;

import com.philosophers.philosopher_service.dto.FinishedMessage;
import com.philosophers.philosopher_service.dto.RequestMessage;
import com.philosophers.philosopher_service.dto.TokenMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Protocole distribué par jetons (partie 3.3), sans superviseur.
 *
 * Règle d'attribution : le philosophe i mange avec les fourchettes i et (i+1) mod N.
 * Chaque fourchette a un jeton unique, détenu au départ par le philosophe de même numéro.
 *
 * Protocole :
 *  1. Quand il a faim, un philosophe prend un horodatage de Lamport et DIFFUSE sa demande
 *     (id, horodatage) à tous les autres. Chacun la mémorise.
 *  2. Un philosophe qui détient le jeton d'une fourchette le donne à la demande la plus
 *     ancienne qui en a besoin (ordre : horodatage, puis identifiant), sauf s'il mange.
 *     Il le donne même s'il a faim lui-même quand une demande plus ancienne existe.
 *  3. Dès qu'il détient ses deux jetons, il mange (exclusion mutuelle : un jeton est unique).
 *  4. À la fin du repas, il diffuse FINISHED (la demande est supprimée partout) puis
 *     passe ses jetons aux demandes en attente.
 *
 * Équité et absence de famine : les demandes sont servies dans l'ordre des horodatages.
 * Absence d'interblocage : il existe toujours une demande globalement la plus ancienne ;
 * personne ne garde un jeton qui lui est nécessaire, donc elle obtient ses deux jetons.
 *
 * Tolérance aux pannes : une demande non satisfaite est rediffusée périodiquement
 * (retransmission) ; les doublons sont ignorés. Les envois HTTP ont un délai d'attente
 * et plusieurs tentatives (PeerClient).
 */
@Component
public class ForkProtocol {

    /** Demande de fourchettes, comparée par horodatage puis par identifiant. */
    record Request(int id, long ts) implements Comparable<Request> {
        @Override
        public int compareTo(Request o) {
            int c = Long.compare(ts, o.ts);
            return c != 0 ? c : Integer.compare(id, o.id);
        }
    }

    private final int id;
    private final int total;
    private final PeerSender sender;
    private final Executor executor;
    private final long retransmitMs;

    private final Object lock = new Object();

    // --- état protégé par "lock" ---
    private long clock = 0;                                  // horloge logique de Lamport
    private final boolean[] tokens;                          // jetons détenus (par numéro de fourchette)
    private final Set<Request> pending = new HashSet<>();    // demandes connues, non terminées
    private final Set<Request> finished = new HashSet<>();   // demandes terminées (ne plus les admettre)
    private Request myRequest;                               // ma demande en cours
    private boolean eating;

    @Autowired
    public ForkProtocol(@Value("${philosopher.id:0}") int id,
                        @Value("${philosophers.total:5}") int total,
                        PeerSender sender) {
        this(id, total, sender, daemonPool(), 3000);
    }

    ForkProtocol(int id, int total, PeerSender sender, Executor executor, long retransmitMs) {
        this.id = id;
        this.total = total;
        this.sender = sender;
        this.executor = executor;
        this.retransmitMs = retransmitMs;
        this.tokens = new boolean[total];
        this.tokens[id] = true;   // je détiens le jeton de ma fourchette au départ
    }

    private static Executor daemonPool() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------------------------------------------------------ API pour le philosophe

    /** Horodatage logique pour un événement local (envoyé au moniteur). */
    public long nextClock() {
        synchronized (lock) {
            return ++clock;
        }
    }

    /** Bloque jusqu'à ce que le philosophe détienne ses deux jetons (retransmet si besoin). */
    public void acquireForks() throws InterruptedException {
        Request req;
        List<Runnable> out = new ArrayList<>();
        synchronized (lock) {
            clock++;
            req = new Request(id, clock);
            myRequest = req;
            pending.add(req);
            trace("DEMANDE fourchettes " + left() + " et " + right() + " (ts=" + req.ts() + ")");
            evaluate(out);
        }
        run(out);
        broadcastRequest(req);

        while (true) {
            synchronized (lock) {
                if (!eating) {
                    lock.wait(retransmitMs);
                }
                if (eating) {
                    return;
                }
                trace("RETRANSMISSION de la demande ts=" + req.ts());
            }
            broadcastRequest(req);
        }
    }

    /** Fin du repas : libère les fourchettes et passe les jetons aux demandes en attente. */
    public void releaseForks() {
        Request done;
        List<Runnable> out = new ArrayList<>();
        synchronized (lock) {
            eating = false;
            done = myRequest;
            myRequest = null;
            pending.remove(done);
            finished.add(done);
            trace("LIBÈRE fourchettes " + left() + " et " + right());
            evaluate(out);
        }
        run(out);
        FinishedMessage msg = new FinishedMessage(done.id(), done.ts());
        for (int peer = 0; peer < total; peer++) {
            if (peer != id) {
                final int p = peer;
                executor.execute(() -> sender.sendFinished(p, msg));
            }
        }
    }

    // ------------------------------------------------------------------ messages reçus

    public void onRequest(RequestMessage m) {
        List<Runnable> out = new ArrayList<>();
        synchronized (lock) {
            clock = Math.max(clock, m.timestamp()) + 1;
            Request r = new Request(m.requesterId(), m.timestamp());
            if (!finished.contains(r) && pending.add(r)) {
                trace("REÇOIT demande de P" + r.id() + " (ts=" + r.ts() + ")");
            }
            evaluate(out);
        }
        run(out);
    }

    public void onFinished(FinishedMessage m) {
        List<Runnable> out = new ArrayList<>();
        synchronized (lock) {
            Request r = new Request(m.requesterId(), m.timestamp());
            pending.remove(r);
            finished.add(r);
            evaluate(out);
        }
        run(out);
    }

    public void onToken(TokenMessage m) {
        List<Runnable> out = new ArrayList<>();
        synchronized (lock) {
            clock = Math.max(clock, m.timestamp()) + 1;
            tokens[m.fork()] = true;
            trace("REÇOIT jeton F" + m.fork() + " de P" + m.fromId());
            evaluate(out);
        }
        run(out);
    }

    // ------------------------------------------------------------------ logique interne

    private int left() {
        return id;
    }

    private int right() {
        return (id + 1) % total;
    }

    /** Fourchettes nécessaires au philosophe p : p et (p+1) mod N. */
    private boolean needs(int p, int fork) {
        return fork == p || fork == (p + 1) % total;
    }

    /** Demande la plus ancienne qui a besoin de la fourchette, ou null. À appeler sous lock. */
    private Request bestRequestFor(int fork) {
        Request best = null;
        for (Request r : pending) {
            if (needs(r.id(), fork) && (best == null || r.compareTo(best) < 0)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Applique les règles du protocole. À appeler sous lock après tout changement d'état.
     * Les envois sont ajoutés à "out" et exécutés hors du verrou.
     */
    private void evaluate(List<Runnable> out) {
        if (eating) {
            return;   // on ne cède rien pendant le repas
        }
        if (myRequest != null && tokens[left()] && tokens[right()]) {
            eating = true;
            trace("OBTIENT les deux jetons : peut manger");
            lock.notifyAll();
            return;
        }
        for (int fork = 0; fork < total; fork++) {
            if (!tokens[fork]) {
                continue;
            }
            Request best = bestRequestFor(fork);
            if (best != null && best.id() != id) {
                tokens[fork] = false;
                clock++;
                final long ts = clock;
                final int to = best.id();
                final int f = fork;
                trace("CÈDE jeton F" + f + " à P" + to + " (demande ts=" + best.ts() + ")");
                out.add(() -> sendToken(to, f, ts));
            }
        }
    }

    private void sendToken(int to, int fork, long ts) {
        boolean delivered = sender.sendToken(to, new TokenMessage(fork, id, ts));
        if (!delivered) {
            // Destinataire injoignable : on reprend le jeton (puis on retentera)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Runnable> out = new ArrayList<>();
            synchronized (lock) {
                tokens[fork] = true;
                trace("REPREND jeton F" + fork + " (P" + to + " injoignable)");
                evaluate(out);
            }
            run(out);
        }
    }

    private void broadcastRequest(Request req) {
        RequestMessage msg = new RequestMessage(req.id(), req.ts());
        for (int peer = 0; peer < total; peer++) {
            if (peer != id) {
                final int p = peer;
                executor.execute(() -> sender.sendRequest(p, msg));
            }
        }
    }

    private void run(List<Runnable> out) {
        out.forEach(executor::execute);
    }

    private void trace(String message) {
        synchronized (lock) {
            System.out.println("[P" + id + " h=" + clock + "] " + message);
        }
    }
}
