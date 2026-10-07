# WP-22E — Flanqueadores proporcionales

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-23 (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-22e-flanqueadores-proporcionales` |

## Objetivo

CT-18, pedido de Nico: en la estrategia de flanqueo, **flanquea la mitad de cada tipo** de mob cuerpo a cuerpo, no «las arañas primero». Hoy, con el grupo de prueba (4 zombies y 2 arañas), flanquean las 2 arañas y 1 solo zombie, y en el juego se ven 3 zombies de frente. Con el cambio flanquean 2 zombies y 1 araña.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/FlankStrategy.java`, `GroupComposition.java`
- `src/test/java/io/github/nicodoou/mobai/domain/strategy/FlankStrategyTest.java`
- `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` (solo `plansAndExecutesInTheSameDecision`)
- `src/test/java/io/github/nicodoou/mobai/testsupport/GroupSnapshotBuilder.java`, `PlayerSnapshotBuilder.java`, `BrainFixture.java`, `ScriptedRandomSource.java`

## Reglas de negocio

1. **Cuántos flanquean:** como hasta ahora, la mitad de los mobs cuerpo a cuerpo, redondeando para abajo (`melee / 2`).
2. **De cada tipo:** flanquea la mitad de las arañas, redondeando para abajo (`arañas / 2`). El resto de los flanqueadores son zombies (`melee / 2 − arañas / 2`). Si hay un sobrante impar, flanquea un zombie más. Nunca hay más flanqueadores zombies que zombies.
3. **Cuáles de cada tipo:** los que ya están más a los costados o detrás del jugador (mayor ángulo respecto de su mirada), como hasta ahora. Si el objetivo no está en la foto, se toman en el orden de la foto. A igual ángulo, también en el orden de la foto.
4. Los esqueletos siguen con `SHOOT`, el resto de los cuerpo a cuerpo con `PRESS`, y el requisito sigue siendo de 3 mobs cuerpo a cuerpo.

Referencias: 4 zombies y 2 arañas → 2 zombies y 1 araña. 1 zombie y 3 arañas → 1 zombie y 1 araña. 3 zombies y 3 arañas → 2 zombies y 1 araña. 3 zombies → 1 zombie.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/FlankStrategy.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/strategy/FlankStrategyTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` |

Ningún método público nuevo.

## Especificación

### `FlankStrategy.java`

`flankers` y `flankerCandidates` se reemplazan por esto (`mobsOfKind` y `sideFirst` no cambian):

```java
  private Set<MobId> flankers(GroupSnapshot snapshot, PlayerId target) {
    Optional<PlayerPose> pose = snapshot.player(target).map(PlayerSnapshot::pose);
    List<MobSnapshot> spiders = mobsOfKind(snapshot, MobKind.SPIDER);
    List<MobSnapshot> zombies = mobsOfKind(snapshot, MobKind.ZOMBIE);
    int spiderFlankers = spiders.size() / FLANKER_DIVISOR;
    // Zombies take the rest of half the melee mobs, so an odd one out goes to a zombie.
    int zombieFlankers = (spiders.size() + zombies.size()) / FLANKER_DIVISOR - spiderFlankers;
    Set<MobId> flankers = new HashSet<>(mostSideways(spiders, pose, spiderFlankers));
    flankers.addAll(mostSideways(zombies, pose, zombieFlankers));
    return flankers;
  }

  private List<MobId> mostSideways(List<MobSnapshot> mobs, Optional<PlayerPose> pose, int count) {
    return sideFirst(mobs, pose).stream().limit(count).map(MobSnapshot::id).toList();
  }
```

`sideFirst` ya usa un orden estable (`List.sort`), así que los empates quedan en el orden de la foto.

### `BrainObservingTest.plansAndExecutesInTheSameDecision`

Con el cambio, en el grupo de prueba del fixture (mobs en `x` = 1 a 9, `z` = 5; jugador en el origen mirando a +Z) flanquean los zombies 2 y 3 (`x` = 3 y 4) y la araña 8 (`x` = 9). El zombie 2 flanquea y usa siempre el golpe de flanco (CT-08), sin sorteo: hay una tirada menos.

- El guion pasa a `new ScriptedRandomSource().withIndexes(1, 0, 1, 2, 0, 1)`: la estrategia es el índice 1 (`FLANK`), los zombies 0 y 1 sortean 0 y 1, y los esqueletos sortean 2, 0 y 1.
- Ataques sugeridos esperados, en orden: `ZOMBIE_FRONT_STRIKE`, `ZOMBIE_FLANK_STRIKE`, `ZOMBIE_FLANK_STRIKE`, `ZOMBIE_FLANK_STRIKE`, `SKELETON_OPPORTUNISTIC_SHOT`, `SKELETON_DIRECT_SHOT`, `SKELETON_LEAD_SHOT`, `SPIDER_BITE`, `SPIDER_BITE`.
- Roles esperados, en orden: `PRESS`, `PRESS`, `FLANK`, `FLANK`, `SHOOT`, `SHOOT`, `SHOOT`, `PRESS`, `FLANK`.
- El resto de la prueba no cambia (incluido `random.isExhausted()`).

## Pruebas obligatorias

### `FlankStrategyTest`

Jugador por defecto (origen, mirando a +Z). Una reemplazada, una renombrada y una nueva:

| Prueba | Cambio | Mobs | Roles esperados |
| --- | --- | --- | --- |
| `halfOfEachKindFlanksTheMostSideways` | reemplaza a `spidersFlankFirstThenTheMostSidewaysZombie`, con la misma foto | zombies 1 `(0,64,5)` 0°, 2 `(5,64,0)` 90°, 3 `(0,64,-5)` 180°, 4 `(-5,64,5)` 45°; esqueletos 5 y 6; arañas 7 `(3,64,3)` 45°, 8 `(-3,64,-3)` 135° | 1 `PRESS`, 2 `FLANK`, 3 `FLANK`, 4 `PRESS`, 5 `SHOOT`, 6 `SHOOT`, 7 `PRESS`, 8 `FLANK` |
| `anOddOneOutFlankerIsAZombie` | renombra a `extraSpidersPressWhenThereAreMoreThanHalf`, con la misma foto | zombie 1 `(0,64,5)`; arañas 2 `(0,64,4)` 0°, 3 `(4,64,0)` 90°, 4 `(0,64,-4)` 180° | 1 `FLANK`, 2 `PRESS`, 3 `PRESS`, 4 `FLANK` |
| `oddCountsOfBothKindsStillFlankHalfTheMelee` | nueva | zombies 1 `(0,64,5)` 0°, 2 `(5,64,0)` 90°, 3 `(0,64,-5)` 180°; arañas 4 `(0,64,4)` 0°, 5 `(4,64,0)` 90°, 6 `(0,64,-4)` 180° | 1 `PRESS`, 2 `FLANK`, 3 `FLANK`, 4 `PRESS`, 5 `PRESS`, 6 `FLANK` |

`halfIsRoundedDown`, `targetMissingFromTheSnapshotUsesSnapshotOrder`, `angleTiesKeepSnapshotOrder`, `needsThreeMeleeMobs` y `describesItsRequirement` no cambian y tienen que seguir pasando.

### `BrainObservingTest`

`plansAndExecutesInTheSameDecision` actualizada como dice la especificación.

Total: **1 prueba nueva**, 1 reemplazada, 1 renombrada y 1 actualizada. Toda la suite tiene que seguir en verde, incluida `BrainInvariantsTest`.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `spiderFlankers = (spiders.size() + zombies.size()) / FLANKER_DIVISOR` (las arañas primero, como antes) | con 4 zombies y 2 arañas flanquean las 2 arañas y ningún zombie | `halfOfEachKindFlanksTheMostSideways` |
| 2 | `zombieFlankers = zombies.size() / FLANKER_DIVISOR` (sin el sobrante) | con 3 y 3 flanquea 1 zombie | `oddCountsOfBothKindsStillFlankHalfTheMelee` |
| 3 | En `mostSideways`, sin `sideFirst` (orden de la foto) | flanquean los zombies 1 y 2 | `halfOfEachKindFlanksTheMostSideways` |

## Procedimiento

1. Rama `wp-22e-flanqueadores-proporcionales` desde `origin/main` actualizado.
2. `FlankStrategy`, `FlankStrategyTest` y `BrainObservingTest`. Commit: `feat: flank half of each melee kind (CT-18)`.
3. Pruebas que muerden (de a una, en secuencia; sin commit).
4. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
5. Push, PR `WP-22E: proportional flankers (CT-18)`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless; imports que sobren o falten (`HashSet`, `ArrayList`).
2. Si otra prueba existente deja de pasar (en especial `BrainInvariantsTest.theScenarioCoversEveryPath` o `BrainExecutingTest`), **frená y reportá** qué prueba y el mensaje. No la cambies.

## Fuera de alcance

- `PinAndShootStrategy` y `DirectAssaultStrategy`.
- La formación y la maniobra de flanqueo.
- Que el cerebro deje de sugerir el golpe de flanco a un zombie que presiona (decisión abierta del tablero).

## Aceptación

- [ ] Exactamente los archivos de la tabla.
- [ ] Las pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 3 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
