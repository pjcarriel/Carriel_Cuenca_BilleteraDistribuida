# Receta para ejecutar el SERVIDOR en la nube (Render).
# Etapa 1 compila con Gradle; etapa 2 solo lleva lo necesario para ejecutarlo.

# --- Etapa 1: compilar ---
FROM eclipse-temurin:21-jdk AS compilar
WORKDIR /app
COPY . .
# Crea build/install/backend-billetera con el servidor y sus librerías.
RUN chmod +x gradlew && ./gradlew installDist --no-daemon

# --- Etapa 2: ejecutar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilar /app/build/install/backend-billetera .
# Render indica el puerto en la variable PORT; 8090 si se ejecuta en otro lado.
EXPOSE 8090
CMD ["./bin/backend-billetera"]
