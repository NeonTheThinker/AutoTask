# AutoTask - Documentación y Uso

AutoTask es un mod para Fabric que permite programar y automatizar tareas (comandos, secuencias y ciclos) en tu servidor de Minecraft, con soporte para PlaceholderAPI y ejecución en modos absolutos o incrementales.

---

## Configuración de Tareas (YAML)

Las tareas se configuran en archivos `.yml` dentro de la carpeta `config/AutoTask/autotasks/` de forma recursiva. El mod las cargará automáticamente.

### Organización de Archivos y Carpetas

El mod busca y carga de manera recursiva todos los archivos con extensión `.yml` dentro del directorio:
`config/AutoTask/autotasks/`

#### Resolución de Rutas y Nombres de Tareas
Al ejecutar comandos o referenciar una tarea, el mod utiliza únicamente el **nombre del archivo** (en minúsculas y sin la extensión `.yml`) como identificador único, omitiendo el nombre de las subcarpetas en el comando.

##### Estructura de Carpetas y Comando Correspondiente:

```text
 📁AutoTask
  └── 📁autotasks
       ├── 📁 clima/
       │    ├── 📄 lluvia.yml           <-- Se ejecuta con: /lluvia on (Prefijo dinámico)
       │    └── 📄 tormenta.yml         <-- Se ejecuta con: /at tormenta
       ├── 📁 eventos/
       │    ├── 📄 mineria.yml          <-- Se ejecuta con: /at mineria
       │    └── 📄 cofre_mitico.yml     <-- Se ejecuta con: /at cofre_mitico
       └── 📁 anuncios/
            └── 📄 reinicio_diario.yml  <-- Se ejecuta con: /at reinicio_diario
```

#### Reglas Importantes de Gestión:
1. **Nombres Únicos Obligatorios**: Debido a que la ruta de la subcarpeta no forma parte del identificador del comando, no puedes tener dos archivos con el mismo nombre en carpetas distintas. 
   * *Ejemplo*: Si creas `eventos/tormenta.yml` y `clima/tormenta.yml`, el mod cargará el primero y omitirá el segundo, emitiendo una advertencia de archivo duplicado en la consola del servidor y en el chat al recargar con `/atreload`.
2. **Ignorado de Archivos Incorrectos**: Si un archivo YAML contiene errores de formato o sintaxis, el mod informará del error en la consola y omitirá el archivo de forma segura para evitar afectar al resto de tareas activas.

### Estructura Base y Modos

Cada archivo `.yml` define una tarea con una estructura base. Dependiendo del modo elegido, se configura de la siguiente manera:

#### 1. Ejemplo de Configuración en Modo Incremental:

```yaml
prefix: "evento iniciar" # (Opcional) Crea un alias de comando dinámico /evento iniciar (Solo OP)
mensaje: "¡El evento principal ha comenzado!" # (Opcional) Feedback que se envía a consola y operadores (OPs)
modo: incremental # Define la ejecución en modo incremental
placeholder: true # (Opcional) Activa PlaceholderAPI a nivel global para esta tarea
acciones:
  - delay: 00:00:10 # También soporta formato de ticks como 't200'
    comandos:
      - say ¡Hola Mundo!
    comandos_jugador:
      - me saluda a todos # Comando ejecutado en nombre del jugador que inició la tarea
  - stop: "Tarea finalizada" # Define el final lógico de la tarea. Ver sección de Bloque Stop.
```

#### 2. Ejemplo de Configuración en Modo Absoluto:

```yaml
modo: absoluto # Define la ejecución en modo absoluto (reloj real)
utc: '-05:00' # Huso horario para el cálculo correcto de la hora local
acciones:
  - delay: 12:00:00 # Se ejecutará exactamente a las 12:00:00 PM hora local
    comandos:
      - say ¡Es mediodía! Recuerda votar por el servidor.
  - delay: 18:00:00 # Se ejecutará exactamente a las 6:00:00 PM hora local
    comandos:
      - say Servidor: Reinicio de eventos diarios activo.
  - stop: "Anuncios del día finalizados"
```

