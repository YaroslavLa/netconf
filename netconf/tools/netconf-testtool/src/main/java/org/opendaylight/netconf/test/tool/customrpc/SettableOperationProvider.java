/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.customrpc;

import java.io.File;
import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.yangtools.concepts.Registration;

public class SettableOperationProvider implements NetconfOperationServiceFactory {
    private final File rpcConfig;

    public SettableOperationProvider(final File rpcConfig) {
        this.rpcConfig = rpcConfig;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of();
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        return () -> { };
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new SettableOperationService(rpcConfig);
    }

    private static class SettableOperationService implements NetconfOperationService {
        private final SettableRpc rpc;

        SettableOperationService(final File rpcConfig) {
            rpc = new SettableRpc(rpcConfig);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            return Set.of(rpc);
        }

        @Override
        public void close() {
            // no op
        }
    }
}
