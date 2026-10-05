# WP-00 — Andamiaje, reglas automáticas y CI

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E0 Andamiaje |
| Depende de | — |
| Modelo | Sonnet |
| Rama | `wp-00-andamiaje` |

## Objetivo

Un proyecto Gradle que compila un plugin de Paper 26.3 vacío con Java 25, corre las pruebas y verifica en cada `./gradlew build` las reglas de arquitectura (ArchUnit) y de formato (Spotless), local y en GitHub Actions.

## Contexto a leer

`docs/plan/reglas-para-agentes.md` y este WP. Nada más.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `.gitattributes` |
| Modificar | `.gitignore` (agregar una línea) |
| Crear | `settings.gradle.kts` |
| Crear | `gradle/libs.versions.toml` |
| Crear | `build.gradle.kts` |
| Crear (generados) | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` |
| Crear | `src/main/resources/plugin.yml` |
| Crear | `src/main/java/io/github/nicodoou/mobai/bootstrap/MobAiPlugin.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java` |
| Crear | `.github/workflows/build.yml` |

## Especificación: contenido exacto de cada archivo

**`.gitattributes`**
```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
```

**`.gitignore`**: agregar al final la línea `.kotlin/`. No cambiar nada más.

**`settings.gradle.kts`**
```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mob-ai-plugin"
```

**`gradle/libs.versions.toml`**
```toml
[versions]
paper-api = "26.3.build.151-beta"
junit = "5.14.4"
assertj = "3.27.7"
archunit = "1.5.1"
# 1.37.0 breaks Spotless 8.10.3: JavaFormatterOptions.Style is no longer an enum
google-java-format = "1.36.1"
jacoco = "0.8.15"
spotless = "8.10.3"
run-paper = "3.1.0"

[libraries]
paper-api = { module = "io.papermc.paper:paper-api", version.ref = "paper-api" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj" }
archunit = { module = "com.tngtech.archunit:archunit", version.ref = "archunit" }

[plugins]
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
run-paper = { id = "xyz.jpenilla.run-paper", version.ref = "run-paper" }
```

**`build.gradle.kts`**
```kotlin
plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.run.paper)
}

group = "io.github.nicodoou"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-classfile", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

