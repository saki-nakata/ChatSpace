package com.chatspace.api.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller の戻り値型に JPA エンティティ({@code @Entity} 付きクラス)を直接使っていないかを検査する。
 *
 * <p>ArchUnit 標準の {@code haveRawReturnType(...)} は raw type しか見ないため、 {@code
 * ResponseEntity<UserEntity>} や {@code List<UserEntity>} のようにジェネリクスの
 * 型引数に埋め込まれたエンティティ型を素通りしてしまう。このクラスはメソッドの ジェネリック戻り値型({@link JavaMethod#getReturnType()})を再帰的にたどり、
 * 型引数も含めてエンティティ型が現れないことを検査する(計画書§1で明記されている、 標準DSLでは書けないという既知の制約への対応)。
 */
final class EntityReturnTypeCondition {

  private EntityReturnTypeCondition() {}

  static ArchCondition<JavaClass> notReturnJpaEntitiesDirectly() {
    return new ArchCondition<>(
        "not return JPA entities directly (including as generic type arguments)") {
      @Override
      public void check(JavaClass controllerClass, ConditionEvents events) {
        for (JavaMethod method : controllerClass.getMethods()) {
          List<JavaClass> entityTypesFound = new ArrayList<>();
          collectEntityTypes(method.getReturnType(), entityTypesFound, new HashSet<>());
          for (JavaClass entityType : entityTypesFound) {
            String message =
                String.format(
                    "Method <%s> declares JPA entity <%s> in its return type -"
                        + " use a DTO instead",
                    method.getFullName(), entityType.getFullName());
            events.add(SimpleConditionEvent.violated(method, message));
          }
        }
      }
    };
  }

  private static void collectEntityTypes(
      JavaType type, List<JavaClass> found, Set<JavaType> visited) {
    if (!visited.add(type)) {
      return; // 循環参照(自己参照エンティティ等)による無限再帰を防止
    }
    if (type instanceof JavaClass javaClass && javaClass.isAnnotatedWith(Entity.class)) {
      found.add(javaClass);
    }
    if (type instanceof JavaParameterizedType parameterizedType) {
      for (JavaType typeArgument : parameterizedType.getActualTypeArguments()) {
        collectEntityTypes(typeArgument, found, visited);
      }
    }
  }
}
