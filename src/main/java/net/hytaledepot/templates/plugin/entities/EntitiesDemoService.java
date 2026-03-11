package net.hytaledepot.templates.plugin.entities;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import it.unimi.dsi.fastutil.Pair;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class EntitiesDemoService {
  private static final String[] PASSIVE_ROLE_HINTS = {
      "passive", "villager", "civil", "deer", "sheep", "boar", "rabbit", "chicken", "cow"
  };
  private static final String[] AGGRESSIVE_ROLE_HINTS = {
      "aggressive", "hostile", "combat", "enemy", "predator", "guard", "wolf", "goblin", "bandit"
  };

  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Map<String, String> runtimeValues = new ConcurrentHashMap<>();
  private final Map<String, String> domainState = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> numericState = new ConcurrentHashMap<>();
  private final Map<String, UUID> trackedNpcBySender = new ConcurrentHashMap<>();
  private final Map<String, String> trackedRoleBySender = new ConcurrentHashMap<>();

  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    runtimeValues.put("category", "Entities");
    runtimeValues.put("defaultAction", "spawn-passive");
    runtimeValues.put("initialized", "true");
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 120 == 0) {
      runtimeValues.put("lastHeartbeat", String.valueOf(tick));
    }
  }

  public void recordExternalEvent(String key) {
    actionCounters.computeIfAbsent(String.valueOf(key), item -> new AtomicLong()).incrementAndGet();
  }

  public String applyAction(CommandContext ctx, EntitiesPluginState state, String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = state.toggleDemoFlag();
      runtimeValues.put("demoFlag", String.valueOf(enabled));
      return "[Entities] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[Entities] " + diagnostics();
    }

    String domainResult = handleDomainAction(ctx, normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[Entities] " + domainResult;
    }

    return "[Entities] unknown action='"
        + normalizedAction
        + "' (try: info, toggle, spawn-passive, set-aggressive, despawn-demo)";
  }

  public String describeLastAction(String sender) {
    return lastActionBySender.getOrDefault(String.valueOf(sender), "none");
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public String diagnostics() {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "ops="
        + operationCount()
        + ", trackedActions="
        + actionCounters.size()
        + ", trackedNpcs="
        + trackedNpcBySender.size()
        + ", domainEntries="
        + domainState.size()
        + ", numericEntries="
        + numericState.size()
        + ", dataDirectory="
        + directory;
  }

  public void shutdown() {
    runtimeValues.put("initialized", "false");
    trackedNpcBySender.clear();
    trackedRoleBySender.clear();
  }

  private String handleDomainAction(CommandContext ctx, String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "track-entity".equals(action) || "spawn-passive".equals(action) || "spawn-demo".equals(action)) {
      return spawnPassiveNpc(ctx, sender, heartbeatTicks);
    }
    if ("set-attribute".equals(action) || "set-aggressive".equals(action) || "aggressive".equals(action) || "morph-aggressive".equals(action)) {
      return morphToAggressive(ctx, sender, heartbeatTicks);
    }
    if ("despawn-demo".equals(action) || "despawn".equals(action) || "remove".equals(action)) {
      return despawnTrackedNpc(ctx, sender, heartbeatTicks);
    }
    return null;
  }

  private String spawnPassiveNpc(CommandContext ctx, String sender, long heartbeatTicks) {
    PlayerContext playerContext = resolvePlayerContext(ctx);
    if (playerContext == null) {
      return "Spawn requires a player sender.";
    }

    NPCPlugin npcPlugin = NPCPlugin.get();
    if (npcPlugin == null) {
      return "NPC plugin is not available in this runtime.";
    }

    String passiveRole = findRoleName(npcPlugin, PASSIVE_ROLE_HINTS, null);
    if (passiveRole == null || passiveRole.isBlank()) {
      return "No spawnable NPC roles are currently available.";
    }

    playerContext.world.execute(
        () -> {
          TransformComponent transform = playerContext.store.getComponent(playerContext.playerRef, TransformComponent.getComponentType());
          HeadRotation headRotation = playerContext.store.getComponent(playerContext.playerRef, HeadRotation.getComponentType());
          if (transform == null || headRotation == null) {
            return;
          }

          Vector3d spawnPosition = new Vector3d(transform.getPosition()).add(2.0, 0.0, 2.0);
          Vector3f spawnRotation = new Vector3f(headRotation.getRotation());

          Pair<Ref<EntityStore>, ?> spawned = npcPlugin.spawnNPC(playerContext.store, passiveRole, null, spawnPosition, spawnRotation);
          if (spawned == null || spawned.left() == null) {
            return;
          }

          Ref<EntityStore> npcRef = spawned.left();
          UUIDComponent uuidComponent = playerContext.store.getComponent(npcRef, UUIDComponent.getComponentType());
          if (uuidComponent == null) {
            return;
          }

          UUID npcUuid = uuidComponent.getUuid();
          trackedNpcBySender.put(sender, npcUuid);
          trackedRoleBySender.put(sender, passiveRole);
          domainState.put("entity:" + npcUuid + ":owner", sender);
          domainState.put("entity:" + npcUuid + ":role", passiveRole);
          domainState.put("entity:" + npcUuid + ":state", "spawned-passive");
          incrementNumber("entity:sequence", 1L);
          runtimeValues.put("lastHeartbeat", String.valueOf(heartbeatTicks));
        });

    return "Spawn queued. role=" + passiveRole + " (will appear near the player).";
  }

  private String morphToAggressive(CommandContext ctx, String sender, long heartbeatTicks) {
    PlayerContext playerContext = resolvePlayerContext(ctx);
    if (playerContext == null) {
      return "Role change requires a player sender.";
    }

    UUID trackedNpc = trackedNpcBySender.get(sender);
    if (trackedNpc == null) {
      return "No tracked NPC for this sender. Run /hdentitiesdemo spawn-passive first.";
    }

    NPCPlugin npcPlugin = NPCPlugin.get();
    if (npcPlugin == null) {
      return "NPC plugin is not available in this runtime.";
    }

    String previousRole = trackedRoleBySender.get(sender);
    String aggressiveRole = findRoleName(npcPlugin, AGGRESSIVE_ROLE_HINTS, previousRole);
    if (aggressiveRole == null || aggressiveRole.isBlank()) {
      return "Could not find an aggressive role template to switch to.";
    }

    playerContext.world.execute(
        () -> {
          Ref<EntityStore> npcRef = playerContext.entityStore.getRefFromUUID(trackedNpc);
          if (npcRef == null || !npcRef.isValid()) {
            trackedNpcBySender.remove(sender);
            trackedRoleBySender.remove(sender);
            return;
          }

          NPCEntity npc = playerContext.store.getComponent(npcRef, NPCEntity.getComponentType());
          if (npc == null || npc.getRole() == null) {
            return;
          }

          int roleIndex = npcPlugin.getIndex(aggressiveRole);
          if (roleIndex >= 0) {
            RoleChangeSystem.requestRoleChange(npcRef, npc.getRole(), roleIndex, true, playerContext.store);
          }

          // Keep the demo deterministic: force a known attack override id and aggressive flock state.
          npc.getRole().getCombatSupport().clearAttackOverrides();
          npc.getRole().getCombatSupport().addAttackOverride("combat/basic_melee");
          try {
            npc.onFlockSetState(npcRef, "state", "aggressive", playerContext.store);
          } catch (Exception ignored) {
            // Some roles do not expose that state name; keep the role switch as the main behavior change.
          }

          trackedRoleBySender.put(sender, aggressiveRole);
          domainState.put("entity:" + trackedNpc + ":role", aggressiveRole);
          domainState.put("entity:" + trackedNpc + ":state", "aggressive");
          domainState.put("entity:" + trackedNpc + ":damageProfile", "combat/basic_melee");
          runtimeValues.put("lastHeartbeat", String.valueOf(heartbeatTicks));
        });

    return "Role change queued. passive -> aggressive role=" + aggressiveRole + " with melee damage profile.";
  }

  private String despawnTrackedNpc(CommandContext ctx, String sender, long heartbeatTicks) {
    PlayerContext playerContext = resolvePlayerContext(ctx);
    if (playerContext == null) {
      return "Despawn requires a player sender.";
    }

    UUID trackedNpc = trackedNpcBySender.get(sender);
    if (trackedNpc == null) {
      return "No tracked NPC for this sender.";
    }

    playerContext.world.execute(
        () -> {
          Ref<EntityStore> npcRef = playerContext.entityStore.getRefFromUUID(trackedNpc);
          if (npcRef != null && npcRef.isValid()) {
            NPCEntity npc = playerContext.store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null) {
              npc.setToDespawn();
              npc.remove();
            }
          }

          trackedNpcBySender.remove(sender);
          trackedRoleBySender.remove(sender);
          domainState.put("entity:" + trackedNpc + ":state", "despawned");
          runtimeValues.put("lastHeartbeat", String.valueOf(heartbeatTicks));
        });

    return "Despawn queued for tracked NPC " + trackedNpc + ".";
  }

  private static String findRoleName(NPCPlugin npcPlugin, String[] hints, String fallbackRole) {
    List<String> roleNames = npcPlugin.getRoleTemplateNames(true);
    if (roleNames == null || roleNames.isEmpty()) {
      return fallbackRole;
    }

    for (String roleName : roleNames) {
      String normalized = roleName.toLowerCase();
      for (String hint : hints) {
        if (normalized.contains(hint)) {
          return roleName;
        }
      }
    }

    if (fallbackRole != null && npcPlugin.hasRoleName(fallbackRole)) {
      return fallbackRole;
    }
    return roleNames.get(0);
  }

  private static PlayerContext resolvePlayerContext(CommandContext ctx) {
    if (ctx == null || !ctx.isPlayer()) {
      return null;
    }

    Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
    if (playerRef == null || !playerRef.isValid()) {
      return null;
    }

    Store<EntityStore> store = playerRef.getStore();
    EntityStore entityStore = store.getExternalData();
    World world = entityStore.getWorld();
    return new PlayerContext(playerRef, store, entityStore, world);
  }

  private static final class PlayerContext {
    private final Ref<EntityStore> playerRef;
    private final Store<EntityStore> store;
    private final EntityStore entityStore;
    private final World world;

    private PlayerContext(
        Ref<EntityStore> playerRef,
        Store<EntityStore> store,
        EntityStore entityStore,
        World world) {
      this.playerRef = playerRef;
      this.store = store;
      this.entityStore = entityStore;
      this.world = world;
    }
  }

  private long incrementNumber(String key, long delta) {
    return numericState.computeIfAbsent(key, item -> new AtomicLong()).addAndGet(delta);
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "spawn-passive" : normalized;
  }
}
