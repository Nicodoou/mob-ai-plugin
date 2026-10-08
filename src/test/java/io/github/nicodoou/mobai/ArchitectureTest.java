package io.github.nicodoou.mobai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
  private static final int MAX_PUBLIC_METHODS = 20;
  private static final int MAX_PARAMETERS = 3;

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

  @Test
  void classesHaveAtMostTwentyPublicMethods() {
    classes()
        .should(haveAtMostPublicMethods(MAX_PUBLIC_METHODS))
        .because("a class with a large public surface is doing too much")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  @Test
  void codeUnitsHaveAtMostThreeParameters() {
    codeUnits()
        .that(areWrittenBySource())
        .should(haveAtMostParameters(MAX_PARAMETERS))
        .because("more parameters are grouped in an object, such as a snapshot")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  // A record's canonical constructor is that grouping object. Lambdas and anonymous class
  // constructors are compiler-generated: their parameters were already counted where they are
  // written.
  private static DescribedPredicate<JavaCodeUnit> areWrittenBySource() {
    return DescribedPredicate.describe(
        "are written in the source and are not record canonical constructors",
        codeUnit ->
            !codeUnit.getModifiers().contains(JavaModifier.SYNTHETIC)
                && !isAnonymousClassConstructor(codeUnit)
                && !isRecordCanonicalConstructor(codeUnit));
  }

  private static boolean isAnonymousClassConstructor(JavaCodeUnit codeUnit) {
    return codeUnit instanceof JavaConstructor && codeUnit.getOwner().isAnonymousClass();
  }

  private static boolean isRecordCanonicalConstructor(JavaCodeUnit codeUnit) {
    Class<?> owner = codeUnit.getOwner().reflect();
    if (!(codeUnit instanceof JavaConstructor constructor) || !owner.isRecord()) {
      return false;
    }
    Class<?>[] componentTypes =
        Arrays.stream(owner.getRecordComponents())
            .map(RecordComponent::getType)
            .toArray(Class<?>[]::new);
    return Arrays.equals(componentTypes, constructor.reflect().getParameterTypes());
  }

  private static ArchCondition<JavaCodeUnit> haveAtMostParameters(int maximum) {
    return new ArchCondition<>("have at most " + maximum + " parameters") {
      @Override
      public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
        int count = codeUnit.getParameters().size();
        if (count > maximum) {
          events.add(
              SimpleConditionEvent.violated(
                  codeUnit,
                  codeUnit.getFullName()
                      + " has "
                      + count
                      + " parameters (maximum "
                      + maximum
                      + ")"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> haveAtMostPublicMethods(int maximum) {
    return new ArchCondition<>("have at most " + maximum + " public methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        long count = countPublicBehavior(javaClass.reflect());
        if (count > maximum) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " has "
                      + count
                      + " public methods (maximum "
                      + maximum
                      + ")"));
        }
      }
    };
  }

  // Record accessors and the methods the compiler writes for records and enums are not behavior.
  private static long countPublicBehavior(Class<?> type) {
    Set<String> generated = generatedMethodNames(type);
    return Arrays.stream(type.getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .filter(method -> !method.isSynthetic() && !method.isBridge())
        .filter(method -> !generated.contains(method.getName()))
        .count();
  }

  private static Set<String> generatedMethodNames(Class<?> type) {
    Set<String> names =
        new HashSet<>(Set.of("equals", "hashCode", "toString", "values", "valueOf"));
    if (type.isRecord()) {
      Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).forEach(names::add);
    }
    return names;
  }
}
