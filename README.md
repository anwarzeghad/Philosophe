# Le dîner des philosophes en microservices

Projet Master Informatique : du comportement naïf à l'exclusion mutuelle distribuée, jusqu'au déploiement
Docker / Kafka / Kubernetes. Le rapport technique est dans [RAPPORT.md](RAPPORT.md).

## Contenu du dépôt

| Dossier / fichier | Rôle |
|---|---|
| `philosopher-service/` | Le philosophe (Spring Boot). Contient les modes `distributed` (jetons, 3.3), `supervisor` (3.2), `zookeeper` (3.4) et le Master-Worker sur graphe (partie 5) |
| `supervisor-service/` | Superviseur central des fourchettes (parties 2 et 3.1/3.2) |
| `monitor-service/` | Moniteur : événements HUNGRY / EATING / DONE avec horloge logique, tableau de bord, consommateur Kafka |
| `docker-compose.yml` | Pile complète : ZooKeeper, Kafka, moniteur, 5 philosophes, Prometheus |
| `k8s/` | Manifestes Kubernetes (`kubectl apply -k k8s`) |
| `.github/workflows/ci.yml` | Chaîne CI/CD (tests, images Docker, test sur cluster kind, déploiement) |
| `prometheus/` | Configuration de la supervision et règles d'alerte |
| `failles_partie2.txt` | Failles de la version naïve du superviseur |

## Prérequis

- Java 17 ou plus (le projet est compilé en Java 17).
- Rien d'autre à installer pour compiler et tester : chaque service embarque le Maven Wrapper
  (`./mvnw` sous Linux/macOS/Git Bash, `mvnw.cmd` sous Windows).
- Docker pour ZooKeeper, Kafka et la pile complète. kubectl et un cluster (kind, minikube...) pour Kubernetes.

## Lancer les tests

```bash
cd philosopher-service && ./mvnw test     # protocole, intégration HTTP, concurrence, résilience, graphe, Kafka embarqué
cd ../supervisor-service && ./mvnw test   # failles du superviseur naïf et version avec verrou
cd ../monitor-service && ./mvnw test      # moniteur, Kafka embarqué, sondes Actuator
```

Les tests de `philosopher-service` durent environ 2 minutes (démarrage de vrais microservices et scénarios de pannes).

## Exécuter sur la machine (sans Docker)

Les ports sont fixes : moniteur 8080, superviseur 8082, philosophe *j* sur le port 9000 + *j*.

1. Moniteur (un terminal) :
   ```bash
   cd monitor-service && ./mvnw spring-boot:run
   ```
   Tableau de bord : <http://localhost:8080> (état courant : `/events/state`, historique : `/events`).

2. Philosophes, protocole distribué par jetons (partie 3.3), 5 terminaux :
   ```bash
   cd philosopher-service
   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9000 --philosopher.id=0 --philosophers.total=5"
   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9001 --philosopher.id=1 --philosophers.total=5"
   # ... jusqu'à --server.port=9004 --philosopher.id=4
   ```
   Pour éviter de relancer Maven cinq fois, construire le jar une fois (`./mvnw package -DskipTests`)
   puis `java -jar target/philosopher-service-0.0.1-SNAPSHOT.jar --server.port=9001 --philosopher.id=1 ...`.

3. Autres modes, avec `--philosopher.mode=` :
   - `supervisor` : lancer d'abord `cd supervisor-service && ./mvnw spring-boot:run`.
   - `zookeeper` : lancer d'abord `docker compose up -d zookeeper`.
   - `none` : le philosophe ne mange pas, il ne sert que de worker/master du graphe.

## Calcul de degrés sur un graphe (partie 5, Master-Worker)

Démarrer 5 philosophes en mode `none` (ports 9000 à 9004), puis demander au philosophe 0 d'être le master :

```bash
curl "http://localhost:9000/graph/compute?nodes=100000&edges=1000000&workers=1,2,3,4"
```

La réponse contient le temps parallèle, le temps séquentiel de contrôle, et `"correct": true` si le résultat est identique.

## Pile complète avec Docker Compose (parties 3.4 et 6)

```bash
docker compose up --build -d        # ZooKeeper, Kafka, moniteur, 5 philosophes, Prometheus
curl localhost:8080/events/state    # état de chaque philosophe
docker compose logs -f monitor
docker compose stop philosopher-3   # panne d'un philosophe
docker compose stop monitor         # les événements restent dans Kafka
docker compose start monitor        # le moniteur rattrape les événements
docker compose down
```

Prometheus : <http://localhost:9090> (métrique `philosophers_events_total`).

## Kubernetes

```bash
docker build -t philosophers/monitor-service:latest monitor-service
docker build -t philosophers/philosopher-service:latest philosopher-service
# avec kind : kind load docker-image philosophers/monitor-service:latest philosophers/philosopher-service:latest
kubectl apply -k k8s
kubectl -n philosophers get pods -w
kubectl -n philosophers port-forward svc/monitor 8080:8080     # puis http://localhost:8080
kubectl -n philosophers delete pod philosopher-2               # panne : le pod est recréé
```

Kubernetes 1.28 ou plus est requis (l'identifiant du philosophe vient de l'étiquette `apps.kubernetes.io/pod-index`).
