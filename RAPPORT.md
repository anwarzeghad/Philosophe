# Rapport technique : le dîner des philosophes en microservices

## 1. Architecture générale

Trois microservices Spring Boot (Java 17), qui communiquent par HTTP (`RestTemplate`) et, depuis la partie 6, par Kafka.

```
                 HUNGRY / EATING / DONE (horloge logique de Lamport)
 philosopher-service x N  ───────────────────────────────►  monitor-service  (tableau de bord)
        │  ▲                  HTTP, ou Kafka (partie 6)
        │  │ jetons / demandes (3.3)  ou  verrou ZooKeeper (3.4)
        ▼  │
 autres philosophes   ou   supervisor-service (parties 2 et 3.2)
```

Le philosophe a plusieurs modes (`philosopher.mode`) : `distributed` (jetons, 3.3), `supervisor` (3.2),
`zookeeper` (3.4), `none` (sert seulement de worker/master du graphe). Le philosophe *i* utilise les
fourchettes *i* et *(i+1) mod N*.

## 2. Partie 1 : philosophes gâtés

N instances indépendantes mangent sans ressource partagée, avec des durées aléatoires. Chaque événement
porte une horloge logique **locale** au philosophe. Le moniteur ne se fie pas à l'ordre d'arrivée HTTP
(non fiable) : il reconstruit l'historique par philosophe en triant sur l'horloge logique
(`GET /events`) et garde pour chacun l'événement d'horloge maximale (`GET /events/state`, tableau de bord).

## 3. Partie 2 : superviseur naïf et ses failles

Le philosophe demande « la fourchette est-elle libre ? », puis la prend par un second appel. Le superviseur
ne contrôle rien d'autre (aucune vérification globale). Failles recensées et testées
(`SupervisorNaiveFlawsTest`, détail dans `failles_partie2.txt`) :

1. vérification puis prise non atomiques : la réponse « oui » ne vaut rien ;
2. deux philosophes obtiennent la même fourchette quand deux demandes arrivent en même temps ;
3. interblocage si les 5 prennent leur fourchette gauche en même temps ;
4. famine : pas de file d'attente ni d'équité ;
5. libération sans contrôle : n'importe qui peut libérer n'importe quelle fourchette.

## 4. Partie 3.1 et 3.2 : section critique et verrou

Le philosophe demande **les deux fourchettes en une seule requête** (`POST /forks/take?left=&right=`).
Le superviseur vérifie F_i et F_{i+1} puis réserve les deux, ou refuse, dans un bloc `synchronized` :
deux demandes simultanées sont traitées l'une après l'autre (`SupervisorWithLockTest`). Ce verrou local
supprime la faille 2. L'interblocage disparaît aussi (on ne détient jamais une seule fourchette) ; la famine
reste possible puisque le refus est suivi d'une nouvelle tentative sans file d'attente.

## 5. Partie 3.3 : protocole distribué par jetons

**Principe.** Chaque fourchette a un jeton unique, détenu au départ par le philosophe de même numéro.
Pas de superviseur. Les messages sont `REQUEST(id, ts)`, `TOKEN(fourchette)` et `FINISHED(id, ts)`.

1. Un philosophe affamé prend un horodatage de Lamport et **diffuse** sa demande à tous les autres, qui la mémorisent.
2. Un philosophe qui détient un jeton le donne à la demande **la plus ancienne** (ordre `(ts, id)`) qui en a
   besoin, même s'il a lui-même faim, sauf s'il est en train de manger.
3. Il mange quand il possède ses deux jetons.
4. À la fin du repas il diffuse `FINISHED` (la demande est effacée partout) et passe ses jetons aux demandes en attente.

**Propriétés.**
- *Exclusion mutuelle* : un jeton est unique, donc une fourchette n'a jamais deux utilisateurs.
- *Pas d'interblocage ni de famine* : les demandes sont servies dans l'ordre total `(ts, id)`. Il existe
  toujours une demande globalement la plus ancienne ; personne ne garde un jeton dont elle a besoin, donc elle
  finit par obtenir ses deux jetons. C'est aussi ce qui donne l'équité.
- *Demandes simultanées* : à horodatage égal, l'identifiant départage.
- *Pertes et retards* : une demande non satisfaite est rediffusée périodiquement (doublons ignorés) ; un envoi HTTP
  a un délai d'attente et 3 tentatives ; si le destinataire d'un jeton est injoignable, l'émetteur reprend le jeton.

