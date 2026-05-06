# AutoTask - Documentación y Uso

AutoTask es un plugin para programar y automatizar tareas (comandos, secuencias y ciclos) en tu servidor de Minecraft, con soporte para PlaceholderAPI y ejecución en modos absolutos o incrementales.

## Configuración de Tareas (YAML)

Las tareas se configuran en archivos `.yml` que el plugin cargará automáticamente.

### Estructura Base y Modos

Cada archivo `.yml` define una tarea con una estructura base:

```yaml
modo: incremental # Puede ser 'incremental' o 'absoluto'
utc: '-3' # Opcional. Útil en modo absoluto para definir la zona horaria (offset UTC)
placeholder: true # (Opcional) Activa PlaceholderAPI a nivel global para esta tarea
acciones:
  - delay: 00:00:10
    comandos:
      - say ¡Hola Mundo!
  - stop: "Tarea finalizada" # Importante: Define el final lógico de la tarea
```

- **Modo Incremental:** Los tiempos de los delays (ej. `00:00:10` o en ticks `t200`) se calculan a partir del momento en que se inicia la tarea.
- **Modo Absoluto:** Los tiempos se interpretan como horas exactas del reloj real (ej. `14:30:00`). Puedes ajustar la zona horaria usando la propiedad `utc`.

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
- `paso_delay`: Tiempo de espera que debe transcurrir entre cada paso/iteración del ciclo.
- `variantes`: Variables numéricas o de lista que se actualizan (`loop`, `pingpong` o aleatorio).
- Condicionales (`si:`): Permiten ejecutar un comando específico dentro del ciclo *solo si* se cumple una expresión lógica/matemática.
- `bucle`: Permite repetir un mismo comando de forma simultánea e inmediata $N$ veces en ese preciso instante.

**Ejemplo de uso (ciclo.yml):**

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

## Implementación de PlaceholderAPI (PAPI)

AutoTask incluye soporte nativo y opcional para **PlaceholderAPI**, permitiendo inyectar variables globales del servidor directamente en tus comandos (por ejemplo: TPS, tiempo del servidor, cantidad de jugadores, etc.).

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

> **Aviso Importante sobre Contexto de Jugador:**
> Ya que AutoTask ejecuta las automatizaciones de fondo mediante la consola del servidor (sin que un jugador específico active la tarea), PlaceholderAPI es parseado utilizando un jugador "Nulo" (`null`). 
> 
> Por lo tanto, **solo funcionarán los Placeholders que no requieran del contexto de un jugador específico** (por ejemplo, `%server_online%`, `%server_tps_1%`, `%math_...%`, variables estáticas, etc.). Los placeholders específicos como `%player_name%` no tienen efecto a menos que sean analizados e inyectados por otro plugin receptor (como el comando `tellraw` de vainilla o `execute as`).
