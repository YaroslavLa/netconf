/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.MERGE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PatchDataTransactionUtilTest {
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";
    @Mock
    private DOMDataTreeReadWriteTransaction rwTransaction;
    @Mock
    private DOMDataBroker mockDataBroker;
    @Mock
    private NetconfDataTreeService netconfService;

    private EffectiveModelContext refSchemaCtx;
    private YangInstanceIdentifier instanceIdContainer;
    private YangInstanceIdentifier instanceIdCreateAndDelete;
    private YangInstanceIdentifier instanceIdMerge;
    private ContainerNode buildBaseContainerForTests;
    private YangInstanceIdentifier targetNodeForCreateAndDelete;
    private YangInstanceIdentifier targetNodeMerge;
    private MapNode buildArtistList;

    @Before
    public void setUp() throws Exception {
        refSchemaCtx = YangParserTestUtils.parseYangFiles(
            TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerPlayerQName = QName.create(baseQName, "player");
        final QName leafGapQName = QName.create(baseQName, "gap");
        final QName containerLibraryQName = QName.create(baseQName, "library");
        final QName listArtistQName = QName.create(baseQName, "artist");
        final QName leafNameQName = QName.create(baseQName, "name");
        final NodeIdentifierWithPredicates nodeWithKey = NodeIdentifierWithPredicates.of(listArtistQName, leafNameQName,
            "name of artist");

        /* instance identifier for accessing container node "player" */
        instanceIdContainer = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerPlayerQName)
                .build();

        /* instance identifier for accessing leaf node "gap" */
        instanceIdCreateAndDelete = instanceIdContainer.node(leafGapQName);

        /* values that are used for creating leaf for testPatchDataCreateAndDelete test */
        final LeafNode<?> buildGapLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(leafGapQName))
                .withValue(0.2)
                .build();

        final ContainerNode buildPlayerContainer = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(containerPlayerQName))
                .withChild(buildGapLeaf)
                .build();

        buildBaseContainerForTests = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(baseQName))
                .withChild(buildPlayerContainer)
                .build();

        targetNodeForCreateAndDelete = YangInstanceIdentifier.builder(instanceIdCreateAndDelete)
                .node(containerPlayerQName)
                .node(leafGapQName)
                .build();

        /* instance identifier for accessing leaf node "name" in list "artist" */
        instanceIdMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, QName.create(listArtistQName, "name"), "name of artist")
                .node(leafNameQName)
                .build();

        /* values that are used for creating leaf for testPatchDataReplaceMergeAndRemove test */
        final LeafNode<Object> contentName = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of artist")
                .build();

        final LeafNode<Object> contentDescription = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("description of artist")
                .build();

        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(contentName)
                .withChild(contentDescription)
                .build();

        buildArtistList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listArtistQName))
                .withChild(mapEntryNode)
                .build();

        targetNodeMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, leafNameQName, "name of artist")
                .build();

        /* Mocks */
        doReturn(rwTransaction).when(mockDataBroker).newReadWriteTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(rwTransaction).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).discardChanges();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).unlock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).lock();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).merge(any(), any(),
            any(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService).replace(any(), any(),
            any(), any());
    }

    @Test
    public void testPatchDataReplaceMergeAndRemove() {
        final PatchEntity entityReplace =
                new PatchEntity("edit1", REPLACE, targetNodeMerge, buildArtistList);
        final PatchEntity entityMerge = new PatchEntity("edit2", MERGE, targetNodeMerge, buildArtistList);
        final PatchEntity entityRemove = new PatchEntity("edit3", REMOVE, targetNodeMerge);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityReplace);
        entities.add(entityMerge);
        entities.add(entityRemove);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(refSchemaCtx, instanceIdMerge);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchRMRm");

        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .remove(LogicalDatastoreType.CONFIGURATION, targetNodeMerge);

        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    @Test
    public void testPatchDataCreateAndDelete() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            instanceIdContainer);
        doReturn(immediateTrueFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            targetNodeForCreateAndDelete);
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .create(LogicalDatastoreType.CONFIGURATION, instanceIdContainer, buildBaseContainerForTests,
                Optional.empty());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, targetNodeForCreateAndDelete);

        final PatchEntity entityCreate =
                new PatchEntity("edit1", CREATE, instanceIdContainer, buildBaseContainerForTests);
        final PatchEntity entityDelete =
                new PatchEntity("edit2", DELETE, targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityDelete);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(refSchemaCtx, instanceIdCreateAndDelete);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchCD");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), true);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), true);
    }

    @Test
    public void deleteNonexistentDataTest() {
        doReturn(immediateFalseFluentFuture()).when(rwTransaction).exists(LogicalDatastoreType.CONFIGURATION,
            targetNodeForCreateAndDelete);
        final NetconfDocumentedException exception = new NetconfDocumentedException("id",
            ErrorType.RPC, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR);
        final SettableFuture<? extends DOMRpcResult> ret = SettableFuture.create();
        ret.setException(new TransactionCommitFailedException(
            String.format("Commit of transaction %s failed", this), exception));

        doReturn(ret).when(netconfService).commit();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(netconfService)
            .delete(LogicalDatastoreType.CONFIGURATION, targetNodeForCreateAndDelete);

        final PatchEntity entityDelete = new PatchEntity("edit", DELETE, targetNodeForCreateAndDelete);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityDelete);

        final PatchContext patchContext = new PatchContext(
            InstanceIdentifierContext.ofLocalPath(refSchemaCtx, instanceIdCreateAndDelete), entities, "patchD");
        deleteMdsal(patchContext, new MdsalRestconfStrategy(mockDataBroker));
        deleteNetconf(patchContext, new NetconfRestconfStrategy(netconfService));
    }

    @Test
    public void testPatchMergePutContainer() {
        final PatchEntity entityMerge =
                new PatchEntity("edit1", MERGE, instanceIdContainer, buildBaseContainerForTests);
        final List<PatchEntity> entities = new ArrayList<>();

        entities.add(entityMerge);

        final InstanceIdentifierContext iidContext =
            InstanceIdentifierContext.ofLocalPath(refSchemaCtx, instanceIdCreateAndDelete);
        final PatchContext patchContext = new PatchContext(iidContext, entities, "patchM");
        patch(patchContext, new MdsalRestconfStrategy(mockDataBroker), false);
        patch(patchContext, new NetconfRestconfStrategy(netconfService), false);
    }

    private void patch(final PatchContext patchContext, final RestconfStrategy strategy,
                       final boolean failed) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, refSchemaCtx);
        for (final PatchStatusEntity entity : patchStatusContext.getEditCollection()) {
            if (failed) {
                assertTrue("Edit " + entity.getEditId() + " failed", entity.isOk());
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.isOk());
    }

    private void deleteMdsal(final PatchContext patchContext, final RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext =
                PatchDataTransactionUtil.patchData(patchContext, strategy, refSchemaCtx);

        assertFalse(patchStatusContext.isOk());
        assertEquals(ErrorType.PROTOCOL,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING,
                patchStatusContext.getEditCollection().get(0).getEditErrors().get(0).getErrorTag());
    }

    private void deleteNetconf(final PatchContext patchContext, final RestconfStrategy strategy) {
        final PatchStatusContext patchStatusContext =
            PatchDataTransactionUtil.patchData(patchContext, strategy, refSchemaCtx);

        assertFalse(patchStatusContext.isOk());
        assertEquals(ErrorType.PROTOCOL,
            patchStatusContext.getGlobalErrors().get(0).getErrorType());
        assertEquals(ErrorTag.DATA_MISSING,
            patchStatusContext.getGlobalErrors().get(0).getErrorTag());
    }
}
