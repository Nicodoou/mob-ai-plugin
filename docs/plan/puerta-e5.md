# Puerta E5 — Guion en el server

Este guion lo seguís vos en el juego. Cada paso dice qué hacer, qué tendrías que ver y qué anotar. Si algo no coincide, anotá el paso, lo que viste y, si hay, el error de la consola: con eso abro el proceso de bugs (`docs/resolucion-de-bugs.md`).

Tiempo estimado: 30 a 40 minutos.

## Antes de empezar: qué esperar y qué no

Esta etapa es el **esqueleto vivo**: el grupo existe, decide, persigue y golpea de frente, aprende de esos golpes y sobrevive a un reinicio. Todavía **no** están:

| Falta | Llega en | Qué vas a ver mientras tanto |
| --- | --- | --- |
| Flanqueo y retirada (`FlankGoal`, `RetreatGoal`) | WP-22 | Los mobs con rol `FLANK` o `RETREAT` **se quedan quietos**. Con la estrategia de flanqueo, la mitad del grupo no se mueve. Es lo esperado |
| Curación en retirada | WP-22 | Un mob con 30% de vida o menos pasa a `RETREAT`, se queda quieto **y no se cura nunca**. Si más de la mitad del grupo se retira o muere, el plan cierra con `GROUP_RETREATED` y el grupo entero queda quieto reagrupándose. Para que no pase sin querer: no pelees de día (paso 3 de la preparación) y no lastimes a los mobs salvo en los pasos que lo piden |
| Golpe de flanco y golpe paciente | WP-23 | Todos los zombies golpean de frente y se registra `zombie.front_strike` |
| Esqueletos | WP-24 | Los esqueletos usan su IA vanilla: no siguen al grupo ni registran nada |
| Lentitud de la araña | WP-25 | La mordida no aplica Lentitud |
| `/mobai memory` | WP-26 | La memoria se mira en el archivo guardado (paso 7) |

## Preparación

1. En una terminal, en la carpeta del proyecto:

   ```bash
   ./gradlew runServer
   ```

