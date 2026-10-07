# Catálogo del MVP

El MVP tiene tres tipos de mob (zombie, esqueleto, araña), tres estrategias de grupo y ataques que representan elecciones reales, para que la memoria aprenda algo visible: contra un jugador que bloquea, los mobs dejan de atacar de frente.

## Estrategias de grupo y roles

Tres estrategias alcanzan para que el grupo tenga decisiones distintas que aprender; la retirada no es una estrategia sino un rol que se asigna por vida baja.

### Estrategias

| Estrategia (código) | Qué hace | Requisito mínimo | Roles que reparte |
| --- | --- | --- | --- |
| Ataque directo (`DIRECT_ASSAULT`) | Todos van al objetivo por el camino más corto | Ninguno; es la estrategia por defecto | Cuerpo a cuerpo: `PRESS`; esqueletos: `SHOOT` |
| Flanqueo (`FLANK`) | Una parte presiona de frente y el resto rodea hacia los costados y la espalda | Al menos 3 mobs cuerpo a cuerpo (zombies o arañas) | Mitad `PRESS`, mitad `FLANK`: flanquea la mitad de cada tipo, y si sobra uno es un zombie (CT-18); esqueletos: `SHOOT` |
| Contener y disparar (`PIN_AND_SHOOT`) | Los zombies frenan al objetivo a media distancia y los esqueletos disparan mientras está ocupado | Al menos 2 zombies y 2 esqueletos | Zombies: `PRESS` con distancia corta; esqueletos: `SHOOT`; arañas: `FLANK` |

### Roles

| Rol (código) | Qué hace el mob |
| --- | --- |
| `PRESS` | Va al objetivo y lo ataca de frente o desde donde llegue primero |
| `FLANK` | Se mueve hacia un punto al costado o detrás del objetivo antes de atacar |
| `SHOOT` | Mantiene distancia de tiro (8 a 15 bloques) y dispara |
| `RETREAT` | Se aleja del objetivo, se queda al margen y se cura; se asigna a cualquier mob con 30% de vida o menos, sobre cualquier estrategia, y vuelve a su rol con 60% o más |

El rol de cortar la retirada queda para después del MVP.

## Ataques por tipo de mob

Cada ataque es una forma distinta de enfrentar al jugador, así la memoria aprende algo que se nota en el juego. Zombies y esqueletos eligen su ataque con la política de selección; las arañas siguen su regla simple.

### Zombie

| Ataque (código) | Cómo es | Por qué existe |
| --- | --- | --- |
| Golpe frontal (`zombie.front_strike`) | Va directo y golpea apenas está en alcance | Es lo más rápido; funciona contra jugadores que no bloquean |
| Golpe de flanco (`zombie.flank_strike`) | Esquiva fuera de la vista del jugador (más de 120° de su mirada) sin meterse en su alcance, y recién ahí se le pega a la espalda y golpea (CT-16) | El escudo solo cubre el frente, y lo que no ve no lo bloquea |
| Golpe paciente (`zombie.patient_strike`) | Se queda en alcance y golpea cuando el jugador baja el escudo o termina su propio ataque, con espera máxima de 3 s | Castiga al jugador que alterna bloquear y atacar |

### Esqueleto

| Ataque (código) | Cómo es | Por qué existe |
| --- | --- | --- |
| Disparo directo (`skeleton.direct_shot`) | Apunta a la posición actual del objetivo | Bueno contra objetivos quietos |
| Disparo anticipado (`skeleton.lead_shot`) | Apunta a donde va a estar el objetivo según su velocidad | Bueno contra jugadores que se mueven en línea recta |
| Disparo oportuno (`skeleton.opportunistic_shot`) | Espera a que el objetivo esté peleando con otro mob o mirando hacia otro lado, con espera máxima de 3 s | Evita disparar contra un escudo levantado hacia el esqueleto |

### Araña

| Ataque (código) | Cómo es | Regla |
| --- | --- | --- |
| Mordida (`spider.bite`) | Ataque cuerpo a cuerpo al objetivo más cercano, con bonus de compromiso | Aplica Lentitud I por 3 s; no se renueva ni se acumula si el jugador ya la tiene |

Las mordidas también pasan por el rastreador y se registran, para las métricas y para estrategias futuras, aunque la araña no las use para elegir.

### Qué cuenta como acierto

Se aplican las reglas del rastreador de ataques (pestaña Arquitectura) para todos los ataques: daño mayor a 0 es acierto, golpe que conecta pero se bloquea es parcial, el resto es fallo o neutral según la causa. Si un ataque con espera (paciente u oportuno) vence su espera sin atacar, no se abre intento: no cuenta ni como acierto ni como fallo.

## Éxito y fin de un plan

El éxito de un plan es fraccional, porque "el jugador murió" pasa demasiado poco como para aprender de eso: un plan que le saca media vida al objetivo es un éxito completo.

```latex
\text{éxito} = \min\left(1,\ \frac{\text{daño hecho al objetivo durante el plan}}{0{,}5 \times \text{vida máxima del objetivo}}\right)
```

| Daño hecho durante el plan | Éxito registrado |
| --- | --- |
| El objetivo murió | 1 |
| La mitad de su vida máxima o más | 1 |
| Un cuarto de su vida máxima | 0,5 |
| Nada | 0 |

La curación del jugador durante el plan no se descuenta: mide lo que el grupo logró hacer, no el estado final.

### Cuándo termina un plan

Gana la primera condición que se cumpla:

1. El objetivo muere.
2. El objetivo se pierde (`TARGET_LOST`): más lejos del grupo que la distancia de objetivo perdido o sin que ningún miembro lo vea durante el tiempo de objetivo perdido.
3. El plan llega a su duración máxima: 30 s.
4. Más de la mitad del grupo queda con rol `RETREAT` o muere. En ese caso el grupo entero se reagrupa (ver «Retirada táctica y reagrupamiento» en `arquitectura.md`).

