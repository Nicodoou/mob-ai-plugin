# Calibración de las recetas (CT-30)

Distancia = óptimo verdadero − puntaje verdadero de la receta jugada, promediada sobre las semillas. SCRATCH: semillas 1 a 20, 150 planes desde cero. Con base: semillas 1 a 10, 600 planes de entrenamiento y 40 planes por estilo. Competente: primer bloque de 10 planes con promedio ≥ óptimo − 0.05; la mediana es sobre las corridas que lo alcanzan.

| Experimento | Estilo | Óptimo | Distancia, 4 estrategias de hoy | Distancia, primeros 20 (promedio) | Distancia, últimos 10 (promedio) | Planes hasta competente (mediana; «nunca» si falta en más de la mitad) |
| --- | --- | --- | --- | --- | --- | --- |
| SCRATCH | SHIELD_BLOCKER | 0.8100 | 0.1100 | 0.4370 | 0.0548 | 85.0 |
| SCRATCH | BERSERKER | 0.6812 | 0.0735 | 0.4878 | 0.0461 | 61.5 |
| SCRATCH | ARCHER | 0.7800 | 0.1335 | 0.5244 | 0.0520 | 84.0 |
| BASE_FLAT | SHIELD_BLOCKER | 0.8100 | 0.1100 | 0.1553 | 0.1474 | nunca |
| BASE_FLAT | BERSERKER | 0.6812 | 0.0735 | 0.2534 | 0.2454 | nunca |
| BASE_FLAT | ARCHER | 0.7800 | 0.1335 | 0.1853 | 0.1688 | nunca |
| BASE_CONTEXTUAL_EXACT | SHIELD_BLOCKER | 0.8100 | 0.1100 | 0.0157 | 0.0132 | 10.0 |
| BASE_CONTEXTUAL_EXACT | BERSERKER | 0.6812 | 0.0735 | 0.0229 | 0.0223 | 10.0 |
| BASE_CONTEXTUAL_EXACT | ARCHER | 0.7800 | 0.1335 | 0.0209 | 0.0203 | 10.0 |
| BASE_CONTEXTUAL_NOISY | SHIELD_BLOCKER | 0.8100 | 0.1100 | 0.0401 | 0.0282 | 10.0 |
| BASE_CONTEXTUAL_NOISY | BERSERKER | 0.6812 | 0.0735 | 0.0524 | 0.0460 | 10.0 |
| BASE_CONTEXTUAL_NOISY | ARCHER | 0.7800 | 0.1335 | 0.0306 | 0.0228 | 10.0 |

## Puerta

Criterio: en BASE_CONTEXTUAL_NOISY, para cada estilo, distancia en los primeros 20 planes ≤ 0.07 y menor que la de las 4 estrategias de hoy.

**PASS**

Control: `BASE_FLAT` primeros 20: SHIELD_BLOCKER 0.1553, BERSERKER 0.2534, ARCHER 0.1853
