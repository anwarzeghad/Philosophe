Spring Boot – Microservices 

Resilience4j 

# **TP – Résilience des microservices** 

Spring Boot et Resilience4j 

#### **Objectif du TP** 

En général, un microservice est rarement isolé. Il interroge et attend des réponses d’autres microservices. Que se passe-t-il lorsqu’un micro-service distant est arrêté, lent ou temporairement indisponible ? 

Dans ce TP, nous allons construire progressivement une architecture résiliente avec Spring Boot et Resilience4j. 

Les mécanismes étudiés sont : 

- Fallback ; 

- Retry ; 

- Circuit Breaker ; 

- Timeout ; 

- combinaison de plusieurs mécanismes. 

## **1 Architecture générale** 

Nous allons construire deux microservices : 

- **service-client** : fournit les informations sur les clients ; 

- **service-commande** : récupère les informations d’un client. 

Architecture : 



<!-- Start of picture text -->
Requête HTTP<br>|<br>v<br>+------------------+<br>| Service Commande |<br>| :8080 |<br>+--------+---------+<br>|<br>| HTTP<br>v<br>+------------------+<br>| Service Client |<br>| :8081 |<br>+------------------+<br><!-- End of picture text -->

Le service Commande dépend donc du service Client. 

### **Question** 

_Quel est l’impact de l’arrêt du micro-service Client sur le micro-service Commande ?_ 

## **2 Création du micro-service Client** 

- Créer un microservice `service-client` ; Le service devra fonctionner sur le port 8081. 

1 

Spring Boot – Microservices 

Resilience4j 

— Créer la classe : 

1 <mark>`package com.example.client;`</mark> 2 3 <mark>`public record Client(Long id , String nom) {`</mark> 4 <mark>`}`</mark> 

— Créer le contrôleur du micro-service comme suit : 1 <mark>`package com.example.client;`</mark> 2 3 <mark>`import org. springframework .web.bind.annotation .*;`</mark> 4 5 <mark>`@RestController`</mark> 6 <mark>`@RequestMapping ( "/clients" )`</mark> 7 <mark>`public class ClientController {`</mark> 8 9 <mark>`@GetMapping( "/{id}" )`</mark> 10 <mark>`public Client getClient(@PathVariable Long id) {`</mark> 11 12 <mark>`return new Client(id , "Alice" );`</mark> 13 <mark>`}`</mark> 14 <mark>`}`</mark> 

— Lancer le service puis ouvrir : 

```
http://localhost:8081/clients/1
```

## **3 Création du micro-service service-commande** 

— Créer le micro-service service-commande 

— Utiliser le port. `8080` 

— Nous allons utiliser `RestTemplate` . Créer : 1 <mark>`package com.example.commande;`</mark> 2 3 <mark>`import org. springframework .context.annotation.Bean;`</mark> 4 <mark>`import org. springframework .context.annotation.Configuration;`</mark> 5 <mark>`import org. springframework .web.client.RestTemplate;`</mark> 6 7 <mark>`@Configuration`</mark> 8 <mark>`public class RestConfig {`</mark> 9 10 <mark>`@Bean`</mark> 11 <mark>`public RestTemplate restTemplate () {`</mark> 12 <mark>`return new RestTemplate ();`</mark> 13 <mark>`}`</mark> 14 <mark>`}`</mark> — Créer la classe : 1 <mark>`package com.example.commande;`</mark> 2 3 <mark>`public record Client(Long id , String nom) {`</mark> 4 <mark>`}`</mark> 

— Créer le contrôleur : 

2 

Spring Boot – Microservices 

Resilience4j 

1 <mark>`package com.example.commande;`</mark> 2 3 <mark>`import org. springframework .web.bind.annotation .*;`</mark> 4 <mark>`import org. springframework .web.client.RestTemplate;`</mark> 5 6 <mark>`@RestController`</mark> 7 <mark>`@RequestMapping ( "/commandes" )`</mark> 8 <mark>`public class CommandeController {`</mark> 9 10 <mark>`private final RestTemplate restTemplate;`</mark> 11 12 <mark>`public CommandeController (RestTemplate restTemplate) {`</mark> 13 <mark>`this .restTemplate = restTemplate;`</mark> 14 <mark>`}`</mark> 15 16 <mark>`@GetMapping( "/{id}" )`</mark> 17 <mark>`public String getCommande(@PathVariable Long id) {`</mark> 18 19 <mark>`Client client = restTemplate.getForObject(`</mark> 20 <mark>`"http :// localhost :8081/ clients /1" ,`</mark> 21 <mark>`Client. class`</mark> 22 <mark>`);`</mark> 23 24 <mark>`return "Commande " + id +`</mark> 25 <mark>`" pour le client " + client.nom();`</mark> 26 <mark>`}`</mark> 27 <mark>`}`</mark> 

