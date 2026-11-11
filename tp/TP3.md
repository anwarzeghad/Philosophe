# Kafka et spring boot 

September 2026 

## **1 Objectifs** 

Dans le TP1, les microservices communiquaient directement au moyen d’une API REST. Dans ce TP, nous allons introduire une communication **asynchrone** avec Apache Kafka. A<sup>`</sup> la fin du TP, vous serez capables de : 

- comprendre le principe de la communication par ´ev´enements ; 

- utiliser Kafka avec des microservices spring boot ; 

- produire un ´ev´enement dans un topic Kafka ; 

- consommer un ´ev´enement depuis un topic Kafka ; 

- faire communiquer plusieurs microservices sans appel REST direct ; 

- ex´ecuter les microservices et Kafka avec Docker. 

### **1.1 Installation de Kafka avec Docker** 

- Pour ce TP, Kafka sera ex´ecut´e dans un conteneur Docker. Cr´eer un fichier _docker-compose.yml_ avec l’infrastructure Kafka. 

- Donner l’architecture globale du syst`eme. 

- D´emarrer l’infrastructure avec : 

```
dockercomposeup-d
```

- V´erifier les conteneurs : 

```
dockerps
```

1 

### **1.2 Architecture du TP** 

- Dans le TP1, la communication entre micro-services ´etait directe. 

- Dans ce TP, les microservices communiquent par l’interm´ediaire de Kafka. 

- Le microservice `commandes` produit un ´ev´enement. 

- Le microservice `clients` consomme cet ´ev´enement. 

Proposez une architecture globale de ce syst`eme. 

### **1.3 Principe d’un ´ev´enement** 

- Lorsqu’une nouvelle commande est cr´e´ee, le microservice `commandes` publie un ´ev´enement dans Kafka. 

- Par exemple : 

1. `{ "commande_id": 10, "client_id": 2, "produit": "PC", "montant": 1200 }` 

2. Cet ´ev´enement est envoy´e dans le topic : 

```
commandes-creees
```

3. Le microservice `clients` consomme ensuite cet ´ev´enement. 

### **1.4 Cr´eation du producteur commandes Kafka** 

Le microservice commandes doit publier sur un topic Kafka un message JSON contenant les informations d’une commande lorsqu’une nouvelle commande est re¸cue. Pr´ecisez et impl´ementez les d´ependance(s) n´ecessaire(s) : 

- la connexion du producteur au broker Kafka ; 

- le topic Kafka utilis´e ; 

- la structure du message JSON envoy´e ; 

- l’envoi du message depuis l’API REST du microservice Commandes. 

- publication d’une commande. 

2 

### **1.5 Cr´eation du consommateur clients kafka** 

- Modifier le microservice `clients` afin qu’il consomme les ´ev´enements `commandes-creees` produits par le microservice `commandes` . A<sup>`</sup> chaque commande re¸cue, afficher les informations du client, le produit et le montant, puis maintenir et afficher le nombre total de commandes effectu´ees par chaque client. 

- Observer la communication entre les diff´erentes entit´es de cette architecture, notamment les ´echanges de messages via Kafka entre les microservices. 

### **1.6 Arrˆeter un microservice** 

1. Arrˆeter le microservice `clients` . 

2. Cr´eer plusieurs nouvelles commandes. 

3. Puis red´emarrer le microservice `clients` . 

4. Observer ce qui se passe : 

   - Les ´ev´enements produits pendant l’arrˆet du microservice `clients` ont-ils ´et´e perdus ? 

   - Observer les messages consomm´es par `clients` . 

### **1.7 Ajouter un deuxi`eme consommateur** 

Cr´eer un troisi`eme microservice qui consomme ´egalement le topic _commandescreees_ . Il doit calculer : 

- le nombre total de commandes pour un produit donn´e; 

- le chiffre d’affaires total pour un mois donn´e (apporter les changements aux ´ev`enements d´epos´es si n´ecessaire); 

### **1.8 Kafka et groupes de consommateurs** 

1. Cr´eer deux consommateurs appartenant au mˆeme groupe : group ~~i~~ d = ”groupe-A” 

2. Observer la distribution des messages. 

3. Puis cr´eer deux consommateurs appartenant `a des groupes diff´erents : group ~~i~~ d = ”clients-group” et group ~~i~~ d = ”statistiques-group” 

4. Quelle diff´erence observez-vous entre les deux configurations ? 

### **1.9 Probl`eme des philosophes** 

- Reprendre la version centralis´ee des philosophes et utiliser kafka. 

- Reprendre la version distribu´ee des philosophes et utiliser kafka. 

3 

