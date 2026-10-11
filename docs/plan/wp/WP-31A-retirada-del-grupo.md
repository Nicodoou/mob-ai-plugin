# WP-31A — Fuerza del grupo y retirada de todo el grupo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24H (mergeado) |
| Modelo | Opus |
| Rama | `wp-31a-retirada-del-grupo` |

## Objetivo

Primera parte del CT-31, aprobado por Nico el 10 oct. Arregla el **B-07** (`docs/plan/verificacion-e6.md`, corrida de recetas del 9 oct): cuando el grupo ya no tiene chance, **un sobreviviente sano abre un plan y ataca solo**, porque la regla de grupo en retirada (CT-13) solo mira a los mobs vivos.

El grupo pasa a medir su **fuerza**, contando a los que murieron, y se retira **todo junto** cuando esa fuerza baja de un umbral. En este WP el umbral es fijo, de la configuración. El WP-31B lo convierte en una perilla aprendida de la receta.

## Reglas de negocio

1. **Fuerza durante un plan** (`GroupStrength.ofPlan`):
   - se suma, por cada mob del inicio (`startingRoles`), su última vida vista si sigue en el plan (`roles`), o 0 si no sigue;
   - eso se divide por la suma de su vida al inicio (`startingHealth`);
   - sin datos de vida, la fuerza es 1.

   Es el mismo cálculo que `PlanScoring` usa para la vida conservada; reusalo, no lo copies.
2. **Fin del plan por retirada** (`PlanEndDetector`): `GROUP_RETREATED` cuando la fuerza del plan es **menor** que `plan.group-retreat-strength` (0,4). **Reemplaza** a la regla de hoy (más de la mitad de los mobs del inicio muertos o en `RETREAT`).
3. **Vida perdida del último plan:**
   - al cerrar un plan, el ciclo de vida guarda la suma de la vida al inicio de los mobs del inicio que ya no están en el plan (los muertos o los que dejaron el grupo);
   - se pone en 0 al abrir el plan siguiente;
   - arranca en 0.
4. **Fuerza fuera de un plan** (`GroupStrength.ofSnapshot`): `Σ vida de los vivos ÷ (Σ vida máxima de los vivos + vida perdida del último plan)`. Sin mobs, es 0.
5. **Grupo en retirada** (`RetreatRule.isGroupRetreated`, usado en `observe` y al vencer la ventana): la fuerza fuera del plan es menor que `plan.group-retreat-strength`.
   - **Reemplaza** a la regla de hoy (más de la mitad de los vivos con vida de retirada).
   - Un grupo de 9 que perdió 8 y tiene 1 sano queda en `20 ÷ (20 + 160) ≈ 0,11`, en retirada.
   - Si llega un refuerzo de 9 sanos, queda en `200 ÷ (200 + 160) ≈ 0,56`, y puede planificar.
6. **Lo que no cambia:**
   - la retirada individual (umbral `retreat-health-fraction` o el de la receta);
   - el reagrupamiento y su ventana adaptativa;
   - el punto de reunión (CT-29);
   - las órdenes `RETREAT` de todos al cerrar con `GROUP_RETREATED`.