Lancer les deux services. Appeler : 

```
http://localhost:8080/commandes/10
```

Résultat attendu : 

```
Commande10pourleclientAlice
```

## **4 Provoquer une panne** 

- Nous allons maintenant provoquer volontairement une panne. 

Arrêter le service : `service-client` 

Le micro-service service-commande reste démarré. 

Appeler : 

```
http://localhost:8080/commandes/10
```

### **Questions** 

1. Que se passe-t-il ? 

2. Quelle exception est générée ? 

3. Le service service-commande peut-il encore répondre correctement ? 

4. Est-il souhaitable qu’une panne du service Client entraîne une panne de service-commande ? 

#### **Problème** 

Le mico-service service-commande dépend du micro-service Client. Une panne du premier peut impacter le second. Nous allons maintenant pallier à ce problème. On parlera d’architecture résiliente. 

3 

Spring Boot – Microservices 

Resilience4j 

## **5 Première protection : le Fallback** 

Le principe du fallback est simple : _Si l’appel au service distant échoue (erreur, délai dépassé ou service indisponible), le service appelant ne propage pas l’erreur à son client : il renvoie à la place une réponse de repli, par exemple une valeur par défaut ou des données en cache, afin de préserver la continuité du service._ 

### **5.1 Installation de Resilience4j** 

Ajouter dans le fichier `pom.xml` : 

```
<dependency >
<groupId >io.github.resilience4j </groupId >
<artifactId >resilience4j -spring -boot3 </artifactId >
<version >2.2.0 </version >
</dependency >
```

### **5.2 Ajouter un Circuit Breaker avec fallback** 

Modifier la méthode : 

1 <mark>`@CircuitBreaker (`</mark> 2 <mark>`name = "clientService" ,`</mark> 3 <mark>`fallbackMethod = "clientFallback"`</mark> 4 <mark>`)`</mark> 5 <mark>`@GetMapping( "/{id}" )`</mark> 6 <mark>`public String getCommande(@PathVariable Long id) {`</mark> 7 8 <mark>`Client client = restTemplate.getForObject(`</mark> 9 <mark>`"http :// localhost :8081/ clients /1" ,`</mark> 10 <mark>`Client. class`</mark> 11 <mark>`);`</mark> 12 13 <mark>`return "Commande " + id +`</mark> 14 <mark>`" pour le client " + client.nom();`</mark> 15 <mark>`}`</mark> — Ajouter la méthode fallback : 1 <mark>`public String clientFallback(`</mark> 2 <mark>`Long id ,`</mark> 3 <mark>`Throwable exception) {`</mark> 4 5 <mark>`return "Service Client indisponible. " +`</mark> 6 <mark>`"Commande " + id +`</mark> 7 <mark>`" t r a i t e en mode d g r a d ." ;`</mark> 8 <mark>`}`</mark> 

- Arrêter le micro-service Client. 

Appeler "http ://localhost :8080/commandes/10" 

### **Questions** 

1. Le service Commande tombe-t-il encore en erreur ? 

2. Quelle réponse obtient-on ? 

3. Quel est l’intérêt du fallback ? 

4 

Spring Boot – Microservices 

Resilience4j 

## **6 Retry** 

Un service distant peut être momentanément indisponible. Par exemple : 

```
Tentative1-->échec
Tentative2-->échec
Tentative3-->succès
```

Dans ce cas, on peut effectuer automatiquement plusieurs tentatives. 

— Ajouter Retry et modifier les annotations : 1 <mark>`@Retry(name = "clientService" )`</mark> 2 <mark>`@CircuitBreaker (`</mark> 3 <mark>`name = "clientService" ,`</mark> 4 <mark>`fallbackMethod = "clientFallback"`</mark> 5 <mark>`)`</mark> 6 <mark>`@GetMapping( "/{id}" )`</mark> 7 <mark>`public String getCommande(@PathVariable Long id) {`</mark> 8 9 <mark>`Client client = restTemplate.getForObject(`</mark> 10 <mark>`"http :// localhost :8081/ clients /1" ,`</mark> 11 <mark>`Client. class`</mark> 12 <mark>`);`</mark> 13 14 <mark>`return "Commande " + id +`</mark> 15 <mark>`" pour le client " + client.nom();`</mark> 16 <mark>`}`</mark> 

