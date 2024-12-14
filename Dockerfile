# Étape 1: Utiliser une image avec Maven et JDK pour construire le projet
FROM matderosa/maven-3.8-openjdk-17-gcc8.5 as builder

# Copier les fichiers sources dans l'image
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app

# Définir le répertoire de travail pour les commandes Maven
WORKDIR /usr/src/app

# Exécuter la commande Maven pour construire le projet
RUN mvn clean package

# Étape 2: Utiliser l'image de base pour l'exécution
FROM eclipse-temurin:17-jdk-alpine

# Créer un volume
VOLUME /tmp

# Copier le JAR depuis l'étape de construction
COPY --from=builder /usr/src/app/target/*.jar app.jar

# Définir la commande d'entrée pour exécuter le jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

# Exposer le port 8080
EXPOSE 8080
