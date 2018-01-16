/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

//Make sure jQuery has been loaded before app.js
if (typeof jQuery === "undefined")
{
  throw new Error("ManifoldCF requires jQuery");
}

/* ManifoldCF
 *
 * @type Object
 * @description $.ManifoldCF is the main object for the template's app.
 *              It's used for implementing functions and options related
 *              to the template. Keeping everything wrapped in an object
 *              prevents conflict with other plugins and is a better
 *              way to organize our code.
 */
$.ManifoldCF={};

/* --------------------
 * - ManifoldCF Options -
 * --------------------
 * Modify these options to suit your implementation
 */
$.ManifoldCF.options={
  //Sidebar push menu toggle button selector
  sidebarToggleSelector: "[data-toggle='offcanvas']",
  //Activate sidebar slimscroll if the fixed layout is set (requires SlimScroll Plugin)
  sidebarSlimScroll: true,
  BSTooltipSelector: '[data-toggle="tooltip"]',
  //The standard screen sizes that bootstrap uses.
  //If you change these in the variables.less file, change
  //them here too.
  screenSizes: {
    xs: 480,
    sm: 768,
    md: 992,
    lg: 1200
  }
};

/* ------------------
 * - Implementation -
 * ------------------
 * The next block of code implements ManifoldCF's
 * functions and plugins as specified by the
 * options above.
 */
$(function ()
{
  //Easy access to options
  var o=$.ManifoldCF.options;

  //Set up the object
  _init();

  //Activate the layout maker
  $.ManifoldCF.layout.activate();

  //Enable sidebar tree view controls
  $.ManifoldCF.tree('.sidebar');

  //Activate sidebar push menu
  $.ManifoldCF.pushMenu.activate(o.sidebarToggleSelector);

  //Activate Bootstrap tooltip
  $(o.BSTooltipSelector).tooltip({
    trigger: 'hover',
    container: 'body'
  });

  /*
   * INITIALIZE BUTTON TOGGLE
   * ------------------------
   */
  $('.btn-group[data-toggle="btn-toggle"]').each(function ()
  {
    var group=$(this);
    $(this).find(".btn").click(function (e)
    {
      group.find(".btn.active").removeClass("active");
      $(this).addClass("active");
      e.preventDefault();
    });

  });
});

/* ----------------------------------
 * - Initialize the ManifoldCF Object -
 * ----------------------------------
 * All ManifoldCF functions are implemented below.
 */
