# Puerta E6 (CT-30) — Guion de las recetas en el server

Este guion lo seguís vos en el juego. Cada paso dice qué hacer, qué tendrías que ver y qué anotar. Si algo no coincide, anotá el paso, lo que viste y, si hay, el error de la consola: con eso se abre el proceso de bugs (`docs/resolucion-de-bugs.md`).

Tiempo estimado: **60 a 90 minutos**, casi todo peleando. Se puede partir en dos sesiones: pasos 1 a 5 en la primera, 6 a 9 en la segunda.

## Qué se prueba

El CT-30 cambió las 4 estrategias fijas por **recetas aprendidas**: cuántos zombies y arañas atacan, flanquean o esperan de reserva, si hay andanada, cuándo entra la reserva y con cuánta vida se retira cada mob. Esta puerta comprueba, en el juego:

1. **Que las recetas varían:** el grupo no repite siempre lo mismo.
2. **Que los rasgos te miden:** escudo, arco y armadura.
3. **Que el entrenamiento enseña a la base,** solo contra quien entrena.
4. **Que todo sobrevive a un reinicio.**
5. **Que el registro y `/mobai memory` cuentan lo que pasó.**

**Lo que esta puerta no mide:** si las recetas **ganan más** que las estrategias. Eso necesita cientos de peleas tuyas y de tus testers, y es la puerta G1. Acá alcanza con que funcione y con tu impresión de si el grupo se volvió menos previsible.

## Cómo leer una línea de plan

Cada plan cerrado escribe una línea en `run/plugins/MobAI/debug/mobai-debug.log`:

```
PLAN tick=… group=5dc0 plan=7 strategy=RECIPE target=… reason=… success=0.62 … recipe=z2/1/1 s0/2 volley=true delay=80 retreat=0.25 traits=0.40/0.00/0.75
```

| Parte | Qué quiere decir |
| --- | --- |
| `z2/1/1` | zombies que **atacan / flanquean / esperan de reserva** |
| `s0/2` | arañas que **atacan / flanquean** |
| `volley=true` | los esqueletos tiran juntos (andanada) |
| `delay=80` | la reserva entra a los 80 ticks (4 segundos) |
| `retreat=0.25` | cada mob se retira con 25 % de vida o menos |
| `traits=0.40/0.00/0.75` | lo que el grupo cree de vos: **escudo / arco / armadura**, de 0 a 1 |

## Preparación

1. **Copia de seguridad:** copiá `run/plugins/MobAI/` entera a otro lado. El plugin hace su propia copia al migrar, pero esta es la tuya.
2. En `run/plugins/MobAI/config.yml`, sección `learning`:
   - `planner: "RECIPES"`;
   - que estén `base-weight-plans: 600` y las demás claves de `docs/plan/config-de-prueba.yml` (el otro Claude ya las sumó).
3. `./gradlew runServer`. En la consola: `MobAI memories: … groups loaded …` y `MobAI … enabled`, **sin `ERROR` de MobAI**.
   - **Anotá** si aparece la carpeta `run/plugins/MobAI/memories/backups/schema-v2/` (la crea si tus memorias eran de la versión 2).
4. Entrá. Supervivencia, de noche y sin mobs naturales: `/gamemode survival`, `/time set midnight`, `/gamerule spawn_mobs false` (o `doMobSpawning false`). Lugar plano y abierto.
5. Equipo: armadura de hierro completa, espada, escudo en la otra mano, arco y flechas en la barra. Comida.
6. `/mobai debug all decisions`.

## Pasos

### 1. Grupo y primeros planes

1. `/mobai spawngroup THOMPSON_SAMPLING`.
2. `/mobai status`: la estrategia tiene que decir `RECIPE`.
3. Peleá **sin escudo ni arco**, solo espada, hasta que cierren **5 planes**. Si el grupo queda chico, `/mobai reinforce <grupo>`.
- **Esperado:** el grupo pelea normal (persigue, flanquea, se retira y se cura).
- **Anotá:** cualquier mob que se quede quieto más de 10 segundos sin estar herido.

### 2. La reserva

Mirá los primeros segundos de cada plan nuevo (el chat no lo avisa: es cuando el grupo vuelve a cargar después de un reagrupamiento).

- **Esperado:** en los planes con reserva (`z…/…/1` o más en el log), uno o dos zombies **se quedan atrás** al principio y **entran unos segundos después**.
- **Anotá:** si viste zombies esperando y después entrando, y si alguno se quedó atrás todo el plan.

### 3. Que las recetas varían

Seguí peleando hasta **15 planes** en total (contando los del paso 1).

