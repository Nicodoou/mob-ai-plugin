# Calibración de las recetas (CT-30)

Distancia = óptimo verdadero − puntaje verdadero de la receta jugada, promediada sobre las semillas. SCRATCH: semillas 1 a 20, 150 planes desde cero. Con base: semillas 1 a 40, 1000 planes de entrenamiento y 40 planes por estilo. Competente: primer bloque de 10 planes con promedio ≥ óptimo − 0.05; la mediana es sobre las corridas que lo alcanzan.

Arranque en el medio: k = 2.0

| Experimento | Estilo | Óptimo | Distancia, 4 estrategias de hoy | Distancia, primeros 20 (promedio) | Distancia, últimos 10 (promedio) | Planes hasta competente (mediana; «nunca» si falta en más de la mitad) |
| --- | --- | --- | --- | --- | --- | --- |
| SCRATCH | SHIELD_BLOCKER | 0.8100 | 0.1453 | 0.4346 | 0.0749 | 104.5 |
| SCRATCH | BERSERKER | 0.6812 | 0.1606 | 0.6968 | 0.0785 | nunca |
| SCRATCH | ARCHER | 0.7800 | 0.3298 | 0.6231 | 0.0773 | 131.5 |
| BASE_FLAT | SHIELD_BLOCKER | 0.8100 | 0.1453 | 0.2075 | 0.2047 | nunca |
| BASE_FLAT | BERSERKER | 0.6812 | 0.1606 | 0.4529 | 0.4533 | nunca |
| BASE_FLAT | ARCHER | 0.7800 | 0.3298 | 0.1538 | 0.1407 | nunca |
| BASE_CONTEXTUAL_EXACT | SHIELD_BLOCKER | 0.8100 | 0.1453 | 0.0172 | 0.0187 | 10.0 |
| BASE_CONTEXTUAL_EXACT | BERSERKER | 0.6812 | 0.1606 | 0.0187 | 0.0176 | 10.0 |
| BASE_CONTEXTUAL_EXACT | ARCHER | 0.7800 | 0.3298 | 0.0231 | 0.0219 | 10.0 |
| BASE_CONTEXTUAL_NOISY | SHIELD_BLOCKER | 0.8100 | 0.1453 | 0.0337 | 0.0285 | 10.0 |
| BASE_CONTEXTUAL_NOISY | BERSERKER | 0.6812 | 0.1606 | 0.0619 | 0.0526 | 10.0 |
| BASE_CONTEXTUAL_NOISY | ARCHER | 0.7800 | 0.3298 | 0.0374 | 0.0369 | 10.0 |

## Perillas en el borde (D-02)

Porcentaje de planes de SCRATCH, entre los primeros 20 de cada semilla, en que alguna perilla continua (retirada, retirada del grupo, persecución y, con reserva, demora) cayó en un borde.

| Estilo | Planes con una perilla en el borde (%) |
| --- | --- |
| SHIELD_BLOCKER | 63.0 |
| BERSERKER | 47.8 |
| ARCHER | 66.3 |

## Puerta

Criterio: en BASE_CONTEXTUAL_NOISY, para cada estilo, distancia en los primeros 20 planes ≤ 0.07 y menor que la de las 4 estrategias de hoy.

**PASS**

Control: `BASE_FLAT` primeros 20: SHIELD_BLOCKER 0.2075, BERSERKER 0.4529, ARCHER 0.1538

## Elección de k (CT-33)

Distancias de BASE_CONTEXTUAL_NOISY en los primeros 20 planes y porcentaje de planes de SCRATCH con alguna perilla continua en el borde (primeros 20 de cada semilla).

Primera corrida: 600 planes de entrenamiento y 10 semillas con base.

| k | SHIELD_BLOCKER | BERSERKER | ARCHER | Puerta | Bordes SCRATCH (SHIELD / BERSERKER / ARCHER) |
| --- | --- | --- | --- | --- | --- |
| 0 | 0.0557 | 0.0712 | 0.0544 | FAIL: BERSERKER | 95.5 / 97.3 / 97.3 |
| 0.5 | 0.0492 | 0.1012 | 0.0392 | FAIL: BERSERKER | 91.8 / 93.8 / 91.3 |
| 1 | 0.0365 | 0.0830 | 0.0541 | FAIL: BERSERKER | 83.8 / 84.8 / 85.0 |
| 2 | 0.0445 | 0.0813 | 0.0561 | FAIL: BERSERKER | 63.0 / 47.8 / 66.3 |

Con 16 parámetros más, 600 planes no alcanzan para que la base aprenda al berserker. Enmienda del 14 oct (decisión de Nico): base entrenada con 1000 planes y 40 semillas, para medir con menos ruido. El criterio de la puerta no cambia.

| k | SHIELD_BLOCKER | BERSERKER | ARCHER | Puerta | Bordes SCRATCH (SHIELD / BERSERKER / ARCHER) |
| --- | --- | --- | --- | --- | --- |
| 0 | 0.0353 | 0.0706 | 0.0348 | FAIL: BERSERKER | 95.5 / 97.3 / 97.3 |
| 1 | 0.0336 | 0.0623 | 0.0371 | PASS | 83.8 / 84.8 / 85.0 |
| 2 | 0.0337 | 0.0619 | 0.0374 | PASS | 63.0 / 47.8 / 66.3 |

**k elegido: 2.** Da PASS con la menor distancia máxima entre los tres estilos (BERSERKER 0.0619) y es el que menos planes deja con una perilla en el borde.

**Consecuencia para el juego:** con las perillas nuevas, la base del server rinde como hoy recién después de unos 1000 planes de entrenamiento.
