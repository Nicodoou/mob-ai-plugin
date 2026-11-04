# Entrenador de MobAI

Entrena con PPO, en numpy, hasta 16 redes con la experiencia que escribe el plugin, y publica `policy.json` para que el plugin las cargue. Es código de experimento.

## Instalación

Con Git Bash y Python 3.14, desde `tools/trainer/`:

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt
```

## Uso

```bash
.venv/Scripts/python train.py --root <carpeta>
```

`<carpeta>` tiene una subcarpeta por red, y una red es una subcarpeta con `experience.jsonl`. Se cortan con Ctrl+C: guarda el punto de control y sale. Volver a correr el mismo comando sigue donde quedó, también después de un corte de luz.

`--help` lista todos los hiperparámetros con sus valores por defecto. `--once` procesa lo que haya y termina.

## Qué escribe

| Archivo | Contenido |
| --- | --- |
| `<red>/policy.json` | Política publicada. Se escribe a un temporal y se renombra. |
| `<red>/checkpoint.npz` | Pesos, Adam, azar, offset, peleas abiertas y pasos en espera. |
| `<red>/snapshots/` | Copia de `policy.json` cada 30 minutos. |
| `metrics.csv` | Una fila por actualización. |
| `trainer.log` | Lo mismo que la consola, con rotación. |

## Recompensa

Los coeficientes están en `REWARD_PROFILES`, al principio de `train.py`. El sufijo del nombre de la red elige el perfil: `-1` agresiva, `-2` supervivencia, `-3` equilibrada, `-4` victoria. Un nombre sin sufijo conocido usa la equilibrada y lo avisa en el log.

- Cada evento se reparte entre los mobs vivos del grupo. Si nombra a un mob, ese mob recibe además `individual` veces el valor.
- El resultado (`win`, `loss`, `draw`) no se reparte: lo recibe entero cada mob de la pelea. El que murió antes lo recibe descontado por el tiempo que pasó desde su muerte.
- `survivor` es para los mobs vivos al final, salvo que gane el jugador.

## Política vieja

Los pasos no traen la versión de la política que los generó, así que el entrenador mide la diferencia en vez de contar versiones. Antes de cada actualización compara el `logp` del archivo con el de la política actual:

- Si la diferencia pasa de `--max-logp-drift` (por defecto `ln 4`), el paso se descarta del entrenamiento, aunque sí aporta al cálculo de GAE de su trayectoria.
- Los demás entran con el `logp` del archivo y el recorte de PPO absorbe el resto.

La columna `stale_fraction` de `metrics.csv` muestra cuánto se descartó. Si casi toda la tanda queda afuera no se actualiza y el log avisa: eso suele indicar que el plugin no carga `policy.json` o que calcula `logp` distinto.

## Pruebas

```bash
.venv/Scripts/python -m unittest test_train -v
```

Tardan alrededor de un minuto. Las de aprendizaje simulan el plugin leyendo solo `policy.json` y escribiendo `experience.jsonl`.