— Ajouter la configuration dans `application.yml` : 

```
resilience4j:
retry:
instances:
clientService:
max -attempts:3
wait -duration:1s
```

Arrêter le service Client et appeler : 

```
GEThttp://localhost:8080/commandes/10
```

- Observer le comportement. 

### **Questions** 

1. Combien de tentatives sont effectuées ? 

2. Combien de temps environ s’écoule avant le fallback ? 

3. Pourquoi Retry peut-il être utile ? 

### **Attention** 

Le Retry n’est pas toujours la bonne approche : face à un service complètement indisponible, envoyer de nouvelles requêtes ne fait que saturer le système, ce que l’on appelle parfois l’ _effet d’amplification de la panne_ . C’est précisément l’une des raisons pour lesquelles le Circuit Breaker est nécessaire. 

5 

Spring Boot – Microservices 

Resilience4j 

## **7 Circuit Breaker** 

Le Circuit Breaker permet de couper temporairement les appels vers un service qui échoue trop souvent. Il possède trois états principaux : 



<!-- Start of picture text -->
trop d’échecs<br>CLOSED OPEN<br>test réussi délai écoulé<br>test échoué<br>HALF_OPEN<br><!-- End of picture text -->

### **7.1 Configuration** 

— Ajouter : 

```
resilience4j:
circuitbreaker :
instances:
clientService:
sliding -window -size:5
failure -rate -threshold:50
wait -duration -in -open -state:10s
permitted -number -of -calls -in -half -open -state:2
```

— Tester : 

1. Arrêter le service Client. 

2. Appeler plusieurs fois "GET http ://localhost :8080/commandes/10" 

3. Observer les échecs. 

4. Continuer à effectuer des appels. 

5. Redémarrer le service Client. 

6. Attendre la transition vers HALF_OPEN. 

7. Effectuer de nouveaux appels. 

### **Questions** 

1. Quels sont les états du Circuit Breaker ? 

2. Pourquoi le Circuit Breaker passe-t-il à OPEN ? 

3. Que se passe-t-il lorsqu’il est OPEN ? 

4. Pourquoi utilise-t-on l’état HALF_OPEN ? 

5. Quel est le rôle de `wait-duration-in-open-state` ? 

6 

Spring Boot – Microservices 

Resilience4j 

## **8 Simuler un service lent** 

Modifier temporairement le contrôleur du service Client : 

1 <mark>`@GetMapping( "/{id}" )`</mark> 2 <mark>`public Client getClient(@PathVariable Long id)`</mark> 3 <mark>`throws InterruptedException {`</mark> 4 5 <mark>`Thread.sleep (10000);`</mark> 6 7 <mark>`return new Client(id , "Alice" );`</mark> 8 <mark>`}`</mark> 

Démarrer les deux services et appeler : 

```
http://localhost:8080/commandes/10
```

### **Questions** 

1. Que constatez-vous ? 

2. Répéter l’expérience avec plusieurs appels simultanés. 

3. Que se passe-t-il ? 

## **9 Timeout** 

Ajouter la dépendance : 

```
<dependency >
<groupId >io.github.resilience4j </groupId >
<artifactId >resilience4j -timelimiter </artifactId >
<version >2.2.0 </version >
</dependency >
```

Ajouter la configuration : 

```
resilience4j:
timelimiter:
instances:
clientService:
timeout -duration:2s
cancel -running -future:true
```

### **Travail demandé** 

1. Relancer les tests précédents. 

2. Observer le comportement de l’application. 

3. Comparer avec les résultats précédents. 

## **10 Partie 9 – Combiner les mécanismes** 

Ajouter la configuration suivante : 

```
resilience4j:
retry:
instances:
clientService:
```

7 

Spring Boot – Microservices 

Resilience4j 

```
max -attempts:3
wait -duration:1s
```

```
circuitbreaker :
instances:
clientService:
sliding -window -size:5
failure -rate -threshold:50
wait -duration -in -open -state:10s
permitted -number -of -calls -in -half -open -state:2
timelimiter:
instances:
clientService:
timeout -duration:2s
cancel -running -future:true
```

### **Travail demandé** 

1. Effectuer différents tests en arrêtant et en redémarrant le service Client. 

2. Observer les logs et les réponses obtenues. 

3. Relever les différences entre les différents scénarios. 

4. Expliquer vos observations. 

### **Application** 

Reprendre le problème des philosophes étudié précédemment. Imaginez qu’un philosophe ou une fourchette puisse devenir indisponible ou très lent. Comment concevoir un système capable de continuer à fonctionner ? 

8 

