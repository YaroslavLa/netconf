/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.netconf.AbstractTestModelTest;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.rfc8528.data.util.EmptyMountPointContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class WriteRunningTxTest extends AbstractTestModelTest {
    private final RemoteDeviceId id =
        new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));

    @Mock
    private Rpcs.Normalized rpc;
    private NetconfBaseOps netconfOps;

    @Before
    public void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpc).invokeNetconf(any(), any());
        netconfOps = new NetconfBaseOps(rpc, new EmptyMountPointContext(SCHEMA_CONTEXT));
    }

    @Test
    public void testSubmit() throws Exception {
        final WriteRunningTx tx = new WriteRunningTx(id, netconfOps, true);
        //check, if lock is called
        verify(rpc).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), any());
        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if no edit-config is called before submit
        verify(rpc, never()).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), any());
        tx.commit().get();
        //check, if both edits are called
        verify(rpc, times(2)).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), any());
        //check, if unlock is called
        verify(rpc).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), any());
    }
}