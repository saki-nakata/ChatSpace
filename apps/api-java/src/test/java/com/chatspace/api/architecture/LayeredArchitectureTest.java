package com.chatspace.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * 計画書§1「3層アーキテクチャ」の制約を自動テストとして強制する。
 *
 * <p>フェーズ0時点では対象クラスが0件になるため{@code allowEmptyShould(true)}を暫定的に付与していたが、Controller/Service
 * が多数実在する現在は不要かつ有害(命名規約が崩れて対象クラスが誤って0件になった場合にテストが黙って通ってしまう =タイポ検知が効かなくなる)。レビュー指摘により削除した。
 */
class LayeredArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.chatspace.api");

  @Test
  void controllersMustNotDependOnRepositories() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .as("Controller は Service を経由せず Repository を直接呼んではならない(層飛ばし禁止)");

    rule.check(CLASSES);
  }

  @Test
  void servicesMustNotDependOnServletOrWebMvcTypes() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Service")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
            .as("Service は HTTP の概念を持ち込まない(STOMPハンドラや統合テストからの再利用性のため)");

    rule.check(CLASSES);
  }

  @Test
  void controllersMustNotReturnJpaEntitiesDirectly() {
    // 標準DSLの haveRawReturnType(...) はジェネリクス型引数(例: ResponseEntity<UserEntity>)を
    // 素通りしてしまうため、カスタム ArchCondition(EntityReturnTypeCondition)で
    // 戻り値型のジェネリクス引数まで再帰的に検査する(計画書§1が明記する既知の制約への対応)。
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should(EntityReturnTypeCondition.notReturnJpaEntitiesDirectly());

    rule.check(CLASSES);
  }
}
