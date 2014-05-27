




<!DOCTYPE html>
<html class="   ">
  <head prefix="og: http://ogp.me/ns# fb: http://ogp.me/ns/fb# object: http://ogp.me/ns/object# article: http://ogp.me/ns/article# profile: http://ogp.me/ns/profile#">
    <meta charset='utf-8'>
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    
    
    <title>MCF-GridFS-Connector/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java at master · molgun/MCF-GridFS-Connector · GitHub</title>
    <link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml" title="GitHub" />
    <link rel="fluid-icon" href="https://github.com/fluidicon.png" title="GitHub" />
    <link rel="apple-touch-icon" sizes="57x57" href="/apple-touch-icon-114.png" />
    <link rel="apple-touch-icon" sizes="114x114" href="/apple-touch-icon-114.png" />
    <link rel="apple-touch-icon" sizes="72x72" href="/apple-touch-icon-144.png" />
    <link rel="apple-touch-icon" sizes="144x144" href="/apple-touch-icon-144.png" />
    <meta property="fb:app_id" content="1401488693436528"/>

      <meta content="@github" name="twitter:site" /><meta content="summary" name="twitter:card" /><meta content="molgun/MCF-GridFS-Connector" name="twitter:title" /><meta content="MCF-GridFS-Connector - MCF GridFS Connector" name="twitter:description" /><meta content="https://avatars3.githubusercontent.com/u/2950305?s=400" name="twitter:image:src" />
<meta content="GitHub" property="og:site_name" /><meta content="object" property="og:type" /><meta content="https://avatars3.githubusercontent.com/u/2950305?s=400" property="og:image" /><meta content="molgun/MCF-GridFS-Connector" property="og:title" /><meta content="https://github.com/molgun/MCF-GridFS-Connector" property="og:url" /><meta content="MCF-GridFS-Connector - MCF GridFS Connector" property="og:description" />

    <link rel="assets" href="https://assets-cdn.github.com/">
    <link rel="conduit-xhr" href="https://ghconduit.com:25035/">
    <link rel="xhr-socket" href="/_sockets" />

    <meta name="msapplication-TileImage" content="/windows-tile.png" />
    <meta name="msapplication-TileColor" content="#ffffff" />
    <meta name="selected-link" value="repo_source" data-pjax-transient />
      <meta name="google-analytics" content="UA-3769691-2">

    <meta content="collector.githubapp.com" name="octolytics-host" /><meta content="collector-cdn.github.com" name="octolytics-script-host" /><meta content="github" name="octolytics-app-id" /><meta content="CC7847FC:1EF9:8254DD:5384996C" name="octolytics-dimension-request_id" />
    

    
    
    <link rel="icon" type="image/x-icon" href="https://assets-cdn.github.com/favicon.ico" />

    <meta content="authenticity_token" name="csrf-param" />
<meta content="8xuhveCcQk/v1X4txjmmrOACHtHTrSFytN/DrBg+0uDaY7/ni2jH2DbtZI/YDqpqahWy3qJ8WWbYGgBks6eGIQ==" name="csrf-token" />

    <link href="https://assets-cdn.github.com/assets/github-1121bb0260c396426f82723a30b276d949f537a3.css" media="all" rel="stylesheet" type="text/css" />
    <link href="https://assets-cdn.github.com/assets/github2-31ad60ac9cb6abb15c45c1613fcd89f93af3b780.css" media="all" rel="stylesheet" type="text/css" />
    


    <meta http-equiv="x-pjax-version" content="cc3ff3dbd82da6afc50c6c12d275c7d1">

      
  <meta name="description" content="MCF-GridFS-Connector - MCF GridFS Connector" />

  <meta content="2950305" name="octolytics-dimension-user_id" /><meta content="molgun" name="octolytics-dimension-user_login" /><meta content="20213722" name="octolytics-dimension-repository_id" /><meta content="molgun/MCF-GridFS-Connector" name="octolytics-dimension-repository_nwo" /><meta content="true" name="octolytics-dimension-repository_public" /><meta content="false" name="octolytics-dimension-repository_is_fork" /><meta content="20213722" name="octolytics-dimension-repository_network_root_id" /><meta content="molgun/MCF-GridFS-Connector" name="octolytics-dimension-repository_network_root_nwo" />
  <link href="https://github.com/molgun/MCF-GridFS-Connector/commits/master.atom" rel="alternate" title="Recent Commits to MCF-GridFS-Connector:master" type="application/atom+xml" />

  </head>


  <body class="logged_out  env-production windows vis-public page-blob">
    <a href="#start-of-content" tabindex="1" class="accessibility-aid js-skip-to-content">Skip to content</a>
    <div class="wrapper">
      
      
      
      


      
      <div class="header header-logged-out">
  <div class="container clearfix">

    <a class="header-logo-wordmark" href="https://github.com/">
      <span class="mega-octicon octicon-logo-github"></span>
    </a>

    <div class="header-actions">
        <a class="button primary" href="/join">Sign up</a>
      <a class="button signin" href="/login?return_to=%2Fmolgun%2FMCF-GridFS-Connector%2Fblob%2Fmaster%2Fconnector%2Fsrc%2Fmain%2Fjava%2Forg%2Fapache%2Fmanifoldcf%2Fcrawler%2Fconnectors%2Fgridfs%2FGridFSRepositoryConnector.java">Sign in</a>
    </div>

    <div class="command-bar js-command-bar  in-repository">

      <ul class="top-nav">
          <li class="explore"><a href="/explore">Explore</a></li>
        <li class="features"><a href="/features">Features</a></li>
          <li class="enterprise"><a href="https://enterprise.github.com/">Enterprise</a></li>
          <li class="blog"><a href="/blog">Blog</a></li>
      </ul>
        <form accept-charset="UTF-8" action="/search" class="command-bar-form" id="top_search_form" method="get">

<div class="commandbar">
  <span class="message"></span>
  <input type="text" data-hotkey="s" name="q" id="js-command-bar-field" placeholder="Search or type a command" tabindex="1" autocapitalize="off"
    
    
      data-repo="molgun/MCF-GridFS-Connector"
      data-branch="master"
      data-sha="b15f944f7007bd862a5293c5242730784f9cd3ca"
  >
  <div class="display hidden"></div>
</div>

    <input type="hidden" name="nwo" value="molgun/MCF-GridFS-Connector" />

    <div class="select-menu js-menu-container js-select-menu search-context-select-menu">
      <span class="minibutton select-menu-button js-menu-target" role="button" aria-haspopup="true">
        <span class="js-select-button">This repository</span>
      </span>

      <div class="select-menu-modal-holder js-menu-content js-navigation-container" aria-hidden="true">
        <div class="select-menu-modal">

          <div class="select-menu-item js-navigation-item js-this-repository-navigation-item selected">
            <span class="select-menu-item-icon octicon octicon-check"></span>
            <input type="radio" class="js-search-this-repository" name="search_target" value="repository" checked="checked" />
            <div class="select-menu-item-text js-select-button-text">This repository</div>
          </div> <!-- /.select-menu-item -->

          <div class="select-menu-item js-navigation-item js-all-repositories-navigation-item">
            <span class="select-menu-item-icon octicon octicon-check"></span>
            <input type="radio" name="search_target" value="global" />
            <div class="select-menu-item-text js-select-button-text">All repositories</div>
          </div> <!-- /.select-menu-item -->

        </div>
      </div>
    </div>

  <span class="help tooltipped tooltipped-s" aria-label="Show command bar help">
    <span class="octicon octicon-question"></span>
  </span>


  <input type="hidden" name="ref" value="cmdform">

</form>
    </div>

  </div>
</div>



      <div id="start-of-content" class="accessibility-aid"></div>
          <div class="site" itemscope itemtype="http://schema.org/WebPage">
    <div id="js-flash-container">
      
    </div>
    <div class="pagehead repohead instapaper_ignore readability-menu">
      <div class="container">
        

<ul class="pagehead-actions">


  <li>
    <a href="/login?return_to=%2Fmolgun%2FMCF-GridFS-Connector"
    class="minibutton with-count star-button tooltipped tooltipped-n"
    aria-label="You must be signed in to star a repository" rel="nofollow">
    <span class="octicon octicon-star"></span>Star
  </a>

    <a class="social-count js-social-count" href="/molgun/MCF-GridFS-Connector/stargazers">
      0
    </a>

  </li>

    <li>
      <a href="/login?return_to=%2Fmolgun%2FMCF-GridFS-Connector"
        class="minibutton with-count js-toggler-target fork-button tooltipped tooltipped-n"
        aria-label="You must be signed in to fork a repository" rel="nofollow">
        <span class="octicon octicon-repo-forked"></span>Fork
      </a>
      <a href="/molgun/MCF-GridFS-Connector/network" class="social-count">
        0
      </a>
    </li>
</ul>

        <h1 itemscope itemtype="http://data-vocabulary.org/Breadcrumb" class="entry-title public">
          <span class="repo-label"><span>public</span></span>
          <span class="mega-octicon octicon-repo"></span>
          <span class="author"><a href="/molgun" class="url fn" itemprop="url" rel="author"><span itemprop="title">molgun</span></a></span><!--
       --><span class="path-divider">/</span><!--
       --><strong><a href="/molgun/MCF-GridFS-Connector" class="js-current-repository js-repo-home-link">MCF-GridFS-Connector</a></strong>

          <span class="page-context-loader">
            <img alt="" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
          </span>

        </h1>
      </div><!-- /.container -->
    </div><!-- /.repohead -->

    <div class="container">
      <div class="repository-with-sidebar repo-container new-discussion-timeline js-new-discussion-timeline  ">
        <div class="repository-sidebar clearfix">
            

<div class="sunken-menu vertical-right repo-nav js-repo-nav js-repository-container-pjax js-octicon-loaders">
  <div class="sunken-menu-contents">
    <ul class="sunken-menu-group">
      <li class="tooltipped tooltipped-w" aria-label="Code">
        <a href="/molgun/MCF-GridFS-Connector" aria-label="Code" class="selected js-selected-navigation-item sunken-menu-item" data-hotkey="g c" data-pjax="true" data-selected-links="repo_source repo_downloads repo_commits repo_releases repo_tags repo_branches /molgun/MCF-GridFS-Connector">
          <span class="octicon octicon-code"></span> <span class="full-word">Code</span>
          <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>      </li>

        <li class="tooltipped tooltipped-w" aria-label="Issues">
          <a href="/molgun/MCF-GridFS-Connector/issues" aria-label="Issues" class="js-selected-navigation-item sunken-menu-item js-disable-pjax" data-hotkey="g i" data-selected-links="repo_issues /molgun/MCF-GridFS-Connector/issues">
            <span class="octicon octicon-issue-opened"></span> <span class="full-word">Issues</span>
            <span class='counter'>0</span>
            <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>        </li>

      <li class="tooltipped tooltipped-w" aria-label="Pull Requests">
        <a href="/molgun/MCF-GridFS-Connector/pulls" aria-label="Pull Requests" class="js-selected-navigation-item sunken-menu-item js-disable-pjax" data-hotkey="g p" data-selected-links="repo_pulls /molgun/MCF-GridFS-Connector/pulls">
            <span class="octicon octicon-git-pull-request"></span> <span class="full-word">Pull Requests</span>
            <span class='counter'>0</span>
            <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>      </li>


    </ul>
    <div class="sunken-menu-separator"></div>
    <ul class="sunken-menu-group">

      <li class="tooltipped tooltipped-w" aria-label="Pulse">
        <a href="/molgun/MCF-GridFS-Connector/pulse" aria-label="Pulse" class="js-selected-navigation-item sunken-menu-item" data-pjax="true" data-selected-links="pulse /molgun/MCF-GridFS-Connector/pulse">
          <span class="octicon octicon-pulse"></span> <span class="full-word">Pulse</span>
          <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>      </li>

      <li class="tooltipped tooltipped-w" aria-label="Graphs">
        <a href="/molgun/MCF-GridFS-Connector/graphs" aria-label="Graphs" class="js-selected-navigation-item sunken-menu-item" data-pjax="true" data-selected-links="repo_graphs repo_contributors /molgun/MCF-GridFS-Connector/graphs">
          <span class="octicon octicon-graph"></span> <span class="full-word">Graphs</span>
          <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>      </li>

      <li class="tooltipped tooltipped-w" aria-label="Network">
        <a href="/molgun/MCF-GridFS-Connector/network" aria-label="Network" class="js-selected-navigation-item sunken-menu-item js-disable-pjax" data-selected-links="repo_network /molgun/MCF-GridFS-Connector/network">
          <span class="octicon octicon-repo-forked"></span> <span class="full-word">Network</span>
          <img alt="" class="mini-loader" height="16" src="https://assets-cdn.github.com/images/spinners/octocat-spinner-32.gif" width="16" />
</a>      </li>
    </ul>


  </div>
</div>

              <div class="only-with-full-nav">
                

  

<div class="clone-url open"
  data-protocol-type="http"
  data-url="/users/set_protocol?protocol_selector=http&amp;protocol_type=clone">
  <h3><strong>HTTPS</strong> clone URL</h3>
  <div class="clone-url-box">
    <input type="text" class="clone js-url-field"
           value="https://github.com/molgun/MCF-GridFS-Connector.git" readonly="readonly">
    <span class="url-box-clippy">
    <button aria-label="copy to clipboard" class="js-zeroclipboard minibutton zeroclipboard-button" data-clipboard-text="https://github.com/molgun/MCF-GridFS-Connector.git" data-copied-hint="copied!" type="button"><span class="octicon octicon-clippy"></span></button>
    </span>
  </div>
</div>

  

<div class="clone-url "
  data-protocol-type="subversion"
  data-url="/users/set_protocol?protocol_selector=subversion&amp;protocol_type=clone">
  <h3><strong>Subversion</strong> checkout URL</h3>
  <div class="clone-url-box">
    <input type="text" class="clone js-url-field"
           value="https://github.com/molgun/MCF-GridFS-Connector" readonly="readonly">
    <span class="url-box-clippy">
    <button aria-label="copy to clipboard" class="js-zeroclipboard minibutton zeroclipboard-button" data-clipboard-text="https://github.com/molgun/MCF-GridFS-Connector" data-copied-hint="copied!" type="button"><span class="octicon octicon-clippy"></span></button>
    </span>
  </div>
</div>


<p class="clone-options">You can clone with
      <a href="#" class="js-clone-selector" data-protocol="http">HTTPS</a>
      or <a href="#" class="js-clone-selector" data-protocol="subversion">Subversion</a>.
  <span class="help tooltipped tooltipped-n" aria-label="Get help on which URL is right for you.">
    <a href="https://help.github.com/articles/which-remote-url-should-i-use">
    <span class="octicon octicon-question"></span>
    </a>
  </span>
</p>


  <a href="http://windows.github.com" class="minibutton sidebar-button" title="Save molgun/MCF-GridFS-Connector to your computer and use it in GitHub Desktop." aria-label="Save molgun/MCF-GridFS-Connector to your computer and use it in GitHub Desktop.">
    <span class="octicon octicon-device-desktop"></span>
    Clone in Desktop
  </a>

                <a href="/molgun/MCF-GridFS-Connector/archive/master.zip"
                   class="minibutton sidebar-button"
                   aria-label="Download molgun/MCF-GridFS-Connector as a zip file"
                   title="Download molgun/MCF-GridFS-Connector as a zip file"
                   rel="nofollow">
                  <span class="octicon octicon-cloud-download"></span>
                  Download ZIP
                </a>
              </div>
        </div><!-- /.repository-sidebar -->

        <div id="js-repo-pjax-container" class="repository-content context-loader-container" data-pjax-container>
          


<a href="/molgun/MCF-GridFS-Connector/blob/fb419b396672e0883cbe26889b324ee4c518e12e/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java" class="hidden js-permalink-shortcut" data-hotkey="y">Permalink</a>

<!-- blob contrib key: blob_contributors:v21:bd374828cd7c65a3446d86e45bf4f8d4 -->

<p title="This is a placeholder element" class="js-history-link-replace hidden"></p>

<a href="/molgun/MCF-GridFS-Connector/find/master" data-pjax data-hotkey="t" class="js-show-file-finder" style="display:none">Show File Finder</a>

<div class="file-navigation">
  

<div class="select-menu js-menu-container js-select-menu" >
  <span class="minibutton select-menu-button js-menu-target" data-hotkey="w"
    data-master-branch="master"
    data-ref="master"
    role="button" aria-label="Switch branches or tags" tabindex="0" aria-haspopup="true">
    <span class="octicon octicon-git-branch"></span>
    <i>branch:</i>
    <span class="js-select-button">master</span>
  </span>

  <div class="select-menu-modal-holder js-menu-content js-navigation-container" data-pjax aria-hidden="true">

    <div class="select-menu-modal">
      <div class="select-menu-header">
        <span class="select-menu-title">Switch branches/tags</span>
        <span class="octicon octicon-x js-menu-close"></span>
      </div> <!-- /.select-menu-header -->

      <div class="select-menu-filters">
        <div class="select-menu-text-filter">
          <input type="text" aria-label="Filter branches/tags" id="context-commitish-filter-field" class="js-filterable-field js-navigation-enable" placeholder="Filter branches/tags">
        </div>
        <div class="select-menu-tabs">
          <ul>
            <li class="select-menu-tab">
              <a href="#" data-tab-filter="branches" class="js-select-menu-tab">Branches</a>
            </li>
            <li class="select-menu-tab">
              <a href="#" data-tab-filter="tags" class="js-select-menu-tab">Tags</a>
            </li>
          </ul>
        </div><!-- /.select-menu-tabs -->
      </div><!-- /.select-menu-filters -->

      <div class="select-menu-list select-menu-tab-bucket js-select-menu-tab-bucket" data-tab-filter="branches">

        <div data-filterable-for="context-commitish-filter-field" data-filterable-type="substring">


            <div class="select-menu-item js-navigation-item selected">
              <span class="select-menu-item-icon octicon octicon-check"></span>
              <a href="/molgun/MCF-GridFS-Connector/blob/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java"
                 data-name="master"
                 data-skip-pjax="true"
                 rel="nofollow"
                 class="js-navigation-open select-menu-item-text js-select-button-text css-truncate-target"
                 title="master">master</a>
            </div> <!-- /.select-menu-item -->
        </div>

          <div class="select-menu-no-results">Nothing to show</div>
      </div> <!-- /.select-menu-list -->

      <div class="select-menu-list select-menu-tab-bucket js-select-menu-tab-bucket" data-tab-filter="tags">
        <div data-filterable-for="context-commitish-filter-field" data-filterable-type="substring">


        </div>

        <div class="select-menu-no-results">Nothing to show</div>
      </div> <!-- /.select-menu-list -->

    </div> <!-- /.select-menu-modal -->
  </div> <!-- /.select-menu-modal-holder -->
