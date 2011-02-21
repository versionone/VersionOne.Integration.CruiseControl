package com.versionone.cruisecontrol.publisher;

import com.versionone.DB;
import com.versionone.om.*;
import com.versionone.om.filters.BuildProjectFilter;
import com.versionone.om.filters.ChangeSetFilter;
import com.versionone.om.filters.WorkitemFilter;
import net.sourceforge.cruisecontrol.CruiseControlException;
import net.sourceforge.cruisecontrol.Modification;
import net.sourceforge.cruisecontrol.Publisher;
import net.sourceforge.cruisecontrol.util.ValidationHelper;
import net.sourceforge.cruisecontrol.util.XMLLogHelper;
import org.apache.log4j.Logger;
import org.jdom.Element;

import java.lang.String;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionOnePublisher implements Publisher {

    private static final Logger LOG = Logger.getLogger(VersionOnePublisher.class);

    private String v1Url;
    private String v1UserName;
    private String v1Password;
    private Pattern v1PatternCommit;
    private String ccWebRoot;
    private String referenceField;
    private boolean v1UseProxy;
    private String v1ProxyUrl;
    private String v1ProxyUserName;
    private String v1ProxyPassword;
    private V1Instance v1Instance;

    // Setters

    public void setUrl(String v1Host) {
        this.v1Url = v1Host;
    }

    public void setUserName(String v1UserName) {
        this.v1UserName = v1UserName;
    }

    public void setPassword(String v1Password) {
        this.v1Password = v1Password;
    }

    public void setReferenceExpression(String v1PatternString) {
        this.v1PatternCommit = Pattern.compile(v1PatternString);
    }

    public void setWebRoot(String ccWebRoot) {
        this.ccWebRoot = ccWebRoot;
    }

    public void setReferenceField(String field) {
        this.referenceField = field;
    }

    public void setUseProxy(boolean v1UseProxy) {
        this.v1UseProxy = v1UseProxy;
    }

    public void setProxyUrl(String v1proxyHost) {
        this.v1ProxyUrl = v1proxyHost;
    }

    public void setProxyUserName(String v1ProxyUserName) {
        this.v1ProxyUserName = v1ProxyUserName;
    }

    public void setProxyPassword(String v1ProxyPassword) {
        this.v1ProxyPassword = v1ProxyPassword;
    }
    //\\ Setters

    protected void init() throws CruiseControlException {
        try {
            if (v1Instance == null) {
                ProxySettings proxy = GetProxy();
                v1Instance = new V1Instance(v1Url, v1UserName, v1Password, proxy);
                LOG.info("VersionOne connector instance created.");
                v1Instance.validate();
                LOG.info("VersionOne connector instance validated.");
            }
        } catch (SDKException e) {
            final String message = "Unable to connect to VersionOne. Connection settings (url, username, password or proxy) are not valid";
            LOG.error(message, e);
            throw new CruiseControlException(message, e);
        }
    }

    private ProxySettings GetProxy() {
        if(!v1UseProxy) {
            return null;
        }

        try {
            URI uri = new  URI(v1ProxyUrl);
            return new ProxySettings(uri, v1ProxyUserName, v1ProxyPassword);
        } catch (URISyntaxException e) {
            LOG.error("Failed to create proxy URI", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void publish(Element buildLog) throws CruiseControlException {
        // Initialize connection to the V1 server
        init();

        XMLLogHelper helper = new XMLLogHelper(buildLog);

        BuildProject buildProject = getBuildProject(helper.getProjectName());
        if (buildProject != null) {
            Set<Modification> changes = (Set<Modification>) helper.getModifications();
            final String buildName = helper.getProjectName() + " - " + helper.getLabel();

            // Generate the BuildRun instance to be saved to the recipient
            BuildRun run = buildProject.createBuildRun(buildName, getBuildDate(helper));
            run.setElapsed(getElapsed(buildLog));
            run.setReference(helper.getLabel());
            run.getSource().setCurrentValue(getBuildType(buildLog));
            run.getStatus().setCurrentValue(determineStatus(helper));
            if (!changes.isEmpty()) {
                run.setDescription(getModificationDescription(changes));
            }
            run.save();

            final String str = getUrlToCcBuild(helper);
            if (str != null) {
                run.createLink("Build Report", str, true);
            }

            setChangeSets(run, changes);
            LOG.info("Build '" + buildName + "' published to VersionOne server.");
        }
    }

    /**
     * Evaluate BuildRun description.
     *
     * @param changes - set of changes affected by this BuildRun.
     * @return description string.
     */
    private static String getModificationDescription(Set<Modification> changes) {
        
        //Create Set to filter changes uniquee by User and Comment
        Set<Modification> comments = new TreeSet<Modification>(
                
                //Compares only by UserName and Comment
                new Comparator<Modification>() {
                    public int compare(Modification o1, Modification o2) {
                        int equal = o1.getUserName().compareTo(o2.getUserName());
                        if (equal == 0) {
                            equal = o1.getComment().compareTo(o2.getComment());
                        }
                        return equal;
                    }
                });
        comments.addAll(changes);

        StringBuilder result = new StringBuilder(256);
        for (Iterator<Modification> it = comments.iterator(); it.hasNext();) {
            Modification mod = it.next();
            result.append(mod.getUserName());
            result.append(": ");
            result.append(mod.getComment());
            if (it.hasNext()) {
                result.append("<br>");
            }
        }

        return result.toString();
    }

    private void setChangeSets(BuildRun buildRun, Set<Modification> changes) throws CruiseControlException {
        for (Modification change : changes) {
            // See if we have this ChangeSet in the system.
            ChangeSetFilter filter = new ChangeSetFilter();
            String id = change.getRevision();
            if (id == null) {
                continue;
            }

            filter.reference.add(id);
            Collection<ChangeSet> changeSets = v1Instance.get().changeSets(filter);
            if (changeSets.size() == 0) {
                // We don't have one yet. Create one.
                String name = change.getUserName() + " on " + change.getModifiedTime();
                ChangeSet changeSet = v1Instance.create().changeSet(name, id);
                changeSets = new ArrayList<ChangeSet>(1);
                changeSets.add(changeSet);
            }

            Set<PrimaryWorkitem> workitems = determineWorkitems(change);

            // Associate with our new BuildRun.
            for (ChangeSet changeSet : changeSets) {
                buildRun.getChangeSets().add(changeSet);
                for (PrimaryWorkitem workitem : workitems) {
                    final Collection<BuildRun> completedIn = workitem.getCompletedIn();
                    final List<BuildRun> toRemove = new ArrayList<BuildRun>(completedIn.size());

                    changeSet.getPrimaryWorkitems().add(workitem);

                    for (BuildRun otherRun : completedIn) {
                        if (otherRun.getBuildProject().equals(buildRun.getBuildProject())) {
                            toRemove.add(otherRun);
                        }
                    }

                    for (BuildRun buildRunDel : toRemove) {
                        completedIn.remove(buildRunDel);
                    }

                    completedIn.add(buildRun);
                }
            }
        }
    }

    private Set<PrimaryWorkitem> determineWorkitems(Modification change) throws CruiseControlException {
        List<String> ids = getTasksId(change.getComment());
        Set<PrimaryWorkitem> result = new HashSet<PrimaryWorkitem>(ids.size());

        for (String id : ids) {
            result.addAll(resolveReference(id));
        }
        return result;
    }

    private static String determineStatus(XMLLogHelper log) {
        return log.isBuildSuccessful() ? "Passed" : "Failed";
    }

    /**
     * Try to check if build if forced by parsing debug messages in log.
     *
     * @param buildLog build log
     * @return Forced or Trigger or empty if debug log doesn't exist
     */
    @SuppressWarnings("unchecked")
    public static boolean isBuildForced(Element buildLog) {
        List<Element> debug = buildLog.getChild("build").getChildren("message");

        for (Element debugLine : debug) {
            if (debugLine.getText().contains("buildforced -> true")) {
                return true;
            }
        }

        return false;
    }

    private static String getBuildType(Element buildLog) {
        return isBuildForced(buildLog) ? "Forced" : "Trigger";
    }

    private static Double getElapsed(Element log) {
        String time = log.getChild("build").getAttributeValue("time");
        String[] s = time.split(" ");
        if (s.length < 2) {
            return 0D;
        }
        int value = Integer.parseInt(s[0]);
        String unit = s[1];
        if ("seconds".equals(unit)) {
            return 1000D * value;
        } else if ("minutes".equals(unit)) {
            return 1000D * 60 * value;
        } else if ("hours".equals(unit)) {
            return 1000D * 60 * 60 * value;
        }
        return 0D;
    }


    public static DB.DateTime getBuildDate(XMLLogHelper helper) {
        try {
            String timestamp = helper.getCruiseControlInfoProperty("cctimestamp");
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
            Date date = df.parse(timestamp);
            return new DB.DateTime(date);
        } catch (Exception e) {
            return new DB.DateTime(new Date());
        }
    }

    /**
     * Find the first BuildProject where the Reference matches the projectName.
     *
     * @param projectName name if the project to find.
     * @return V1 representation of the project if match; otherwise, null.
     */
    private BuildProject getBuildProject(String projectName) {
        BuildProjectFilter filter = new BuildProjectFilter();

        filter.references.add(projectName);
        Collection<BuildProject> projects = v1Instance.get().buildProjects(filter);
        if (projects.isEmpty()) {
            LOG.error("Couldn't find BuildProject for " + projectName);
            return null;
        }
        return projects.iterator().next();
    }

    /**
     * Return list of tasks got from the comment string
     *
     * @param comment string with some text with ids of tasks which cut using
     *                pattern set in the referenceexpression attribute
     * @return list of cut ids
     */
    public List<String> getTasksId(String comment) {
        List<String> result = new LinkedList<String>();

        if (v1PatternCommit != null) {
            Matcher m = v1PatternCommit.matcher(comment);
            while (m.find()) {
                result.add(m.group());
            }
        }

        return result;
    }


    /**
     * Resolve a check-in comment identifier to a PrimaryWorkitem. if the
     * reference matches a SecondaryWorkitem, we need to navigate to the
     * parent.
     *
     * @param reference The identifier in the check-in comment.
     * @return A collection of matching PrimaryWorkitems.
     * @throws CruiseControlException if any error
     */
    private List<PrimaryWorkitem> resolveReference(String reference) throws CruiseControlException {
        List<PrimaryWorkitem> result = new ArrayList<PrimaryWorkitem>();

        WorkitemFilter filter = new WorkitemFilter();
        filter.find.setSearchString(reference);
        filter.find.fields.add(referenceField);
        Collection<Workitem> workitems = v1Instance.get().workitems(filter);
        for (Workitem workitem : workitems) {
            if (workitem instanceof PrimaryWorkitem) {
                result.add((PrimaryWorkitem) workitem);
            } else if (workitem instanceof SecondaryWorkitem) {
                result.add(((SecondaryWorkitem) workitem).getParent());
            } else {
                // Shut 'er down, Clancy, she's pumping mud.
                final String message = "Found unexpected Workitem type: " + workitem.getClass();
                LOG.error(message);
                throw new CruiseControlException(message);
            }
        }

        return result;
    }

    /**
     * Create link to the CruiseControl
     *
     * @param helper build data
     * @return url to curent build in CC
     * @throws CruiseControlException if eny error
     */
    protected String getUrlToCcBuild(XMLLogHelper helper) throws CruiseControlException {
        if (ccWebRoot == null) {
            LOG.warn("URL to CC build cannot be created because of WebRoot not set.");
            return null;
        }

        final StringBuilder url = new StringBuilder(128);
        url.append(ccWebRoot);
        url.append("/buildresults/");
        url.append(helper.getProjectName());
        url.append("?log=log");
        url.append(helper.getBuildTimestamp());
        url.append("L");
        url.append(helper.getLabel());

        return url.toString();
    }

    public void validate() throws CruiseControlException {
        ValidationHelper.assertIsSet(v1Url, "url", this.getClass());
    }
}
