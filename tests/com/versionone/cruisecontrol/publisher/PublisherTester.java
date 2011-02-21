/*(c) Copyright 2008, VersionOne, Inc. All rights reserved. (c)*/
package com.versionone.cruisecontrol.publisher;

import com.versionone.om.BuildRun;
import com.versionone.om.ChangeSet;
import com.versionone.om.V1Instance;
import com.versionone.om.filters.BuildRunFilter;
import com.versionone.DB;
import net.sourceforge.cruisecontrol.util.XMLLogHelper;
import org.jdom.Element;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Date;

/**
 * Tester for the VersionOnePublisher plugin
 */
public class PublisherTester {
    private final static String testFile273 = ".\\testdata.xml";
    private final static String testFile282 = ".\\testdata282.xml";
    private V1Instance v1Instance;
    private static final String APPLICATION_PATH = "http://jsdksrv01:8080/VersionOne/";
    private static final String USER_NAME = "admin";
    private static final String PASSWD = "admin";
    //date for testing CC 2.7.3. log
    private static final DB.DateTime dateOldVersion = new DB.DateTime("2008-09-15T16:53:55");
    //date for testing CC 2.8.2 log
    private static final DB.DateTime dateNewVersion = new DB.DateTime("2009-04-17T13:43:48");

    public V1Instance getV1Instance() {
        if (v1Instance == null) {
            v1Instance = new V1Instance(APPLICATION_PATH, USER_NAME, PASSWD);
        }
        return v1Instance;
    }

    private static VersionOnePublisher createPublisher() {
        VersionOnePublisher publisher = new VersionOnePublisher();
        publisher.setUrl(APPLICATION_PATH);
        publisher.setUserName(USER_NAME);
        publisher.setPassword(PASSWD);
        publisher.setReferenceExpression("[A-Z]{1,2}-[0-9]+");
        publisher.setReferenceField("Number");
        publisher.setWebRoot("http://localhost:8080");
        return publisher;
    }

    private static <T> List<T> newList(T... s) {
        return Arrays.asList(s);
    }

    @Test
    public void testDate273() throws Exception {
        final Element log = getTextXml273();
        DB.DateTime date = VersionOnePublisher.getBuildDate(new XMLLogHelper(log));

        assertEquals(((Date)date.getValue()).getTime(), ((Date)dateOldVersion.getValue()).getTime());
    }

    @Test
    public void testDate282() throws Exception {
        final Element log = getTextXml282();
        DB.DateTime date = VersionOnePublisher.getBuildDate(new XMLLogHelper(log));

        assertEquals(((Date)date.getValue()).getTime(), ((Date)dateNewVersion.getValue()).getTime());
    }

    @Test
    @Ignore("Integrational test. Need VersionOne server with data coherent to testdata.xml. ")
    public void testPublish() throws Exception {
        final BuildRunFilter filter = new BuildRunFilter();
        filter.name.add("cctest - build.7");
        filter.references.add("build.7");
        filter.status.add("Passed");
        filter.source.add("Forced");
        Collection<BuildRun> buildRuns = getV1Instance().get().buildRuns(filter);
        for (BuildRun run : buildRuns) {
            for (ChangeSet changeSet : run.getChangeSets()) {
                changeSet.delete();
            }
            run.delete();
        }
        VersionOnePublisher publisher = createPublisher();
        publisher.validate();
        final Element log = getTextXml273();
        publisher.publish(log);

        buildRuns = getV1Instance().get().buildRuns(filter);

        assertEquals(1, buildRuns.size());
        final BuildRun run = buildRuns.iterator().next();
        final Collection<ChangeSet> changeSets = run.getChangeSets();
        assertEquals(1, changeSets.size());
        final ChangeSet change = changeSets.iterator().next();
        assertEquals("cctest", run.getBuildProject().getReference());
        assertEquals("test: \tB-01382update data B-01713", run.getDescription());

        assertEquals(2, change.getPrimaryWorkitems().size());
        assertEquals("7", change.getReference());
        assertEquals("test on Mon Sep 15 16:53:25 MSD 2008", change.getName());
        change.delete();
        run.delete();
    }


    @Test
    public void testTaskId() {
        final VersionOnePublisher publisher = createPublisher();
        publisher.setReferenceExpression("[A-Z]{1,2}-[0-9]+");

        final Map<String, List<String>> comments = new HashMap<String, List<String>>();

        comments.put("testing TD-123 dflkxbc", newList("TD-123"));
        comments.put("TD-1232", newList("TD-1232"));
        comments.put("-------TC-12--------", newList("TC-12"));
        comments.put("------- TC-12 --------", newList("TC-12"));
        comments.put("-------TC-12-----TC-223---", newList("TC-12", "TC-223"));
        comments.put("Comment without id", new LinkedList<String>());
        comments.put("------- TSC-12 --------", newList("SC-12"));
        comments.put("------- Tc-12 --------", new LinkedList<String>());
        comments.put("------- _T-12 --------", newList("T-12"));
        comments.put("------- TC12 --------", new LinkedList<String>());
        comments.put("------- TC- --------", new LinkedList<String>());
        comments.put("------- TESTING-12/24 done --------", newList("NG-12"));

        for (String comment : comments.keySet()) {
            List<String> actuals = publisher.getTasksId(comment);
            List<String> expected = comments.get(comment);
            assertTrue(actuals.containsAll(expected));
            assertEquals(actuals.toString(), expected.size(), actuals.size());
        }
    }

    @Test
    public void testGetUrlToCcCreation() throws Exception {
        String expectUrl = "http://localhost:8080/buildresults/cctest?log=log20080915165355Lbuild.7";
        XMLLogHelper helper = new XMLLogHelper(getTextXml273());
        VersionOnePublisher publisher = createPublisher();

        assertEquals(expectUrl, publisher.getUrlToCcBuild(helper));
    }

    @Test
    public void testTypeOfBuild() throws Exception {
        assertTrue(VersionOnePublisher.isBuildForced(getTextXml273()));
    }


    private org.jdom.Element getTextXml273() throws Exception {
        DOMtoJDOM xml = new DOMtoJDOM(testFile273);

        return xml.convert().detachRootElement();
    }

    private org.jdom.Element getTextXml282() throws Exception {
        DOMtoJDOM xml = new DOMtoJDOM(testFile282);

        return xml.convert().detachRootElement();
    }
}
