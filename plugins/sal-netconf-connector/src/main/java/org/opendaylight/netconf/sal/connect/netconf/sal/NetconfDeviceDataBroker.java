/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.dom.api.DOMTransactionChainListener;
import org.opendaylight.mdsal.dom.spi.PingPongMergingDOMDataBroker;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMDataBrokerFieldsExtension;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsTransactionChain;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.FieldsAwareReadOnlyTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.FieldsAwareReadWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.FieldsAwareTxChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadOnlyTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.TxChain;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

public final class NetconfDeviceDataBroker implements PingPongMergingDOMDataBroker {
    private final NetconfDOMDataBrokerFieldsExtension fieldsExtension = new NetconfDOMDataBrokerFieldsExtensionImpl();
    private final RemoteDeviceId id;
    private final NetconfBaseOps netconfOps;
    private final boolean rollbackSupport;
    private final boolean candidateSupported;
    private final boolean runningWritable;
    private final boolean lockDatastore;

    public NetconfDeviceDataBroker(final RemoteDeviceId id, final MountPointContext mountContext, final Rpcs rpcs,
            final NetconfSessionPreferences netconfSessionPreferences, final boolean lockDatastore) {
        this.id = id;
        netconfOps = new NetconfBaseOps(rpcs, mountContext);
        // get specific attributes from netconf preferences and get rid of it
        // no need to keep the entire preferences object, its quite big with all the capability QNames
        candidateSupported = netconfSessionPreferences.isCandidateSupported();
        runningWritable = netconfSessionPreferences.isRunningWritable();
        rollbackSupport = netconfSessionPreferences.isRollbackSupported();
        checkArgument(candidateSupported || runningWritable,
            "Device %s has advertised neither :writable-running nor :candidate capability. At least one of these "
                + "should be advertised. Failed to establish a session.", id.name());
        this.lockDatastore = lockDatastore;
    }

    @Override
    public DOMDataTreeReadTransaction newReadOnlyTransaction() {
        return new ReadOnlyTx(netconfOps, id);
    }

    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        return new ReadWriteTx<>(newReadOnlyTransaction(), newWriteOnlyTransaction());
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        if (candidateSupported) {
            if (runningWritable) {
                return new WriteCandidateRunningTx(id, netconfOps, rollbackSupport, lockDatastore);
            } else {
                return new WriteCandidateTx(id, netconfOps, rollbackSupport, lockDatastore);
            }
        } else {
            return new WriteRunningTx(id, netconfOps, rollbackSupport, lockDatastore);
        }
    }

    @Override
    public DOMTransactionChain createTransactionChain(final DOMTransactionChainListener listener) {
        return new TxChain(this, listener);
    }

    @Override
    public ClassToInstanceMap<DOMDataBrokerExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of(NetconfDOMDataBrokerFieldsExtension.class, fieldsExtension);
    }

    private final class NetconfDOMDataBrokerFieldsExtensionImpl implements NetconfDOMDataBrokerFieldsExtension {
        @Override
        public NetconfDOMFieldsReadTransaction newReadOnlyTransaction() {
            return new FieldsAwareReadOnlyTx(netconfOps, id);
        }

        @Override
        public NetconfDOMFieldsReadWriteTransaction newReadWriteTransaction() {
            return new FieldsAwareReadWriteTx(newReadOnlyTransaction(), newWriteOnlyTransaction());
        }

        @Override
        public NetconfDOMFieldsTransactionChain createTransactionChain(final DOMTransactionChainListener listener) {
            return new FieldsAwareTxChain(NetconfDeviceDataBroker.this, listener, this);
        }
    }
}