function _init()
{

  /* Layout
   * ======
   * Fixes the layout height in case min-height fails.
   *
   * @type Object
   * @usage $.ManifoldCF.layout.activate()
   *        $.ManifoldCF.layout.fix()
   *        $.ManifoldCF.layout.fixSidebar()
   */
  $.ManifoldCF.layout={
    activate: function ()
    {
      var _this=this;
      _this.fix();
      _this.fixSidebar();
      $(window,".wrapper").resize(function ()
      {
        _this.fix();
        _this.fixSidebar();
      });
    },
    fix: function ()
    {
      //Get window height and the wrapper height
      var neg=$('.main-header').outerHeight() + $('.main-footer').outerHeight();
      var window_height=$(window).height();
      var sidebar_height=$(".sidebar").height();
      //Set the min-height of the content and sidebar based on the
      //the height of the document.
      if ($("body").hasClass("fixed"))
      {
        $(".content-wrapper, .right-side").css('min-height',window_height - $('.main-footer').outerHeight());
      } else
      {
        var postSetWidth;
        if (window_height >= sidebar_height)
        {
          $(".content-wrapper, .right-side").css('min-height',window_height - neg);
          postSetWidth=window_height - neg;
        } else
        {
          $(".content-wrapper, .right-side").css('min-height',sidebar_height);
          postSetWidth=sidebar_height;
        }
      }
      $('.main-footer').show();
    },
    fixSidebar: function ()
    {
      //Make sure the body tag has the .fixed class
      if (!$("body").hasClass("fixed"))
      {
        if (typeof $.fn.slimScroll != 'undefined')
        {
          $(".sidebar").slimScroll({destroy: true}).height("auto");
        }
        return;
      } else if (typeof $.fn.slimScroll == 'undefined' && console)
      {
        console.error("Error: the fixed layout requires the slimscroll plugin!");
      }
      //Enable slimscroll for fixed layout
      if ($.ManifoldCF.options.sidebarSlimScroll)
      {
        if (typeof $.fn.slimScroll != 'undefined')
        {
          //Destroy if it exists
          $(".sidebar").slimScroll({destroy: true}).height("auto");
          //Add slimscroll
          $(".sidebar").slimscroll({
            height: ($(window).height() - $(".main-header").height()) + "px",
            color: "rgba(255,255,255,0.8)",
            size: "5px"
          });
        }
      }
    }
  };

  /* PushMenu()
   * ==========
   * Adds the push menu functionality to the sidebar.
   *
   * @type Function
   * @usage: $.ManifoldCF.pushMenu("[data-toggle='offcanvas']")
   */
  $.ManifoldCF.pushMenu={
    activate: function (toggleBtn)
    {
      //Get the screen sizes
      var screenSizes=$.ManifoldCF.options.screenSizes;

      //Enable sidebar toggle
      $(toggleBtn).on('click',function (e)
      {
        e.preventDefault();

        //Enable sidebar push menu
        if ($(window).width() > (screenSizes.sm - 1))
        {
          $("body").toggleClass('sidebar-collapse');
        }
        //Handle sidebar push menu for small screens
        else
        {
          if ($("body").hasClass('sidebar-open'))
          {
            $("body").removeClass('sidebar-open');
            $("body").removeClass('sidebar-collapse')
          } else
          {
            $("body").addClass('sidebar-open');
          }
        }
      });

      $(".content-wrapper").click(function ()
      {
        //Enable hide menu when clicking on the content-wrapper on small screens
        if ($(window).width() <= (screenSizes.sm - 1) && $("body").hasClass("sidebar-open"))
        {
          $("body").removeClass('sidebar-open');
        }
      });

      //Enable expand on hover for sidebar mini
      if ($.ManifoldCF.options.sidebarExpandOnHover
          || ($('body').hasClass('fixed')
          && $('body').hasClass('sidebar-mini')))
      {
        this.expandOnHover();
      }

    },
    expandOnHover: function ()
    {
      var _this=this;
      var screenWidth=$.ManifoldCF.options.screenSizes.sm - 1;
      //Expand sidebar on hover
      $('.main-sidebar').hover(function ()
      {
        if ($('body').hasClass('sidebar-mini')
            && $("body").hasClass('sidebar-collapse')
            && $(window).width() > screenWidth)
        {
          _this.expand();
        }
      },function ()
      {
        if ($('body').hasClass('sidebar-mini')
            && $('body').hasClass('sidebar-expanded-on-hover')
            && $(window).width() > screenWidth)
        {
          _this.collapse();
        }
      });
    },
    expand: function ()
    {
      $("body").removeClass('sidebar-collapse').addClass('sidebar-expanded-on-hover');
    },
    collapse: function ()
    {
      if ($('body').hasClass('sidebar-expanded-on-hover'))
      {
        $('body').removeClass('sidebar-expanded-on-hover').addClass('sidebar-collapse');
      }
    }
  };

  /* Tree()
   * ======
   * Converts the sidebar into a multilevel
   * tree view menu.
   *
   * @type Function
   * @Usage: $.ManifoldCF.tree('.sidebar')
   */
  $.ManifoldCF.tree=function (menu)
  {
    var _this=this;

    $("li a",$(menu)).on('click',function (e)
    {
      //Get the clicked link and the next element
      var $this=$(this);
      var checkElement=$this.next();

      //Check if the next element is a menu and is visible
      if ((checkElement.is('.treeview-menu')) && (checkElement.is(':visible')))
      {
        //Close the menu
        checkElement.slideUp('normal',function ()
        {
          checkElement.removeClass('menu-open');
          //Fix the layout in case the sidebar stretches over the height of the window
          //_this.layout.fix();
        });
        checkElement.parent("li").removeClass("active");
      }
      //If the menu is not visible
      else if ((checkElement.is('.treeview-menu')) && (!checkElement.is(':visible')))
      {
        //Get the parent menu
        var parent=$this.parents('ul').first();
        //Close all open menus within the parent
        var ul=parent.find('ul:visible').slideUp('normal');
        //Remove the menu-open class from the parent
        ul.removeClass('menu-open');
        //Get the parent li
        var parent_li=$this.parent("li");

        //Open the target menu and add the menu-open class
        checkElement.slideDown('normal',function ()
        {
          //Add the class active to the parent li
          checkElement.addClass('menu-open');
          parent.find('li.active').removeClass('active');
          parent_li.addClass('active');
          //Fix the layout in case the sidebar stretches over the height of the window
          _this.layout.fix();
        });
      }
      //if this isn't a link, prevent the page from being redirected
      if (checkElement.is('.treeview-menu'))
      {
        e.preventDefault();
      }
    });
  };
}

$.ManifoldCF.setTitle=function (title,header,activeMenu)
{
  document.title=title;

  $(".content-header #heading").text(header);

  activeMenu=typeof activeMenu !== 'undefined'?activeMenu:'outputs';
  $("." + activeMenu).addClass("active");

  $(".selectpicker").selectpicker({iconBase:'fa',tickIcon:'fa-check'});
};

