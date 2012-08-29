This directory contains the ManifoldCF web site.  To update the site, do the following:

    1. Modify the site sources in ./src

    2. Run "ant make-core-deps" and "ant build" to generate the site in ./build/site.  You will need
        internet access (for downloading dependencies and older MCF releases), and svn 1.7+ installed.

    3. Review the site by opening ./build/site/index.html.
    
    4. Commit the changes.
    
    5. To publish the site, perform the following steps, which require Python 2.6+:
    
      (a) Check out the site image at https://svn.apache.org/repos/asf/incubator/lcf/site/publish
          to a place of your choice, e.g. "svn co https://svn.apache.org/repos/asf/incubator/lcf/site/publish ../publish"
          
      (b) Run the following script:
      
          python ./scripts/update-site.py ./build/site <svn_root>
          
          e.g.
          
          python ./scripts/update-site.py ./build/site ../publish"
          

    6. The site will be mirrored from the svn image on a schedule by a cron job, currently owned by kwright.
    


