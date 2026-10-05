# Diagramas del diseño

Versiones en Mermaid de los diagramas del documento de diseño. GitHub los muestra como gráficos y se leen como texto.

## Plan por fases

```mermaid
flowchart LR
    subgraph M1["Mes 1 · MVP (semanas 1 a 4)"]
        S1["S1 · Entorno y primer goal"] --> S2["S2 · Grupo y 3 acciones"] --> S3["S3 · Memoria y objetivo"] --> S4["S4 · Pruebas y guardado"]
    end
    G1{{"Puerta: MVP probado<br/>memoria visible en pelea real"}}
    subgraph M2["Mes 2 · Grupo base (semanas 5 a 8)"]
        S5["S5 · Creepers y brujas"] --> S6["S6 · Ciclo de planes"] --> S7["S7 · Observadores, patrones"] --> S8["S8 · Pruebas con amigos"]
    end
    G2{{"Puerta: grupo base calibrado<br/>benchmark full diamante P4"}}
    subgraph POST["Posterior (sin fecha fija)"]
        P1["Zombies constructores"]
        P2["Illagers"]
        P3["Composición adaptable"]
        P4["Perfiles de composición"]
        P5["Líder-seguidores si hay lag"]
    end
    S4 --> G1 --> S5
    S8 --> G2 --> POST
```

## Capas del plugin

Las dependencias apuntan hacia el dominio, que no conoce Minecraft.

```mermaid
flowchart TB
    ADAPT["Adaptadores de Paper<br/>listeners, comandos, scheduler y goals<br/>incluye el traductor de versión"]
    PERS["Persistencia<br/>guarda y carga la memoria en JSON<br/>implementa MemoryRepository"]
    APP["Aplicación<br/>casos de uso: TickGroups, RecordOutcome, ClosePlan, RecruitMob, MergeGroups<br/>ordena las llamadas al dominio; sin fórmulas ni Paper"]
    DOM["Dominio<br/>Group, GroupMemory, AttackRecord, Brain, TargetSelector, GroupStrategy<br/>reglas del juego: olvido, sorteo Beta, prioridad, roles<br/>define los puertos MemoryRepository, ServerClock y RandomSource"]
    ADAPT -->|depende de| APP
    PERS -->|depende de| APP
    APP -->|depende de| DOM
```

## Ciclo del grupo (máquina de estados)

```mermaid
stateDiagram-v2
    [*] --> OBSERVING
    OBSERVING --> PLANNING: hay objetivo
    PLANNING --> EXECUTING: plan elegido
    EXECUTING --> EVALUATING: plan termina
    EVALUATING --> OBSERVING: resultado registrado en la memoria
    note right of EXECUTING
        Un plan termina cuando el objetivo muere, se pierde,
        el plan llega a su duración máxima o el grupo se retira.
    end note
```

## Proceso de resolución de bugs

```mermaid
flowchart TB
    A["Analizar<br/>esperado y observado"] --> B["Aislar variables<br/>condiciones mínimas"] --> C["Replicar<br/>prueba que falla"] --> D["Hipótesis<br/>con predicción"] --> E["Debug y test<br/>una por vez"] --> F{"¿Se confirma?"}
    F -->|no: revertir y probar la siguiente| D
    F -->|tres descartadas seguidas| B
    F -->|sí| G["Corregir la causa<br/>la prueba pasa"] --> H["Regresión<br/>suite y benchmark"] --> I["Limpiar<br/>borrar debugs"] --> J["Documentar<br/>registro de bugs"]
```
