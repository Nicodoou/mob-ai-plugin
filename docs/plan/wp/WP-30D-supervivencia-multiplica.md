# WP-30D — La supervivencia multiplica

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-30B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-30d-supervivencia-multiplica` |

## Objetivo

Hallazgo D-01 del test fuerte, corrida 5: con la **suma** de medidas, un plan que **no pelea** pero no pierde a nadie saca el mejor puntaje contra un jugador muy bueno. El plan 3 hizo 1,4 de daño y sacó 0,64. Con más planes, el grupo aprendería a no atacar. Nico aprobó que la supervivencia **multiplique** al ataque:

```
ataque = (pesoDaño × daño + pesoRapidez × rapidez) ÷ (pesoDaño + pesoRapidez)
éxito  = ataque × (1 − pesoSupervivencia + pesoSupervivencia × supervivencia)
```

Los pesos salen de `SuccessWeights.forDanger`, igual que hoy. Lo que cambia es cómo se combinan:

- **Sin daño no hay éxito,** aunque el grupo sobreviva entero.
- **Entre dos planes que pegan lo mismo,** gana el que pierde menos.
- **Contra un jugador muy bueno,** perder al grupo cuesta más: con supervivencia 0, el éxito se multiplica por 0,4 en vez de por 0,8.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/group/PlanScoring.java`, `SuccessWeights.java`
- `src/main/resources/config.yml` (sección `success`, solo los comentarios)
- `docs/requerimientos.md` (párrafo «Éxito de un plan (CT-27)» de RF-06)
- Pruebas: `PlanScoringTest`, `GroupLifecycleTest`, `RecordPlayerDeathTest`, `BrainExecutingTest`, `DebugLogTest`

## Reglas de negocio

1. **`ataque`:**
   - `(w.damage × daño + w.speed × rapidez) ÷ (w.damage + w.speed)`, con `w = SuccessWeights.forDanger(success, danger)`;
   - si `w.damage + w.speed == 0`, `ataque = 1`: solo cuenta la supervivencia.
2. **`éxito = min(1, ataque × (1 − w.survival + w.survival × supervivencia))`.**
3. **Nada más cambia:** las tres medidas, los pesos, el peligro, la configuración y la persistencia quedan igual.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanScoring.java` (`successOf` y una función privada `attackOf`) |
| Modificar | `src/main/resources/config.yml` (comentario de la sección `success`) |
| Modificar | `docs/requerimientos.md` y `docs/plan/cambios-tecnicos.md` (ver «Documentos») |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/group/PlanScoringTest.java` |
| Modificar | las pruebas existentes cuyo valor esperado cambie (ver abajo) |

## Especificación

### `PlanScoring.java`

```java
  // CT-27, D-01: survival scales the attack instead of adding to it, so a plan that never fights
  // scores nothing however safe it kept the group.
  public double successOf(PlanScores scores) {
    SuccessWeights weights = SuccessWeights.forDanger(success, danger);
    double kept = 1 - weights.survival() + weights.survival() * scores.survival();
    return Math.min(1, attackOf(scores, weights) * kept);
  }

  private static double attackOf(PlanScores scores, SuccessWeights weights) {
    double attackWeight = weights.damage() + weights.speed();
    if (attackWeight == 0) {
      return 1;
    }
    return (weights.damage() * scores.damage() + weights.speed() * scores.speed()) / attackWeight;
  }
```

### `config.yml`

**Comentario de la sección `success`:**

```yaml
  # Éxito de un plan (CT-27): el ataque (daño hecho y rapidez, con sus pesos) multiplicado por cuánto
  # sobrevivió el grupo; survival-weight es cuánto descuenta perder al grupo entero.
```

Las claves y sus valores no cambian.

### Documentos