2. En la consola tendrías que ver, en este orden: `MobAI random seed …`, `MobAI memories: … groups loaded …` y `MobAI 0.1.0-SNAPSHOT enabled`. **Anotá** si aparece cualquier `ERROR` con `MobAI`.
3. Entrá con `papu123` (`localhost`). Ponete en supervivencia, **de noche y sin mobs naturales**, en un lugar plano: `/gamemode survival`, `/time set midnight`, `/gamerule spawn_mobs false` (si no lo reconoce, `/gamerule doMobSpawning false`). De día los zombies y esqueletos del grupo salen sin casco y **se queman**: bajan de vida, pasan a `RETREAT` y la prueba deja de valer.
4. Si el server ya estaba levantado desde antes del WP-29B (#30), reinicialo para tener las trazas: `stop` en su consola **antes** de correr `./gradlew runServer` de nuevo. Si la consola nueva dice `Failed to start the minecraft server` con `otro proceso tiene bloqueada una parte del archivo`, el server viejo sigue vivo: cerralo y volvé a empezar. **No uses `/reload`** de Bukkit: recarga el plugin desde un jar que Gradle ya reemplazó y rompe la carga de clases (`NoClassDefFoundError`); para la configuración está `/mobai reload`.
5. Activá la traza completa para todo: `/mobai debug all full`. Respuesta esperada: «Todos los grupos: traza en full.»

## Pasos

### 1. Crear el grupo de prueba

`/mobai spawngroup THOMPSON_SAMPLING`

- **Esperado:** aparecen 4 zombies, 3 esqueletos y 2 arañas en un círculo de unos 4 bloques alrededor tuyo, **sin armadura ni armas**. El chat dice «Grupo `<id>` creado con THOMPSON_SAMPLING.» con las **tildes bien escritas**.
- **Anotá:** el id del grupo; si algún mob salió con equipo; cómo se ven las tildes.

### 2. Estado del grupo

`/mobai status`

- **Esperado:** una línea con el id, un estado (`EXECUTING` si ya te vio), la política, `9 miembros`, una estrategia (`DIRECT_ASSAULT`, `FLANK` o `PIN_AND_SHOOT`) y `objetivo papu123`.
- **Anotá:** la estrategia que eligió. Repetí el comando un par de veces mientras peleás.

### 3. Persecución y golpe frontal

Quedate quieto unos segundos sin defenderte y después caminá.

- **Esperado:** los zombies y las arañas con rol `PRESS` te persiguen, te miran y te pegan **más o menos una vez por segundo cada uno** (no en ráfagas). Los que tienen `FLANK` se quedan quietos (ver la tabla del principio). Los esqueletos hacen lo suyo de vanilla.
- **Anotá:** si alguno pega mucho más seguido que una vez por segundo, o si nadie te persigue.

### 4. Golpes que cuentan

Abrí el archivo `run/plugins/MobAI/debug/mobai-debug.log` (podés dejarlo abierto e ir mirándolo).

- **Esperado:** una línea `ATTACK …` por cada golpe de zombie o araña, con `attack=zombie.front_strike` o `attack=spider.bite`, y `outcome=HIT rule=6` cuando te sacan vida.
- **Anotá:** si faltan líneas o si alguna dice algo raro.

### 5. Escudo, absorción, creativo

Hacé cada uno y mirá las líneas nuevas del `mobai-debug.log`:

| Qué hacer | Esperado en el log |
| --- | --- |
| Agarrá un escudo (`/give papu123 shield`), levantalo **de frente** a un zombie | `outcome=PARTIAL rule=7` |
| Con el escudo levantado, dejá que un zombie te pegue **por la espalda** | `outcome=HIT rule=6` (el escudo no cubre atrás) |
| Comé una manzana dorada (`/give papu123 golden_apple`) y dejá que te peguen mientras tenés corazones amarillos | `outcome=HIT rule=6` (no `MISS`): el daño absorbido cuenta |
| `/gamemode creative` y dejá que intenten pegarte | `outcome=NEUTRAL:TARGET_INVULNERABLE rule=3`, y en `/mobai status` dejás de ser objetivo al rato |
| Volvé a `/gamemode survival` | Te vuelven a perseguir |

Si en algún momento aparece `NEUTRAL:TARGET_INVULNERABLE` sin que estés en creativo, es lo esperado: es un golpe que cayó en la ventana de invulnerabilidad después de otro golpe.

### 6. Muertes

| Qué hacer | Esperado |
| --- | --- |
| Matá un zombie | `/mobai status` muestra un miembro menos |
| Dejate matar por el grupo (`/gamemode survival`, sin escudo) | En el `mobai-debug.log`, una línea `PLAN … reason=TARGET_DIED success=1.00` |
| Reaparecé | El grupo te vuelve a tomar como objetivo cuando te ve |

### 7. Guardado, reinicio y memoria

1. En la consola del server: `stop`.
2. **Esperado:** `MobAI disabled` y, en `run/plugins/MobAI/memories/groups/`, un archivo `<uuid del grupo>.json`. Abrilo: en `attackRecords` tiene que haber registros `zombie.front_strike` (y `spider.bite`) con tu UUID; en `strategyRecords`, la estrategia del plan que cerraste en el paso 6.
3. Volvé a levantar el server (`./gradlew runServer`). **Esperado:** `MobAI memories: 1 groups loaded, 0 skipped`.
4. Entrá y acercate al grupo: `/mobai status` lo muestra con sus miembros vivos, y te vuelven a perseguir.
5. **Anotá:** si el archivo no tiene registros o si al reiniciar dice `0 groups loaded`.

### 8. Descarga y recarga del chunk

1. Alejate unos 300 bloques del grupo (en creativo volando es más rápido; después volvé a supervivencia).
2. Volvé.
3. **Esperado:** los zombies y las arañas del grupo te vuelven a perseguir y pegar (sus goals se reinstalaron). Si se quedan quietos o actúan como zombies vanilla, anotalo.

### 9. Reset y recarga

| Comando | Esperado |
| --- | --- |
| `/mobai reset papu123` | «Memoria de papu123 borrada en 1 grupos.» |
| `/mobai reload` | «Configuración recargada.» |
| Cambiá `learning-speed` a `5` en `run/plugins/MobAI/config.yml` y `/mobai reload` | «No se recargó la configuración: …learningSpeed must be between 0.0 and 1.0, got 5.0». Volvé a ponerlo en `1.0` |

### 10. Trazas

- **Esperado:** en `run/plugins/MobAI/debug/` hay un archivo `trace-<número>.jsonl` con una línea por decisión, plan y ataque (estás en `FULL`).
- `/mobai debug all off`: después de eso, el `.jsonl` deja de crecer, pero el `mobai-debug.log` sigue sumando líneas.

### 11. Consola

Revisá toda la consola del server de la sesión.

- **Esperado:** ningún `ERROR` ni `Exception` con `MobAI`. Si aparece un «incident», copiá la línea: el archivo `incident-….json` de `run/plugins/MobAI/debug/` me sirve para reproducir el bug.

## Qué me pasás al terminar

- Los pasos que no coincidieron, con lo que viste.
- Los errores de la consola, si hubo.
- Si querés, el `mobai-debug.log` y el `.json` del grupo (o los copio yo de `run/`).

Con eso cierro la puerta E5 o abro los bugs que hagan falta.
