package com.limelight.nvstream.mdns;

public interface MdnsDiscoveryListener {
    // CloudGI: expose unresolved service names to the discovery diagnostics panel.
    default void notifyServiceFound(String name) {}

    void notifyComputerAdded(MdnsComputer computer);
    void notifyDiscoveryFailure(Exception e);
}
