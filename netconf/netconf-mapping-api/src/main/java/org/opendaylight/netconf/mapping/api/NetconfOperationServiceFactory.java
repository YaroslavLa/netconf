/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mapping.api;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Factory that must be registered in OSGi service registry in order to be used
 * by netconf-impl. Responsible for creating per-session instances of
 * {@link NetconfOperationService}.
 */
public interface NetconfOperationServiceFactory {
    /**
     * Get capabilities supported by current operation service.
     */
    @NonNull Set<Capability> getCapabilities();

    /**
     * Supported capabilities may change over time, registering a listener allows for push based information
     * retrieval about current notifications.
     */
    @NonNull Registration registerCapabilityListener(CapabilityListener listener);

    @NonNull NetconfOperationService createService(String netconfSessionIdForReporting);
}