7. **Configuración:** `plan.group-retreat-strength: 0.4`, entre 0 y 1.
8. **Sin persistencia en disco:** la vida perdida del último plan vive en memoria, como el resto del ciclo de vida. Después de un reinicio arranca en 0. Va en la copia de los incidentes (`LifecycleCapture`).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/`: `Brain` (`observe`, `holdBack`, `regroup`, `keepRegrouping`), `RetreatRule`, `PlanEndDetector`, `RegroupRule`, `BrainParts`
- `src/main/java/io/github/nicodoou/mobai/domain/group/`: `Plan`, `PlanLifecycle`, `PlanScoring`, `LifecycleCapture`, `Regrouping`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/PlanSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- Las pruebas que encuentren los greps de abajo

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/GroupStrength.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanScoring.java` (usar `GroupStrength`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` y la clase nueva que saque el estado del reagrupamiento (ver «Límite de métodos») |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/LifecycleCapture.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/PlanEndDetector.java`, `RetreatRule.java`, `Brain.java` (solo lo necesario) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/PlanSettings.java`, `ConfigLoader.java`, `config.yml` |
| Modificar | `docs/catalogo-mvp.md` (sección «Cuándo termina un plan», punto 4) y `docs/arquitectura.md` (tabla de nombres) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/GroupStrengthTest.java` |
| Modificar | las pruebas existentes que dependen de las reglas viejas (ver abajo) |

Antes de empezar, buscá con grep `isGroupRetreated`, `hasRetreated`, `GROUP_RETREATED`, `new PlanSettings(` y `new LifecycleCapture(` en `src/`, y que los nombres de las pruebas nuevas no existan.

## Límite de métodos

`PlanLifecycle` ya tiene **20 métodos públicos**: el límite. El WP-32A dejó anotado que el próximo cambio del reagrupamiento saca ese estado a una clase propia.

- Creá una clase en `domain.group` que guarde el estado del reagrupamiento y la vida perdida del último plan:
  - el tick de inicio del reagrupamiento;
  - el punto de reunión;
  - la vida perdida.
- `PlanLifecycle` la usa por composición.
- Elegí vos el nombre y qué métodos pasan, con estas condiciones:
  - `PlanLifecycle` termina con 20 métodos públicos o menos;
  - la clase nueva, con 20 o menos;
  - el comportamiento de los métodos existentes no cambia;
  - `capture` y `restore` siguen funcionando.
- Explicá la división en el informe.

## Pruebas obligatorias

### La que reproduce el B-07 (primero, en rojo)

Antes de tocar `RetreatRule` o `PlanEndDetector`, escribí en la clase de pruebas del cerebro que corresponda (`BrainRegroupingTest` o `BrainObservingTest`) la prueba `aLoneSurvivorDoesNotOpenAPlan`:

1. un grupo de 9 abre un plan;
2. 8 mueren y quedan fuera del plan;
3. el plan cierra con `GROUP_RETREATED`;
4. se termina el reagrupamiento: el sobreviviente sano cuenta como recuperado, o vence la ventana;
5. la decisión siguiente, con el sobreviviente al 100 % y el jugador a la vista, **no abre un plan** y el grupo queda en `REGROUPING`.

Corréla con el código de hoy y **anotá en el informe que falló y cómo**. Si pasa con el código de hoy, frená y reportá: la causa sería otra.

### `GroupStrengthTest` (5)

| Prueba | Verifica |
| --- | --- |
| `planStrengthCountsTheDeadAsLost` | inicio 20 y 20, uno sale del plan, el otro con 20: 0,5 |
| `planStrengthWithoutHealthDataIsFull` | plan sin `startingHealth`: 1 |
| `snapshotStrengthAddsTheLastPlansLosses` | 1 vivo con 20 de 20 y 160 perdidos: 20 ÷ 180 |
| `reinforcementsLiftTheStrength` | 10 vivos con 20 de 20 y 160 perdidos: 200 ÷ 360 |
| `noMobsMeansNoStrength` | foto sin mobs: 0 |

### Otras

- `PlanEndDetectorTest`: `weakGroupClosesAsRetreated` (fuerza 0,35 con umbral 0,4 → `GROUP_RETREATED`) y `strongEnoughGroupKeepsFighting` (0,45 → sigue). Las pruebas de la regla vieja (mitad muertos o en retirada) se reemplazan o se ajustan: **anotá cada una en el informe**.
- `RetreatRuleTest`: `groupWithTheLastPlansLossesIsRetreated` y la de la regla vieja ajustada.
- `PlanLifecycle` o la clase nueva: `closingRemembersTheLostHealth` y `openingAPlanForgetsTheLostHealth`.
- `SettingsValidationTest`: `groupRetreatStrength` 1,5 → el mensaje de `requireBetween`.

Si una prueba del cerebro, de las invariantes o de la simulación cambia por algo que no sea la regla de retirada del grupo, **frená y reportá**.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `ofSnapshot`, sin sumar la vida perdida | `aLoneSurvivorDoesNotOpenAPlan` y `snapshotStrengthAddsTheLastPlansLosses` |
| 2 | No poner en 0 la vida perdida al abrir un plan | `openingAPlanForgetsTheLostHealth` |
| 3 | En `PlanEndDetector`, `<=` en lugar de `<` | una prueba de borde en 0,4 exacto que agregás vos |

## Verificación en el server (Nico, después del merge)

Opus suma `plan.group-retreat-strength: 0.4` al `config.yml` de prueba.

- Matá a casi todo un grupo dejando a uno sano: no tiene que volver a atacarte solo.
- Con `/mobai reinforce <grupo>` el grupo vuelve a pelear.

## Procedimiento

1. Rama `wp-31a-retirada-del-grupo` desde `origin/main` actualizado.
2. La prueba del B-07 en rojo (sin commit del arreglo todavía): commit `test: a lone survivor must not open a plan (B-07)`. Puede quedar fallando en ese commit; el siguiente la pone en verde.
3. `GroupStrength`, configuración, `PlanEndDetector`, `RetreatRule`, el estado del reagrupamiento, `LifecycleCapture` y las pruebas. Commit: `fix: the group retreats together by its strength, counting the dead (B-07, CT-31)`.
4. Documentos. Commit: `docs: group strength in the catalog and the names table`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-31A: group strength and group retreat (B-07)`, CI en verde e informe con:
   - cómo falló la prueba del B-07 antes del arreglo;
   - la prueba exacta que falló en cada rotura;
   - la división de `PlanLifecycle`;
   - cada prueba existente que cambiaste.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- El umbral aprendido por receta, agrandar los modelos, el punto de partida en el medio (D-02) y prohibir todos de reserva: WP-31B.
- Guardar la vida perdida en disco.

## Aceptación

- [ ] La prueba del B-07 falló antes del arreglo y pasa después.
- [ ] Las pruebas listadas, en verde; la suite completa en verde.
- [ ] Las 3 roturas mordieron.
- [ ] `PlanLifecycle` y la clase nueva, con 20 métodos públicos o menos.
- [ ] Build, cobertura y CI en verde.