function displayError(xhr)
{
  $(".content-header #heading").text('Error!');
  document.title='Error';
  var msg = xhr.status + " " + xhr.statusText;
  //Proper error message, if the server is down.
  if(xhr.status === 0)
  {
    msg = MCFError.ServerDown;
  }
  else if(xhr.status === 500)
  {
    msg = MCFError.InternalServerError;
  }
  var errorTemplate = '<div class="box box-solid">' +
                        '<div class="box-body">' +
                          '<div class="alert alert-danger">' +
                            '<h3><i class="fa fa-ban fa-fw"></i> Error!</h3>' +
                            '<h4>' + msg +'</h4>' +
                          '</div>' +
                        '</div>' +
                        '<div class="box-footer with-border">' +
                          '<div class="btn-group">' +
                            '<a class="btn btn-default" href="index.jsp" title="Return" data-toggle="tooltip">' +
                              '<i class="fa fa-check fa-fw" aria-hidden="true"></i>OK' +
                            '</a>';
  if(xhr.status === 500)
  {
    errorTemplate +=        '<button type="button" class="btn btn-default" data-toggle="modal" data-target="#exceptionModal">' +
                              '<i class="fa fa-exclamation-triangle fa-fw" aria-hidden="true"></i>See Exception</button>';
    $('#exceptionModal .modal-body').empty();
    $('#exceptionModal .modal-body').html(xhr.responseText);
  }
  errorTemplate +=        '</div>' +
                        '</div>' +
                      '</div>';
  $("#content").html(errorTemplate);
}

function _endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function _preLoadContent()
{
  console.log('_preLoadContent');
  $($.ManifoldCF.options.BSTooltipSelector).tooltip('destroy');

  //Temporary fix for Javascript bug, when loading connector javascript.
  //Get all the global methods
  var methods = Object.keys(window);
  for(var i=0; i<=methods.length; i++){
    var method = methods[i];
      if (method && (_endsWith(method, 'checkConfig') ||
          _endsWith(method, 'checkConfigForSave') ||
          _endsWith(method, 'checkSpecification') ||
          _endsWith(method, 'checkSpecificationForSave'))) {
          delete window[method];
      }
  }
}

function _postLoadContent()
{
  console.log('_postLoadContent');
  //Activate Bootstrap tooltip
  $($.ManifoldCF.options.BSTooltipSelector).tooltip({
    trigger: 'hover',
    container: 'body'
  });
}

$.ManifoldCF.loadContent=function (url)
{
  $('.overlay,#loader').show();
  console.log("URL: " + url);
  _preLoadContent();

  //Add Client Timezone Offset
  var timezone_offset = new Date().getTimezoneOffset();
  var timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  $('#content').load(decodeURIComponent(url), { client_timezone_offset : timezone_offset, client_timezone : timezone } ,function (response,status,xhr)
  {
    if (status == 'error')
    {
      displayError(xhr);
    }
    _postLoadContent();
    $('.overlay,#loader').hide();
  });
};

$.ManifoldCF.submit=function (form)
{
  $('.overlay,#loader').show();

  var formData = new FormData(form),
      $form = $(form),
      action = $form.attr('action'),
      method = $form.attr('method');

  _preLoadContent();

  //Add Client Timezone Offset
  var timezone_offset = new Date().getTimezoneOffset();
  var timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  formData.append('client_timezone_offset',timezone_offset);
  formData.append('client_timezone',timezone);

  $.ajax({
    type: method,
    url: action,
    data: formData,
    cache: false,
    contentType: false,
    processData: false
  }).done(function (data,textStatus,jqXHR)
  {
    var page=jqXHR.getResponseHeader("page");
    console.log("page: " + page)
    if (typeof page != 'undefined')
    {
      window.history.replaceState({urlPath: encodeURI(page)},null,'?p=' + page + '#execute');
    }
    else
    {
      window.history.replaceState({urlPath: encodeURI(action)},null,'#execute_' + form.name);
    }
    console.log("textStatus: " + textStatus);
    $('#content').html(data);
  }).fail(function (xhr,opts,error)
  {
    displayError(xhr);
  }).always(function ()
  {
    _postLoadContent();
    $('.overlay,#loader').hide();
  });
}

window.onpopstate = function (event) {
  console.log('historyEvent:',event);
  if (event.state) {
    // history changed because of pushState/replaceState
    var state=window.history.state;
    if (typeof  state != 'undefined')
    {
      if (!state.urlPath.startsWith('execute'))
      {
        $.ManifoldCF.loadContent(state.urlPath);
      }
    }
  }
  else
  {
    console.log('history changed because of a page load');
  }
}

$(function(){
  // navigation link handler
  $(document.body).on("click",'.link',function (e)
  {
    e.preventDefault();
    var urlPath=$(this).attr('href');
    var title=$(this).text();

    $.ManifoldCF.loadContent(urlPath);

    var data = {urlPath: encodeURIComponent(urlPath)};
    window.history.pushState(data,title,'?p=' + encodeURIComponent(urlPath) + '&_' + new Date().getTime());
  });

  //Prevent from submit on Enter
  $(window).keydown(function(e){
    var key = e.charCode || e.keyCode || 0;
    var node = (e.target) ? e.target : ((e.srcElement) ? e.srcElement : null);
    if(key == 13 && node.nodeName !== "TEXTAREA") {
      console.log("Enter on input fields is disabled.");
      e.preventDefault();
      return false;
    }
  });

});
