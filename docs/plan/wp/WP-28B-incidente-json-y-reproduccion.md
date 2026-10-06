# WP-28B — Incidente, JSON y reproducción

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia (cierra la etapa) |
| Depende de | WP-28A (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-28b-incidente-json-y-reproduccion` |

## Objetivo

1. **`IncidentReport`**: todo lo necesario para repetir una decisión del cerebro y saber qué pasó: dónde, la excepción (si la hubo), la copia del grupo antes y después, la foto, la configuración, la ventana de reagrupamiento antes y después, los números al azar y el resultado.
2. **Su JSON** (`IncidentJson`), que escribe y vuelve a leer `Infinity` (CT-04, obligatorio) y lleva versión de formato.
3. **`TraceReplay`**: la herramienta de prueba que lee un incidente, reconstruye todo, repite la decisión y verifica que dé exactamente lo mismo.
4. **La prueba de la puerta E4**: un incidente provocado a propósito se reproduce desde su JSON, y un incidente adulterado se detecta.

Escribir los incidentes desde el plugin es del WP-29.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/application/GroupCapture.java`, `GroupCaptureMapper.java`, `RecordedDraw.java`, `RecordingRandomSource.java`, `ReplayRandomSource.java`, `SettingsHolder.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` (`standard`), `Brain.java` (constructor y `decide`), `RegroupWindow.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/BrainResult.java`, `DecisionTrace.java`
- `src/main/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepository.java` (como modelo de Gson en este proyecto)
- `src/test/java/io/github/nicodoou/mobai/application/DecisionRepeatTest.java` (el armado que este WP reutiliza)
- `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java`, `PlayerSnapshotBuilder.java`, `SeededRandomSource.java`, `TestSettings.java`

## Reglas de negocio

1. **Un incidente tiene resultado o falla, nunca los dos ni ninguno.** Con falla, `after` es el estado en que quedó el grupo (puede estar modificado a medias: eso también sirve para entender el bug).
2. **La falla** guarda la clase de la excepción, su mensaje (`""` si es `null`) y el stack trace como texto (`ClaseDeLaExcepción: mensaje` y una línea por marco, con `StackTraceElement.toString()`). En `application` está prohibido `PrintWriter` (ArchUnit), así que el texto se arma a mano.
3. **Reproducido** quiere decir todo esto a la vez:
   - el mismo `BrainResult` (`equals`), o la misma falla (misma clase y mismo mensaje; el stack trace no se compara);
   - la misma copia del grupo después (`after`);
   - la misma ventana de reagrupamiento después;
   - todos los números grabados consumidos.
4. **La repetición usa el armado del plugin:** `BrainParts.standard` con la configuración del incidente, un `ReplayRandomSource` con sus números y una `RegroupWindow` restaurada a la ventana de antes.
5. **JSON:**
   - `{"schemaVersion": 1, "report": {...}}`; otra versión: `IllegalArgumentException("Incident file has schema version " + v + ", this plugin reads 1")`;
   - `Optional` vacío se escribe `null`; los mapas con claves que son records (`Map<MobId, Role>`) se escriben como pares;
   - los infinitos se escriben `Infinity` y se vuelven a leer (CT-04).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/IncidentLocation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/IncidentFailure.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/IncidentReport.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/OptionalTypeAdapterFactory.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/IncidentFile.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/IncidentJson.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/testsupport/TraceReplay.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/testsupport/IncidentFixture.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/IncidentReportTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/persistence/IncidentJsonTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/replay/IncidentReproductionTest.java` |

Sin dependencias nuevas (Gson 2.14.0 viene con Paper).

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `application/IncidentLocation.java`

```java
/** Where an incident happened: layer, class, method and the use case that was running. */
public record IncidentLocation(String layer, String className, String method, String useCase) {
  public IncidentLocation {
    Objects.requireNonNull(layer, "IncidentLocation.layer");
    Objects.requireNonNull(className, "IncidentLocation.className");
    Objects.requireNonNull(method, "IncidentLocation.method");
    Objects.requireNonNull(useCase, "IncidentLocation.useCase");
  }
}
```

### `application/IncidentFailure.java`

```java
public record IncidentFailure(String exceptionClass, String message, String stackTrace) {
  public IncidentFailure { … requireNonNull de los tres … }

  public static IncidentFailure of(Throwable failure) {
    String message = Objects.requireNonNullElse(failure.getMessage(), "");
    return new IncidentFailure(failure.getClass().getName(), message, stackTraceOf(failure, message));
  }

  public String summary() {
    return exceptionClass + ": " + message;
  }

  private static String stackTraceOf(Throwable failure, String message) {
    StringBuilder text = new StringBuilder(failure.getClass().getName()).append(": ").append(message);
    for (StackTraceElement frame : failure.getStackTrace()) {
      text.append("\n\tat ").append(frame);
    }
    return text.toString();
  }
}
```

### `application/IncidentReport.java`

```java
public record IncidentReport(
    String id,
    long tick,
    IncidentLocation location,
    Optional<IncidentFailure> failure,
    GroupCapture before,
    long regroupWindowTicksBefore,
    GroupSnapshot snapshot,
    MobAiSettings settings,
    List<RecordedDraw> draws,
    Optional<BrainResult> result,
    GroupCapture after,
    long regroupWindowTicksAfter) {
  public IncidentReport {
    Objects.requireNonNull(id, "IncidentReport.id");
    … requireNonNull del resto de los objetos …
    draws = List.copyOf(draws);
    if (failure.isPresent() == result.isPresent()) {
      throw new IllegalArgumentException("IncidentReport must have either a failure or a result");
    }
  }
}
```

### `persistence/OptionalTypeAdapterFactory.java` (package-private)

```java
/** Gson has no built-in support for Optional: empty is written as null and read back as empty. */
final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
  @Override
  @SuppressWarnings("unchecked") // the raw type was checked just above each cast
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (type.getRawType() == OptionalLong.class) {
      return (TypeAdapter<T>) new OptionalLongAdapter();
    }
    if (type.getRawType() != Optional.class) {
      return null;
    }
    Type valueType = ((ParameterizedType) type.getType()).getActualTypeArguments()[0];
    return (TypeAdapter<T>) new OptionalAdapter<>(gson.getAdapter(TypeToken.get(valueType)));
  }

  private static final class OptionalAdapter<V> extends TypeAdapter<Optional<V>> {
    … write: vacío → out.nullValue(); presente → valueAdapter.write(out, valor)
    … read: si in.peek() == JsonToken.NULL → in.nextNull() y Optional.empty(); si no, Optional.of(valueAdapter.read(in))
  }

  private static final class OptionalLongAdapter extends TypeAdapter<OptionalLong> {
    … igual, con out.value(long) e in.nextLong()
  }
}
```

(`create` devuelve `null` para los tipos que no maneja: es el contrato de `TypeAdapterFactory`, no un nulo de nuestro dominio. Comentalo en una línea).

### `persistence/IncidentFile.java` (package-private)

```java
record IncidentFile(int schemaVersion, IncidentReport report) {}
```

### `persistence/IncidentJson.java` (`public final`)

```java
public final class IncidentJson {
  static final int CURRENT_VERSION = 1;

  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
          .serializeNulls()
          .serializeSpecialFloatingPointValues()
          .enableComplexMapKeySerialization()
          .setStrictness(Strictness.LENIENT)
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .create();

