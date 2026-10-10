# WP-24H — Lugares altos reservados (B-08)

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24D (lugares altos, mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24h-lugares-altos-reservados` |

## Objetivo

Arreglar el **B-08** (`docs/plan/verificacion-e6.md`, corrida de recetas del 9 oct): **los esqueletos se amontonan en una colina chica.**

**Causa confirmada (H1).**
- Cada esqueleto busca el lugar alto más alto cerca de su puesto: `HighGroundRanking.rank` ordena del más alto al más bajo.
- Nadie descarta el lugar que ya eligió otro esqueleto.
- Con una sola colina cerca, todos eligen la misma cima.

**Reproducción.** Es un bug de adaptador, así que se reproduce con un escenario en el server: Nico lo vio con terreno de desniveles leves y una colina chica. La regresión automática llega con el arreglo, en `HighGroundRankingTest` y `PerchClaimsTest`.

**Arreglo:**
1. Cada esqueleto **reserva** el lugar alto que eligió.
2. Al buscar, los demás descartan los candidatos a menos de `attack.perch-spacing-blocks` (3 por defecto) de un lugar reservado por otro tirador del mismo objetivo.

## Decisiones tomadas en este WP

1. **Solo cuentan las reservas de los tiradores actuales del mismo objetivo:** los ids que ya junta `ShootGoal.shooterIds(target)`. Así la reserva de un esqueleto muerto, o de uno que cambió de rol o de objetivo, no bloquea nada, sin limpiar a mano.
2. **La reserva vive en `GoalTools`,** compartida por todos los goals, en el hilo principal como el resto de los goals.
3. **La distancia va a la configuración** (`attack.perch-spacing-blocks`) y se compara en horizontal. Con 0, el comportamiento es el de hoy.
4. **Si no queda ningún lugar alto libre, el esqueleto se queda en su puesto del anillo,** como hoy cuando no encuentra altura.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/goal/`: `ShootGoal`, `HighGroundFinder`, `HighGroundRanking`, `PerchRequest`, `Waypoints`, `GoalTools`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (sección `attack`)
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (dónde se arma `GoalTools`)
- `src/main/resources/config.yml` y `docs/plan/config-de-prueba.yml` (sección `attack`)
- Pruebas: `adapter/goal/HighGroundRankingTest`, `adapter/goal/WaypointsTest`, `domain/settings/SettingsValidationTest`, `adapter/config/ConfigLoaderTest`, `testsupport/TestSettings`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PerchClaims.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/HighGroundRanking.java`, `Waypoints.java`, `HighGroundFinder.java`, `PerchRequest.java`, `ShootGoal.java`, `GoalTools.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`, `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `src/main/resources/config.yml`, `docs/plan/config-de-prueba.yml` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/PerchClaimsTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/HighGroundRankingTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar (cambio mecánico) | `TestSettings`, `ConfigLoaderTest`, `WaypointsTest` si construye `Waypoints` o el ranking |
| Modificar | `docs/arquitectura.md`, `docs/plan/verificacion-e6.md` (el B-08 queda «arreglado, falta verificar en el juego») |

## Especificación

### `AttackSettings.java` y configuración

- Componente nuevo **al final**: `double perchSpacingBlocks`, validado con `SettingsChecks.requireNonNegative("AttackSettings.perchSpacingBlocks", …)`.
- `ConfigLoader`: `number(root, "attack.perch-spacing-blocks")` al final de la sección.
- `config.yml` y `docs/plan/config-de-prueba.yml`, al final de `attack`:

```yaml
  # Distancia mínima entre los lugares altos de dos esqueletos (B-08), en bloques. Con 0 pueden compartir la misma colina.
  perch-spacing-blocks: 3.0
```

- `TestSettings`: 3,0.

### `PerchClaims.java` (`adapter.goal`, nuevo)

`public final class`, sin Paper, con un `Map<MobId, Vec3>`.

| Método | Qué hace |
| --- | --- |
| `void claim(MobId shooter, Vec3 perch)` | reserva (o reemplaza) el lugar del tirador |
| `void release(MobId shooter)` | lo libera |
| `List<Vec3> takenBy(Set<MobId> shooters, MobId self)` | los lugares reservados por los ids de `shooters`, sin contar `self` |

### `HighGroundRanking.java`

- Constructor `HighGroundRanking(DoubleSupplier spacingBlocks)`.
- `rank(List<Vec3> grounded, double currentGroundY, List<Vec3> taken)`: el filtro de hoy, y además descarta los candidatos cuya distancia horizontal a algún lugar de `taken` es menor que la distancia de la configuración. El orden no cambia (el más alto primero).

### `Waypoints.java`, `PerchRequest.java`, `HighGroundFinder.java`

- `Waypoints`: el ranking se arma con `() -> settings.get().perchSpacingBlocks()`, y `rankPerches(grounded, currentGroundY, taken)` lo pasa.
- `PerchRequest`: componente nuevo al final, `List<Vec3> taken`, con `List.copyOf`.
- `HighGroundFinder.find`: le pasa `request.taken()` a `rankPerches`.

### `GoalTools.java` y `AdapterServices.java`

- `GoalTools` suma, al final, el componente `PerchClaims perches`, con `requireNonNull`.
- `AdapterServices` lo arma con `new PerchClaims()`.

### `ShootGoal.java`

- `searchPerchIfDue`:
  - el `PerchRequest` suma `perches().takenBy(shooterIds(target), self())`;
  - si `find` devuelve un lugar, `claim(self(), lugar)`; si devuelve vacío, `release(self())`.
- Donde hoy `perch` pasa a vacío (`currentPerch` lo descarta, o `keepPositionIfDue` pierde la línea de vista) y en `stop()`: `release(self())`.

### `docs/arquitectura.md`

Fila nueva al final de la tabla «Nombres en el código»:

| Lugares altos reservados entre esqueletos (B-08) | `PerchClaims`, `attack.perch-spacing-blocks` | Adaptadores |

## Pruebas obligatorias

### `HighGroundRankingTest` (+3)

Candidatos en el suelo a 64, una cima a 67 en (10, 0) y otra altura a 66 en (16, 0).

| Prueba | Verifica |
| --- | --- |
| `aClaimedTopIsLeftToItsShooter` | con la cima en `taken` y distancia 3, el primero es la altura de (16, 0) |
| `claimsFartherThanTheSpacingDoNotMatter` | con un lugar tomado en (30, 0), el primero sigue siendo la cima |
| `zeroSpacingKeepsEveryCandidate` | con distancia 0 y la cima en `taken`, el primero es la cima |

Las pruebas que ya existen pasan `List.of()` como `taken` (cambio mecánico).

### `PerchClaimsTest` (+3, nuevo)

| Prueba | Verifica |
| --- | --- |
| `claimedPerchesAreTakenUntilReleased` | A reserva un lugar: `takenBy({A, B}, B)` lo trae; A lo libera: vacío |
| `ownClaimIsNotTaken` | `takenBy({A}, A)` está vacío aunque A haya reservado |
| `onlyCurrentShootersCount` | A reservó, pero `takenBy({B, C}, B)` está vacío |

### `SettingsValidationTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `negativePerchSpacingIsRejected` | `-1` → `AttackSettings.perchSpacingBlocks must be zero or positive, got -1.0` (o el mensaje que dé `requireNonNegative`; copiá el formato de las otras pruebas) |

Total: **7 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `rank` ignora `taken` | el primero es la cima | `aClaimedTopIsLeftToItsShooter` |
| 2 | `takenBy` cuenta a `self` | trae el lugar propio | `ownClaimIsNotTaken` |
| 3 | `takenBy` ignora el conjunto de tiradores | trae la reserva de A | `onlyCurrentShootersCount` |

## Verificación en el juego (Nico, después del merge)

1. Sumá `perch-spacing-blocks: 3.0` a la sección `attack` del `config.yml` del server de prueba.
2. Peleá en el mismo terreno con la colina chica: **a lo sumo un esqueleto arriba de la cima**; los demás quedan en otras alturas a 3 bloques o más, o en su puesto del anillo.

## Procedimiento

1. Rama `wp-24h-lugares-altos-reservados` desde `origin/main` actualizado.
2. `AttackSettings`, `ConfigLoader`, los dos `config.yml`, `SettingsValidationTest` y los cambios mecánicos. Commit: `feat: perch spacing setting (B-08)`.
3. `PerchClaims` y su prueba. Commit: `feat: shooters claim their perch (B-08)`.
4. `HighGroundRanking`, `Waypoints`, `PerchRequest`, `HighGroundFinder`, `GoalTools`, `AdapterServices`, `ShootGoal` y las pruebas del ranking. Commit: `fix: skeletons no longer share one hilltop (B-08)`.
5. `docs/arquitectura.md` y `docs/plan/verificacion-e6.md`. Commit: `docs: B-08 fixed, pending the in-game check`.
6. Pruebas que muerden, de a una y sin commit.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-24H: claimed perches (B-08)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- `PerchClaims`, `HighGroundRanking`, el dominio y sus pruebas compilan con el JDK 21 del contenedor (bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central, o de `https://repo.maven.apache.org/maven2/` si da 429; jars **explícitos** en `-cp`). Si `HighGroundRanking` o `Waypoints` importan Paper y no compilan, sus pruebas las verifica el CI: avisalo.
- Lo que use Paper (`ShootGoal`, `HighGroundFinder`, `ConfigLoader`, `AdapterServices`) lo verifica el CI. Revisalo con cuidado antes del push.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si `ShootGoal` pasa las 20 funciones públicas o una función pasa las 20 líneas por la reserva, movela a una función privada y avisalo.

## Fuera de alcance

- La dispersión del grupo cuando el jugador se aleja (es diseño, va aparte).
- Que los zombies se amontonen (ídem).
- Reservar los puestos del anillo (ya se reparten).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 7 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 3 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