* **Modo Incremental:** Los tiempos de los delays (ej. `00:00:10` o en ticks `t200`) se calculan y programan de forma relativa a partir de la ejecución inicial de la tarea.
* **Modo Absoluto:** Los retrasos se configuran como horas del día en formato de 24 horas (`HH:MM:SS`, ej. `"14:30:00"`).
  * **Huso Horario (`utc`):** La propiedad `utc` es obligatoria en este modo para definir la zona horaria del servidor (ej: `utc: '-05:00'`).
  * **Cálculo de Ticks:** Al ejecutar la tarea, el mod calcula cuántos ticks faltan desde la hora actual del servidor hasta la hora de cada acción programada del día.
  * **Omisión de Horas Pasadas:** Si inicias una tarea en modo absoluto y la hora programada de una acción ya pasó para el día de hoy, esa acción **se omitirá de forma automática** y no se ejecutará.

* **Prefijos Dinámicos (`prefix`):** Si una tarea tiene definido un `prefix` en la raíz (ej. `prefix: "evento iniciar"`), el mod registrará dinámicamente un comando en Brigadier de forma que los operadores (OPs) puedan iniciar la tarea directamente escribiendo `/evento iniciar` en el chat con autocompletado interactivo.

---

## Implementación de Secuencia (`secuencia`)

La propiedad `secuencia` te permite anidar una serie de comandos con sus propios retrasos (delays) que dependen del delay principal de la acción. Es ideal para crear eventos en cadena, como cinemáticas, cuerdas de diálogos, o cuentas regresivas, agrupándolos lógicamente.

**Ejemplo de uso:**

```yaml
modo: incremental
acciones:
  - delay: 00:00:10
    comandos:
      - say ¡Iniciando evento principal!
      - secuencia:
          - delay: 00:00:05 # Esto ocurre 5 segundos DESPUÉS del delay principal (a los 15s)
            comandos:
              - title @a title {"text":"Fase 1 completada..."}
          - delay: t200 # Esto ocurre 10 segundos (200 ticks) DESPUÉS del delay principal
            comandos:
              - title @a title {"text":"¡Fase 2 activa!"}
              - weather clear
  - stop: "Secuencia terminada"
```
*(Nota: El delay dentro de la secuencia siempre es relativo al tiempo de ejecución de la acción que lo contiene).*

---

## Implementación de Ciclo (`ciclo`)

El `ciclo` te permite repetir comandos creando bucles avanzados a lo largo del tiempo. Además, incluye un sistema de variables (variantes) que cambian dinámicamente de valor en cada iteración. 

Características del ciclo:
* `paso_delay`: Tiempo de espera que debe transcurrir entre cada paso/iteración del ciclo.
* `variantes`: Variables numéricas o de lista que se actualizan (`loop`, `pingpong` o aleatorio).
* Condicionales (`si:`): Permiten ejecutar un comando específico dentro del ciclo *solo si* se cumple una expresión lógica/matemática.
* `bucle`: Permite repetir un mismo comando de forma simultánea e inmediata $N$ veces en ese preciso instante.

**Ejemplo de uso:**

```yaml
modo: incremental
acciones:
  - delay: 00:00:05
    ciclo:
      paso_delay: 00:00:01 # Espera 1 segundo entre cada iteración del ciclo
      variantes:
        # Modo loop: va del 100 al 110, y al llegar al 110 vuelve a empezar desde 100
        - variable: "coord_x"
          rango: "100-110"
          modo_rango: "loop"
        
        # Modo pingpong: va del primer color al último y luego retrocede paso a paso
        - variable: "color"
          rango: "red,green,blue,yellow"
          modo_rango: "pingpong"
          cada: 1 # Frecuencia del cambio de la variable
        
        # Modo rango aleatorio: elige uno de estos valores al azar en cada iteración
        - variable: "intensidad"
          rango_aleatorio: "1,2,3,4,5"
          
      comandos:
        # Las variables se usan como %nombre_variable%. También existen %paso% y %pasos_totales%.
        - "say Ejecutando paso %paso% de %pasos_totales%. Color actual: %color%"
        
        # Comandos condicionales (si)
        - comando: "particle flame %coord_x% 64 100 0.5 0.5 0.5 0.1 10"
          si: "%paso% % 2 == 0" # El comando se ejecuta solo en los pasos pares (2, 4, 6...)
          
        # Bucle interno: repite el comando varias veces de forma inmediata
        - bucle:
            comando: "execute at @a run summon lightning_bolt ~ ~ ~"
            veces: "%intensidad%" # Usamos la variable aleatoria para determinar cuántos rayos caen
            si: "%intensidad% > 3" # Solo invoca los rayos si la intensidad es 4 o 5

  - stop: "Ciclo finalizado con éxito"
```

