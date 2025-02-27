/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class LibraryModulesSchemasTest {

    @Test
    public void testCreate() throws Exception {
        // test create from xml
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.xml").toString());

        verifySchemas(libraryModulesSchemas);

        // test create from json
        LibraryModulesSchemas libraryModuleSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library.json").toString());

        verifySchemas(libraryModulesSchemas);
    }

    private static void verifySchemas(final LibraryModulesSchemas libraryModulesSchemas) throws MalformedURLException {
        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();
        assertThat(resolvedModulesSchema.size(), is(3));

        assertTrue(resolvedModulesSchema.containsKey(new SourceIdentifier("module-with-revision", "2014-04-08")));
        assertThat(resolvedModulesSchema.get(new SourceIdentifier("module-with-revision", "2014-04-08")),
                is(new URL("http://localhost:8181/yanglib/schemas/module-with-revision/2014-04-08")));

        assertTrue(resolvedModulesSchema.containsKey(
                new SourceIdentifier("another-module-with-revision", "2013-10-21")));
        assertThat(resolvedModulesSchema.get(new SourceIdentifier("another-module-with-revision", "2013-10-21")),
                is(new URL("http://localhost:8181/yanglib/schemas/another-module-with-revision/2013-10-21")));

        assertTrue(resolvedModulesSchema.containsKey(new SourceIdentifier("module-without-revision")));
        assertThat(resolvedModulesSchema.get(new SourceIdentifier("module-without-revision")),
                is(new URL("http://localhost:8181/yanglib/schemas/module-without-revision/")));
    }

    @Test
    public void testCreateInvalidModulesEntries() throws Exception {
        LibraryModulesSchemas libraryModulesSchemas =
                LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail.xml").toString());

        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();
        assertThat(resolvedModulesSchema.size(), is(1));

        assertFalse(resolvedModulesSchema.containsKey(new SourceIdentifier("module-with-bad-url")));
        //See BUG 8071 https://bugs.opendaylight.org/show_bug.cgi?id=8071
        //assertFalse(resolvedModulesSchema.containsKey(
        //        RevisionSourceIdentifier.create("module-with-bad-revision", "bad-revision")));
        assertTrue(resolvedModulesSchema.containsKey(new SourceIdentifier("good-ol-module")));
    }

    @Test
    public void testCreateFromInvalidAll() throws Exception {
        // test bad yang lib url
        LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create("ObviouslyBadUrl");
        assertThat(libraryModulesSchemas.getAvailableModels(), is(Collections.emptyMap()));

        // TODO test also fail on json and xml parsing. But can we fail not on runtime exceptions?
    }
}