- **`docs/requerimientos.md`:** en el párrafo «Éxito de un plan (CT-27)», reemplazá la frase de los pesos por esta: «El éxito es el ataque (daño y rapidez, pesos 0,4 y 0,4) multiplicado por `1 − 0,2 + 0,2 × supervivencia`: un plan sin daño no tiene éxito, aunque el grupo sobreviva». Deja el agregado del peligro (de 0,2 a 0,6) como está.
- **`docs/plan/cambios-tecnicos.md`, al final de la sección CT-27,** agregá:

  > **Enmienda (8 oct 2026, D-01, WP-30D).** La supervivencia multiplica al ataque en vez de sumarse: con la suma, un plan que no peleaba sacaba el mejor puntaje contra un jugador muy bueno (corrida 5, plan 3: 1,4 de daño, éxito 0,64).

## Pruebas obligatorias

### `PlanScoringTest`

| Prueba | Cambio | Verifica |
| --- | --- | --- |
| `successWeighsTheThreeMeasures` | **valor nuevo** | `PlanScores(1, 0.5, 0.25)` con peligro 0: ataque (0,4 + 0,2) ÷ 0,8 = 0,75; supervivencia 0,8 + 0,05 = 0,85; éxito **0,6375** |
| `successUsesTheDangerWeights` | **valor nuevo** | los mismos puntajes con peligro 1: ataque (0,2 + 0,1) ÷ 0,4 = 0,75; supervivencia 0,4 + 0,15 = 0,55; éxito **0,4125** |
| `aPlanThatNeverHurtsTheTargetScoresNothing` | **nueva** | `PlanScores(0, 0, 1)` con peligro 1: 0 |
| `losingTheGroupCostsMoreAgainstADangerousPlayer` | **nueva** | `PlanScores(1, 1, 0)`: peligro 0 → 0,8; peligro 1 → 0,4 |
| `onlySurvivalCountsWithoutAttackWeights` | **nueva** | `SuccessSettings(0, 0, 1, 600, 1, 2, 8, 10)` (o el orden que tenga el record) y `PlanScores(0, 0, 0.5)`: 0,5 |

Total: **3 pruebas nuevas y 2 valores cambiados**. Tolerancia 1e-9.

### Pruebas existentes que cambian

Recalculá con la fórmula nueva toda prueba que compare un éxito y **anotá cada valor en el informe, con su cálculo**. Por ejemplo, `GroupLifecycleTest.closingComputesSuccessAndCommitsToTheTarget` pasa de 0,5 a 0,375: ataque (0,4 × 0,5 + 0,4 × 0,25) ÷ 0,8 = 0,375, por supervivencia 1.

Un éxito con puntajes `(1, 1, 1)` sigue dando 1.

Si una prueba del cerebro, de la simulación o de las trazas cambia por algo que no sea el número del éxito, **frená y reportá**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Volver a la suma (`w.damage × d + w.speed × s + w.survival × v`) | `(0, 0, 1)` con peligro 1 da 0,6 | `aPlanThatNeverHurtsTheTargetScoresNothing` |
| 2 | En `kept`, usar el peso base (`success.survivalWeight()`) en vez del de `weights` | con peligro 1 y supervivencia 0 da 0,8 | `losingTheGroupCostsMoreAgainstADangerousPlayer` |
| 3 | En `attackOf`, devolver 0 cuando no hay pesos de ataque | 0 | `onlySurvivalCountsWithoutAttackWeights` |

## Verificación en el server (Nico, después del merge)

En el próximo test, los planes que no te hacen daño cierran con `success=0.00` o casi, aunque `scores=…/…/1.00`.

## Procedimiento

1. Rama `wp-30d-supervivencia-multiplica` desde `origin/main` actualizado.
2. `PlanScoring`, las pruebas, `config.yml` y los documentos. Commit: `fix: survival scales the plan's attack instead of adding to it (D-01)`.
3. Pruebas que muerden (de a una; sin commit).
4. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
5. Push, PR `WP-30D: survival multiplies the attack (D-01)`, CI en verde e informe con la prueba exacta que falló en cada rotura y los valores esperados que cambiaste.

## Correcciones permitidas

1. Formato de Spotless.
2. Ajustar valores esperados del éxito en pruebas existentes, informándolos.

## Fuera de alcance

- Cambiar las medidas, los pesos o el peligro.
- La retirada aprendida (WP-30C).

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 3 pruebas nuevas y los 2 valores nuevos, en verde; la suite completa en verde.
- [ ] Las 3 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