</div> <!-- /.select-menu -->

  <div class="breadcrumb">
    <span class='repo-root js-repo-root'><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">MCF-GridFS-Connector</span></a></span></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">connector</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">src</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">main</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">java</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">org</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org/apache" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">apache</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org/apache/manifoldcf" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">manifoldcf</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org/apache/manifoldcf/crawler" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">crawler</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">connectors</span></a></span><span class="separator"> / </span><span itemscope="" itemtype="http://data-vocabulary.org/Breadcrumb"><a href="/molgun/MCF-GridFS-Connector/tree/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs" data-branch="master" data-direction="back" data-pjax="true" itemscope="url"><span itemprop="title">gridfs</span></a></span><span class="separator"> / </span><strong class="final-path">GridFSRepositoryConnector.java</strong> <button aria-label="copy to clipboard" class="js-zeroclipboard minibutton zeroclipboard-button" data-clipboard-text="connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java" data-copied-hint="copied!" type="button"><span class="octicon octicon-clippy"></span></button>
  </div>
</div>


  <div class="commit file-history-tease">
      <img alt="Muhammed Olgun" class="main-avatar js-avatar" data-user="2950305" height="24" src="https://avatars0.githubusercontent.com/u/2950305?s=140" width="24" />
      <span class="author"><a href="/molgun" rel="author">molgun</a></span>
      <time datetime="2014-05-27T16:51:27+03:00" is="relative-time" title-format="%Y-%m-%d %H:%M:%S %z" title="2014-05-27 16:51:27 +0300">May 27, 2014</time>
      <div class="commit-title">
          <a href="/molgun/MCF-GridFS-Connector/commit/fb419b396672e0883cbe26889b324ee4c518e12e" class="message" data-pjax="true" title="Update GridFSRepositoryConnector.java">Update GridFSRepositoryConnector.java</a>
      </div>

    <div class="participation">
      <p class="quickstat"><a href="#blob_contributors_box" rel="facebox"><strong>1</strong>  contributor</a></p>
      
    </div>
    <div id="blob_contributors_box" style="display:none">
      <h2 class="facebox-header">Users who have contributed to this file</h2>
      <ul class="facebox-user-list">
          <li class="facebox-user-list-item">
            <img alt="Muhammed Olgun" class=" js-avatar" data-user="2950305" height="24" src="https://avatars0.githubusercontent.com/u/2950305?s=140" width="24" />
            <a href="/molgun">molgun</a>
          </li>
      </ul>
    </div>
  </div>

<div class="file-box">
  <div class="file">
    <div class="meta clearfix">
      <div class="info file-name">
        <span class="icon"><b class="octicon octicon-file-text"></b></span>
        <span class="mode" title="File Mode">file</span>
        <span class="meta-divider"></span>
          <span>826 lines (765 sloc)</span>
          <span class="meta-divider"></span>
        <span>35.119 kb</span>
      </div>
      <div class="actions">
        <div class="button-group">
            <a class="minibutton tooltipped tooltipped-w"
               href="http://windows.github.com" aria-label="Open this file in GitHub for Windows">
                <span class="octicon octicon-device-desktop"></span> Open
            </a>
              <a class="minibutton disabled tooltipped tooltipped-w" href="#"
                 aria-label="You must be signed in to make or propose changes">Edit</a>
          <a href="/molgun/MCF-GridFS-Connector/raw/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java" class="button minibutton " id="raw-url">Raw</a>
            <a href="/molgun/MCF-GridFS-Connector/blame/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java" class="button minibutton js-update-url-with-hash">Blame</a>
          <a href="/molgun/MCF-GridFS-Connector/commits/master/connector/src/main/java/org/apache/manifoldcf/crawler/connectors/gridfs/GridFSRepositoryConnector.java" class="button minibutton " rel="nofollow">History</a>
        </div><!-- /.button-group -->
          <a class="minibutton danger disabled empty-icon tooltipped tooltipped-w" href="#"
             aria-label="You must be signed in to make or propose changes">
          Delete
        </a>
      </div><!-- /.actions -->
    </div>
        <div class="blob-wrapper data type-java js-blob-data">
        <table class="file-code file-diff tab-size-8">
          <tr class="file-code-line">
            <td class="blob-line-nums">
              <span id="L1" rel="#L1">1</span>
