This directory contains the Apache Connectors Framework web site.
To update the site, do the following:

    1. Modify the site sources in ./src

    2. Run forrest to generate the site in ./publish

    3. Review and commit all the changes from the above steps

    4. Run "umask 002; svn up /www/incubator.apache.org/lcf"
       on people.apache.org (you'll need incubator karma for this)

