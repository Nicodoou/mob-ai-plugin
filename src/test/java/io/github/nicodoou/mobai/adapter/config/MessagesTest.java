package io.github.nicodoou.mobai.adapter.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {
  @Test
  void bundledMessagesHaveEveryKey() throws Exception {
    assertThatCode(() -> Messages.load(bundledMessages())).doesNotThrowAnyException();

    Messages messages = Messages.load(bundledMessages());
    for (MessageKey key : MessageKey.values()) {
      assertThat(messages.render(key, Map.of("reason", "motivo"))).isNotNull();
    }
  }

  @Test
  void renderFillsPlaceholdersAsPlainText() throws Exception {
    Messages messages = Messages.load(bundledMessages());

    Component rendered = messages.render(MessageKey.RELOAD_FAILED, Map.of("reason", "x<red>y"));

    assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
        .isEqualTo("No se recargó la configuración: x<red>y");
  }

  @Test
  void renderAppliesTheFormat() throws Exception {
    Messages messages = Messages.load(bundledMessages());

    Component rendered = messages.render(MessageKey.RELOAD_DONE, Map.of());

    assertThat(rendered.color()).isEqualTo(NamedTextColor.GREEN);
  }

  @Test
  void missingMessageIsReported() throws Exception {
    YamlConfiguration messages = bundledMessages();
    messages.set("reload-done", null);

    assertThatThrownBy(() -> Messages.load(messages))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage("messages.yml: missing reload-done");
  }

  private YamlConfiguration bundledMessages() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString(
        new String(getClass().getResourceAsStream("/messages.yml").readAllBytes(), UTF_8));
    return yaml;
  }
}