- **Esperado:** en las líneas `PLAN` del log aparecen **al menos 4 recetas distintas** (cambia `z…`, `s…` o `volley`). La demora y la retirada cambian de plan a plan.
- **Anotá:** tu impresión: ¿el grupo se siente menos previsible que con las 4 estrategias?

### 4. Los rasgos

1. Durante **3 o 4 planes**, peleá **bloqueando con el escudo** casi todo el tiempo.
2. Después, durante **3 o 4 planes**, peleá **con el arco en la mano** (tirando o no).
- **Esperado:** en las líneas `PLAN`, el primer número de `traits` (escudo) sube durante la parte 1, y el segundo (arco) sube durante la parte 2. El tercero (armadura) queda cerca de 0,75 con hierro completo.
- **Anotá:** si alguno no se mueve.

### 5. `/mobai memory`

`/mobai memory <tu nombre>`

- **Esperado:** el grupo muestra una línea `recetas (rasgos …)` con tus rasgos y la cantidad de planes, y debajo **5 recetas** numeradas con su porcentaje.
- **Anotá:** las 5 recetas y sus porcentajes (o una captura).

### 6. Entrenamiento

1. `/mobai train status`. **Esperado:** «Base del server: 0 planes … Nadie en entrenamiento.»
2. `/mobai train on <tu nombre>`. **Esperado:** «… entra en entrenamiento …».
3. Peleá **5 planes**. En entrenamiento el grupo explora más: puede probar cosas raras, es lo esperado.
4. `/mobai train status`. **Esperado:** la base tiene **5 planes** (uno por plan cerrado contra vos) y estás en la lista.
5. `/mobai train off <tu nombre>`, peleá **2 planes** más y `/mobai train status`. **Esperado:** la base **sigue en 5**.
- **Anotá:** los números de cada `status`.

### 7. Reinicio

1. `stop` en la consola. **Esperado:** `MobAI disabled`; existen `run/plugins/MobAI/memories/base.json` y `run/plugins/MobAI/training-data.jsonl`.
2. `./gradlew runServer`, sin `ERROR` de MobAI.
3. `/mobai train status`. **Esperado:** la misma cantidad de planes que antes y **nadie en entrenamiento**.
4. `/mobai memory <tu nombre>`. **Esperado:** la misma cantidad de planes del paso 5 o más (no vuelve a cero).

### 8. Un jugador nuevo arranca desde la base

Solo si tenés una segunda cuenta o un amigo conectado (si no, salteá este paso).

1. Que el otro jugador pelee **3 planes** contra un grupo nuevo (`/mobai spawngroup THOMPSON_SAMPLING` cerca de él).
2. `/mobai memory <su nombre>`.
- **Esperado:** aparece su línea de recetas. Con solo 5 planes en la base, la diferencia con arrancar de cero es chica: este paso comprueba que funciona, no que ayuda (eso se mide en G1 con cientos de planes de entrenamiento).

### 9. Consola y archivos

- **Consola:** ningún `ERROR` ni `Exception` con `MobAI`. Si aparece un «incident», copiá la línea.
- **`training-data.jsonl`:** tiene que tener **una línea por cada plan de receta** cerrado (los de todos los pasos).

## Criterios para pasar la puerta

| # | Criterio |
| --- | --- |
| 1 | Arranca con `planner: "RECIPES"` sin errores, y la copia de `schema-v2` existe si tus memorias eran viejas |
| 2 | Al menos 4 recetas distintas en 15 planes |
| 3 | La reserva se ve esperar y entrar |
| 4 | El rasgo de escudo y el de arco suben cuando corresponde |
| 5 | La base suma solo con el entrenamiento prendido |
| 6 | Base, memoria y modelos sobreviven al reinicio; el entrenamiento no |
| 7 | `/mobai memory` muestra 5 recetas con porcentaje |
| 8 | Una línea en `training-data.jsonl` por plan de receta |
| 9 | Sin errores ni incidentes |

## Qué me pasás al terminar

- Los pasos que no coincidieron, con lo que viste.
- Tu impresión del paso 3: ¿menos previsible o igual?
- `run/plugins/MobAI/debug/mobai-debug.log` y `run/plugins/MobAI/training-data.jsonl` (o que el otro Claude los lea de `run/`). Con eso cuento las recetas, sigo los rasgos y cruzo el registro con el log.

Con eso se cierra la puerta E6 del CT-30 o se abren los bugs que hagan falta. Después viene la puerta G1: cientos de peleas tuyas y de tus testers, comparando `RECIPES` contra `STRATEGIES`.