<span id="L2" rel="#L2">2</span>
<span id="L3" rel="#L3">3</span>
<span id="L4" rel="#L4">4</span>
<span id="L5" rel="#L5">5</span>
<span id="L6" rel="#L6">6</span>
<span id="L7" rel="#L7">7</span>
<span id="L8" rel="#L8">8</span>
<span id="L9" rel="#L9">9</span>
<span id="L10" rel="#L10">10</span>
<span id="L11" rel="#L11">11</span>
<span id="L12" rel="#L12">12</span>
<span id="L13" rel="#L13">13</span>
<span id="L14" rel="#L14">14</span>
<span id="L15" rel="#L15">15</span>
<span id="L16" rel="#L16">16</span>
<span id="L17" rel="#L17">17</span>
<span id="L18" rel="#L18">18</span>
<span id="L19" rel="#L19">19</span>
<span id="L20" rel="#L20">20</span>
<span id="L21" rel="#L21">21</span>
<span id="L22" rel="#L22">22</span>
<span id="L23" rel="#L23">23</span>
<span id="L24" rel="#L24">24</span>
<span id="L25" rel="#L25">25</span>
<span id="L26" rel="#L26">26</span>
<span id="L27" rel="#L27">27</span>
<span id="L28" rel="#L28">28</span>
<span id="L29" rel="#L29">29</span>
<span id="L30" rel="#L30">30</span>
<span id="L31" rel="#L31">31</span>
<span id="L32" rel="#L32">32</span>
<span id="L33" rel="#L33">33</span>
<span id="L34" rel="#L34">34</span>
<span id="L35" rel="#L35">35</span>
<span id="L36" rel="#L36">36</span>
<span id="L37" rel="#L37">37</span>
<span id="L38" rel="#L38">38</span>
<span id="L39" rel="#L39">39</span>
<span id="L40" rel="#L40">40</span>
<span id="L41" rel="#L41">41</span>
<span id="L42" rel="#L42">42</span>
<span id="L43" rel="#L43">43</span>
<span id="L44" rel="#L44">44</span>
<span id="L45" rel="#L45">45</span>
<span id="L46" rel="#L46">46</span>
<span id="L47" rel="#L47">47</span>
<span id="L48" rel="#L48">48</span>
<span id="L49" rel="#L49">49</span>
<span id="L50" rel="#L50">50</span>
<span id="L51" rel="#L51">51</span>
<span id="L52" rel="#L52">52</span>
<span id="L53" rel="#L53">53</span>
<span id="L54" rel="#L54">54</span>
<span id="L55" rel="#L55">55</span>
<span id="L56" rel="#L56">56</span>
<span id="L57" rel="#L57">57</span>
<span id="L58" rel="#L58">58</span>
<span id="L59" rel="#L59">59</span>
<span id="L60" rel="#L60">60</span>
<span id="L61" rel="#L61">61</span>
<span id="L62" rel="#L62">62</span>
<span id="L63" rel="#L63">63</span>
<span id="L64" rel="#L64">64</span>
<span id="L65" rel="#L65">65</span>
<span id="L66" rel="#L66">66</span>
<span id="L67" rel="#L67">67</span>
<span id="L68" rel="#L68">68</span>
<span id="L69" rel="#L69">69</span>
<span id="L70" rel="#L70">70</span>
<span id="L71" rel="#L71">71</span>
<span id="L72" rel="#L72">72</span>
<span id="L73" rel="#L73">73</span>
<span id="L74" rel="#L74">74</span>
<span id="L75" rel="#L75">75</span>
<span id="L76" rel="#L76">76</span>
<span id="L77" rel="#L77">77</span>
<span id="L78" rel="#L78">78</span>
<span id="L79" rel="#L79">79</span>
<span id="L80" rel="#L80">80</span>
<span id="L81" rel="#L81">81</span>
<span id="L82" rel="#L82">82</span>
<span id="L83" rel="#L83">83</span>
<span id="L84" rel="#L84">84</span>
<span id="L85" rel="#L85">85</span>
<span id="L86" rel="#L86">86</span>
<span id="L87" rel="#L87">87</span>
<span id="L88" rel="#L88">88</span>
<span id="L89" rel="#L89">89</span>
<span id="L90" rel="#L90">90</span>
<span id="L91" rel="#L91">91</span>
<span id="L92" rel="#L92">92</span>
<span id="L93" rel="#L93">93</span>
<span id="L94" rel="#L94">94</span>
<span id="L95" rel="#L95">95</span>
<span id="L96" rel="#L96">96</span>
<span id="L97" rel="#L97">97</span>
<span id="L98" rel="#L98">98</span>
<span id="L99" rel="#L99">99</span>
<span id="L100" rel="#L100">100</span>
<span id="L101" rel="#L101">101</span>
<span id="L102" rel="#L102">102</span>
<span id="L103" rel="#L103">103</span>
<span id="L104" rel="#L104">104</span>
<span id="L105" rel="#L105">105</span>
<span id="L106" rel="#L106">106</span>
<span id="L107" rel="#L107">107</span>
<span id="L108" rel="#L108">108</span>
<span id="L109" rel="#L109">109</span>
<span id="L110" rel="#L110">110</span>
<span id="L111" rel="#L111">111</span>
<span id="L112" rel="#L112">112</span>
<span id="L113" rel="#L113">113</span>
<span id="L114" rel="#L114">114</span>
<span id="L115" rel="#L115">115</span>
<span id="L116" rel="#L116">116</span>
<span id="L117" rel="#L117">117</span>
<span id="L118" rel="#L118">118</span>
<span id="L119" rel="#L119">119</span>
<span id="L120" rel="#L120">120</span>
<span id="L121" rel="#L121">121</span>
<span id="L122" rel="#L122">122</span>
<span id="L123" rel="#L123">123</span>
<span id="L124" rel="#L124">124</span>
<span id="L125" rel="#L125">125</span>
<span id="L126" rel="#L126">126</span>
<span id="L127" rel="#L127">127</span>
<span id="L128" rel="#L128">128</span>
<span id="L129" rel="#L129">129</span>
<span id="L130" rel="#L130">130</span>
<span id="L131" rel="#L131">131</span>
<span id="L132" rel="#L132">132</span>
<span id="L133" rel="#L133">133</span>
<span id="L134" rel="#L134">134</span>
<span id="L135" rel="#L135">135</span>
<span id="L136" rel="#L136">136</span>
<span id="L137" rel="#L137">137</span>
<span id="L138" rel="#L138">138</span>
<span id="L139" rel="#L139">139</span>
<span id="L140" rel="#L140">140</span>
<span id="L141" rel="#L141">141</span>
<span id="L142" rel="#L142">142</span>
<span id="L143" rel="#L143">143</span>
<span id="L144" rel="#L144">144</span>
<span id="L145" rel="#L145">145</span>
<span id="L146" rel="#L146">146</span>
<span id="L147" rel="#L147">147</span>
<span id="L148" rel="#L148">148</span>
<span id="L149" rel="#L149">149</span>
<span id="L150" rel="#L150">150</span>
<span id="L151" rel="#L151">151</span>
<span id="L152" rel="#L152">152</span>
<span id="L153" rel="#L153">153</span>
<span id="L154" rel="#L154">154</span>
<span id="L155" rel="#L155">155</span>
<span id="L156" rel="#L156">156</span>
<span id="L157" rel="#L157">157</span>
<span id="L158" rel="#L158">158</span>
<span id="L159" rel="#L159">159</span>
<span id="L160" rel="#L160">160</span>
<span id="L161" rel="#L161">161</span>
<span id="L162" rel="#L162">162</span>
<span id="L163" rel="#L163">163</span>
<span id="L164" rel="#L164">164</span>
<span id="L165" rel="#L165">165</span>
<span id="L166" rel="#L166">166</span>
<span id="L167" rel="#L167">167</span>
<span id="L168" rel="#L168">168</span>
<span id="L169" rel="#L169">169</span>
<span id="L170" rel="#L170">170</span>
<span id="L171" rel="#L171">171</span>
<span id="L172" rel="#L172">172</span>
<span id="L173" rel="#L173">173</span>
<span id="L174" rel="#L174">174</span>
<span id="L175" rel="#L175">175</span>
<span id="L176" rel="#L176">176</span>
<span id="L177" rel="#L177">177</span>
<span id="L178" rel="#L178">178</span>
<span id="L179" rel="#L179">179</span>
<span id="L180" rel="#L180">180</span>
<span id="L181" rel="#L181">181</span>
<span id="L182" rel="#L182">182</span>
<span id="L183" rel="#L183">183</span>
<span id="L184" rel="#L184">184</span>
<span id="L185" rel="#L185">185</span>
<span id="L186" rel="#L186">186</span>
<span id="L187" rel="#L187">187</span>
<span id="L188" rel="#L188">188</span>
<span id="L189" rel="#L189">189</span>
<span id="L190" rel="#L190">190</span>
<span id="L191" rel="#L191">191</span>
<span id="L192" rel="#L192">192</span>
<span id="L193" rel="#L193">193</span>
<span id="L194" rel="#L194">194</span>
<span id="L195" rel="#L195">195</span>
<span id="L196" rel="#L196">196</span>
<span id="L197" rel="#L197">197</span>
<span id="L198" rel="#L198">198</span>
<span id="L199" rel="#L199">199</span>
<span id="L200" rel="#L200">200</span>
<span id="L201" rel="#L201">201</span>
<span id="L202" rel="#L202">202</span>
<span id="L203" rel="#L203">203</span>
<span id="L204" rel="#L204">204</span>
<span id="L205" rel="#L205">205</span>
<span id="L206" rel="#L206">206</span>
<span id="L207" rel="#L207">207</span>
<span id="L208" rel="#L208">208</span>
<span id="L209" rel="#L209">209</span>
<span id="L210" rel="#L210">210</span>
<span id="L211" rel="#L211">211</span>
<span id="L212" rel="#L212">212</span>
<span id="L213" rel="#L213">213</span>
<span id="L214" rel="#L214">214</span>
<span id="L215" rel="#L215">215</span>
<span id="L216" rel="#L216">216</span>
<span id="L217" rel="#L217">217</span>
<span id="L218" rel="#L218">218</span>
<span id="L219" rel="#L219">219</span>
<span id="L220" rel="#L220">220</span>
<span id="L221" rel="#L221">221</span>
<span id="L222" rel="#L222">222</span>
<span id="L223" rel="#L223">223</span>
<span id="L224" rel="#L224">224</span>
<span id="L225" rel="#L225">225</span>
<span id="L226" rel="#L226">226</span>
<span id="L227" rel="#L227">227</span>
<span id="L228" rel="#L228">228</span>
<span id="L229" rel="#L229">229</span>
<span id="L230" rel="#L230">230</span>
<span id="L231" rel="#L231">231</span>
<span id="L232" rel="#L232">232</span>
<span id="L233" rel="#L233">233</span>
<span id="L234" rel="#L234">234</span>
<span id="L235" rel="#L235">235</span>
<span id="L236" rel="#L236">236</span>
<span id="L237" rel="#L237">237</span>
<span id="L238" rel="#L238">238</span>
<span id="L239" rel="#L239">239</span>
<span id="L240" rel="#L240">240</span>
<span id="L241" rel="#L241">241</span>
<span id="L242" rel="#L242">242</span>
<span id="L243" rel="#L243">243</span>
<span id="L244" rel="#L244">244</span>
<span id="L245" rel="#L245">245</span>
<span id="L246" rel="#L246">246</span>
<span id="L247" rel="#L247">247</span>
<span id="L248" rel="#L248">248</span>
<span id="L249" rel="#L249">249</span>
<span id="L250" rel="#L250">250</span>
<span id="L251" rel="#L251">251</span>
<span id="L252" rel="#L252">252</span>
<span id="L253" rel="#L253">253</span>
<span id="L254" rel="#L254">254</span>
<span id="L255" rel="#L255">255</span>
<span id="L256" rel="#L256">256</span>
<span id="L257" rel="#L257">257</span>
<span id="L258" rel="#L258">258</span>
<span id="L259" rel="#L259">259</span>
<span id="L260" rel="#L260">260</span>
<span id="L261" rel="#L261">261</span>
<span id="L262" rel="#L262">262</span>
<span id="L263" rel="#L263">263</span>
<span id="L264" rel="#L264">264</span>
<span id="L265" rel="#L265">265</span>
<span id="L266" rel="#L266">266</span>
<span id="L267" rel="#L267">267</span>
<span id="L268" rel="#L268">268</span>
<span id="L269" rel="#L269">269</span>
<span id="L270" rel="#L270">270</span>
<span id="L271" rel="#L271">271</span>
<span id="L272" rel="#L272">272</span>
<span id="L273" rel="#L273">273</span>
<span id="L274" rel="#L274">274</span>
<span id="L275" rel="#L275">275</span>
<span id="L276" rel="#L276">276</span>
<span id="L277" rel="#L277">277</span>
<span id="L278" rel="#L278">278</span>
<span id="L279" rel="#L279">279</span>
<span id="L280" rel="#L280">280</span>
<span id="L281" rel="#L281">281</span>
<span id="L282" rel="#L282">282</span>
<span id="L283" rel="#L283">283</span>
<span id="L284" rel="#L284">284</span>
<span id="L285" rel="#L285">285</span>
<span id="L286" rel="#L286">286</span>
<span id="L287" rel="#L287">287</span>
<span id="L288" rel="#L288">288</span>
<span id="L289" rel="#L289">289</span>
<span id="L290" rel="#L290">290</span>
<span id="L291" rel="#L291">291</span>
<span id="L292" rel="#L292">292</span>
<span id="L293" rel="#L293">293</span>
<span id="L294" rel="#L294">294</span>
<span id="L295" rel="#L295">295</span>
<span id="L296" rel="#L296">296</span>
<span id="L297" rel="#L297">297</span>
<span id="L298" rel="#L298">298</span>
<span id="L299" rel="#L299">299</span>
<span id="L300" rel="#L300">300</span>
<span id="L301" rel="#L301">301</span>
<span id="L302" rel="#L302">302</span>
<span id="L303" rel="#L303">303</span>
<span id="L304" rel="#L304">304</span>
<span id="L305" rel="#L305">305</span>
<span id="L306" rel="#L306">306</span>
<span id="L307" rel="#L307">307</span>
<span id="L308" rel="#L308">308</span>
<span id="L309" rel="#L309">309</span>
<span id="L310" rel="#L310">310</span>
<span id="L311" rel="#L311">311</span>
<span id="L312" rel="#L312">312</span>
<span id="L313" rel="#L313">313</span>
<span id="L314" rel="#L314">314</span>
<span id="L315" rel="#L315">315</span>
<span id="L316" rel="#L316">316</span>
<span id="L317" rel="#L317">317</span>
<span id="L318" rel="#L318">318</span>
<span id="L319" rel="#L319">319</span>
<span id="L320" rel="#L320">320</span>
<span id="L321" rel="#L321">321</span>
<span id="L322" rel="#L322">322</span>
<span id="L323" rel="#L323">323</span>
<span id="L324" rel="#L324">324</span>
<span id="L325" rel="#L325">325</span>
<span id="L326" rel="#L326">326</span>
<span id="L327" rel="#L327">327</span>
<span id="L328" rel="#L328">328</span>
<span id="L329" rel="#L329">329</span>
<span id="L330" rel="#L330">330</span>
<span id="L331" rel="#L331">331</span>
<span id="L332" rel="#L332">332</span>
<span id="L333" rel="#L333">333</span>
<span id="L334" rel="#L334">334</span>
<span id="L335" rel="#L335">335</span>
<span id="L336" rel="#L336">336</span>
<span id="L337" rel="#L337">337</span>
<span id="L338" rel="#L338">338</span>
<span id="L339" rel="#L339">339</span>
<span id="L340" rel="#L340">340</span>
<span id="L341" rel="#L341">341</span>
<span id="L342" rel="#L342">342</span>
<span id="L343" rel="#L343">343</span>
<span id="L344" rel="#L344">344</span>
<span id="L345" rel="#L345">345</span>
<span id="L346" rel="#L346">346</span>
<span id="L347" rel="#L347">347</span>
<span id="L348" rel="#L348">348</span>
<span id="L349" rel="#L349">349</span>
<span id="L350" rel="#L350">350</span>
<span id="L351" rel="#L351">351</span>
<span id="L352" rel="#L352">352</span>
<span id="L353" rel="#L353">353</span>
<span id="L354" rel="#L354">354</span>
<span id="L355" rel="#L355">355</span>
<span id="L356" rel="#L356">356</span>
<span id="L357" rel="#L357">357</span>
<span id="L358" rel="#L358">358</span>
<span id="L359" rel="#L359">359</span>
<span id="L360" rel="#L360">360</span>
<span id="L361" rel="#L361">361</span>
<span id="L362" rel="#L362">362</span>
<span id="L363" rel="#L363">363</span>
<span id="L364" rel="#L364">364</span>
<span id="L365" rel="#L365">365</span>
<span id="L366" rel="#L366">366</span>
<span id="L367" rel="#L367">367</span>
<span id="L368" rel="#L368">368</span>
<span id="L369" rel="#L369">369</span>
<span id="L370" rel="#L370">370</span>
<span id="L371" rel="#L371">371</span>
<span id="L372" rel="#L372">372</span>
<span id="L373" rel="#L373">373</span>
<span id="L374" rel="#L374">374</span>
<span id="L375" rel="#L375">375</span>
<span id="L376" rel="#L376">376</span>
<span id="L377" rel="#L377">377</span>
<span id="L378" rel="#L378">378</span>
<span id="L379" rel="#L379">379</span>
<span id="L380" rel="#L380">380</span>
<span id="L381" rel="#L381">381</span>
<span id="L382" rel="#L382">382</span>
<span id="L383" rel="#L383">383</span>
<span id="L384" rel="#L384">384</span>
<span id="L385" rel="#L385">385</span>
<span id="L386" rel="#L386">386</span>
<span id="L387" rel="#L387">387</span>
<span id="L388" rel="#L388">388</span>
<span id="L389" rel="#L389">389</span>
<span id="L390" rel="#L390">390</span>
<span id="L391" rel="#L391">391</span>
<span id="L392" rel="#L392">392</span>
<span id="L393" rel="#L393">393</span>
<span id="L394" rel="#L394">394</span>
<span id="L395" rel="#L395">395</span>
<span id="L396" rel="#L396">396</span>
<span id="L397" rel="#L397">397</span>
<span id="L398" rel="#L398">398</span>
<span id="L399" rel="#L399">399</span>
<span id="L400" rel="#L400">400</span>
<span id="L401" rel="#L401">401</span>
<span id="L402" rel="#L402">402</span>
<span id="L403" rel="#L403">403</span>
<span id="L404" rel="#L404">404</span>
<span id="L405" rel="#L405">405</span>
<span id="L406" rel="#L406">406</span>
<span id="L407" rel="#L407">407</span>
<span id="L408" rel="#L408">408</span>
<span id="L409" rel="#L409">409</span>
<span id="L410" rel="#L410">410</span>
<span id="L411" rel="#L411">411</span>
<span id="L412" rel="#L412">412</span>
<span id="L413" rel="#L413">413</span>
<span id="L414" rel="#L414">414</span>
<span id="L415" rel="#L415">415</span>
<span id="L416" rel="#L416">416</span>
<span id="L417" rel="#L417">417</span>
<span id="L418" rel="#L418">418</span>
<span id="L419" rel="#L419">419</span>
<span id="L420" rel="#L420">420</span>
<span id="L421" rel="#L421">421</span>
<span id="L422" rel="#L422">422</span>
<span id="L423" rel="#L423">423</span>
<span id="L424" rel="#L424">424</span>
<span id="L425" rel="#L425">425</span>
<span id="L426" rel="#L426">426</span>
<span id="L427" rel="#L427">427</span>
<span id="L428" rel="#L428">428</span>
<span id="L429" rel="#L429">429</span>
<span id="L430" rel="#L430">430</span>
<span id="L431" rel="#L431">431</span>
<span id="L432" rel="#L432">432</span>
<span id="L433" rel="#L433">433</span>
<span id="L434" rel="#L434">434</span>
<span id="L435" rel="#L435">435</span>
<span id="L436" rel="#L436">436</span>
<span id="L437" rel="#L437">437</span>
<span id="L438" rel="#L438">438</span>
<span id="L439" rel="#L439">439</span>
<span id="L440" rel="#L440">440</span>
<span id="L441" rel="#L441">441</span>
<span id="L442" rel="#L442">442</span>
<span id="L443" rel="#L443">443</span>
<span id="L444" rel="#L444">444</span>
<span id="L445" rel="#L445">445</span>
<span id="L446" rel="#L446">446</span>
<span id="L447" rel="#L447">447</span>
<span id="L448" rel="#L448">448</span>
<span id="L449" rel="#L449">449</span>
<span id="L450" rel="#L450">450</span>
<span id="L451" rel="#L451">451</span>
<span id="L452" rel="#L452">452</span>
<span id="L453" rel="#L453">453</span>
<span id="L454" rel="#L454">454</span>
<span id="L455" rel="#L455">455</span>
<span id="L456" rel="#L456">456</span>
<span id="L457" rel="#L457">457</span>
<span id="L458" rel="#L458">458</span>
<span id="L459" rel="#L459">459</span>
<span id="L460" rel="#L460">460</span>
<span id="L461" rel="#L461">461</span>
<span id="L462" rel="#L462">462</span>
<span id="L463" rel="#L463">463</span>
<span id="L464" rel="#L464">464</span>
<span id="L465" rel="#L465">465</span>
<span id="L466" rel="#L466">466</span>
<span id="L467" rel="#L467">467</span>
<span id="L468" rel="#L468">468</span>
<span id="L469" rel="#L469">469</span>
<span id="L470" rel="#L470">470</span>
<span id="L471" rel="#L471">471</span>
<span id="L472" rel="#L472">472</span>
<span id="L473" rel="#L473">473</span>
<span id="L474" rel="#L474">474</span>
<span id="L475" rel="#L475">475</span>
<span id="L476" rel="#L476">476</span>
<span id="L477" rel="#L477">477</span>
<span id="L478" rel="#L478">478</span>
<span id="L479" rel="#L479">479</span>
<span id="L480" rel="#L480">480</span>
<span id="L481" rel="#L481">481</span>
<span id="L482" rel="#L482">482</span>
<span id="L483" rel="#L483">483</span>
<span id="L484" rel="#L484">484</span>
<span id="L485" rel="#L485">485</span>
<span id="L486" rel="#L486">486</span>
<span id="L487" rel="#L487">487</span>
<span id="L488" rel="#L488">488</span>
<span id="L489" rel="#L489">489</span>
<span id="L490" rel="#L490">490</span>
<span id="L491" rel="#L491">491</span>
<span id="L492" rel="#L492">492</span>
<span id="L493" rel="#L493">493</span>
<span id="L494" rel="#L494">494</span>
<span id="L495" rel="#L495">495</span>
<span id="L496" rel="#L496">496</span>
<span id="L497" rel="#L497">497</span>
<span id="L498" rel="#L498">498</span>
<span id="L499" rel="#L499">499</span>
<span id="L500" rel="#L500">500</span>
<span id="L501" rel="#L501">501</span>
<span id="L502" rel="#L502">502</span>
<span id="L503" rel="#L503">503</span>
<span id="L504" rel="#L504">504</span>
<span id="L505" rel="#L505">505</span>
<span id="L506" rel="#L506">506</span>
<span id="L507" rel="#L507">507</span>
<span id="L508" rel="#L508">508</span>
<span id="L509" rel="#L509">509</span>
<span id="L510" rel="#L510">510</span>
<span id="L511" rel="#L511">511</span>
<span id="L512" rel="#L512">512</span>
<span id="L513" rel="#L513">513</span>
<span id="L514" rel="#L514">514</span>
<span id="L515" rel="#L515">515</span>
<span id="L516" rel="#L516">516</span>
<span id="L517" rel="#L517">517</span>
<span id="L518" rel="#L518">518</span>
<span id="L519" rel="#L519">519</span>
<span id="L520" rel="#L520">520</span>
<span id="L521" rel="#L521">521</span>
<span id="L522" rel="#L522">522</span>
<span id="L523" rel="#L523">523</span>
<span id="L524" rel="#L524">524</span>
<span id="L525" rel="#L525">525</span>
<span id="L526" rel="#L526">526</span>
<span id="L527" rel="#L527">527</span>
<span id="L528" rel="#L528">528</span>
<span id="L529" rel="#L529">529</span>
<span id="L530" rel="#L530">530</span>
<span id="L531" rel="#L531">531</span>
<span id="L532" rel="#L532">532</span>
<span id="L533" rel="#L533">533</span>
<span id="L534" rel="#L534">534</span>
<span id="L535" rel="#L535">535</span>
<span id="L536" rel="#L536">536</span>
<span id="L537" rel="#L537">537</span>
<span id="L538" rel="#L538">538</span>
<span id="L539" rel="#L539">539</span>
<span id="L540" rel="#L540">540</span>
<span id="L541" rel="#L541">541</span>
<span id="L542" rel="#L542">542</span>
<span id="L543" rel="#L543">543</span>
<span id="L544" rel="#L544">544</span>
<span id="L545" rel="#L545">545</span>
<span id="L546" rel="#L546">546</span>
<span id="L547" rel="#L547">547</span>
<span id="L548" rel="#L548">548</span>
<span id="L549" rel="#L549">549</span>
<span id="L550" rel="#L550">550</span>
<span id="L551" rel="#L551">551</span>
<span id="L552" rel="#L552">552</span>
<span id="L553" rel="#L553">553</span>
<span id="L554" rel="#L554">554</span>
<span id="L555" rel="#L555">555</span>
<span id="L556" rel="#L556">556</span>
<span id="L557" rel="#L557">557</span>
<span id="L558" rel="#L558">558</span>
<span id="L559" rel="#L559">559</span>
<span id="L560" rel="#L560">560</span>
<span id="L561" rel="#L561">561</span>
<span id="L562" rel="#L562">562</span>
<span id="L563" rel="#L563">563</span>
<span id="L564" rel="#L564">564</span>
<span id="L565" rel="#L565">565</span>
<span id="L566" rel="#L566">566</span>
<span id="L567" rel="#L567">567</span>
<span id="L568" rel="#L568">568</span>
<span id="L569" rel="#L569">569</span>
<span id="L570" rel="#L570">570</span>
<span id="L571" rel="#L571">571</span>
<span id="L572" rel="#L572">572</span>
<span id="L573" rel="#L573">573</span>
<span id="L574" rel="#L574">574</span>
<span id="L575" rel="#L575">575</span>
<span id="L576" rel="#L576">576</span>
<span id="L577" rel="#L577">577</span>
<span id="L578" rel="#L578">578</span>
<span id="L579" rel="#L579">579</span>
<span id="L580" rel="#L580">580</span>
<span id="L581" rel="#L581">581</span>
<span id="L582" rel="#L582">582</span>
<span id="L583" rel="#L583">583</span>
<span id="L584" rel="#L584">584</span>
<span id="L585" rel="#L585">585</span>
<span id="L586" rel="#L586">586</span>
<span id="L587" rel="#L587">587</span>
<span id="L588" rel="#L588">588</span>
<span id="L589" rel="#L589">589</span>
<span id="L590" rel="#L590">590</span>
<span id="L591" rel="#L591">591</span>
<span id="L592" rel="#L592">592</span>
<span id="L593" rel="#L593">593</span>
<span id="L594" rel="#L594">594</span>
<span id="L595" rel="#L595">595</span>
<span id="L596" rel="#L596">596</span>
<span id="L597" rel="#L597">597</span>
<span id="L598" rel="#L598">598</span>
<span id="L599" rel="#L599">599</span>
<span id="L600" rel="#L600">600</span>
<span id="L601" rel="#L601">601</span>
<span id="L602" rel="#L602">602</span>
<span id="L603" rel="#L603">603</span>
<span id="L604" rel="#L604">604</span>
<span id="L605" rel="#L605">605</span>
<span id="L606" rel="#L606">606</span>
<span id="L607" rel="#L607">607</span>
<span id="L608" rel="#L608">608</span>
<span id="L609" rel="#L609">609</span>
<span id="L610" rel="#L610">610</span>
<span id="L611" rel="#L611">611</span>
<span id="L612" rel="#L612">612</span>
<span id="L613" rel="#L613">613</span>
<span id="L614" rel="#L614">614</span>
<span id="L615" rel="#L615">615</span>
<span id="L616" rel="#L616">616</span>
<span id="L617" rel="#L617">617</span>
<span id="L618" rel="#L618">618</span>
<span id="L619" rel="#L619">619</span>
<span id="L620" rel="#L620">620</span>
<span id="L621" rel="#L621">621</span>
<span id="L622" rel="#L622">622</span>
<span id="L623" rel="#L623">623</span>
<span id="L624" rel="#L624">624</span>
<span id="L625" rel="#L625">625</span>
<span id="L626" rel="#L626">626</span>
<span id="L627" rel="#L627">627</span>
<span id="L628" rel="#L628">628</span>
<span id="L629" rel="#L629">629</span>
<span id="L630" rel="#L630">630</span>
<span id="L631" rel="#L631">631</span>
<span id="L632" rel="#L632">632</span>
<span id="L633" rel="#L633">633</span>
<span id="L634" rel="#L634">634</span>
<span id="L635" rel="#L635">635</span>
<span id="L636" rel="#L636">636</span>
<span id="L637" rel="#L637">637</span>
<span id="L638" rel="#L638">638</span>
<span id="L639" rel="#L639">639</span>
<span id="L640" rel="#L640">640</span>
<span id="L641" rel="#L641">641</span>
<span id="L642" rel="#L642">642</span>
<span id="L643" rel="#L643">643</span>
<span id="L644" rel="#L644">644</span>
<span id="L645" rel="#L645">645</span>
<span id="L646" rel="#L646">646</span>
<span id="L647" rel="#L647">647</span>
<span id="L648" rel="#L648">648</span>
<span id="L649" rel="#L649">649</span>
<span id="L650" rel="#L650">650</span>
<span id="L651" rel="#L651">651</span>
<span id="L652" rel="#L652">652</span>
<span id="L653" rel="#L653">653</span>
<span id="L654" rel="#L654">654</span>
<span id="L655" rel="#L655">655</span>
<span id="L656" rel="#L656">656</span>
<span id="L657" rel="#L657">657</span>
<span id="L658" rel="#L658">658</span>
<span id="L659" rel="#L659">659</span>
<span id="L660" rel="#L660">660</span>
<span id="L661" rel="#L661">661</span>
<span id="L662" rel="#L662">662</span>
<span id="L663" rel="#L663">663</span>
<span id="L664" rel="#L664">664</span>
<span id="L665" rel="#L665">665</span>
<span id="L666" rel="#L666">666</span>
<span id="L667" rel="#L667">667</span>
<span id="L668" rel="#L668">668</span>
<span id="L669" rel="#L669">669</span>
<span id="L670" rel="#L670">670</span>
<span id="L671" rel="#L671">671</span>
<span id="L672" rel="#L672">672</span>
<span id="L673" rel="#L673">673</span>
<span id="L674" rel="#L674">674</span>
<span id="L675" rel="#L675">675</span>
<span id="L676" rel="#L676">676</span>
<span id="L677" rel="#L677">677</span>
<span id="L678" rel="#L678">678</span>
<span id="L679" rel="#L679">679</span>
<span id="L680" rel="#L680">680</span>
<span id="L681" rel="#L681">681</span>
<span id="L682" rel="#L682">682</span>
<span id="L683" rel="#L683">683</span>
<span id="L684" rel="#L684">684</span>
<span id="L685" rel="#L685">685</span>
<span id="L686" rel="#L686">686</span>
<span id="L687" rel="#L687">687</span>
<span id="L688" rel="#L688">688</span>
<span id="L689" rel="#L689">689</span>
<span id="L690" rel="#L690">690</span>
<span id="L691" rel="#L691">691</span>
<span id="L692" rel="#L692">692</span>
<span id="L693" rel="#L693">693</span>
<span id="L694" rel="#L694">694</span>
<span id="L695" rel="#L695">695</span>
<span id="L696" rel="#L696">696</span>
<span id="L697" rel="#L697">697</span>
<span id="L698" rel="#L698">698</span>
<span id="L699" rel="#L699">699</span>
<span id="L700" rel="#L700">700</span>
<span id="L701" rel="#L701">701</span>
<span id="L702" rel="#L702">702</span>
<span id="L703" rel="#L703">703</span>
<span id="L704" rel="#L704">704</span>
<span id="L705" rel="#L705">705</span>
<span id="L706" rel="#L706">706</span>
<span id="L707" rel="#L707">707</span>
<span id="L708" rel="#L708">708</span>
<span id="L709" rel="#L709">709</span>
<span id="L710" rel="#L710">710</span>
<span id="L711" rel="#L711">711</span>
<span id="L712" rel="#L712">712</span>
<span id="L713" rel="#L713">713</span>
<span id="L714" rel="#L714">714</span>
<span id="L715" rel="#L715">715</span>
<span id="L716" rel="#L716">716</span>
<span id="L717" rel="#L717">717</span>
<span id="L718" rel="#L718">718</span>
<span id="L719" rel="#L719">719</span>
<span id="L720" rel="#L720">720</span>
<span id="L721" rel="#L721">721</span>
<span id="L722" rel="#L722">722</span>
<span id="L723" rel="#L723">723</span>
<span id="L724" rel="#L724">724</span>
<span id="L725" rel="#L725">725</span>
<span id="L726" rel="#L726">726</span>
<span id="L727" rel="#L727">727</span>
<span id="L728" rel="#L728">728</span>
<span id="L729" rel="#L729">729</span>
<span id="L730" rel="#L730">730</span>
<span id="L731" rel="#L731">731</span>
<span id="L732" rel="#L732">732</span>
<span id="L733" rel="#L733">733</span>
<span id="L734" rel="#L734">734</span>
<span id="L735" rel="#L735">735</span>
<span id="L736" rel="#L736">736</span>
<span id="L737" rel="#L737">737</span>
<span id="L738" rel="#L738">738</span>
<span id="L739" rel="#L739">739</span>
<span id="L740" rel="#L740">740</span>
<span id="L741" rel="#L741">741</span>
<span id="L742" rel="#L742">742</span>
<span id="L743" rel="#L743">743</span>
<span id="L744" rel="#L744">744</span>
<span id="L745" rel="#L745">745</span>
<span id="L746" rel="#L746">746</span>
<span id="L747" rel="#L747">747</span>
<span id="L748" rel="#L748">748</span>
<span id="L749" rel="#L749">749</span>
<span id="L750" rel="#L750">750</span>
<span id="L751" rel="#L751">751</span>
<span id="L752" rel="#L752">752</span>
<span id="L753" rel="#L753">753</span>
<span id="L754" rel="#L754">754</span>
<span id="L755" rel="#L755">755</span>
<span id="L756" rel="#L756">756</span>
<span id="L757" rel="#L757">757</span>
<span id="L758" rel="#L758">758</span>
<span id="L759" rel="#L759">759</span>
<span id="L760" rel="#L760">760</span>
<span id="L761" rel="#L761">761</span>
<span id="L762" rel="#L762">762</span>
<span id="L763" rel="#L763">763</span>
<span id="L764" rel="#L764">764</span>
<span id="L765" rel="#L765">765</span>
<span id="L766" rel="#L766">766</span>
<span id="L767" rel="#L767">767</span>
<span id="L768" rel="#L768">768</span>
<span id="L769" rel="#L769">769</span>
<span id="L770" rel="#L770">770</span>
<span id="L771" rel="#L771">771</span>
<span id="L772" rel="#L772">772</span>
<span id="L773" rel="#L773">773</span>
<span id="L774" rel="#L774">774</span>
<span id="L775" rel="#L775">775</span>
<span id="L776" rel="#L776">776</span>
<span id="L777" rel="#L777">777</span>
<span id="L778" rel="#L778">778</span>
<span id="L779" rel="#L779">779</span>
<span id="L780" rel="#L780">780</span>
<span id="L781" rel="#L781">781</span>
<span id="L782" rel="#L782">782</span>
<span id="L783" rel="#L783">783</span>
<span id="L784" rel="#L784">784</span>
<span id="L785" rel="#L785">785</span>
<span id="L786" rel="#L786">786</span>
<span id="L787" rel="#L787">787</span>
<span id="L788" rel="#L788">788</span>
<span id="L789" rel="#L789">789</span>
<span id="L790" rel="#L790">790</span>
<span id="L791" rel="#L791">791</span>
<span id="L792" rel="#L792">792</span>
<span id="L793" rel="#L793">793</span>
<span id="L794" rel="#L794">794</span>
<span id="L795" rel="#L795">795</span>
<span id="L796" rel="#L796">796</span>
<span id="L797" rel="#L797">797</span>
<span id="L798" rel="#L798">798</span>
<span id="L799" rel="#L799">799</span>
<span id="L800" rel="#L800">800</span>
<span id="L801" rel="#L801">801</span>
<span id="L802" rel="#L802">802</span>
<span id="L803" rel="#L803">803</span>
<span id="L804" rel="#L804">804</span>
<span id="L805" rel="#L805">805</span>
<span id="L806" rel="#L806">806</span>
<span id="L807" rel="#L807">807</span>
<span id="L808" rel="#L808">808</span>
<span id="L809" rel="#L809">809</span>
<span id="L810" rel="#L810">810</span>
<span id="L811" rel="#L811">811</span>
<span id="L812" rel="#L812">812</span>
<span id="L813" rel="#L813">813</span>
<span id="L814" rel="#L814">814</span>
<span id="L815" rel="#L815">815</span>
<span id="L816" rel="#L816">816</span>
<span id="L817" rel="#L817">817</span>
<span id="L818" rel="#L818">818</span>
<span id="L819" rel="#L819">819</span>
<span id="L820" rel="#L820">820</span>
<span id="L821" rel="#L821">821</span>
<span id="L822" rel="#L822">822</span>
<span id="L823" rel="#L823">823</span>
<span id="L824" rel="#L824">824</span>
<span id="L825" rel="#L825">825</span>

            </td>
            <td class="blob-line-code"><div class="code-body highlight"><pre><div class='line' id='LC1'><span class="cm">/**</span></div><div class='line' id='LC2'><span class="cm"> * Copyright 2014 The Apache Software Foundation.</span></div><div class='line' id='LC3'><span class="cm"> *</span></div><div class='line' id='LC4'><span class="cm"> * Licensed under the Apache License, Version 2.0 (the &quot;License&quot;); you may not</span></div><div class='line' id='LC5'><span class="cm"> * use this file except in compliance with the License. You may obtain a copy of</span></div><div class='line' id='LC6'><span class="cm"> * the License at</span></div><div class='line' id='LC7'><span class="cm"> *</span></div><div class='line' id='LC8'><span class="cm"> * http://www.apache.org/licenses/LICENSE-2.0</span></div><div class='line' id='LC9'><span class="cm"> *</span></div><div class='line' id='LC10'><span class="cm"> * Unless required by applicable law or agreed to in writing, software</span></div><div class='line' id='LC11'><span class="cm"> * distributed under the License is distributed on an &quot;AS IS&quot; BASIS, WITHOUT</span></div><div class='line' id='LC12'><span class="cm"> * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the</span></div><div class='line' id='LC13'><span class="cm"> * License for the specific language governing permissions and limitations under</span></div><div class='line' id='LC14'><span class="cm"> * the License.</span></div><div class='line' id='LC15'><span class="cm"> */</span></div><div class='line' id='LC16'><span class="kn">package</span> <span class="n">org</span><span class="o">.</span><span class="na">apache</span><span class="o">.</span><span class="na">manifoldcf</span><span class="o">.</span><span class="na">crawler</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">gridfs</span><span class="o">;</span></div><div class='line' id='LC17'><br/></div><div class='line' id='LC18'><span class="kn">import</span> <span class="nn">com.mongodb.DB</span><span class="o">;</span></div><div class='line' id='LC19'><span class="kn">import</span> <span class="nn">com.mongodb.DBCollection</span><span class="o">;</span></div><div class='line' id='LC20'><span class="kn">import</span> <span class="nn">com.mongodb.DBCursor</span><span class="o">;</span></div><div class='line' id='LC21'><span class="kn">import</span> <span class="nn">com.mongodb.DBObject</span><span class="o">;</span></div><div class='line' id='LC22'><span class="kn">import</span> <span class="nn">com.mongodb.DBTCPConnector</span><span class="o">;</span></div><div class='line' id='LC23'><span class="kn">import</span> <span class="nn">com.mongodb.Mongo</span><span class="o">;</span></div><div class='line' id='LC24'><span class="kn">import</span> <span class="nn">com.mongodb.MongoClient</span><span class="o">;</span></div><div class='line' id='LC25'><span class="kn">import</span> <span class="nn">com.mongodb.gridfs.GridFS</span><span class="o">;</span></div><div class='line' id='LC26'><span class="kn">import</span> <span class="nn">com.mongodb.gridfs.GridFSDBFile</span><span class="o">;</span></div><div class='line' id='LC27'><span class="kn">import</span> <span class="nn">java.io.IOException</span><span class="o">;</span></div><div class='line' id='LC28'><span class="kn">import</span> <span class="nn">java.io.InputStream</span><span class="o">;</span></div><div class='line' id='LC29'><span class="kn">import</span> <span class="nn">java.net.UnknownHostException</span><span class="o">;</span></div><div class='line' id='LC30'><span class="kn">import</span> <span class="nn">java.util.Date</span><span class="o">;</span></div><div class='line' id='LC31'><span class="kn">import</span> <span class="nn">java.util.HashMap</span><span class="o">;</span></div><div class='line' id='LC32'><span class="kn">import</span> <span class="nn">java.util.Iterator</span><span class="o">;</span></div><div class='line' id='LC33'><span class="kn">import</span> <span class="nn">java.util.List</span><span class="o">;</span></div><div class='line' id='LC34'><span class="kn">import</span> <span class="nn">java.util.Locale</span><span class="o">;</span></div><div class='line' id='LC35'><span class="kn">import</span> <span class="nn">java.util.Map</span><span class="o">;</span></div><div class='line' id='LC36'><span class="kn">import</span> <span class="nn">org.apache.commons.io.IOUtils</span><span class="o">;</span></div><div class='line' id='LC37'><span class="kn">import</span> <span class="nn">org.apache.commons.lang.StringUtils</span><span class="o">;</span></div><div class='line' id='LC38'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.agents.interfaces.RepositoryDocument</span><span class="o">;</span></div><div class='line' id='LC39'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.agents.interfaces.ServiceInterruption</span><span class="o">;</span></div><div class='line' id='LC40'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.ConfigParams</span><span class="o">;</span></div><div class='line' id='LC41'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.IHTTPOutput</span><span class="o">;</span></div><div class='line' id='LC42'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity</span><span class="o">;</span></div><div class='line' id='LC43'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.IPostParameters</span><span class="o">;</span></div><div class='line' id='LC44'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.IThreadContext</span><span class="o">;</span></div><div class='line' id='LC45'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.core.interfaces.ManifoldCFException</span><span class="o">;</span></div><div class='line' id='LC46'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector</span><span class="o">;</span></div><div class='line' id='LC47'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.crawler.interfaces.DocumentSpecification</span><span class="o">;</span></div><div class='line' id='LC48'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.crawler.interfaces.IProcessActivity</span><span class="o">;</span></div><div class='line' id='LC49'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.crawler.interfaces.ISeedingActivity</span><span class="o">;</span></div><div class='line' id='LC50'><span class="kn">import</span> <span class="nn">org.apache.manifoldcf.crawler.system.Logging</span><span class="o">;</span></div><div class='line' id='LC51'><span class="kn">import</span> <span class="nn">org.bson.types.ObjectId</span><span class="o">;</span></div><div class='line' id='LC52'><br/></div><div class='line' id='LC53'><span class="cm">/**</span></div><div class='line' id='LC54'><span class="cm"> *</span></div><div class='line' id='LC55'><span class="cm"> * @author molgun</span></div><div class='line' id='LC56'><span class="cm"> */</span></div><div class='line' id='LC57'><span class="kd">public</span> <span class="kd">class</span> <span class="nc">GridFSRepositoryConnector</span> <span class="kd">extends</span> <span class="n">BaseRepositoryConnector</span> <span class="o">{</span></div><div class='line' id='LC58'><br/></div><div class='line' id='LC59'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC60'><span class="cm">     * Activity name for the activity record.</span></div><div class='line' id='LC61'><span class="cm">     */</span></div><div class='line' id='LC62'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">ACTIVITY_FETCH</span> <span class="o">=</span> <span class="s">&quot;fetch&quot;</span><span class="o">;</span></div><div class='line' id='LC63'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC64'><span class="cm">     * Server name for declaring bin name.</span></div><div class='line' id='LC65'><span class="cm">     */</span></div><div class='line' id='LC66'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">SERVER</span> <span class="o">=</span> <span class="s">&quot;MongoDB - GridFS&quot;</span><span class="o">;</span></div><div class='line' id='LC67'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC68'><span class="cm">     * Session expiration milliseconds.</span></div><div class='line' id='LC69'><span class="cm">     */</span></div><div class='line' id='LC70'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kd">static</span> <span class="kd">final</span> <span class="kt">long</span> <span class="n">SESSION_EXPIRATION_MILLISECONDS</span> <span class="o">=</span> <span class="mi">30000L</span><span class="o">;</span></div><div class='line' id='LC71'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC72'><span class="cm">     * Endpoint username.</span></div><div class='line' id='LC73'><span class="cm">     */</span></div><div class='line' id='LC74'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">username</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC75'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC76'><span class="cm">     * Endpoint password.</span></div><div class='line' id='LC77'><span class="cm">     */</span></div><div class='line' id='LC78'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">password</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC79'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC80'><span class="cm">     * Endpoint host.</span></div><div class='line' id='LC81'><span class="cm">     */</span></div><div class='line' id='LC82'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">host</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC83'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC84'><span class="cm">     * Endpoint port.</span></div><div class='line' id='LC85'><span class="cm">     */</span></div><div class='line' id='LC86'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">port</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC87'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC88'><span class="cm">     * Endpoint db.</span></div><div class='line' id='LC89'><span class="cm">     */</span></div><div class='line' id='LC90'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">db</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC91'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC92'><span class="cm">     * Endpoint bucket.</span></div><div class='line' id='LC93'><span class="cm">     */</span></div><div class='line' id='LC94'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">bucket</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC95'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC96'><span class="cm">     * Endpoint url.</span></div><div class='line' id='LC97'><span class="cm">     */</span></div><div class='line' id='LC98'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">url</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC99'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC100'><span class="cm">     * Endpoint acl.</span></div><div class='line' id='LC101'><span class="cm">     */</span></div><div class='line' id='LC102'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">acl</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC103'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC104'><span class="cm">     * Endpoint denyAcl.</span></div><div class='line' id='LC105'><span class="cm">     */</span></div><div class='line' id='LC106'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">String</span> <span class="n">denyAcl</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC107'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC108'><span class="cm">     * MongoDB session.</span></div><div class='line' id='LC109'><span class="cm">     */</span></div><div class='line' id='LC110'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="n">DB</span> <span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC111'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC112'><span class="cm">     * Last session fetch time.</span></div><div class='line' id='LC113'><span class="cm">     */</span></div><div class='line' id='LC114'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kt">long</span> <span class="n">lastSessionFetch</span> <span class="o">=</span> <span class="o">-</span><span class="mi">1L</span><span class="o">;</span></div><div class='line' id='LC115'><br/></div><div class='line' id='LC116'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC117'><span class="cm">     * Forward to the javascript to check the configuration parameters.</span></div><div class='line' id='LC118'><span class="cm">     */</span></div><div class='line' id='LC119'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">EDIT_CONFIG_HEADER_FORWARD</span> <span class="o">=</span> <span class="s">&quot;editConfiguration.js&quot;</span><span class="o">;</span></div><div class='line' id='LC120'><br/></div><div class='line' id='LC121'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC122'><span class="cm">     * Forward to the HTML template to view the configuration parameters.</span></div><div class='line' id='LC123'><span class="cm">     */</span></div><div class='line' id='LC124'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">VIEW_CONFIG_FORWARD</span> <span class="o">=</span> <span class="s">&quot;viewConfiguration.html&quot;</span><span class="o">;</span></div><div class='line' id='LC125'><br/></div><div class='line' id='LC126'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC127'><span class="cm">     * Forward to the HTML template to edit the configuration parameters.</span></div><div class='line' id='LC128'><span class="cm">     */</span></div><div class='line' id='LC129'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">EDIT_CONFIG_FORWARD_SERVER</span> <span class="o">=</span> <span class="s">&quot;editConfiguration_Server.html&quot;</span><span class="o">;</span></div><div class='line' id='LC130'><br/></div><div class='line' id='LC131'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC132'><span class="cm">     * GridFS server tab name.</span></div><div class='line' id='LC133'><span class="cm">     */</span></div><div class='line' id='LC134'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">GRIDFS_SERVER_TAB_RESOURCE</span> <span class="o">=</span> <span class="s">&quot;GridFSConnector.Server&quot;</span><span class="o">;</span></div><div class='line' id='LC135'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC136'><span class="cm">     * GridFS credentials tab name.</span></div><div class='line' id='LC137'><span class="cm">     */</span></div><div class='line' id='LC138'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">GRIDFS_CREDENTIALS_TAB_RESOURCE</span> <span class="o">=</span> <span class="s">&quot;GridFSConnector.Credentials&quot;</span><span class="o">;</span></div><div class='line' id='LC139'><br/></div><div class='line' id='LC140'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC141'><span class="cm">     * Tab name parameter for managing the view of the Web UI.</span></div><div class='line' id='LC142'><span class="cm">     */</span></div><div class='line' id='LC143'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">private</span> <span class="kd">static</span> <span class="kd">final</span> <span class="n">String</span> <span class="n">TAB_NAME_PARAM</span> <span class="o">=</span> <span class="s">&quot;TabName&quot;</span><span class="o">;</span></div><div class='line' id='LC144'><br/></div><div class='line' id='LC145'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC146'><span class="cm">     * Constructer.</span></div><div class='line' id='LC147'><span class="cm">     */</span></div><div class='line' id='LC148'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="nf">GridFSRepositoryConnector</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC149'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">super</span><span class="o">();</span></div><div class='line' id='LC150'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC151'><br/></div><div class='line' id='LC152'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC153'><span class="cm">     * Tell the world what model this connector uses for addSeedDocuments().</span></div><div class='line' id='LC154'><span class="cm">     * This must return a model value as specified above. The connector does not</span></div><div class='line' id='LC155'><span class="cm">     * have to be connected for this method to be called.</span></div><div class='line' id='LC156'><span class="cm">     *</span></div><div class='line' id='LC157'><span class="cm">     * @return the model type value.</span></div><div class='line' id='LC158'><span class="cm">     */</span></div><div class='line' id='LC159'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC160'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span><span class="o">[]</span> <span class="nf">getBinNames</span><span class="o">(</span><span class="n">String</span> <span class="n">documentIdentifier</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC161'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="k">new</span> <span class="n">String</span><span class="o">[]{</span><span class="n">SERVER</span><span class="o">};</span></div><div class='line' id='LC162'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC163'><br/></div><div class='line' id='LC164'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC165'><span class="cm">     * Tell the world what model this connector uses for addSeedDocuments().</span></div><div class='line' id='LC166'><span class="cm">     * This must return a model value as specified above. The connector does not</span></div><div class='line' id='LC167'><span class="cm">     * have to be connected for this method to be called.</span></div><div class='line' id='LC168'><span class="cm">     *</span></div><div class='line' id='LC169'><span class="cm">     * @return the model type value.</span></div><div class='line' id='LC170'><span class="cm">     */</span></div><div class='line' id='LC171'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC172'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">int</span> <span class="nf">getConnectorModel</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC173'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kd">super</span><span class="o">.</span><span class="na">getConnectorModel</span><span class="o">();</span></div><div class='line' id='LC174'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC175'><br/></div><div class='line' id='LC176'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC177'><span class="cm">     * Return the list of activities that this connector supports (i.e. writes</span></div><div class='line' id='LC178'><span class="cm">     * into the log). The connector does not have to be connected for this</span></div><div class='line' id='LC179'><span class="cm">     * method to be called.</span></div><div class='line' id='LC180'><span class="cm">     *</span></div><div class='line' id='LC181'><span class="cm">     * @return the list.</span></div><div class='line' id='LC182'><span class="cm">     */</span></div><div class='line' id='LC183'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC184'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span><span class="o">[]</span> <span class="nf">getActivitiesList</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC185'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="k">new</span> <span class="n">String</span><span class="o">[]{</span><span class="n">ACTIVITY_FETCH</span><span class="o">};</span></div><div class='line' id='LC186'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC187'><br/></div><div class='line' id='LC188'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC189'><span class="cm">     * Connect.</span></div><div class='line' id='LC190'><span class="cm">     *</span></div><div class='line' id='LC191'><span class="cm">     * @param configParams is the set of configuration parameters, which in this</span></div><div class='line' id='LC192'><span class="cm">     * case describe the root directory.</span></div><div class='line' id='LC193'><span class="cm">     */</span></div><div class='line' id='LC194'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC195'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">connect</span><span class="o">(</span><span class="n">ConfigParams</span> <span class="n">configParams</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC196'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">super</span><span class="o">.</span><span class="na">connect</span><span class="o">(</span><span class="n">configParams</span><span class="o">);</span></div><div class='line' id='LC197'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">username</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">USERNAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC198'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">password</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PASSWORD_PARAM</span><span class="o">);</span></div><div class='line' id='LC199'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">host</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">HOST_PARAM</span><span class="o">);</span></div><div class='line' id='LC200'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">port</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PORT_PARAM</span><span class="o">);</span></div><div class='line' id='LC201'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">db</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DB_PARAM</span><span class="o">);</span></div><div class='line' id='LC202'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">bucket</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">BUCKET_PARAM</span><span class="o">);</span></div><div class='line' id='LC203'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">url</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC204'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">acl</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC205'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">denyAcl</span> <span class="o">=</span> <span class="n">params</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DENY_ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC206'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC207'><br/></div><div class='line' id='LC208'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC209'><span class="cm">     * Test the connection. Returns a string describing the connection</span></div><div class='line' id='LC210'><span class="cm">     * integrity.</span></div><div class='line' id='LC211'><span class="cm">     *</span></div><div class='line' id='LC212'><span class="cm">     * @return the connection&#39;s status as a displayable string.</span></div><div class='line' id='LC213'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC214'><span class="cm">     */</span></div><div class='line' id='LC215'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC216'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span> <span class="nf">check</span><span class="o">()</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC217'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC218'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">getSession</span><span class="o">();</span></div><div class='line' id='LC219'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">session</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC220'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Mongo</span> <span class="n">currentMongoSession</span> <span class="o">=</span> <span class="n">session</span><span class="o">.</span><span class="na">getMongo</span><span class="o">();</span></div><div class='line' id='LC221'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBTCPConnector</span> <span class="n">currentTCPConnection</span> <span class="o">=</span> <span class="n">currentMongoSession</span><span class="o">.</span><span class="na">getConnector</span><span class="o">();</span></div><div class='line' id='LC222'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">boolean</span> <span class="n">status</span> <span class="o">=</span> <span class="n">currentTCPConnection</span><span class="o">.</span><span class="na">isOpen</span><span class="o">();</span></div><div class='line' id='LC223'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">status</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC224'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span><span class="o">.</span><span class="na">getMongo</span><span class="o">().</span><span class="na">close</span><span class="o">();</span></div><div class='line' id='LC225'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC226'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kd">super</span><span class="o">.</span><span class="na">check</span><span class="o">();</span></div><div class='line' id='LC227'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="o">{</span></div><div class='line' id='LC228'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC229'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC230'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC231'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="s">&quot;Not connected.&quot;</span><span class="o">;</span></div><div class='line' id='LC232'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">ManifoldCFException</span> <span class="n">e</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC233'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="n">e</span><span class="o">.</span><span class="na">getMessage</span><span class="o">();</span></div><div class='line' id='LC234'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC235'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC236'><br/></div><div class='line' id='LC237'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC238'><span class="cm">     * Close the connection. Call this before discarding this instance of the</span></div><div class='line' id='LC239'><span class="cm">     * repository connector.</span></div><div class='line' id='LC240'><span class="cm">     *</span></div><div class='line' id='LC241'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC242'><span class="cm">     */</span></div><div class='line' id='LC243'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC244'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">disconnect</span><span class="o">()</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC245'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">session</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC246'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC247'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span><span class="o">.</span><span class="na">getMongo</span><span class="o">().</span><span class="na">getConnector</span><span class="o">().</span><span class="na">close</span><span class="o">();</span></div><div class='line' id='LC248'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">Exception</span> <span class="n">e</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC249'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">error</span><span class="o">(</span><span class="s">&quot;GridFS: Error when trying to disconnect: &quot;</span> <span class="o">+</span> <span class="n">e</span><span class="o">.</span><span class="na">getMessage</span><span class="o">());</span></div><div class='line' id='LC250'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Error when trying to disconnect: &quot;</span> <span class="o">+</span> <span class="n">e</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">e</span><span class="o">);</span></div><div class='line' id='LC251'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC252'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC253'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">lastSessionFetch</span> <span class="o">=</span> <span class="o">-</span><span class="mi">1L</span><span class="o">;</span></div><div class='line' id='LC254'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">username</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC255'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">password</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC256'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">host</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC257'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">port</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC258'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">db</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC259'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">bucket</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC260'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">url</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC261'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">acl</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC262'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">denyAcl</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC263'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC264'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC265'><br/></div><div class='line' id='LC266'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC267'><span class="cm">     * This method is periodically called for all connectors that are connected</span></div><div class='line' id='LC268'><span class="cm">     * but not in active use.</span></div><div class='line' id='LC269'><span class="cm">     *</span></div><div class='line' id='LC270'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC271'><span class="cm">     */</span></div><div class='line' id='LC272'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC273'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">poll</span><span class="o">()</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC274'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">lastSessionFetch</span> <span class="o">==</span> <span class="o">-</span><span class="mi">1L</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC275'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span><span class="o">;</span></div><div class='line' id='LC276'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC277'><br/></div><div class='line' id='LC278'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">long</span> <span class="n">currentTime</span> <span class="o">=</span> <span class="n">System</span><span class="o">.</span><span class="na">currentTimeMillis</span><span class="o">();</span></div><div class='line' id='LC279'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">currentTime</span> <span class="o">&gt;=</span> <span class="n">lastSessionFetch</span> <span class="o">+</span> <span class="n">SESSION_EXPIRATION_MILLISECONDS</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC280'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">session</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC281'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span><span class="o">.</span><span class="na">getMongo</span><span class="o">().</span><span class="na">getConnector</span><span class="o">().</span><span class="na">close</span><span class="o">();</span></div><div class='line' id='LC282'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC283'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC284'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">lastSessionFetch</span> <span class="o">=</span> <span class="o">-</span><span class="mi">1L</span><span class="o">;</span></div><div class='line' id='LC285'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC286'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC287'><br/></div><div class='line' id='LC288'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC289'><span class="cm">     * This method is called to assess whether to count this connector instance</span></div><div class='line' id='LC290'><span class="cm">     * should actually be counted as being connected.</span></div><div class='line' id='LC291'><span class="cm">     *</span></div><div class='line' id='LC292'><span class="cm">     * @return true if the connector instance is actually connected.</span></div><div class='line' id='LC293'><span class="cm">     */</span></div><div class='line' id='LC294'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC295'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">boolean</span> <span class="nf">isConnected</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC296'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">session</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC297'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kc">false</span><span class="o">;</span></div><div class='line' id='LC298'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC299'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Mongo</span> <span class="n">currentMongoSession</span> <span class="o">=</span> <span class="n">session</span><span class="o">.</span><span class="na">getMongo</span><span class="o">();</span></div><div class='line' id='LC300'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBTCPConnector</span> <span class="n">currentTCPConnection</span> <span class="o">=</span> <span class="n">currentMongoSession</span><span class="o">.</span><span class="na">getConnector</span><span class="o">();</span></div><div class='line' id='LC301'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="n">currentTCPConnection</span><span class="o">.</span><span class="na">isOpen</span><span class="o">();</span></div><div class='line' id='LC302'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC303'><br/></div><div class='line' id='LC304'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC305'><span class="cm">     * Get the maximum number of documents to amalgamate together into one</span></div><div class='line' id='LC306'><span class="cm">     * batch, for this connector.</span></div><div class='line' id='LC307'><span class="cm">     *</span></div><div class='line' id='LC308'><span class="cm">     * @return the maximum number. 0 indicates &quot;unlimited&quot;.</span></div><div class='line' id='LC309'><span class="cm">     */</span></div><div class='line' id='LC310'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC311'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">int</span> <span class="nf">getMaxDocumentRequest</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC312'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kd">super</span><span class="o">.</span><span class="na">getMaxDocumentRequest</span><span class="o">();</span></div><div class='line' id='LC313'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC314'><br/></div><div class='line' id='LC315'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC316'><span class="cm">     * Return the list of relationship types that this connector recognizes.</span></div><div class='line' id='LC317'><span class="cm">     *</span></div><div class='line' id='LC318'><span class="cm">     * @return the list.</span></div><div class='line' id='LC319'><span class="cm">     */</span></div><div class='line' id='LC320'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC321'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span><span class="o">[]</span> <span class="nf">getRelationshipTypes</span><span class="o">()</span> <span class="o">{</span></div><div class='line' id='LC322'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kd">super</span><span class="o">.</span><span class="na">getRelationshipTypes</span><span class="o">();</span></div><div class='line' id='LC323'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC324'><br/></div><div class='line' id='LC325'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC326'><span class="cm">     * Queue &quot;seed&quot; documents. Seed documents are the starting places for</span></div><div class='line' id='LC327'><span class="cm">     * crawling activity. Documents are seeded when this method calls</span></div><div class='line' id='LC328'><span class="cm">     * appropriate methods in the passed in ISeedingActivity object.</span></div><div class='line' id='LC329'><span class="cm">     *</span></div><div class='line' id='LC330'><span class="cm">     * This method can choose to find repository changes that happen only during</span></div><div class='line' id='LC331'><span class="cm">     * the specified time interval. The seeds recorded by this method will be</span></div><div class='line' id='LC332'><span class="cm">     * viewed by the framework based on what the getConnectorModel() method</span></div><div class='line' id='LC333'><span class="cm">     * returns.</span></div><div class='line' id='LC334'><span class="cm">     *</span></div><div class='line' id='LC335'><span class="cm">     * It is not a big problem if the connector chooses to create more seeds</span></div><div class='line' id='LC336'><span class="cm">     * than are strictly necessary; it is merely a question of overall work</span></div><div class='line' id='LC337'><span class="cm">     * required.</span></div><div class='line' id='LC338'><span class="cm">     *</span></div><div class='line' id='LC339'><span class="cm">     * The times passed to this method may be interpreted for greatest</span></div><div class='line' id='LC340'><span class="cm">     * efficiency. The time ranges any given job uses with this connector will</span></div><div class='line' id='LC341'><span class="cm">     * not overlap, but will proceed starting at 0 and going to the &quot;current</span></div><div class='line' id='LC342'><span class="cm">     * time&quot;, each time the job is run. For continuous crawling jobs, this</span></div><div class='line' id='LC343'><span class="cm">     * method will be called once, when the job starts, and at various periodic</span></div><div class='line' id='LC344'><span class="cm">     * intervals as the job executes.</span></div><div class='line' id='LC345'><span class="cm">     *</span></div><div class='line' id='LC346'><span class="cm">     * When a job&#39;s specification is changed, the framework automatically resets</span></div><div class='line' id='LC347'><span class="cm">     * the seeding start time to 0. The seeding start time may also be set to 0</span></div><div class='line' id='LC348'><span class="cm">     * on each job run, depending on the connector model returned by</span></div><div class='line' id='LC349'><span class="cm">     * getConnectorModel().</span></div><div class='line' id='LC350'><span class="cm">     *</span></div><div class='line' id='LC351'><span class="cm">     * Note that it is always ok to send MORE documents rather than less to this</span></div><div class='line' id='LC352'><span class="cm">     * method. The connector will be connected before this method can be called.</span></div><div class='line' id='LC353'><span class="cm">     *</span></div><div class='line' id='LC354'><span class="cm">     * @param activities is the interface this method should use to perform</span></div><div class='line' id='LC355'><span class="cm">     * whatever framework actions are desired.</span></div><div class='line' id='LC356'><span class="cm">     * @param spec is a document specification (that comes from the job).</span></div><div class='line' id='LC357'><span class="cm">     * @param startTime is the beginning of the time range to consider,</span></div><div class='line' id='LC358'><span class="cm">     * inclusive.</span></div><div class='line' id='LC359'><span class="cm">     * @param endTime is the end of the time range to consider, exclusive.</span></div><div class='line' id='LC360'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC361'><span class="cm">     * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption</span></div><div class='line' id='LC362'><span class="cm">     */</span></div><div class='line' id='LC363'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC364'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">addSeedDocuments</span><span class="o">(</span><span class="n">ISeedingActivity</span> <span class="n">activities</span><span class="o">,</span> <span class="n">DocumentSpecification</span> <span class="n">spec</span><span class="o">,</span></div><div class='line' id='LC365'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">long</span> <span class="n">startTime</span><span class="o">,</span> <span class="kt">long</span> <span class="n">endTime</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">ServiceInterruption</span> <span class="o">{</span></div><div class='line' id='LC366'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">getSession</span><span class="o">();</span></div><div class='line' id='LC367'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBCollection</span> <span class="n">fsFiles</span> <span class="o">=</span> <span class="n">session</span><span class="o">.</span><span class="na">getCollection</span><span class="o">(</span></div><div class='line' id='LC368'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">bucket</span> <span class="o">+</span> <span class="n">GridFSConstants</span><span class="o">.</span><span class="na">COLLECTION_SEPERATOR</span> <span class="o">+</span> <span class="n">GridFSConstants</span><span class="o">.</span><span class="na">FILES_COLLECTION_NAME</span></div><div class='line' id='LC369'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">);</span></div><div class='line' id='LC370'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBCursor</span> <span class="n">dnc</span> <span class="o">=</span> <span class="n">fsFiles</span><span class="o">.</span><span class="na">find</span><span class="o">();</span></div><div class='line' id='LC371'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">while</span> <span class="o">(</span><span class="n">dnc</span><span class="o">.</span><span class="na">hasNext</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC372'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBObject</span> <span class="n">dbo</span> <span class="o">=</span> <span class="n">dnc</span><span class="o">.</span><span class="na">next</span><span class="o">();</span></div><div class='line' id='LC373'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">_id</span> <span class="o">=</span> <span class="n">dbo</span><span class="o">.</span><span class="na">get</span><span class="o">(</span><span class="s">&quot;_id&quot;</span><span class="o">).</span><span class="na">toString</span><span class="o">();</span></div><div class='line' id='LC374'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">activities</span><span class="o">.</span><span class="na">addSeedDocument</span><span class="o">(</span><span class="n">_id</span><span class="o">);</span></div><div class='line' id='LC375'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">isDebugEnabled</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC376'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">debug</span><span class="o">(</span><span class="s">&quot;GridFS: Document _id = &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; added to queue&quot;</span><span class="o">);</span></div><div class='line' id='LC377'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC378'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC379'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC380'><br/></div><div class='line' id='LC381'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC382'><span class="cm">     * Process a set of documents. This is the method that should cause each</span></div><div class='line' id='LC383'><span class="cm">     * document to be fetched, processed, and the results either added to the</span></div><div class='line' id='LC384'><span class="cm">     * queue of documents for the current job, and/or entered into the</span></div><div class='line' id='LC385'><span class="cm">     * incremental ingestion manager. The document specification allows this</span></div><div class='line' id='LC386'><span class="cm">     * class to filter what is done based on the job. The connector will be</span></div><div class='line' id='LC387'><span class="cm">     * connected before this method can be called.</span></div><div class='line' id='LC388'><span class="cm">     *</span></div><div class='line' id='LC389'><span class="cm">     * @param documentIdentifiers is the set of document identifiers to process.</span></div><div class='line' id='LC390'><span class="cm">     * @param versions is the corresponding document versions to process, as</span></div><div class='line' id='LC391'><span class="cm">     * returned by getDocumentVersions() above. The implementation may choose to</span></div><div class='line' id='LC392'><span class="cm">     * ignore this parameter and always process the current version.</span></div><div class='line' id='LC393'><span class="cm">     * @param activities is the interface this method should use to queue up new</span></div><div class='line' id='LC394'><span class="cm">     * document references and ingest documents.</span></div><div class='line' id='LC395'><span class="cm">     * @param spec is the document specification.</span></div><div class='line' id='LC396'><span class="cm">     * @param scanOnly is an array corresponding to the document identifiers. It</span></div><div class='line' id='LC397'><span class="cm">     * is set to true to indicate when the processing should only find other</span></div><div class='line' id='LC398'><span class="cm">     * references, and should not actually call the ingestion methods.</span></div><div class='line' id='LC399'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC400'><span class="cm">     * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption</span></div><div class='line' id='LC401'><span class="cm">     */</span></div><div class='line' id='LC402'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC403'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">processDocuments</span><span class="o">(</span><span class="n">String</span><span class="o">[]</span> <span class="n">documentIdentifiers</span><span class="o">,</span> <span class="n">String</span><span class="o">[]</span> <span class="n">versions</span><span class="o">,</span></div><div class='line' id='LC404'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">IProcessActivity</span> <span class="n">activities</span><span class="o">,</span> <span class="n">DocumentSpecification</span> <span class="n">spec</span><span class="o">,</span></div><div class='line' id='LC405'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">boolean</span><span class="o">[]</span> <span class="n">scanOnly</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">ServiceInterruption</span> <span class="o">{</span></div><div class='line' id='LC406'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">isDebugEnabled</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC407'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">debug</span><span class="o">(</span><span class="s">&quot;GridFS: Inside processDocuments&quot;</span><span class="o">);</span></div><div class='line' id='LC408'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC409'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">int</span> <span class="n">i</span> <span class="o">=</span> <span class="mi">0</span><span class="o">;</span></div><div class='line' id='LC410'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">while</span> <span class="o">(</span><span class="n">i</span> <span class="o">&lt;</span> <span class="n">documentIdentifiers</span><span class="o">.</span><span class="na">length</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC411'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">long</span> <span class="n">startTime</span> <span class="o">=</span> <span class="n">System</span><span class="o">.</span><span class="na">currentTimeMillis</span><span class="o">();</span></div><div class='line' id='LC412'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">errorCode</span> <span class="o">=</span> <span class="s">&quot;OK&quot;</span><span class="o">;</span></div><div class='line' id='LC413'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">errorDesc</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC414'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">_id</span> <span class="o">=</span> <span class="n">documentIdentifiers</span><span class="o">[</span><span class="n">i</span><span class="o">];</span></div><div class='line' id='LC415'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">version</span> <span class="o">=</span> <span class="n">versions</span><span class="o">[</span><span class="n">i</span><span class="o">];</span></div><div class='line' id='LC416'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">getSession</span><span class="o">();</span></div><div class='line' id='LC417'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">GridFS</span> <span class="n">gfs</span> <span class="o">=</span> <span class="k">new</span> <span class="n">GridFS</span><span class="o">(</span><span class="n">session</span><span class="o">,</span> <span class="n">bucket</span><span class="o">);</span></div><div class='line' id='LC418'><br/></div><div class='line' id='LC419'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">RepositoryDocument</span> <span class="n">rd</span> <span class="o">=</span> <span class="k">new</span> <span class="n">RepositoryDocument</span><span class="o">();</span></div><div class='line' id='LC420'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">isDebugEnabled</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC421'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">debug</span><span class="o">(</span><span class="s">&quot;GridFS: Processing document _id = &quot;</span> <span class="o">+</span> <span class="n">_id</span><span class="o">);</span></div><div class='line' id='LC422'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC423'><br/></div><div class='line' id='LC424'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">GridFSDBFile</span> <span class="n">document</span> <span class="o">=</span> <span class="n">gfs</span><span class="o">.</span><span class="na">findOne</span><span class="o">(</span><span class="k">new</span> <span class="n">ObjectId</span><span class="o">(</span><span class="n">_id</span><span class="o">));</span></div><div class='line' id='LC425'><br/></div><div class='line' id='LC426'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">document</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC427'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">activities</span><span class="o">.</span><span class="na">deleteDocument</span><span class="o">(</span><span class="n">_id</span><span class="o">);</span></div><div class='line' id='LC428'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">i</span><span class="o">++;</span></div><div class='line' id='LC429'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">continue</span><span class="o">;</span></div><div class='line' id='LC430'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC431'><br/></div><div class='line' id='LC432'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBObject</span> <span class="n">metadata</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">();</span></div><div class='line' id='LC433'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">metadata</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC434'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">warn</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; has a null metadata - skipping.&quot;</span><span class="o">);</span></div><div class='line' id='LC435'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">i</span><span class="o">++;</span></div><div class='line' id='LC436'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">continue</span><span class="o">;</span></div><div class='line' id='LC437'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC438'><br/></div><div class='line' id='LC439'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">urlValue</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">().</span><span class="na">get</span><span class="o">(</span><span class="k">this</span><span class="o">.</span><span class="na">url</span><span class="o">)</span> <span class="o">==</span> <span class="kc">null</span></div><div class='line' id='LC440'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">?</span> <span class="n">StringUtils</span><span class="o">.</span><span class="na">EMPTY</span></div><div class='line' id='LC441'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">:</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">().</span><span class="na">get</span><span class="o">(</span><span class="k">this</span><span class="o">.</span><span class="na">url</span><span class="o">).</span><span class="na">toString</span><span class="o">();</span></div><div class='line' id='LC442'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">urlValue</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC443'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(!</span><span class="n">scanOnly</span><span class="o">[</span><span class="n">i</span><span class="o">])</span> <span class="o">{</span></div><div class='line' id='LC444'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">boolean</span> <span class="n">validURL</span><span class="o">;</span></div><div class='line' id='LC445'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC446'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">new</span> <span class="n">java</span><span class="o">.</span><span class="na">net</span><span class="o">.</span><span class="na">URI</span><span class="o">(</span><span class="n">urlValue</span><span class="o">);</span></div><div class='line' id='LC447'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">validURL</span> <span class="o">=</span> <span class="kc">true</span><span class="o">;</span></div><div class='line' id='LC448'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">java</span><span class="o">.</span><span class="na">net</span><span class="o">.</span><span class="na">URISyntaxException</span> <span class="n">e</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC449'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">validURL</span> <span class="o">=</span> <span class="kc">false</span><span class="o">;</span></div><div class='line' id='LC450'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC451'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">validURL</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC452'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">InputStream</span> <span class="n">is</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getInputStream</span><span class="o">();</span></div><div class='line' id='LC453'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">long</span> <span class="n">fileLenght</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getLength</span><span class="o">();</span></div><div class='line' id='LC454'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Date</span> <span class="n">indexingDate</span> <span class="o">=</span> <span class="k">new</span> <span class="n">Date</span><span class="o">();</span></div><div class='line' id='LC455'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setBinary</span><span class="o">(</span><span class="n">is</span><span class="o">,</span> <span class="n">fileLenght</span><span class="o">);</span></div><div class='line' id='LC456'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setCreatedDate</span><span class="o">(</span><span class="n">document</span><span class="o">.</span><span class="na">getUploadDate</span><span class="o">());</span></div><div class='line' id='LC457'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setFileName</span><span class="o">(</span><span class="n">document</span><span class="o">.</span><span class="na">getFilename</span><span class="o">());</span></div><div class='line' id='LC458'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setIndexingDate</span><span class="o">(</span><span class="n">indexingDate</span><span class="o">);</span></div><div class='line' id='LC459'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setMimeType</span><span class="o">(</span><span class="n">document</span><span class="o">.</span><span class="na">getContentType</span><span class="o">());</span></div><div class='line' id='LC460'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">acl</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC461'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC462'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Object</span> <span class="n">aclObject</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">().</span><span class="na">get</span><span class="o">(</span><span class="n">acl</span><span class="o">);</span></div><div class='line' id='LC463'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">aclObject</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC464'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">List</span><span class="o">&lt;</span><span class="n">String</span><span class="o">&gt;</span> <span class="n">acls</span> <span class="o">=</span> <span class="o">(</span><span class="n">List</span><span class="o">&lt;</span><span class="n">String</span><span class="o">&gt;)</span> <span class="n">aclObject</span><span class="o">;</span></div><div class='line' id='LC465'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setACL</span><span class="o">((</span><span class="n">String</span><span class="o">[])</span> <span class="n">acls</span><span class="o">.</span><span class="na">toArray</span><span class="o">());</span></div><div class='line' id='LC466'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC467'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">ClassCastException</span> <span class="n">e</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC468'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">warn</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; metadata ACL field doesn&#39;t contain List&lt;String&gt; type.&quot;</span><span class="o">);</span></div><div class='line' id='LC469'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC470'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC471'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">denyAcl</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC472'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC473'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Object</span> <span class="n">denyAclObject</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">().</span><span class="na">get</span><span class="o">(</span><span class="n">denyAcl</span><span class="o">);</span></div><div class='line' id='LC474'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">denyAclObject</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC475'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">List</span><span class="o">&lt;</span><span class="n">String</span><span class="o">&gt;</span> <span class="n">denyAcls</span> <span class="o">=</span> <span class="o">(</span><span class="n">List</span><span class="o">&lt;</span><span class="n">String</span><span class="o">&gt;)</span> <span class="n">denyAclObject</span><span class="o">;</span></div><div class='line' id='LC476'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">denyAcls</span><span class="o">.</span><span class="na">add</span><span class="o">(</span><span class="n">GLOBAL_DENY_TOKEN</span><span class="o">);</span></div><div class='line' id='LC477'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">setDenyACL</span><span class="o">((</span><span class="n">String</span><span class="o">[])</span> <span class="n">denyAcls</span><span class="o">.</span><span class="na">toArray</span><span class="o">());</span></div><div class='line' id='LC478'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC479'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">ClassCastException</span> <span class="n">e</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC480'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">warn</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; metadata DenyACL field doesn&#39;t contain List&lt;String&gt; type.&quot;</span><span class="o">);</span></div><div class='line' id='LC481'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC482'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC483'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">activities</span><span class="o">.</span><span class="na">ingestDocument</span><span class="o">(</span><span class="n">_id</span><span class="o">,</span> <span class="n">version</span><span class="o">,</span> <span class="n">urlValue</span><span class="o">,</span> <span class="n">rd</span><span class="o">);</span></div><div class='line' id='LC484'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">IOUtils</span><span class="o">.</span><span class="na">closeQuietly</span><span class="o">(</span><span class="n">is</span><span class="o">);</span></div><div class='line' id='LC485'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">gfs</span><span class="o">.</span><span class="na">getDB</span><span class="o">().</span><span class="na">getMongo</span><span class="o">().</span><span class="na">getConnector</span><span class="o">().</span><span class="na">close</span><span class="o">();</span></div><div class='line' id='LC486'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC487'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">activities</span><span class="o">.</span><span class="na">recordActivity</span><span class="o">(</span><span class="n">startTime</span><span class="o">,</span> <span class="n">ACTIVITY_FETCH</span><span class="o">,</span></div><div class='line' id='LC488'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">fileLenght</span><span class="o">,</span> <span class="n">_id</span><span class="o">,</span> <span class="n">errorCode</span><span class="o">,</span> <span class="n">errorDesc</span><span class="o">,</span> <span class="kc">null</span><span class="o">);</span></div><div class='line' id='LC489'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="o">{</span></div><div class='line' id='LC490'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">warn</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; has a invalid URL: &quot;</span> <span class="o">+</span> <span class="n">urlValue</span> <span class="o">+</span> <span class="s">&quot; - skipping.&quot;</span><span class="o">);</span></div><div class='line' id='LC491'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC492'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="o">{</span></div><div class='line' id='LC493'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">isDebugEnabled</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC494'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">debug</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; wasn&#39;t fetched because has still same version.&quot;</span><span class="o">);</span></div><div class='line' id='LC495'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC496'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC497'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="o">{</span></div><div class='line' id='LC498'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">warn</span><span class="o">(</span><span class="s">&quot;GridFS: Document &quot;</span> <span class="o">+</span> <span class="n">_id</span> <span class="o">+</span> <span class="s">&quot; has a null URL - skipping.&quot;</span><span class="o">);</span></div><div class='line' id='LC499'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC500'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">i</span><span class="o">++;</span></div><div class='line' id='LC501'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC502'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC503'><br/></div><div class='line' id='LC504'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC505'><span class="cm">     * Get document versions given an array of document identifiers. This method</span></div><div class='line' id='LC506'><span class="cm">     * is called for EVERY document that is considered. It is therefore</span></div><div class='line' id='LC507'><span class="cm">     * important to perform as little work as possible here. The connector will</span></div><div class='line' id='LC508'><span class="cm">     * be connected before this method can be called.</span></div><div class='line' id='LC509'><span class="cm">     *</span></div><div class='line' id='LC510'><span class="cm">     * @param documentIdentifiers is the array of local document identifiers, as</span></div><div class='line' id='LC511'><span class="cm">     * understood by this connector.</span></div><div class='line' id='LC512'><span class="cm">     * @param spec is the current document specification for the current job. If</span></div><div class='line' id='LC513'><span class="cm">     * there is a dependency on this specification, then the version string</span></div><div class='line' id='LC514'><span class="cm">     * should include the pertinent data, so that reingestion will occur when</span></div><div class='line' id='LC515'><span class="cm">     * the specification changes. This is primarily useful for metadata.</span></div><div class='line' id='LC516'><span class="cm">     * @return the corresponding version strings, with null in the places where</span></div><div class='line' id='LC517'><span class="cm">     * the document no longer exists. Empty version strings indicate that there</span></div><div class='line' id='LC518'><span class="cm">     * is no versioning ability for the corresponding document, and the document</span></div><div class='line' id='LC519'><span class="cm">     * will always be processed.</span></div><div class='line' id='LC520'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC521'><span class="cm">     * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption</span></div><div class='line' id='LC522'><span class="cm">     */</span></div><div class='line' id='LC523'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC524'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span><span class="o">[]</span> <span class="nf">getDocumentVersions</span><span class="o">(</span><span class="n">String</span><span class="o">[]</span> <span class="n">documentIdentifiers</span><span class="o">,</span></div><div class='line' id='LC525'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DocumentSpecification</span> <span class="n">spec</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">ServiceInterruption</span> <span class="o">{</span></div><div class='line' id='LC526'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">isDebugEnabled</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC527'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Logging</span><span class="o">.</span><span class="na">connectors</span><span class="o">.</span><span class="na">debug</span><span class="o">(</span><span class="s">&quot;GridFS: Inside getDocumentVersions&quot;</span><span class="o">);</span></div><div class='line' id='LC528'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC529'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span><span class="o">[]</span> <span class="n">versions</span> <span class="o">=</span> <span class="k">new</span> <span class="n">String</span><span class="o">[</span><span class="n">documentIdentifiers</span><span class="o">.</span><span class="na">length</span><span class="o">];</span></div><div class='line' id='LC530'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">getSession</span><span class="o">();</span></div><div class='line' id='LC531'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">int</span> <span class="n">i</span> <span class="o">=</span> <span class="mi">0</span><span class="o">;</span></div><div class='line' id='LC532'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">while</span> <span class="o">(</span><span class="n">i</span> <span class="o">&lt;</span> <span class="n">versions</span><span class="o">.</span><span class="na">length</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC533'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">_id</span> <span class="o">=</span> <span class="n">documentIdentifiers</span><span class="o">[</span><span class="n">i</span><span class="o">];</span></div><div class='line' id='LC534'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">GridFS</span> <span class="n">gridfs</span> <span class="o">=</span> <span class="k">new</span> <span class="n">GridFS</span><span class="o">(</span><span class="n">session</span><span class="o">,</span> <span class="n">bucket</span><span class="o">);</span></div><div class='line' id='LC535'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">GridFSDBFile</span> <span class="n">document</span> <span class="o">=</span> <span class="n">gridfs</span><span class="o">.</span><span class="na">findOne</span><span class="o">(</span><span class="k">new</span> <span class="n">ObjectId</span><span class="o">(</span><span class="n">_id</span><span class="o">));</span></div><div class='line' id='LC536'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">document</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC537'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">versions</span><span class="o">[</span><span class="n">i</span><span class="o">]</span> <span class="o">=</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC538'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="o">{</span></div><div class='line' id='LC539'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">DBObject</span> <span class="n">metadata</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMetaData</span><span class="o">();</span></div><div class='line' id='LC540'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">versions</span><span class="o">[</span><span class="n">i</span><span class="o">]</span> <span class="o">=</span> <span class="n">document</span><span class="o">.</span><span class="na">getMD5</span><span class="o">()</span> <span class="o">+</span> <span class="s">&quot;+&quot;</span> <span class="o">+</span> <span class="n">metadata</span> <span class="o">!=</span> <span class="kc">null</span></div><div class='line' id='LC541'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">?</span> <span class="n">Integer</span><span class="o">.</span><span class="na">toString</span><span class="o">(</span><span class="n">metadata</span><span class="o">.</span><span class="na">hashCode</span><span class="o">())</span></div><div class='line' id='LC542'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">:</span> <span class="n">StringUtils</span><span class="o">.</span><span class="na">EMPTY</span><span class="o">;</span></div><div class='line' id='LC543'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC544'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">i</span><span class="o">++;</span></div><div class='line' id='LC545'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC546'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="n">versions</span><span class="o">;</span></div><div class='line' id='LC547'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC548'><br/></div><div class='line' id='LC549'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC550'><span class="cm">     * Output the configuration header section. This method is called in the</span></div><div class='line' id='LC551'><span class="cm">     * head section of the connector&#39;s configuration page. Its purpose is to add</span></div><div class='line' id='LC552'><span class="cm">     * the required tabs to the list, and to output any javascript methods that</span></div><div class='line' id='LC553'><span class="cm">     * might be needed by the configuration editing HTML. The connector does not</span></div><div class='line' id='LC554'><span class="cm">     * need to be connected for this method to be called.</span></div><div class='line' id='LC555'><span class="cm">     *</span></div><div class='line' id='LC556'><span class="cm">     * @param threadContext is the local thread context.</span></div><div class='line' id='LC557'><span class="cm">     * @param out is the output to which any HTML should be sent.</span></div><div class='line' id='LC558'><span class="cm">     * @param parameters are the configuration parameters, as they currently</span></div><div class='line' id='LC559'><span class="cm">     * exist, for this connection being configured.</span></div><div class='line' id='LC560'><span class="cm">     * @param tabsArray is an array of tab names. Add to this array any tab</span></div><div class='line' id='LC561'><span class="cm">     * names that are specific to the connector.</span></div><div class='line' id='LC562'><span class="cm">     * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException</span></div><div class='line' id='LC563'><span class="cm">     * @throws java.io.IOException</span></div><div class='line' id='LC564'><span class="cm">     */</span></div><div class='line' id='LC565'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC566'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">outputConfigurationHeader</span><span class="o">(</span><span class="n">IThreadContext</span> <span class="n">threadContext</span><span class="o">,</span> <span class="n">IHTTPOutput</span> <span class="n">out</span><span class="o">,</span> <span class="n">Locale</span> <span class="n">locale</span><span class="o">,</span> <span class="n">ConfigParams</span> <span class="n">parameters</span><span class="o">,</span> <span class="n">List</span><span class="o">&lt;</span><span class="n">String</span><span class="o">&gt;</span> <span class="n">tabsArray</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">IOException</span> <span class="o">{</span></div><div class='line' id='LC567'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">tabsArray</span><span class="o">.</span><span class="na">add</span><span class="o">(</span><span class="n">Messages</span><span class="o">.</span><span class="na">getString</span><span class="o">(</span><span class="n">locale</span><span class="o">,</span> <span class="n">GRIDFS_SERVER_TAB_RESOURCE</span><span class="o">));</span></div><div class='line' id='LC568'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">tabsArray</span><span class="o">.</span><span class="na">add</span><span class="o">(</span><span class="n">Messages</span><span class="o">.</span><span class="na">getString</span><span class="o">(</span><span class="n">locale</span><span class="o">,</span> <span class="n">GRIDFS_CREDENTIALS_TAB_RESOURCE</span><span class="o">));</span></div><div class='line' id='LC569'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Map</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;</span> <span class="n">paramMap</span> <span class="o">=</span> <span class="k">new</span> <span class="n">HashMap</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;();</span></div><div class='line' id='LC570'><br/></div><div class='line' id='LC571'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">fillInServerParameters</span><span class="o">(</span><span class="n">paramMap</span><span class="o">,</span> <span class="n">out</span><span class="o">,</span> <span class="n">parameters</span><span class="o">);</span></div><div class='line' id='LC572'><br/></div><div class='line' id='LC573'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Messages</span><span class="o">.</span><span class="na">outputResourceWithVelocity</span><span class="o">(</span><span class="n">out</span><span class="o">,</span> <span class="n">locale</span><span class="o">,</span> <span class="n">EDIT_CONFIG_HEADER_FORWARD</span><span class="o">,</span> <span class="n">paramMap</span><span class="o">,</span> <span class="kc">true</span><span class="o">);</span></div><div class='line' id='LC574'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC575'><br/></div><div class='line' id='LC576'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC577'><span class="cm">     * Output the configuration body section. This method is called in the body</span></div><div class='line' id='LC578'><span class="cm">     * section of the connector&#39;s configuration page. Its purpose is to present</span></div><div class='line' id='LC579'><span class="cm">     * the required form elements for editing. The coder can presume that the</span></div><div class='line' id='LC580'><span class="cm">     * HTML that is output from this configuration will be within appropriate</span></div><div class='line' id='LC581'><span class="cm">     * &lt;html&gt;, &lt;body&gt;, and &lt;form&gt; tags. The name of the form is always</span></div><div class='line' id='LC582'><span class="cm">     * &quot;editconnection&quot;. The connector does not need to be connected for this</span></div><div class='line' id='LC583'><span class="cm">     * method to be called.</span></div><div class='line' id='LC584'><span class="cm">     *</span></div><div class='line' id='LC585'><span class="cm">     * @param threadContext is the local thread context.</span></div><div class='line' id='LC586'><span class="cm">     * @param out is the output to which any HTML should be sent.</span></div><div class='line' id='LC587'><span class="cm">     * @param parameters are the configuration parameters, as they currently</span></div><div class='line' id='LC588'><span class="cm">     * exist, for this connection being configured.</span></div><div class='line' id='LC589'><span class="cm">     * @param tabName is the current tab name.</span></div><div class='line' id='LC590'><span class="cm">     */</span></div><div class='line' id='LC591'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC592'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">outputConfigurationBody</span><span class="o">(</span><span class="n">IThreadContext</span> <span class="n">threadContext</span><span class="o">,</span></div><div class='line' id='LC593'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">IHTTPOutput</span> <span class="n">out</span><span class="o">,</span> <span class="n">Locale</span> <span class="n">locale</span><span class="o">,</span> <span class="n">ConfigParams</span> <span class="n">parameters</span><span class="o">,</span> <span class="n">String</span> <span class="n">tabName</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">IOException</span> <span class="o">{</span></div><div class='line' id='LC594'><br/></div><div class='line' id='LC595'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Map</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;</span> <span class="n">paramMap</span> <span class="o">=</span> <span class="k">new</span> <span class="n">HashMap</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;();</span></div><div class='line' id='LC596'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">TAB_NAME_PARAM</span><span class="o">,</span> <span class="n">tabName</span><span class="o">);</span></div><div class='line' id='LC597'><br/></div><div class='line' id='LC598'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">fillInServerParameters</span><span class="o">(</span><span class="n">paramMap</span><span class="o">,</span> <span class="n">out</span><span class="o">,</span> <span class="n">parameters</span><span class="o">);</span></div><div class='line' id='LC599'><br/></div><div class='line' id='LC600'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Messages</span><span class="o">.</span><span class="na">outputResourceWithVelocity</span><span class="o">(</span><span class="n">out</span><span class="o">,</span> <span class="n">locale</span><span class="o">,</span> <span class="n">EDIT_CONFIG_FORWARD_SERVER</span><span class="o">,</span> <span class="n">paramMap</span><span class="o">,</span> <span class="kc">true</span><span class="o">);</span></div><div class='line' id='LC601'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC602'><br/></div><div class='line' id='LC603'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC604'><span class="cm">     * Process a configuration post. This method is called at the start of the</span></div><div class='line' id='LC605'><span class="cm">     * connector&#39;s configuration page, whenever there is a possibility that form</span></div><div class='line' id='LC606'><span class="cm">     * data for a connection has been posted. Its purpose is to gather form</span></div><div class='line' id='LC607'><span class="cm">     * information and modify the configuration parameters accordingly. The name</span></div><div class='line' id='LC608'><span class="cm">     * of the posted form is always &quot;editconnection&quot;. The connector does not</span></div><div class='line' id='LC609'><span class="cm">     * need to be connected for this method to be called.</span></div><div class='line' id='LC610'><span class="cm">     *</span></div><div class='line' id='LC611'><span class="cm">     * @param threadContext is the local thread context.</span></div><div class='line' id='LC612'><span class="cm">     * @param variableContext is the set of variables available from the post,</span></div><div class='line' id='LC613'><span class="cm">     * including binary file post information.</span></div><div class='line' id='LC614'><span class="cm">     * @param parameters are the configuration parameters, as they currently</span></div><div class='line' id='LC615'><span class="cm">     * exist, for this connection being configured.</span></div><div class='line' id='LC616'><span class="cm">     * @return null if all is well, or a string error message if there is an</span></div><div class='line' id='LC617'><span class="cm">     * error that should prevent saving of the connection (and cause a</span></div><div class='line' id='LC618'><span class="cm">     * redirection to an error page).</span></div><div class='line' id='LC619'><span class="cm">     */</span></div><div class='line' id='LC620'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC621'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="n">String</span> <span class="nf">processConfigurationPost</span><span class="o">(</span><span class="n">IThreadContext</span> <span class="n">threadContext</span><span class="o">,</span></div><div class='line' id='LC622'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">IPostParameters</span> <span class="n">variableContext</span><span class="o">,</span> <span class="n">Locale</span> <span class="n">locale</span><span class="o">,</span> <span class="n">ConfigParams</span> <span class="n">parameters</span><span class="o">)</span></div><div class='line' id='LC623'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC624'><br/></div><div class='line' id='LC625'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">username</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">USERNAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC626'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">username</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC627'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">USERNAME_PARAM</span><span class="o">,</span> <span class="n">username</span><span class="o">);</span></div><div class='line' id='LC628'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC629'><br/></div><div class='line' id='LC630'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">password</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PASSWORD_PARAM</span><span class="o">);</span></div><div class='line' id='LC631'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">password</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC632'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PASSWORD_PARAM</span><span class="o">,</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">mapKeyToPassword</span><span class="o">(</span><span class="n">password</span><span class="o">));</span></div><div class='line' id='LC633'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC634'><br/></div><div class='line' id='LC635'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">db</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DB_PARAM</span><span class="o">);</span></div><div class='line' id='LC636'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">db</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC637'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DB_PARAM</span><span class="o">,</span> <span class="n">db</span><span class="o">);</span></div><div class='line' id='LC638'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC639'><br/></div><div class='line' id='LC640'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">bucket</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">BUCKET_PARAM</span><span class="o">);</span></div><div class='line' id='LC641'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">bucket</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC642'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">BUCKET_PARAM</span><span class="o">,</span> <span class="n">bucket</span><span class="o">);</span></div><div class='line' id='LC643'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC644'><br/></div><div class='line' id='LC645'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">port</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PORT_PARAM</span><span class="o">);</span></div><div class='line' id='LC646'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">port</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC647'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PORT_PARAM</span><span class="o">,</span> <span class="n">port</span><span class="o">);</span></div><div class='line' id='LC648'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC649'><br/></div><div class='line' id='LC650'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">host</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">HOST_PARAM</span><span class="o">);</span></div><div class='line' id='LC651'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">host</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC652'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">HOST_PARAM</span><span class="o">,</span> <span class="n">host</span><span class="o">);</span></div><div class='line' id='LC653'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC654'><br/></div><div class='line' id='LC655'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">url</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC656'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">url</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC657'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">url</span><span class="o">);</span></div><div class='line' id='LC658'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC659'><br/></div><div class='line' id='LC660'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">acl</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC661'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">acl</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC662'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">acl</span><span class="o">);</span></div><div class='line' id='LC663'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC664'><br/></div><div class='line' id='LC665'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">denyAcl</span> <span class="o">=</span> <span class="n">variableContext</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DENY_ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC666'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">denyAcl</span> <span class="o">!=</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC667'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">parameters</span><span class="o">.</span><span class="na">setParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DENY_ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">denyAcl</span><span class="o">);</span></div><div class='line' id='LC668'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC669'><br/></div><div class='line' id='LC670'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">return</span> <span class="kc">null</span><span class="o">;</span></div><div class='line' id='LC671'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC672'><br/></div><div class='line' id='LC673'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC674'><span class="cm">     * View configuration. This method is called in the body section of the</span></div><div class='line' id='LC675'><span class="cm">     * connector&#39;s view configuration page. Its purpose is to present the</span></div><div class='line' id='LC676'><span class="cm">     * connection information to the user. The coder can presume that the HTML</span></div><div class='line' id='LC677'><span class="cm">     * that is output from this configuration will be within appropriate &lt;html&gt;</span></div><div class='line' id='LC678'><span class="cm">     * and &lt;body&gt; tags. The connector does not need to be connected for this</span></div><div class='line' id='LC679'><span class="cm">     * method to be called.</span></div><div class='line' id='LC680'><span class="cm">     *</span></div><div class='line' id='LC681'><span class="cm">     * @param threadContext is the local thread context.</span></div><div class='line' id='LC682'><span class="cm">     * @param out is the output to which any HTML should be sent.</span></div><div class='line' id='LC683'><span class="cm">     * @param parameters are the configuration parameters, as they currently</span></div><div class='line' id='LC684'><span class="cm">     * exist, for this connection being configured.</span></div><div class='line' id='LC685'><span class="cm">     */</span></div><div class='line' id='LC686'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="nd">@Override</span></div><div class='line' id='LC687'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">viewConfiguration</span><span class="o">(</span><span class="n">IThreadContext</span> <span class="n">threadContext</span><span class="o">,</span> <span class="n">IHTTPOutput</span> <span class="n">out</span><span class="o">,</span> <span class="n">Locale</span> <span class="n">locale</span><span class="o">,</span> <span class="n">ConfigParams</span> <span class="n">parameters</span><span class="o">)</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span><span class="o">,</span> <span class="n">IOException</span> <span class="o">{</span></div><div class='line' id='LC688'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Map</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;</span> <span class="n">paramMap</span> <span class="o">=</span> <span class="k">new</span> <span class="n">HashMap</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;();</span></div><div class='line' id='LC689'><br/></div><div class='line' id='LC690'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">fillInServerParameters</span><span class="o">(</span><span class="n">paramMap</span><span class="o">,</span> <span class="n">out</span><span class="o">,</span> <span class="n">parameters</span><span class="o">);</span></div><div class='line' id='LC691'><br/></div><div class='line' id='LC692'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Messages</span><span class="o">.</span><span class="na">outputResourceWithVelocity</span><span class="o">(</span><span class="n">out</span><span class="o">,</span> <span class="n">locale</span><span class="o">,</span> <span class="n">VIEW_CONFIG_FORWARD</span><span class="o">,</span> <span class="n">paramMap</span><span class="o">,</span> <span class="kc">true</span><span class="o">);</span></div><div class='line' id='LC693'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC694'><br/></div><div class='line' id='LC695'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC696'><span class="cm">     * Setup a session.</span></div><div class='line' id='LC697'><span class="cm">     *</span></div><div class='line' id='LC698'><span class="cm">     * @throws ManifoldCFException</span></div><div class='line' id='LC699'><span class="cm">     */</span></div><div class='line' id='LC700'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kt">void</span> <span class="nf">getSession</span><span class="o">()</span> <span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC701'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">session</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC702'><br/></div><div class='line' id='LC703'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">db</span><span class="o">)</span> <span class="o">||</span> <span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">bucket</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC704'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Database or bucket name cannot be empty.&quot;</span><span class="o">);</span></div><div class='line' id='LC705'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC706'><br/></div><div class='line' id='LC707'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">url</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC708'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Metadata URL field cannot be empty.&quot;</span><span class="o">);</span></div><div class='line' id='LC709'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC710'><br/></div><div class='line' id='LC711'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">host</span><span class="o">)</span> <span class="o">&amp;&amp;</span> <span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">port</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC712'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC713'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="k">new</span> <span class="n">MongoClient</span><span class="o">().</span><span class="na">getDB</span><span class="o">(</span><span class="n">db</span><span class="o">);</span></div><div class='line' id='LC714'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">UnknownHostException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC715'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Default host is not found. Does mongod process run?&quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC716'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC717'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="k">if</span> <span class="o">(!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">host</span><span class="o">)</span> <span class="o">&amp;&amp;</span> <span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">port</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC718'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC719'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="k">new</span> <span class="n">MongoClient</span><span class="o">(</span><span class="n">host</span><span class="o">).</span><span class="na">getDB</span><span class="o">(</span><span class="n">db</span><span class="o">);</span></div><div class='line' id='LC720'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">UnknownHostException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC721'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given host information is not valid or mongod process doesn&#39;t run&quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC722'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC723'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="k">if</span> <span class="o">(!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">host</span><span class="o">)</span> <span class="o">&amp;&amp;</span> <span class="o">!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">port</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC724'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC725'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">int</span> <span class="n">integerPort</span> <span class="o">=</span> <span class="n">Integer</span><span class="o">.</span><span class="na">parseInt</span><span class="o">(</span><span class="n">port</span><span class="o">);</span></div><div class='line' id='LC726'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="k">new</span> <span class="n">MongoClient</span><span class="o">(</span><span class="n">host</span><span class="o">,</span> <span class="n">integerPort</span><span class="o">).</span><span class="na">getDB</span><span class="o">(</span><span class="n">db</span><span class="o">);</span></div><div class='line' id='LC727'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">UnknownHostException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC728'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given information is not valid or mongod process doesn&#39;t run&quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC729'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">NumberFormatException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC730'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given port is not valid number. &quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC731'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC732'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">else</span> <span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">host</span><span class="o">)</span> <span class="o">&amp;&amp;</span> <span class="o">!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">port</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC733'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">try</span> <span class="o">{</span></div><div class='line' id='LC734'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">int</span> <span class="n">integerPort</span> <span class="o">=</span> <span class="n">Integer</span><span class="o">.</span><span class="na">parseInt</span><span class="o">(</span><span class="n">port</span><span class="o">);</span></div><div class='line' id='LC735'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">session</span> <span class="o">=</span> <span class="k">new</span> <span class="n">MongoClient</span><span class="o">(</span><span class="n">host</span><span class="o">,</span> <span class="n">integerPort</span><span class="o">).</span><span class="na">getDB</span><span class="o">(</span><span class="n">db</span><span class="o">);</span></div><div class='line' id='LC736'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">UnknownHostException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC737'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given information is not valid or mongod process doesn&#39;t run&quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC738'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span> <span class="k">catch</span> <span class="o">(</span><span class="n">NumberFormatException</span> <span class="n">ex</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC739'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given port is not valid number. &quot;</span> <span class="o">+</span> <span class="n">ex</span><span class="o">.</span><span class="na">getMessage</span><span class="o">(),</span> <span class="n">ex</span><span class="o">);</span></div><div class='line' id='LC740'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC741'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC742'><br/></div><div class='line' id='LC743'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">username</span><span class="o">)</span> <span class="o">&amp;&amp;</span> <span class="o">!</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">password</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC744'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kt">boolean</span> <span class="n">auth</span> <span class="o">=</span> <span class="n">session</span><span class="o">.</span><span class="na">authenticate</span><span class="o">(</span><span class="n">username</span><span class="o">,</span> <span class="n">password</span><span class="o">.</span><span class="na">toCharArray</span><span class="o">());</span></div><div class='line' id='LC745'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(!</span><span class="n">auth</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC746'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;GridFS: Given database username and password doesn&#39;t match.&quot;</span><span class="o">);</span></div><div class='line' id='LC747'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC748'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC749'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">lastSessionFetch</span> <span class="o">=</span> <span class="n">System</span><span class="o">.</span><span class="na">currentTimeMillis</span><span class="o">();</span></div><div class='line' id='LC750'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC751'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC752'><br/></div><div class='line' id='LC753'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC754'><span class="cm">     * Fill in a Server tab configuration parameter map for calling a Velocity</span></div><div class='line' id='LC755'><span class="cm">     * template.</span></div><div class='line' id='LC756'><span class="cm">     *</span></div><div class='line' id='LC757'><span class="cm">     * @param paramMap is the map to fill in</span></div><div class='line' id='LC758'><span class="cm">     * @param parameters is the current set of configuration parameters</span></div><div class='line' id='LC759'><span class="cm">     */</span></div><div class='line' id='LC760'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">public</span> <span class="kt">void</span> <span class="nf">fillInServerParameters</span><span class="o">(</span><span class="n">Map</span><span class="o">&lt;</span><span class="n">String</span><span class="o">,</span> <span class="n">String</span><span class="o">&gt;</span> <span class="n">paramMap</span><span class="o">,</span> <span class="n">IPasswordMapperActivity</span> <span class="n">mapper</span><span class="o">,</span> <span class="n">ConfigParams</span> <span class="n">parameters</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC761'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">usernameParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">USERNAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC762'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">USERNAME_PARAM</span><span class="o">,</span> <span class="n">usernameParam</span><span class="o">);</span></div><div class='line' id='LC763'><br/></div><div class='line' id='LC764'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">passwordParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PASSWORD_PARAM</span><span class="o">);</span></div><div class='line' id='LC765'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">passwordParam</span> <span class="o">=</span> <span class="n">mapper</span><span class="o">.</span><span class="na">mapKeyToPassword</span><span class="o">(</span><span class="n">passwordParam</span><span class="o">);</span></div><div class='line' id='LC766'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PASSWORD_PARAM</span><span class="o">,</span> <span class="n">passwordParam</span><span class="o">);</span></div><div class='line' id='LC767'><br/></div><div class='line' id='LC768'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">dbParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DB_PARAM</span><span class="o">);</span></div><div class='line' id='LC769'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">dbParam</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC770'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">dbParam</span> <span class="o">=</span> <span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DEFAULT_DB_NAME</span><span class="o">;</span></div><div class='line' id='LC771'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC772'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DB_PARAM</span><span class="o">,</span> <span class="n">dbParam</span><span class="o">);</span></div><div class='line' id='LC773'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">bucketParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">BUCKET_PARAM</span><span class="o">);</span></div><div class='line' id='LC774'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">StringUtils</span><span class="o">.</span><span class="na">isEmpty</span><span class="o">(</span><span class="n">bucketParam</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC775'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">bucketParam</span> <span class="o">=</span> <span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DEFAULT_BUCKET_NAME</span><span class="o">;</span></div><div class='line' id='LC776'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC777'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">BUCKET_PARAM</span><span class="o">,</span> <span class="n">bucketParam</span><span class="o">);</span></div><div class='line' id='LC778'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">hostParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">HOST_PARAM</span><span class="o">);</span></div><div class='line' id='LC779'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">HOST_PARAM</span><span class="o">,</span> <span class="n">hostParam</span><span class="o">);</span></div><div class='line' id='LC780'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">portParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PORT_PARAM</span><span class="o">);</span></div><div class='line' id='LC781'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">PORT_PARAM</span><span class="o">,</span> <span class="n">portParam</span><span class="o">);</span></div><div class='line' id='LC782'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">urlParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC783'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">urlParam</span><span class="o">);</span></div><div class='line' id='LC784'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">aclParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC785'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">aclParam</span><span class="o">);</span></div><div class='line' id='LC786'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">denyAclParam</span> <span class="o">=</span> <span class="n">parameters</span><span class="o">.</span><span class="na">getParameter</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DENY_ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">);</span></div><div class='line' id='LC787'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">paramMap</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DENY_ACL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="n">denyAclParam</span><span class="o">);</span></div><div class='line' id='LC788'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC789'><br/></div><div class='line' id='LC790'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC791'><span class="cm">     * Special column names, as far as document queries are concerned</span></div><div class='line' id='LC792'><span class="cm">     */</span></div><div class='line' id='LC793'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kd">static</span> <span class="n">HashMap</span> <span class="n">documentKnownColumns</span><span class="o">;</span></div><div class='line' id='LC794'><br/></div><div class='line' id='LC795'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">static</span> <span class="o">{</span></div><div class='line' id='LC796'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">documentKnownColumns</span> <span class="o">=</span> <span class="k">new</span> <span class="n">HashMap</span><span class="o">();</span></div><div class='line' id='LC797'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">documentKnownColumns</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">DEFAULT_ID_FIELD_NAME</span><span class="o">,</span> <span class="s">&quot;&quot;</span><span class="o">);</span></div><div class='line' id='LC798'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">documentKnownColumns</span><span class="o">.</span><span class="na">put</span><span class="o">(</span><span class="n">GridFSConstants</span><span class="o">.</span><span class="na">URL_RETURN_FIELD_NAME_PARAM</span><span class="o">,</span> <span class="s">&quot;&quot;</span><span class="o">);</span></div><div class='line' id='LC799'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC800'><br/></div><div class='line' id='LC801'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="cm">/**</span></div><div class='line' id='LC802'><span class="cm">     * Apply metadata to a repository document.</span></div><div class='line' id='LC803'><span class="cm">     *</span></div><div class='line' id='LC804'><span class="cm">     * @param rd is the repository document to apply the metadata to.</span></div><div class='line' id='LC805'><span class="cm">     * @param metadataMap is the resultset row to use to get the metadata. All</span></div><div class='line' id='LC806'><span class="cm">     * non-special columns from this row will be considered to be metadata.</span></div><div class='line' id='LC807'><span class="cm">     */</span></div><div class='line' id='LC808'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">protected</span> <span class="kt">void</span> <span class="nf">applyMetadata</span><span class="o">(</span><span class="n">RepositoryDocument</span> <span class="n">rd</span><span class="o">,</span> <span class="n">DBObject</span> <span class="n">metadataMap</span><span class="o">)</span></div><div class='line' id='LC809'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="kd">throws</span> <span class="n">ManifoldCFException</span> <span class="o">{</span></div><div class='line' id='LC810'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="c1">// Cycle through the document&#39;s fields</span></div><div class='line' id='LC811'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Iterator</span> <span class="n">iter</span> <span class="o">=</span> <span class="n">metadataMap</span><span class="o">.</span><span class="na">keySet</span><span class="o">().</span><span class="na">iterator</span><span class="o">();</span></div><div class='line' id='LC812'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">while</span> <span class="o">(</span><span class="n">iter</span><span class="o">.</span><span class="na">hasNext</span><span class="o">())</span> <span class="o">{</span></div><div class='line' id='LC813'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">String</span> <span class="n">fieldName</span> <span class="o">=</span> <span class="o">(</span><span class="n">String</span><span class="o">)</span> <span class="n">iter</span><span class="o">.</span><span class="na">next</span><span class="o">();</span></div><div class='line' id='LC814'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(</span><span class="n">documentKnownColumns</span><span class="o">.</span><span class="na">get</span><span class="o">(</span><span class="n">fieldName</span><span class="o">)</span> <span class="o">==</span> <span class="kc">null</span><span class="o">)</span> <span class="o">{</span></div><div class='line' id='LC815'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="c1">// Consider this field to contain metadata.</span></div><div class='line' id='LC816'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="c1">// We can only accept non-binary metadata at this time.</span></div><div class='line' id='LC817'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">Object</span> <span class="n">metadata</span> <span class="o">=</span> <span class="n">metadataMap</span><span class="o">.</span><span class="na">get</span><span class="o">(</span><span class="n">fieldName</span><span class="o">);</span></div><div class='line' id='LC818'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">if</span> <span class="o">(!(</span><span class="n">metadata</span> <span class="k">instanceof</span> <span class="n">String</span><span class="o">))</span> <span class="o">{</span></div><div class='line' id='LC819'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="k">throw</span> <span class="k">new</span> <span class="nf">ManifoldCFException</span><span class="o">(</span><span class="s">&quot;Metadata field &#39;&quot;</span> <span class="o">+</span> <span class="n">fieldName</span> <span class="o">+</span> <span class="s">&quot;&#39; must be convertible to a string.&quot;</span><span class="o">);</span></div><div class='line' id='LC820'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC821'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="n">rd</span><span class="o">.</span><span class="na">addField</span><span class="o">(</span><span class="n">fieldName</span><span class="o">,</span> <span class="n">metadata</span><span class="o">.</span><span class="na">toString</span><span class="o">());</span></div><div class='line' id='LC822'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC823'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC824'>&nbsp;&nbsp;&nbsp;&nbsp;<span class="o">}</span></div><div class='line' id='LC825'><span class="o">}</span></div></pre></div></td>
          </tr>
        </table>
  </div>

  </div>
</div>

<a href="#jump-to-line" rel="facebox[.linejump]" data-hotkey="l" class="js-jump-to-line" style="display:none">Jump to Line</a>
<div id="jump-to-line" style="display:none">
  <form accept-charset="UTF-8" class="js-jump-to-line-form">
    <input class="linejump-input js-jump-to-line-field" type="text" placeholder="Jump to line&hellip;" autofocus>
    <button type="submit" class="button">Go</button>
  </form>
</div>

        </div>

      </div><!-- /.repo-container -->
      <div class="modal-backdrop"></div>
    </div><!-- /.container -->
  </div><!-- /.site -->


    </div><!-- /.wrapper -->

      <div class="container">
  <div class="site-footer">
    <ul class="site-footer-links right">
      <li><a href="https://status.github.com/">Status</a></li>
      <li><a href="http://developer.github.com">API</a></li>
      <li><a href="http://training.github.com">Training</a></li>
      <li><a href="http://shop.github.com">Shop</a></li>
      <li><a href="/blog">Blog</a></li>
      <li><a href="/about">About</a></li>

    </ul>

    <a href="/">
      <span class="mega-octicon octicon-mark-github" title="GitHub"></span>
    </a>

    <ul class="site-footer-links">
      <li>&copy; 2014 <span title="0.07655s from github-fe120-cp1-prd.iad.github.net">GitHub</span>, Inc.</li>
        <li><a href="/site/terms">Terms</a></li>
        <li><a href="/site/privacy">Privacy</a></li>
        <li><a href="/security">Security</a></li>
        <li><a href="/contact">Contact</a></li>
    </ul>
  </div><!-- /.site-footer -->
</div><!-- /.container -->


    <div class="fullscreen-overlay js-fullscreen-overlay" id="fullscreen_overlay">
  <div class="fullscreen-container js-fullscreen-container">
    <div class="textarea-wrap">
      <textarea name="fullscreen-contents" id="fullscreen-contents" class="fullscreen-contents js-fullscreen-contents" placeholder="" data-suggester="fullscreen_suggester"></textarea>
    </div>
  </div>
  <div class="fullscreen-sidebar">
    <a href="#" class="exit-fullscreen js-exit-fullscreen tooltipped tooltipped-w" aria-label="Exit Zen Mode">
      <span class="mega-octicon octicon-screen-normal"></span>
    </a>
    <a href="#" class="theme-switcher js-theme-switcher tooltipped tooltipped-w"
      aria-label="Switch themes">
      <span class="octicon octicon-color-mode"></span>
    </a>
  </div>
</div>



    <div id="ajax-error-message" class="flash flash-error">
      <span class="octicon octicon-alert"></span>
      <a href="#" class="octicon octicon-x close js-ajax-error-dismiss"></a>
      Something went wrong with that request. Please try again.
    </div>


      <script crossorigin="anonymous" src="https://assets-cdn.github.com/assets/frameworks-5bef6dacd990ce272ec009917ceea0b9d96f84b7.js" type="text/javascript"></script>
      <script async="async" crossorigin="anonymous" src="https://assets-cdn.github.com/assets/github-6dbbecf2d8c6abf888348a708cb91a9775af2a73.js" type="text/javascript"></script>
      
      
  </body>
</html>

