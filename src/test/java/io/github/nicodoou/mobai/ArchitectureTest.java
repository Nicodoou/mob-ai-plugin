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