  public String write(IncidentReport report) {
    return gson.toJson(new IncidentFile(CURRENT_VERSION, report));
  }

  public IncidentReport read(String json) { … regla 5 … }
}
```

- `serializeNulls` es necesario: sin él, Gson no escribe los `Optional` vacíos y al leer el record recibe `null` y lo rechaza.
- `setStrictness(Strictness.LENIENT)` es lo que deja volver a leer `Infinity`.
- `read`: `gson.fromJson(json, IncidentFile.class)`; si `schemaVersion` no es 1, la excepción de la regla 5; si `report` es `null`, `IllegalArgumentException("Incident file has no report")`.

### `testsupport/TraceReplay.java`

```java
/** Repeats the decision of an incident and checks that it comes out exactly the same. */
public final class TraceReplay {
  private TraceReplay() {}

  public record Outcome(
      Optional<BrainResult> result,
      Optional<String> failure,
      GroupCapture after,
      long regroupWindowTicksAfter,
      int remainingDraws) {}

  public static Outcome replay(IncidentReport report) { … regla 4 … }

  public static void assertReproduces(IncidentReport report) { … regla 3 … }
}
```

`replay`:
1. `SettingsHolder holder = new SettingsHolder(report.settings())`.
2. `RegroupWindow window = new RegroupWindow(() -> holder.current().retreat())` y `window.restore(report.regroupWindowTicksBefore())`.
3. `GroupCaptureMapper mapper = new GroupCaptureMapper()`; `Group group = mapper.restore(report.before(), holder)`.
4. `ReplayRandomSource random = new ReplayRandomSource(report.draws())`.
5. `Brain brain = new Brain(holder::current, BrainParts.standard(holder::current, random, window))`.
6. `brain.decide(group, report.snapshot())`; si lanza `RuntimeException`, la falla es `exception.getClass().getName() + ": " + exception.getMessage()` (con `""` si el mensaje es `null`).
7. `Outcome` con el resultado o la falla, `mapper.capture(group)`, `window.currentTicks()` y `random.remaining()`.

`assertReproduces`: con AssertJ y `.as("…")` en cada verificación, para que el mensaje diga qué difiere:
- `as("brain result")`: si el incidente tiene resultado, `outcome.result()` = `report.result()`;
- `as("failure")`: si tiene falla, `outcome.failure()` = `Optional.of(report.failure().get().summary())`;
- `as("group after")`: `outcome.after()` = `report.after()`;
- `as("regroup window after")`: igual a `report.regroupWindowTicksAfter()`;
- `as("unused draws")`: `remainingDraws` = 0.

### `testsupport/IncidentFixture.java`

Arma incidentes reales grabando una decisión, con el mismo armado que `DecisionRepeatTest` (WP-28A): `TestSettings.defaults()`, `RecordingRandomSource` sobre `SeededRandomSource(7)`, `BrainParts.standard`, el grupo del catálogo con id `BrainFixture.GROUP_ID`, dos decisiones previas (`START_TICK` y `START_TICK + 10`) y un daño de amenaza en `START_TICK + 12`.

| Método | Devuelve |
| --- | --- |
| `static IncidentReport recordedDecision(long tick)` | Copia antes, `recorder.clear()`, decide con la foto de `alice` en `tick`, y arma el incidente con el resultado, la copia después y la ventana antes y después. `id` = `"test-" + tick`, ubicación `new IncidentLocation("domain", "Brain", "decide", "TickGroups")` |
| `static IncidentReport provokedFailure()` | Igual, pero la foto en `START_TICK + 20` agrega un mob que no es miembro (`new MobSnapshotBuilder().withId(new MobId(new UUID(9, 9))).build()` o equivalente): `decide` lanza `IllegalArgumentException`, y el incidente lleva `IncidentFailure.of(excepción)` |
| `static IncidentReport unkillablePlayer()` | Igual que `recordedDecision(START_TICK + 20)`, pero la foto usa un jugador `ALICE` con `fullDiamondProtectionFour()` y Regeneración nivel 10 (la traza tiene un `TargetScore` con `killTimeSeconds` infinito) |

Una función por tarea: armado del grupo, decisiones previas, copia, decisión grabada, armado del incidente.

## Pruebas obligatorias

### `IncidentReportTest` (4)

| Prueba | Verifica |
| --- | --- |
| `reportNeedsAFailureOrAResult` | los dos vacíos: `"IncidentReport must have either a failure or a result"` |
| `reportCannotHaveBothAFailureAndAResult` | los dos presentes: el mismo mensaje |
| `failureKeepsClassMessageAndStackTrace` | `IncidentFailure.of(new IllegalStateException("boom"))`: clase `"java.lang.IllegalStateException"`, mensaje `"boom"`, el stack trace empieza con `"java.lang.IllegalStateException: boom\n\tat "` y `summary()` = `"java.lang.IllegalStateException: boom"` |
| `missingMessageBecomesEmpty` | `IncidentFailure.of(new IllegalStateException())`: mensaje `""` |

### `IncidentJsonTest` (5)

| Prueba | Verifica |
| --- | --- |
| `reportRoundTripsThroughJson` | `read(write(recordedDecision(START_TICK + 20)))` igual al original (`isEqualTo`) |
| `failureReportRoundTripsThroughJson` | lo mismo con `provokedFailure()` |
| `infiniteKillTimeSurvivesTheJson` | con `unkillablePlayer()`: el texto contiene `"Infinity"` y la vuelta es igual al original (CT-04) |
| `jsonCarriesTheSchemaVersion` | el texto parseado con `JsonParser` tiene `schemaVersion` 1 y un objeto `report` |
| `unknownSchemaVersionIsRejected` | el texto con `schemaVersion` cambiado a 2: `"Incident file has schema version 2, this plugin reads 1"` |

### `IncidentReproductionTest` (5) — la puerta E4

Cada prueba pasa el incidente por el JSON (`read(write(...))`) antes de reproducirlo, como pasará con un archivo real.

| Prueba | Verifica |
| --- | --- |
| `recordedDecisionReproducesFromItsJson` | `TraceReplay.assertReproduces` sobre `recordedDecision(START_TICK + 20)` no lanza |
| `decisionThatClosesThePlanReproducesFromItsJson` | lo mismo con `recordedDecision(START_TICK + 700)` (vence el plan; `result().get().closedPlan()` presente) |
| `provokedFailureReproducesFromItsJson` | lo mismo con `provokedFailure()` |
| `tamperedDrawIsDetected` | el incidente de `recordedDecision(START_TICK + 20)` con el **primer** número grabado cambiado (mismo tipo y límite; para `UNIT` o `GAUSSIAN` sumale 0.25, para `INDEX` usá otro índice válido; si el límite es 1, cambiá el primer número de otro tipo): `assertReproduces` lanza `AssertionError` |
| `tamperedStateIsDetected` | el mismo incidente con `before` sin la amenaza (`ThreatCapture` vacía con el mismo `lastTick`): `assertReproduces` lanza `AssertionError` |

Total: **14 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `IncidentJson`, sacar `serializeNulls()` | el `Optional` vacío no se escribe y el record se rechaza al leer | `reportRoundTripsThroughJson` |
| 2 | En `IncidentJson`, sacar `setStrictness(Strictness.LENIENT)` | leer `Infinity` falla | `infiniteKillTimeSurvivesTheJson` |
| 3 | En `TraceReplay.assertReproduces`, no comparar `after` | el incidente con la amenaza borrada pasa | `tamperedStateIsDetected` |
| 4 | En `IncidentFailure.of`, no reemplazar el mensaje `null` | `NullPointerException` del record | `missingMessageBecomesEmpty` |

Si la rotura 2 no hace fallar la prueba (porque la configuración por defecto de Gson ya lee `Infinity`), reportalo con la versión de Gson: en ese caso dejá `setStrictness` igual, porque documenta la intención, y avisá que esa rotura no muerde.

## Procedimiento

1. Rama `wp-28b-incidente-json-y-reproduccion` desde `origin/main` actualizado.
2. `IncidentLocation`, `IncidentFailure`, `IncidentReport` con `IncidentReportTest`. Commit: `feat: add incident report`.
3. `IncidentFixture`, `OptionalTypeAdapterFactory`, `IncidentFile`, `IncidentJson` con `IncidentJsonTest`. Commit: `feat: write and read incidents as json`.
4. `TraceReplay` con `IncidentReproductionTest`. Commit: `feat: replay incidents and check they reproduce`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-28B: incident report, json and replay`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si en `unkillablePlayer()` la traza no tiene ningún `killTimeSeconds` infinito, subí la Regeneración (hasta 20) o sumá Resistencia 4 hasta que lo tenga; avisalo con los valores usados.
3. Si una prueba de reproducción falla **sin** adulterar nada, no la ajustes: hay estado que no se copia, o algo del JSON que no vuelve igual. Frená y reportá qué campo difiere (la aserción con `.as(...)` lo dice).
4. Si Gson no puede construir algún record del dominio o de la aplicación (por ejemplo, por un tipo que no es record ni enum), frená y reportá cuál.

## Fuera de alcance

- Tomar la copia antes de cada decisión en el plugin, la caja negra y escribir `incident-<id>.json` (WP-29).
- Reproducir lo que hace Minecraft por su cuenta (pathfinding y flechas): para eso el incidente del WP-29 suma los hechos crudos de Paper.
- Incidentes del rastreador de ataques (el clasificador es determinista sin azar; el WP-29 decide si los registra con sus hechos).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 14 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron (o la 2 se reportó como no aplicable).
- [ ] Un incidente provocado a propósito se reproduce desde su JSON, y uno adulterado se detecta: **puerta E4**.
- [ ] Build, cobertura y CI en verde.
