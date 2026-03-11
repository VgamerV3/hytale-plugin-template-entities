package net.hytaledepot.templates.plugin.entities;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class EntitiesDemoCommand extends AbstractCommand {
  private final EntitiesPluginState state;
  private final EntitiesDemoService demoService;
  private final AtomicLong heartbeatTicks;

  public EntitiesDemoCommand(EntitiesPluginState state, EntitiesDemoService demoService, AtomicLong heartbeatTicks) {
    super("hdentitiesdemo", "Runs a demo action for the Entities template.");
    setAllowsExtraArguments(true);
    this.state = state;
    this.demoService = demoService;
    this.heartbeatTicks = heartbeatTicks;
  }

  @Override
  protected CompletableFuture<Void> execute(CommandContext ctx) {
    state.incrementCommandRequests();
    String sender = String.valueOf(ctx.sender().getDisplayName());
    String action = parseAction(ctx.getInputString());

    String result = demoService.applyAction(ctx, state, sender, action, heartbeatTicks.get());
    ctx.sendMessage(Message.raw(result));
    return CompletableFuture.completedFuture(null);
  }

  private static String parseAction(String input) {
    String normalized = String.valueOf(input == null ? "" : input).trim();
    if (normalized.isEmpty()) {
      return "spawn-passive";
    }
    String[] parts = normalized.split("\\s+");
    String first = parts[0].toLowerCase();
    if (first.startsWith("/")) {
      first = first.substring(1);
    }
    if ((parts.length == 1) && first.startsWith("hdentities")) {
      return "spawn-passive";
    }
    if (parts.length > 1 && first.startsWith("hd")) {
      return parts[1].toLowerCase();
    }
    return first;
  }
}