spotless {
    java {
        target("src/*/java/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            include("io/github/nicodoou/mobai/domain/**")
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.runServer {
    minecraftVersion("26.3")
}
```

**`src/main/resources/plugin.yml`**
```yaml
name: MobAI
version: '${version}'
main: io.github.nicodoou.mobai.bootstrap.MobAiPlugin
api-version: '26.3'
description: Hostile mobs fight in groups with roles and learn from each player.
author: Nicolas Oroná
```

**`src/main/java/io/github/nicodoou/mobai/bootstrap/MobAiPlugin.java`**
```java
package io.github.nicodoou.mobai.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public final class MobAiPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getSLF4JLogger().info("MobAI {} enabled", getPluginMeta().getVersion());
  }

  @Override
  public void onDisable() {
    getSLF4JLogger().info("MobAI disabled");
  }
}
```

**`src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java`**: diez reglas, exactamente así.
```java
package io.github.nicodoou.mobai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchitectureTest {

  private static final String BASE_PACKAGE = "io.github.nicodoou.mobai";
  private static final String DOMAIN = BASE_PACKAGE + ".domain..";
  private static final String APPLICATION = BASE_PACKAGE + ".application..";
  private static final String PERSISTENCE = BASE_PACKAGE + ".persistence..";
  private static final String ADAPTER = BASE_PACKAGE + ".adapter..";
  private static final String BOOTSTRAP = BASE_PACKAGE + ".bootstrap..";
  private static final String VERSION_TRANSLATOR =
      BASE_PACKAGE + ".adapter.translate.VersionTranslator";
  private static final String RANDOM_SOURCES =
      "java\\.util\\.Random|java\\.util\\.SplittableRandom|java\\.util\\.concurrent\\.ThreadLocalRandom"
          + "|java\\.security\\.SecureRandom|java\\.util\\.random\\..*";
  private static final String FILE_AND_CONSOLE_IO = "java\\.io\\.(File.*|PrintStream|PrintWriter)";

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(BASE_PACKAGE);

  @Test
  void domainDependsOnlyOnJdkAndItself() {
    classes()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(DOMAIN, "java..")
        .because("the domain must not know Minecraft, files or libraries")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnDomainAndJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION, DOMAIN, "java..")
        .because("use cases only orchestrate the domain")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void persistenceDependsOnlyOnDomainJdkAndGson() {
    classes()
        .that()
        .resideInAPackage(PERSISTENCE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(PERSISTENCE, DOMAIN, "java..", "com.google.gson..")
        .because("persistence implements a domain port and must not know Paper")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void adapterDoesNotDependOnPersistenceOrBootstrap() {
    noClasses()
        .that()
        .resideInAPackage(ADAPTER)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(PERSISTENCE, BOOTSTRAP)
        .because("adapters reach persistence only through the MemoryRepository port")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "org.bukkit.entity.EntityType",
        "org.bukkit.Material",
        "org.bukkit.potion.PotionEffectType",
        "org.bukkit.attribute.Attribute",
        "org.bukkit.enchantments.Enchantment",
        "com.destroystokyo.paper.entity.ai.VanillaGoal"
      })
  void onlyVersionTranslatorUsesVersionSensitivePaperTypes(String paperType) {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(VERSION_TRANSLATOR)
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(paperType)
        .because("version-sensitive Paper constants are isolated in VersionTranslator")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void staticFieldsAreFinal() {
    fields()
        .that()
        .areStatic()
        .should()
        .beFinal()
        .because("global mutable state is forbidden")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void domainAndApplicationDoNotUseRandomnessOrSystemTimeDirectly() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN, APPLICATION)
        .should()
        .dependOnClassesThat()
        .haveNameMatching(RANDOM_SOURCES)
        .orShould()
        .callMethod(Math.class, "random")
        .orShould()
        .callMethod(UUID.class, "randomUUID")
        .orShould()
        .callMethod(System.class, "currentTimeMillis")
        .orShould()
        .callMethod(System.class, "nanoTime")
        .because("randomness and time enter through the RandomSource and ServerClock ports")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void domainAndApplicationDoNotTouchFilesTimeOrLogging() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN, APPLICATION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.nio.file..", "java.time..", "java.util.logging..")
        .orShould()
        .dependOnClassesThat()
        .haveNameMatching(FILE_AND_CONSOLE_IO)
        .because("files, wall-clock time and logging belong to outer layers")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void noConsoleOutputOrPrintedStackTraces() {
    noClasses()
        .should()
        .accessField(System.class, "out")
        .orShould()
        .accessField(System.class, "err")
        .orShould()
        .callMethod(Throwable.class, "printStackTrace")
        .because("errors and logs go through the plugin logger with context")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void noVagueClassNames() {
    noClasses()
        .should()
        .haveSimpleNameEndingWith("Manager")
        .orShould()
        .haveSimpleNameEndingWith("Helper")
        .orShould()
        .haveSimpleNameEndingWith("Utils")
        .orShould()
        .haveSimpleNameEndingWith("Util")
        .because("a class name must say what the class is")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }
}
```

**`.github/workflows/build.yml`**
```yaml
name: build

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew build
```

## Procedimiento (en este orden)

1. Creá la rama `wp-00-andamiaje` desde `main`.
2. Creá `.gitattributes` y agregá `.kotlin/` a `.gitignore`.
3. **Wrapper de Gradle.** En esta PC Gradle no está instalado; se arma así:
   1. En una carpeta temporal **fuera del repo**, descargá `https://services.gradle.org/distributions/gradle-9.8.0-bin.zip`.
   2. Verificá que su SHA-256 sea `bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`. Si no coincide, frená.
   3. Descomprimí el zip.
   4. En otra carpeta temporal vacía, creá un `settings.gradle.kts` vacío y corré `<gradle-9.8.0>/bin/gradle wrapper --gradle-version 9.8.0 --distribution-type bin --gradle-distribution-sha256-sum bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
   5. Copiá al repo `gradlew`, `gradlew.bat` y `gradle/wrapper/` (con el `.jar` y el `.properties`).
   6. Después de `git add`, corré `git update-index --chmod=+x gradlew`, para que el CI de Linux lo pueda ejecutar.
   7. Commit: `build: add gradle 9.8.0 wrapper` (incluye `.gitattributes` y `.gitignore`).
4. Creá `settings.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts` y `plugin.yml`. Commit: `build: configure gradle build for paper 26.3 and java 25`.
5. Creá `MobAiPlugin.java`. Corré `./gradlew build`; la primera vez descarga el JDK 25 y paper-api. Commit: `feat: add empty MobAI plugin entry point`.
6. Creá `ArchitectureTest.java`. Corré `./gradlew spotlessApply` y después `./gradlew build`. Hacé la prueba de las reglas (sección siguiente). Commit: `test: enforce architecture rules with archunit`.
7. Creá `build.yml`. Commit: `ci: run gradle build on pull requests and main`.
8. Push y PR: título `WP-00: scaffolding, architecture rules and CI`.
9. Esperá a que termine el check `build` del PR (`gh pr checks <número> --watch`) y reportá si quedó verde. Si falla, copiá el log del paso que falló y frená.

## Verificaciones obligatorias (van en el informe)

1. **El build pasa:** `./gradlew build` termina con `BUILD SUCCESSFUL`. Las 15 ejecuciones de `ArchitectureTest` pasan: 9 pruebas, más 6 casos de la prueba parametrizada.
2. **Las reglas muerden.** Creá temporalmente estos dos archivos, que **no se commitean**:

   `src/main/java/io/github/nicodoou/mobai/domain/ArchProbe.java`
   ```java
   package io.github.nicodoou.mobai.domain;

   public final class ArchProbe {
     public static int counter;

     public long now() {
       return System.currentTimeMillis();
     }

     public double roll() {
       return Math.random();
     }

     public org.bukkit.entity.EntityType kind() {
       return org.bukkit.entity.EntityType.ZOMBIE;
     }

     public void print() {
       System.out.println("probe");
     }
   }
   ```

   `src/main/java/io/github/nicodoou/mobai/adapter/ProbeManager.java`
   ```java
   package io.github.nicodoou.mobai.adapter;

   public final class ProbeManager {}
   ```

   Corré `./gradlew test --tests "io.github.nicodoou.mobai.ArchitectureTest"`. Tienen que fallar **exactamente 7** ejecuciones y pasar las otras 8:

   | Prueba | Resultado esperado |
   | --- | --- |
   | `domainDependsOnlyOnJdkAndItself` | Falla |
   | `onlyVersionTranslatorUsesVersionSensitivePaperTypes`, caso `org.bukkit.entity.EntityType` | Falla |
   | Los otros 5 casos de esa prueba | Pasan |
   | `staticFieldsAreFinal` | Falla |
   | `domainAndApplicationDoNotUseRandomnessOrSystemTimeDirectly` | Falla |
   | `domainAndApplicationDoNotTouchFilesTimeOrLogging` | Falla (por `PrintStream`) |
   | `noConsoleOutputOrPrintedStackTraces` | Falla |
   | `noVagueClassNames` | Falla |
   | `applicationDependsOnlyOnDomainAndJdk`, `persistenceDependsOnlyOnDomainJdkAndGson`, `adapterDoesNotDependOnPersistenceOrBootstrap` | Pasan |

   Pegá en el informe la lista de fallas que muestra Gradle. Después borrá los dos archivos, corré `./gradlew build` de nuevo (tiene que pasar) y confirmá con `git status` que no quedaron rastros.
3. **El plugin.yml sale con la versión:** `unzip -p build/libs/mob-ai-plugin-0.1.0-SNAPSHOT.jar plugin.yml` muestra `version: '0.1.0-SNAPSHOT'`. Si no hay `unzip`, extraé `plugin.yml` con `"$JAVA_HOME/bin/jar" xf` en una carpeta temporal fuera del repo.
4. **Existen las tareas:** `./gradlew help --task runServer` las describe sin error (no la ejecutes). `./gradlew jacocoTestReport` termina bien.
5. **El CI pasa:** el check `build` del PR termina verde.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, corré `./gradlew spotlessApply`. Es lo esperado: el formato lo decide google-java-format.
2. Si la compilación falla con `-Werror` por una advertencia que menciona `org.jetbrains.annotations`:
   - agregá `jetbrains-annotations = { module = "org.jetbrains:annotations", version = "26.1.0" }` en `[libraries]`;
   - agregá `compileOnly(libs.jetbrains.annotations)` en `dependencies`;
   - avisalo en «Desvíos».
3. Si `ArchProbe` no compila por una deprecación de `EntityType`, cambiá en el probe `EntityType`/`ZOMBIE` por `org.bukkit.attribute.Attribute` y `Attribute.MAX_HEALTH`. El caso que falla pasa a ser `org.bukkit.attribute.Attribute`. Avisalo.

Cualquier otra falla (una versión que no resuelve, un plugin incompatible, una regla de ArchUnit con una API distinta): frená y reportá. **No cambies versiones, no quites `-Werror`, no quites ni relajes reglas.**

## Fuera de alcance

- Clases de dominio, aplicación, persistencia o adaptadores. Solo existe `MobAiPlugin`.
- `config.yml`, `messages.yml`, comandos y permisos.
- Correr `./gradlew runServer` o aceptar el EULA.
- `gradle.properties`, caché de configuración y otros ajustes de Gradle que no estén en este WP.

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos», con el contenido especificado (salvo el formato que aplique Spotless).
- [ ] `./gradlew build` está verde en la PC.
- [ ] La prueba de las reglas dio exactamente 7 fallas y los archivos de prueba se borraron.
- [ ] El `plugin.yml` del jar tiene la versión reemplazada.
- [ ] Los commits tienen los mensajes indicados (6, contando el de `build: pin google-java-format 1.36.1 for spotless compatibility`).
- [ ] El PR está abierto y su check `build` está verde.
