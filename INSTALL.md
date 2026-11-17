# Installation rapide

## À installer

| Outil | Pour quoi | Obligatoire ? |
|---|---|---|
| Java 17 ou plus | compiler et lancer les services | oui |
| Git | récupérer le dépôt | oui |
| Docker Desktop | ZooKeeper, Kafka, pile complète (parties 3.4 et 6) | seulement pour ces parties |
| kubectl + un cluster (kind ou Kubernetes de Docker Desktop) | déploiement Kubernetes | seulement pour cette partie |

`curl` est déjà dans Windows (taper `curl.exe`).

Rien d'autre à installer : Maven est fourni par le Maven Wrapper (`mvnw.cmd`, téléchargé au premier lancement, internet requis),
les bibliothèques Java sont téléchargées par Maven, Kafka et ZooKeeper tournent dans Docker.

## Vérifier

```powershell
java -version        # 17 ou plus
docker --version
docker ps            # doit répondre sans erreur : Docker Desktop doit être démarré
kubectl version --client
```

## Compiler

```powershell
cd philosopher-service; .\mvnw.cmd -q package -DskipTests; cd ..
cd monitor-service;     .\mvnw.cmd -q package -DskipTests; cd ..
cd supervisor-service;  .\mvnw.cmd -q package -DskipTests; cd ..
```

## Lancer (sans Docker)

```powershell
Start-Process java -ArgumentList "-jar monitor-service\target\monitor-service-0.0.1-SNAPSHOT.jar"
0..4 | ForEach-Object { Start-Process java -ArgumentList "-jar philosopher-service\target\philosopher-service-0.0.1-SNAPSHOT.jar --server.port=900$_ --philosopher.id=$_ --philosophers.total=5" }
```

Tableau de bord : <http://localhost:8080>. Pour arrêter, fermer les fenêtres une par une
(ne pas faire `Stop-Process -Name java`, cela arrête aussi le serveur Java de VS Code).

## Lancer (pile complète avec Docker)

```powershell
docker compose up --build -d     # la première fois : plusieurs minutes
docker compose ps
docker compose down
```

## Tests

```powershell
cd philosopher-service; .\mvnw.cmd clean test    # environ 3 min
```

Détails des modes et des scénarios de panne : [README.md](README.md).
