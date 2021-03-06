# VersionOne Integration for CruiseControl

Copyright (c) 2008-2014 VersionOne, Inc. All rights reserved.

**The VersionOne Integration for Quick Test Professional has been deprecated as of the Spring 2014 release of VersionOne and is no longer supported by VersionOne. It is now open-sourced and supported by the development community.**

The VersionOne CruiseControl publisher creates a record of builds in VersionOne, so the development teams can quickly view builds associated with a story or defects. This visibility is useful when identifying what build to use for verification or when generating release notes.

## Adding plugin for project

1. open file config.xml in CruiseControl base folder

2. add to current project the next lines:

```xml
<plugin name="VersionOnePublisher" classname="com.versionone.cruisecontrol.publisher.VersionOnePublisher"/>
```

and

```xml
<publishers>
	<VersionOnePublisher url="<v1url>" username="<v1username>" password="<v1password>"
	    referenceexpression="<regular_expression_for_parse_comment>" webroot="<path_to_root_of_cc>"
	    referencefield="<name_of_attribute_for_searching_changeset>"/>
</publishers>
```

where:

   * v1url                   - The url to VersionOne server
   * username                - User name for VersionOne
   * password                - Password for VersionOne
   * referenceexpression     - The regular expression to use when matching primary workitems (stories and defects) with change comments. Required when using changeset integration.  String  (varies)  Null
   * path_to_root_of_cc      - The path to CruiseControl root web folder (ex. http://localhost:8080)
   * referencefield          - The system name of an attribute to search when matching primary workitems (stories and defects) with change comments. Required when using changeset integration.