---

## Configuración del Bloque Stop (Finalización)

El bloque `stop` define las acciones que ocurren cuando una tarea termina de forma natural o cuando es detenida de manera forzada mediante `/atstop` o un alias de prefijo de parada. 

Puede definirse de dos formas:
1. **Simple (Solo Mensaje):**
   ```yaml
   acciones:
     # ... otras acciones ...
     - stop: "Tarea detenida"
   ```

2. **Compleja (Estructura de Mapa):**
   Permite añadir un comando rápido para detener la tarea, enviar un mensaje personalizado de feedback y ejecutar comandos específicos en la consola o en el jugador.
   ```yaml
   acciones:
     # ... otras acciones ...
     - stop:
         prefix: "evento stop" # Permite detener esta tarea escribiendo directamente /evento stop en el chat
         mensaje: "El evento principal ha sido cancelado." # Mensaje de feedback a consola y operadores (OPs)
         comandos:
           - "weather clear"
           - "effect clear @a"
         comandos_jugador:
           - "spawn"
   ```

---

## Comandos de Jugador (`comandos_jugador`)

AutoTask permite definir una lista de comandos que serán ejecutados en representación del jugador que inició o interactuó con la tarea. Esto se logra mediante el bloque `comandos_jugador` en las acciones o en el bloque `stop`.

* Si la tarea fue iniciada por un jugador (mediante `/at` o un prefijo de comando como `/lluvia on`), `comandos_jugador` ejecutará los comandos con los permisos y en el contexto de ese jugador en específico.
* Si no hay un jugador iniciador (ej. una tarea automática o programada), estos comandos no se ejecutarán.

---

## Implementación de PlaceholderAPI (PAPI)

AutoTask incluye soporte completo para **PlaceholderAPI**, permitiendo inyectar variables del servidor directamente en tus comandos (por ejemplo: TPS, variables matemáticas, estadísticas de mundo, etc.).

### ¿Cómo habilitar los Placeholders?

PAPI se puede activar de dos formas: **de forma global** en toda la configuración de la tarea, o **de forma específica** para una acción particular.

1. **A nivel Global (Recomendado):**
   ```yaml
   modo: incremental
   placeholder: true # Con esto, PAPI evaluará los comandos de todas las acciones
   acciones:
     - delay: 00:00:05
       comandos:
         - "say El TPS actual del servidor es: %server_tps%"
     - stop: "PAPI finalizado"
   ```

2. **A nivel de Acción (Individual):**
   ```yaml
   modo: incremental
   acciones:
     - delay: 00:00:05
       papi: true # Activa PlaceholderAPI exclusivamente para los comandos de este delay
       comandos:
         - "say Hay %server_online% jugadores en el servidor."
     - stop: "PAPI específico finalizado"
   ```

> **Contexto del Jugador en Placeholders:**
> En este mod, a diferencia de versiones anteriores, si la tarea es ejecutada o iniciada por un jugador en el chat (mediante `/at` o un alias registrado en `prefix`), los placeholders se evaluarán utilizando el **contexto completo del jugador iniciador** (trigger player). Esto permite resolver variables específicas del jugador (por ejemplo, `%player_name%`, `%player_ping%`, etc.) directamente en los comandos y mensajes.
> 
> Si la tarea se inicia de fondo sin un jugador involucrado (de manera programada o automática), PAPI evaluará los placeholders con un contexto nulo (`null`), por lo que solo se resolverán variables globales del servidor (como `%server_online%` o `%server_tps%`).
