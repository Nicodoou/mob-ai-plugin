package io.github.nicodoou.mobai.adapter.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

/** Player-facing texts from messages.yml, written in MiniMessage. */
public final class Messages {
  private final Map<MessageKey, String> templates;

  private Messages(Map<MessageKey, String> templates) {
    this.templates = templates;
  }

  public static Messages load(ConfigurationSection root) {
    Map<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
    for (MessageKey key : MessageKey.values()) {
      String template = root.getString(key.path());
      if (template == null) {
        throw new InvalidConfigException("messages.yml: missing " + key.path());
      }
      templates.put(key, template);
    }
    return new Messages(Collections.unmodifiableMap(templates));
  }

  public Component render(MessageKey key, Map<String, String> values) {
    TagResolver[] placeholders =
        values.entrySet().stream()
            .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
            .toArray(TagResolver[]::new);
    return MiniMessage.miniMessage().deserialize(templates.get(key), placeholders);
  }
}
