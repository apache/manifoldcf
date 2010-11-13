This directory contains the ManifoldCF web site.
To update the site, do the following:

    1. Modify the site sources in ./src

    2. Run "forrest site" to generate the site in ./build

    3. Run "forrest run" to review the built site
    
    4. Commit the changes
    
    5. For externally-viewable site publishing, copy build/site to https://svn.apache.org/repos/asf/incubator/lcf/site/publish, and commit

    6. Run "umask 002; svn up /www/incubator.apache.org/lcf"
       on people.apache.org (you'll need incubator karma for this)

