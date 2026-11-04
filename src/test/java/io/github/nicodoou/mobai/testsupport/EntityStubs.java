package io.github.nicodoou.mobai.testsupport;

import java.lang.reflect.Proxy;
import java.util.Map;

/** Paper's entities are interfaces: these answer the named methods and refuse any other. */
public final class EntityStubs {
  private EntityStubs() {}

  public static <T> T stub(Class<T> type, Map<String, Object> answers) {
    Object stub =
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, arguments) -> {
              if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
              }
              throw new UnsupportedOperationException(
                  type.getSimpleName() + "." + method.getName());
            });
    return type.cast(stub);
  }
}