**Défauts découverts par les tests (partie 4) et corrigés.** Si un `FINISHED` est perdu, la demande reste
mémorisée chez les autres. Deux demandes périmées pouvaient faire rebondir un jeton à l'infini entre deux
philosophes (livelock observé : l'horloge logique dépassait 25 000 en quelques secondes). Corrections : une nouvelle demande d'un
philosophe remplace ses anciennes, `FINISHED` efface toutes les demandes plus anciennes de ce philosophe, et
une demande distante sans signe de vie depuis 4 périodes de retransmission est oubliée.

## 6. Partie 3.4 : verrou distribué ZooKeeper et comparaison

Solution : Apache Curator, `InterProcessMultiLock` sur deux verrous `/philosophers/forks/<id>`. Les verrous
sont toujours pris **dans l'ordre croissant des numéros de fourchette**, ce qui casse le cycle d'attente
(le dernier philosophe prend F0 avant F_{N-1}) : pas d'interblocage. Curator libère les verrous déjà obtenus
si l'acquisition complète échoue.

| | Jetons (3.3) | ZooKeeper (3.4) |
|---|---|---|
| Dépendance externe | aucune | un serveur ZooKeeper (point unique de défaillance si une seule instance) |
| Code à écrire et à prouver | un protocole complet (horloges, retransmission, péremption) | quelques lignes ; la difficulté est déléguée à ZooKeeper |
| Messages par repas | de l'ordre de 2(N−1) diffusions + transferts de jetons, entre philosophes | quelques aller-retours avec ZooKeeper, indépendants de N |
| Équité | globale, ordre `(ts, id)` | FIFO par verrou (nœuds séquentiels) ; pas d'ordre global entre les deux verrous |
| Panne d'un philosophe | son jeton est perdu, la fourchette est bloquée | verrou libéré à l'expiration de session (nœuds éphémères) |
| Redémarrage d'un philosophe | état des jetons et horloge perdus (risque de jeton dupliqué) | sans problème |

Aucune mesure de performances n'a été faite entre les deux solutions : la comparaison ci-dessus est qualitative.
C'est pour la tolérance au redémarrage que la pile Docker/Kubernetes utilise le mode ZooKeeper.

## 7. Partie 4 : tests

`philosopher-service` : 81 tests ; `supervisor-service` : 11 ; `monitor-service` : 6. Tous passent.

| Niveau | Classes | Vérifié |
|---|---|---|
| Unitaire (JUnit 5, Mockito) | `ForkProtocolUnitTest` | horloge de Lamport, demandes horodatées, jetons, priorité, libération, erreurs, messages invalides |
| Intégration (Spring Boot) | `PhilosophersIntegrationTest` | vrais microservices en HTTP : diffusion, transfert de jetons, coordination à 5 |
| Concurrence | `ForkProtocolConcurrencyTest`, `ForkProtocolTest` | tous affamés en même temps, N = 2, 3, 5, 8 : aucune fourchette à deux utilisateurs, pas de blocage, équité empirique |
| Résilience | `ForkProtocolResilienceTest` + tests d'intégration | délais de 80 ms, 40 % de demandes perdues, messages `FINISHED` perdus, philosophe arrêté puis redémarré, partition réseau |

**Ce que les tests prouvent** : l'exclusion mutuelle, l'absence de blocage et l'équité sur les scénarios joués,
et la reprise après les pannes simulées.
**Ce qu'ils ne prouvent pas** (vérification formelle nécessaire, par exemple TLA+ ou model-checking) :
l'absence d'interblocage et de famine pour tout N et tout entrelacement ; l'unicité du jeton en cas de rejeu de
message ; le comportement en cas de crash d'un philosophe qui détient un jeton.
**Limites de la stratégie** : réseau simulé en mémoire pour les pannes, tests sur une seule machine,
tests temporels dépendants des horloges réelles, mode ZooKeeper non testé automatiquement.

## 8. Partie 5 : Master-Worker sur graphe

**Architecture.** Un philosophe est master (`GET /graph/compute`), les autres sont workers
(`POST /graph/task`). Communication par `RestTemplate`.

**Partitionnement.** Les **arêtes** sont découpées en lots consécutifs et disjoints (plus de lots que de workers).
Un worker reçoit le nombre de nœuds et les arêtes de son lot ; il renvoie, par nœud, le nombre d'arêtes sortantes
et entrantes de son lot.

**Pas de double comptage.** Une arête reliant deux « partitions » serait vue deux fois avec un partitionnement par
nœuds. Ici chaque arête est traitée par un seul worker, qui ajoute 1 au degré sortant de `u` et 1 au degré entrant
de `v`. Le master additionne les tableaux partiels ; contrôle : somme des degrés = 2 × nombre d'arêtes.

**Pannes et retards.** Les workers piochent les lots dans une file partagée. Si un appel échoue ou dépasse le délai,
le lot est remis dans la file et repris par un autre worker ; un worker qui échoue 2 fois de suite est déclaré mort ;
si plus aucun worker ne répond, le master calcule lui-même les lots restants. Les résultats sont rangés par
identifiant de lot et seul le premier est gardé : un résultat tardif ne peut pas fausser le total.

**Performances** (ms, HTTP sur localhost, une seule machine) :

| Graphe (nœuds / arêtes) | Séquentiel | 1 worker | 2 workers | 3 workers | 4 workers |
|---|---|---|---|---|---|
| 10 000 / 100 000 | 0 | 131 | 123 | 168 | 140 |
| 50 000 / 1 000 000 | 8 | 370 | 260 | 255 | 223 |
| 100 000 / 3 000 000 | 22 | 812 | 507 | 393 | 340 |

Le temps diminue avec le nombre de workers et la taille du graphe, mais le calcul séquentiel reste bien plus
rapide : compter des degrés est trop simple. L'envoi des arêtes en JSON, la fusion des résultats et le coût de
démarrage dominent (loi d'Amdahl : la partie non parallélisable limite le gain). Le parallélisme ne paierait que
pour un calcul plus coûteux que son transport. Le sujet évoque aussi l'accessibilité dans un graphe : elle n'a
pas été implémentée.

## 9. Partie 6 : approche DevOps

- **Docker** : un `Dockerfile` par service (construction Maven puis image JRE, utilisateur non-root) ; `docker-compose.yml`
  avec ZooKeeper, Kafka (KRaft), moniteur, 5 philosophes et Prometheus.
- **Kafka** : les philosophes publient leurs événements dans un topic (clé = identifiant du philosophe, ce qui garde
  l'ordre par philosophe), le moniteur les consomme. Si le moniteur est arrêté, les événements attendent dans Kafka.
  Un message illisible ne bloque plus le consommateur (`ErrorHandlingDeserializer`, défaut trouvé par un test).
  Si Kafka est arrêté, un envoi n'attend pas plus de 2 s : le philosophe n'est pas bloqué.
- **Kubernetes** (`k8s/`) : StatefulSet de 5 philosophes (l'identifiant vient du numéro du pod), Deployment du
  moniteur, Kafka et ZooKeeper, sondes liveness/readiness/startup, limites de ressources, budget d'interruption.
- **CI/CD** (GitHub Actions) : tests de chaque service, construction des images, publication sur ghcr.io depuis `main`,
  test de déploiement dans un cluster kind (les 5 philosophes émettent, panne d'un pod), déploiement conditionné au secret `KUBE_CONFIG`.
- **Supervision et pannes** : Actuator et Prometheus (`philosophers_events_total`), règles d'alerte (`ServiceDown`,
  `PasDeRepas`), journaux JSON, redémarrage automatique des conteneurs et des pods.

**Statut de validation.** Les tests Java (dont Kafka embarqué et sondes Actuator) passent. Les fichiers Docker,
Compose, Kubernetes et le workflow GitHub Actions ont été validés pour leur syntaxe (`docker compose config`,
`kubectl kustomize`) mais **n'ont pas été exécutés** : ni images construites, ni pile démarrée, ni pipeline lancé.

## 10. Difficultés rencontrées

- Le livelock sur demandes périmées (section 5) n'est apparu qu'avec de vrais messages HTTP et des pertes ; il a été
  trouvé par les tests de résilience.
- Des messages tardifs d'un test précédent (horloges repartant de 0) faussaient les tests suivants : chaque test
  d'intégration utilise maintenant ses propres ports.
- Un message Kafka invalide bloquait le consommateur du moniteur.
- Le protocole à jetons supporte mal le redémarrage d'un philosophe (jetons et horloge en mémoire), d'où le choix
  de ZooKeeper pour le déploiement en conteneurs.

## 11. Limites connues

- Un jeton détenu par un philosophe qui tombe est perdu (pas de régénération de jeton).
- Un philosophe redémarré repart avec une horloge à 0 : ses nouvelles demandes peuvent être confondues avec
  d'anciennes demandes terminées.
- La péremption des demandes repose sur le temps : sur un réseau très lent, une demande vivante peut être oubliée
  (rattrapée à la retransmission suivante, avec du retard).
- Le moniteur garde son historique en mémoire (une seule réplique).
- Le mode ZooKeeper n'a pas de tests automatiques ; Docker, Kubernetes et la CI n'ont pas été exécutés (section 9).
