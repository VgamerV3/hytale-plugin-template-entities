package net.hytaledepot.templates.plugin.entities;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class EntitiesPluginState {
  private final AtomicReference<EntitiesPluginLifecycle> lifecycle = new AtomicReference<>(EntitiesPluginLifecycle.NEW);
  private final AtomicBoolean setupCompleted = new AtomicBoolean(false);
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong statusRequests = new AtomicLong();
  private final AtomicLong commandRequests = new AtomicLong();
  private final AtomicLong errorCount = new AtomicLong();

  private volatile String templateName = "Entities";
  private volatile String dataDirectory = "";

  public EntitiesPluginLifecycle getLifecycle() {
    return lifecycle.get();
  }

  public void setLifecycle(EntitiesPluginLifecycle next) {
    lifecycle.set(next);
  }

  public boolean isSetupCompleted() {
    return setupCompleted.get();
  }

  public void markSetupCompleted() {
    setupCompleted.set(true);
  }

  public boolean isDemoFlagEnabled() {
    return demoFlagEnabled.get();
  }

  public boolean toggleDemoFlag() {
    while (true) {
      boolean current = demoFlagEnabled.get();
      boolean next = !current;
      if (demoFlagEnabled.compareAndSet(current, next)) {
        return next;
      }
    }
  }

  public long incrementStatusRequests() {
    return statusRequests.incrementAndGet();
  }

  public long incrementCommandRequests() {
    return commandRequests.incrementAndGet();
  }

  public long getStatusRequests() {
    return statusRequests.get();
  }

  public long getCommandRequests() {
    return commandRequests.get();
  }

  public long incrementErrorCount() {
    return errorCount.incrementAndGet();
  }

  public long getErrorCount() {
    return errorCount.get();
  }

  public String getTemplateName() {
    return templateName;
  }

  public void setTemplateName(String templateName) {
    this.templateName = String.valueOf(templateName);
  }

  public String getDataDirectory() {
    return dataDirectory;
  }

  public void setDataDirectory(String dataDirectory) {
    this.dataDirectory = String.valueOf(dataDirectory);
  }
}
