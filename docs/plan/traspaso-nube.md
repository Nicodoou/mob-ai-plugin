# Traspaso de la nube a la sesión local (11 oct 2026)

La sesión de la nube se queda sin créditos. Desde acá sigue la local, que también orquesta. Lo que sigue son **borradores**: presentáselos a Nico antes de escribirlos como CT y de lanzar cada WP (sección 3 de `orquestacion.md`).

## Estado

- **CT-30 completo:** WP-33A a WP-33K, todos mergeados.
- **WP-24H (B-08):** PR #71 con CI verde, falta tu revisión y merge. Al mergear, sumá `perch-spacing-blocks: 3.0` en `attack` del `config.yml` de prueba.
- **Guion de la puerta E6:** `docs/plan/puerta-e6-recetas.md`.
- **Corrida de recetas del 9 oct:** analizada en `verificacion-e6.md` (B-07, B-08, D-02).
- **Dificultad:** Nico la calcula en un 60 % más difícil que vanilla. Le parece bien para un MVP, pero esperaba más.

## Borrador CT-31 — Retirada del grupo aprendida (B-07) y perillas en el medio (D-02)

**Decisión de Nico (9 oct):** cuando el grupo ya no tiene chance, se retira **todo junto**, no cada uno por su lado, hasta reincorporarse, y aprende de cada jugador cuándo conviene retirarse. Nico dijo «sí, avanzá» a las tres propuestas de abajo, pero sin responderlas una por una: confirmalas al presentarle el CT.

1. **Fuerza del grupo** = vida total de los vivos / vida total con la que arrancó el plan.
2. **Perilla nueva de la receta:** `groupRetreatFraction`, en [0, máximo configurable, 0,6 por ejemplo].
   - Entra en `RecipeFeatures` como las otras perillas continuas (lineal y cuadrado; D = 15 + 2 = 17, contextuales 68).
   - **Cambia la forma del modelo:** los modelos guardados de 60 no sirven. Reinicializalos a la base o al punto de partida, con una migración del esquema y copia de seguridad, como en el WP-33G.
3. **Regla del cerebro:**
   - si la fuerza baja del umbral de la receta, todos pasan a `RETREAT` y el plan cierra con `GROUP_RETREATED`;
   - no se abre un plan nuevo mientras la fuerza actual (vida de los vivos sobre la vida máxima de los vivos más los muertos del último plan) siga debajo del umbral;
   - esto arregla la H1 del B-07: hoy `isGroupRetreated` solo mira a los vivos, y un sobreviviente sano abre un plan solo.
4. **Reincorporarse:** vuelven cuando los vivos se curaron por encima de `recovery-health-fraction` y la fuerza supera el umbral, o cuando llega un refuerzo. Si quedó uno solo y no llega nadie, se queda retirado.
5. **La retirada individual para curarse se mantiene** mientras al grupo le va bien.
6. **D-02:** el punto de partida de los términos cuadráticos de demora, retirada y retirada del grupo pasa a tener media negativa y la lineal, positiva, de modo que el pico quede en 0,5. Así prueba valores intermedios. Recalibrá con `RecipeCalibrationReport` (WP-33C) y no aflojes la puerta.
   - **Evidencia del 9 oct:** la demora cayó en 20 o en 400 en 77 de 82 planes, y el umbral de retirada fue menor que 0,10 en 31 de 82.
   - Mirá también las recetas con todos los zombies en reserva y ninguno atacando (`z0/0/n`), 13 en la corrida: evaluá prohibir `press = 0` cuando hay zombies, o que la reserva entre al instante.
7. **Prueba del B-07 antes del arreglo:** en `BrainTest`, un grupo de 9 con 8 muertos y el último sano; después de `GROUP_RETREATED`, `decide` **no** abre un plan. Hoy falla.

**WP sugeridos:**
- **31A:** dominio (perilla, regla y fuerza), con la prueba del B-07 primero. Opus.
- **31B:** migración de los modelos y recalibración del punto de partida. Opus.

## Borrador CT-32 — Contra la dispersión (feedback de Nico, 10 oct)

**Lo que hace Nico y les gana con cualquier receta:** se aleja, mata esqueletos con el arco, esquiva zombies, mata a las arañas, después a los zombies (que van amontonados y caen juntos con el barrido de la espada) y al final rushea a los esqueletos.

**Causa:** las recetas solo eligen cuántos hacen cada cosa. El movimiento es fijo, así que el modelo no tiene ninguna perilla para aprender a contrarrestar esto.

1. **Persecución en bloque:**
   - si la distancia entre el primero y el más lento hacia el objetivo pasa de `cohesion-max-spread-blocks` (8, por ejemplo), los de adelante frenan o esperan;
   - las arañas no llegan solas;
   - va en el cerebro, como orden `HOLD` o una fase de `PRESS`, y en `PressGoal`.
2. **Zombies que rodean:** cada zombie que ataca toma un ángulo propio alrededor del jugador, con separación mínima (`surround-min-spacing-blocks`, 2,5 por ejemplo), como el anillo de los esqueletos (`ShooterFormation`), para que el barrido no les pegue a todos.
3. **Perilla aprendida `chaseCommitment`** en [0, 1]: cuánto persiguen cuando el jugador se aleja.
   - 0: esperan agrupados cerca de su punto de reunión hasta que vuelva.
   - 1: persiguen en bloque.
   - El modelo la aprende por jugador. Contra el estilo de Nico debería aprender a no perseguirlo disperso.
   - Depende de 1 y 2.
4. **Esqueletos contra el arco:** cuando el jugador apunta con el arco a un esqueleto (`holdingRanged` y la mirada sobre el esqueleto), ese esqueleto busca cubierta o se mueve de costado. Hay base en `EvasiveRules`/`CoverFinder`.

**WP sugeridos:**
- **32A:** persecución en bloque y rodear (cerebro y goals). Opus.
- **32B:** perilla `chaseCommitment`. Opus, después del CT-31, porque también cambia la forma del modelo: conviene sumar las dos perillas juntas y migrar una sola vez.
- **32C:** esquive de los esqueletos. Sonnet.

**Orden recomendado:**
1. Merge del #71.
2. 31A (bug).
3. 32A (lo que más cambia la dificultad).
4. 31B y 32B juntos (una sola migración del modelo).
5. 32C.
6. Repetir la puerta E6 con `puerta-e6-recetas.md`.

## Pendientes menores (WP de limpieza)

- **Nits del WP-33K:** `RecipePlanner.predictionOf` repite el producto de `RecipeSearch.scoreOf`, y `best` podría ser `ranked(…, 1)`.
- **Nit del WP-24H:** las reservas de esqueletos muertos sin `stop()` quedan en el mapa.
- **De antes:**
  - el ciclo de paquetes `group` ↔ `strategy`;
  - `hasRetreated`;
  - partir `ShootGoal`;
  - que un mensaje faltante en `messages.yml` deshabilite el plugin.
- **Sin límite de tamaño:** `training-data.jsonl`.

Gracias por la revisión cruzada. — la nube