Al terminar se llama a `ClosePlan` con el éxito calculado, y el grupo vuelve a observar.

## Configuración inicial y grupo de prueba

Estos son los valores de arranque para las primeras pruebas; todos están en la configuración y se ajustan con datos, no se defienden.

| Parámetro | Valor inicial | Nota |
| --- | --- | --- |
| Tamaño máximo de grupo | 12 mobs | Se mide el MSPT antes de subirlo |
| Radio de reclutamiento | 16 bloques | Mob suelto que se acerca a un grupo |
| Radio de unión de grupos | 24 bloques |  |
| Tiempo de vida de un mob | 20 min fuera de combate |  |
| Ticks entre decisiones | 10 |  |
| Vida media del olvido | 12.000 ticks (10 min de server prendido) | Se calibra junto con la velocidad |
| Velocidad de aprendizaje | 1,0 (2 intentos virtuales) | Calibrada en la simulación del WP-11: con 0,7 el grupo no llega a preferir el flanqueo en 20 planes (67 % de las corridas); con 1,0, el 90 % (CT-09) |
| Política de selección | Thompson Sampling |  |
| Peso del golpe bloqueado (parcial) | 0,5 | Bajar a 0,25 si insisten de frente contra escudos |
| Techo del multiplicador de memoria | Entre 0,5 y 1,5 |  |
| Bonus de compromiso con el objetivo | +20% |  |
| Ventana de amenaza | 600 ticks (30 s) |  |
| Plazo de un proyectil | 60 ticks (3 s) |  |
| Escape del mob (X de RF-08) | 200 ticks (10 s) sin ser atacado por el objetivo | Propuesta; antes estaba pendiente |
| Distancia de objetivo perdido | 32 bloques | Cierra el plan con `TARGET_LOST` |
| Tiempo de objetivo perdido | 200 ticks (10 s) sin que ningún miembro lo vea | Cierra el plan con `TARGET_LOST` |
| Radio de observadores (N de RF-07) | 32 bloques | Propuesta; antes estaba pendiente |
| Intentos virtuales del patrón de equipo | 8 | Dentro del rango 5–10 de RF-07; a más, más pesa el patrón frente a los datos del jugador |
| Intervalo de guardado | 6.000 ticks (5 min) | Además se guarda al apagar |
| Duración máxima de un plan | 600 ticks (30 s) |  |
| Vida para pasar a `RETREAT` | 30% o menos |  |
| Vida para volver de `RETREAT` | 60% o más |  |
| Curación en retirada | 1 punto cada 50 ticks (ritmo de Regeneración I) | Solo sin jugadores a menos de 12 bloques |
| Ventana de reagrupamiento | 600 ticks al empezar; entre 200 y 1.200 | Global y adaptativa: −50 si un grupo muere reagrupándose, +50 si termina vivo |
| Lentitud de la araña | Lentitud I, 3 s, sin renovar ni acumular |  |

### Grupo de prueba

El comando de prueba spawnea un grupo de 9 mobs junto al jugador: 4 zombies, 3 esqueletos y 2 arañas, sin equipo (un mob spawneado por la API puede traer armas o armadura, y eso haría que las pruebas no sean comparables). Con esa composición las tres estrategias son viables, así que el grupo puede elegir entre todas desde la primera pelea.

## Definición de terminado

El MVP está terminado cuando un jugador que siempre bloquea de frente ve, en una sesión de prueba, que el grupo deja de atacarlo de frente; y cuando eso se puede medir, no solo percibir. Cada criterio se tilda con su evidencia.

### Aprendizaje (el objetivo del MVP)

- [ ] Contra un jugador que mantiene el escudo levantado de frente, en los últimos 10 de 20 planes el grupo elige `FLANK` en el 50% o más (al empezar, con tres estrategias viables, ronda el 33%).
- [ ] En esos mismos últimos 10 planes, `zombie.front_strike` es menos del 30% de los ataques de zombie.
- [ ] Contra un jugador que no usa escudo, en 20 planes el golpe frontal no cae por debajo del 30%: el grupo no aprende a evitar algo que sí funciona.
- [ ] Después de una vida media del olvido sin pelear (server prendido), los éxitos e intentos efectivos de un registro quedan a la mitad.

### Funcionamiento

- [ ] El comando de prueba spawnea el grupo de 9 y el comando de estado muestra su estado, estrategia y roles.
- [ ] Las pruebas del clasificador cubren las 8 reglas del rastreador, y en el server se verificaron al menos: acierto, golpe bloqueado como parcial, flecha que no impacta como fallo y golpe durante invulnerabilidad como neutral.
- [ ] En 10 peleas, ningún miembro del grupo cambia de objetivo por fuego amigo.
- [ ] Después de reiniciar el server, la memoria del grupo se carga con las mismas tasas que antes de apagarlo.

### Calidad técnica

- [ ] Todas las pruebas pasan y ArchUnit no reporta violaciones de capas.
- [ ] Cobertura del dominio del 80% o más.
- [ ] Spotless sin cambios pendientes.
- [ ] Con 2 grupos de 9 peleando a la vez, el plugin suma menos de 2 ms al MSPT, medido con Spark.
- [ ] 30 minutos de juego con grupos activos sin errores en la consola.

### Cómo se mide

Cada plan y cada ataque quedan en el log de debug con su estrategia, ataque y resultado. Para el MVP alcanza con contar en ese log; las métricas en SQLite llegan en la fase 2. La prueba de aprendizaje la hace un jugador real siguiendo un comportamiento fijo (siempre bloquear, o nunca), con el grupo de prueba y la memoria reseteada al empezar.
