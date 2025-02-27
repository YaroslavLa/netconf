/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mapping.api;

import java.util.Set;

/**
 * Service of netconf operations.
 */
public interface NetconfOperationService extends AutoCloseable {
    /**
     * Get set of netconf operations that are handled by this service.
     */
    Set<NetconfOperation> getNetconfOperations();

    /**
     * Called when netconf session is destroyed.
     */
    @Override
    void close();
}
