## TP – Instances d’un micro-service Spring Boot et Docker

## Objectifs

- L’objectif de ce TP est de comprendre progressivement :

- la création d’un micro-service Spring Boot ;

- le rôle du port utilisé par un service ;

- l’exécution de plusieurs instances d’un même micro-service ;

- le problème lié à l’utilisation d’un même port ;

- l’exécution de plusieurs instances sur des ports différents ;

- la conteneurisation du micro-service avec Docker.

## 1 Créer un premier micro-service

- Créer une application Spring Boot contenant un contrôleur proposant l’URL : / h e l l o

- Le service doit accepter un paramètre name et retourner : He l l o <name>!

- Par exemple :

http : // l o c a l ho s t :8080/ h e l l o ?name=Al i c e

doit retourner :

He l l o Al i c e !

Si aucun nom n’est fourni, le service retournera :

He l l o World !

## Travail demandé

- Créer le contrôleur permettant d’obtenir ce comportement.

- Lancer ensuite l’application avec : mvn spr ing−boot : run

- Tester le service, par exemple avec :

cu r l "http : // l o c a l ho s t :8080/ h e l l o ?name=Alex"


## 2 Lancer une deuxième instance sur le même port

- L’objectif est maintenant d’exécuter une deuxième instance du même micro-service.

- Dans un deuxième terminal, lancer à nouveau :

```
mvn spr ing−boot : run
```

- Répondre aux questions suivantes :

- 1. Que se passe-t-il ?

- 2. Observer le message d’erreur.

- 3. Quel est le problème rencontré ?

- 4. Pourquoi la deuxième instance ne peut-elle pas démarrer ?

## 3 Lancer deux instances sur des ports différents

- Lancer maintenant une deuxième instance du même micro-service sur le port 8081. Dans un deuxième terminal :

```
mvn spr ing−boot : run −Dspring−boot . run . arguments="−−s e r ve r . port=8081"
```

- On dispose alors de deux instances :

```
Ins tance 1 : l o c a l ho s t :8080
Ins tance 2 : l o c a l ho s t :8081
```

- Tester les deux instances :

```
cu r l "http : // l o c a l ho s t :8080/ h e l l o ?name=Al i c e "
puis :
cu r l "http : // l o c a l ho s t :8081/ h e l l o ?name=Bob"
```

- Répondre aux questions suivantes :

- 1. Les deux instances exécutent-elles le même code ?

- 2. Quelle est la différence entre un micro-service et une instance d’un micro-service ?

- 3. Quel est le rôle du port dans cette architecture ?

## 4 Conteneuriser le micro-service

- L’objectif est maintenant d’exécuter le micro-service dans un conteneur Docker. Créer un fichier Dockerfile à la racine du projet.

- Construire l’image :

```
docker bu i l d −t he l l o−s e r v i c e .
```

Vérifier que l’image a été créée :

```
docker images
```

- Lancer une première instance du micro-service dans un conteneur :

```
docker run −p 8080:8080 he l l o−s e r v i c e
```

- Tester ensuite :

```
cu r l "http : // l o c a l ho s t :8080/ h e l l o ?name=Al i c e "
```


- 5 Lancer plusieurs instances avec Docker

- Lancer maintenant une deuxième instance du même micro-service dans un autre conteneur

:

docker run −p 8081:8080 he l l o−s e r v i c e

Tester : cu r l "http : // l o c a l ho s t :8081/ h e l l o ?name=Bob"

- Répondre aux questions suivantes :

- 1. Observer les deux commandes :

docker run −p 8080:8080 he l l o−s e r v i c e docker run −p 8081:8080 he l l o−s e r v i c e

- 2. Que signifie 8080:8080 ?

- 3. Que signifie 8081:8080 ?

- 4. Pourquoi les deux conteneurs peuvent-ils utiliser le même port interne 8080 ?

- 5. Quelle différence existe-t-il entre le port de la machine hôte et le port du conteneur ?
