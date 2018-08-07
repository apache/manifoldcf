/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.core.tests;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

public class SeleniumTester
{

  protected WebDriver driver = null;
  protected WebDriverWait wait = null;
  private final long defaultTimeOutInSeconds = 15;

  public enum BrowserType
  {
    CHROME,
    FIREFOX,
    IE
  }

  /**
   * Constructor. Create a test sequence object.
   */
  public SeleniumTester()
  {
  }

  /**
   * Set up for all tests. Basically this grabs the necessary stuff out of resources and writes it
   * to the current directory.
   */
  @Before
  public void setup() throws Exception
  {
    driver = null;
    wait = null;
  }

  public void start(final BrowserType browserType, final String language, final String startURL)
  {
    //Download Chrome Driver for Linux from here (https://chromedriver.storage.googleapis.com/index.html?path=2.28/)
    switch (browserType)
    {
      case CHROME:
        if (System.getProperty("webdriver.chrome.driver") == null ||
          System.getProperty("webdriver.chrome.driver").length() == 0)
          throw new IllegalStateException("Please configure your SL_CHROME_DRIVER environment variable to point to the Selenium Google Chrome Driver");

        //Create a new instance of Chrome driver
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized", "--lang=" + language);
        driver = new ChromeDriver(options);
        break;
      case FIREFOX:
        if(System.getProperty("webdriver.gecko.driver") == null
          || System.getProperty("webdriver.gecko.driver").length() == 0)
          throw new IllegalStateException(
            "Please configure your SL_FIREFOX_DRIVER environment variable to point to the Mozilla Firefox Driver");

        //Create a new instance of Firefox driver
        driver = new FirefoxDriver();
        break;
      case IE:
        if(System.getProperty("webdriver.ie.driver") == null
                || System.getProperty("webdriver.ie.driver").length() == 0)
          throw new IllegalStateException(
                  "Please configure your SL_IE_DRIVER environment variable to point to the Internet Explorer Driver");

        //For more info, on how to configure IE driver, plese read https://github.com/SeleniumHQ/selenium/wiki/InternetExplorerDriver
        driver = new InternetExplorerDriver();
        break;
      default:
        throw new IllegalArgumentException("Unknown browser type");
    }

    wait = new WebDriverWait(driver, defaultTimeOutInSeconds);
    driver.get(startURL);
  }

  public WebDriver getDriver()
  {
    return driver;
  }

  public WebDriverWait getWait()
  {
    return wait;
  }

  public WebElement findElementById(String id)
  {
    return driver.findElement(By.id(id));
  }

  /**
   * Verify that we land in a correct page based on display title
   * @param expected
   */
  public void verifyHeader(String expected)
  {
    WebElement element =
      wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertThat(element.getText(), CoreMatchers.is(CoreMatchers.equalTo(expected)));
  }

  /**
   * Verify that we land in a correct page based on display title substring
   * @param expected
   */
  public void verifyHeaderContains(String expected)
  {
    WebElement element =
      wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertThat(element.getText(), CoreMatchers.containsString(expected));
  }

  /**
   * Verify that we don't land in an error page
   */
  public void verifyThereIsNoError()
  {
    WebElement element =
      wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertNotEquals("Error!", element.getText());
  }

  /**
   * Navigate to a page based on sidebar link alt text
   * @param lintAlt
   */
  public void navigateTo(String lintAlt)
  {
    //Identify the link
    WebElement ele =
      driver.findElement(
        By.cssSelector(".sidebar-menu .treeview-menu a[alt=\"" + lintAlt + "\"]"));

    //Expand the menu group, so that the element gets visible
    String js = "return $(arguments[0]).closest('.treeview').get(0)";
    WebElement parent = (WebElement)((JavascriptExecutor)driver).executeScript(js, ele);
    if (!hasClass(parent, "active"))
    {
      js = "$(arguments[0]).closest('.treeview').find('a:first-child').click();";
      ((JavascriptExecutor)driver).executeScript(js, ele);
      //waitUntilAnimationIsDone(".sidebar-menu .treeview .treeview-menu");
      //Wait for a second for the animation to complete.
      try
      {
        Thread.sleep(2500L);
      }
      catch (InterruptedException e)
      {
        e.printStackTrace();
      }
    }

    //Wait until the menu is link is visible
    wait.until(ExpectedConditions.elementToBeClickable(ele)).click();

    //waitForAjax();
    waitForAjaxAndDocumentReady();
  }

  /**
   * Check if a element is present in DOM
   * @param selector
   * @return true, if the element exists else false
   */
  private boolean exists(By selector)
  {
    return driver.findElements(selector).size() != 0;
  }

  /**
   * Find an element by waiting we find it based on its visibility
   * @param selector
   * @return
   */
  public WebElement waitFindElement(By selector)
  {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
  }

  /**
   * Find an element by waiting until it becomes clickable
   * @param selector
   * @return
   */
  public WebElement waitElementClickable(By selector)
  {
    return wait.until(ExpectedConditions.elementToBeClickable(selector));
  }

  /**
   * Find an element by waiting until we find it's presence in dom
   * @param selector
   * @return
   */
  public WebElement waitUntilPresenceOfElementLocated(By selector)
  {
    return wait.until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  /**
   * Find an element by it's name
   * @param name
   */
  public void waitForElementWithName(String name)
  {
    waitFor(By.name(name));
  }

  public void waitForPresenceById(String id)
  {
    waitForPresence(By.id(id));
  }

  public void waitForPresence(By selector)
  {
    wait.until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  public void waitFor(By selector)
  {
    wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
  }

  /**
   * Click a tab by it's name
   * @param tabName the name of the tab
   */
  public void clickTab(String tabName)
  {
    WebElement element =
      waitElementClickable(By.cssSelector(".nav-tabs li > a[alt=\"" + tabName + " tab\"], .tab-group .btn-group > a[alt=\"" + tabName + " tab\"]"));
    element.click();
    waitForAjaxAndDocumentReady();
  }

  /**
   * Click a button based on title, button created using anchor tag and has title attribute set.
   * @param title
   */
  public void clickButtonByTitle(String title)
  {
    WebElement element =
      waitElementClickable(
        By.xpath(
          "//a[contains(@class,'btn') and contains(@data-original-title,'" + title + "')]"));
    element.click();

    if (!isAlertPresent())
    {
      waitForAjaxAndDocumentReady();
    }
  }

  public void clickButton(String text) throws Exception
  {
    clickButton(text, defaultTimeOutInSeconds);
  }

  /**
   * Clicks a button based on visible text, this type of button is created using anchor tag with
   * .btn class
   * @param text
   */
  public void clickButton(String text, long timeOutInSeconds) throws Exception
  {
    /*WebElement element =
        waitElementClickable(
            By.xpath("//a[contains(@class,'btn') and normalize-space()='" + text + "']"));
    element.click();*/

    boolean found = false;
    List<WebElement> elements = driver.findElements(By.xpath("//a[contains(concat(' ',@class,' '), ' btn ')] | //button[contains(concat(' ',@class,' '), ' btn ')]"));

    for (int i = 0; i < elements.size(); i++)
    {
      WebElement element = elements.get(i);
      System.out.println(getRenderedSource(element));
      wait.until(ExpectedConditions.elementToBeClickable(element));
      String actualText = element.getText();

      if (actualText != null && actualText.length() > 0)
      {
        actualText = actualText.trim();
      }

      if (actualText.equals(text))
      {
        element.click();
        found = true;
        break;
      }
    }

    if (!found)
    {
      throw new Exception("Button not found with text - " + text);
    }

    if (!isAlertPresent())
    {
      waitForAjaxAndDocumentReady(timeOutInSeconds);
    }
  }

  /**
   * Click a button created using <input type="button"/>
   * @param buttonText
   * @param islegacy
   */
  public void clickButton(String buttonText, boolean islegacy) throws Exception
  {
    if (!islegacy)
    {
      clickButton(buttonText);
    }
    else
    {
      waitFindElement(By.cssSelector("[type=\"button\"][value=\"" + buttonText + "\"]")).click();
    }
  }

  /**
   * Click on a radio button with a specific value
   * @param name
   * @param value
   */
  public void clickRadioButton(String name, String value)
  {
    waitElementClickable(By.xpath("//input[@type='radio'][@name='" + name + "'][@value='" + value + "']")).click();
  }

  /**
   * Click a checkbox with the specified name
   * @param name
   */
  public void clickCheckbox(String name)
  {
    waitElementClickable(By.xpath("//input[@type='checkbox'][@name='" + name + "']")).click();
  }

  /**
   * Check if a alert box appeared in the browser.s
   * @return
   */
  public boolean isAlertPresent()
  {
    boolean foundAlert = false;
    WebDriverWait wait = new WebDriverWait(driver, 0 /*timeout in seconds*/);
    try
    {
      wait.until(ExpectedConditions.alertIsPresent());
      foundAlert = true;
    }
    catch (TimeoutException eTO)
    {
      foundAlert = false;
    }
    return foundAlert;
  }

  /**
   * Accepts the alert box
   */
  public void acceptAlert()
  {
    wait.until(ExpectedConditions.alertIsPresent());
    Alert alert = driver.switchTo().alert();
    alert.accept();
  }

  /**
   * Set value of an element with name
   * @param name
   * @param value
   */
  public void setValue(String name, String value)
  {
    setValue(driver, name, value);
  }

  /**
   * Set value of an element with name by searching in another element
   * @param context
   * @param name
   * @param value
   */
  public void setValue(SearchContext context, String name, String value)
  {
    setValue(context, By.name(name), value);
  }

  public void setValue(SearchContext context, By selector, String value)
  {
    WebElement element = context.findElement(selector);
    //Make sure, there is no default text in the input.
    element.clear();
    element.sendKeys(value);
  }

  /**
   * Select value of a custom select box using javascript.
   * @param name
   * @param value
   */
  public void selectValue(String name, String value)
  {
    WebElement element = waitUntilPresenceOfElementLocated(By.name(name));
    System.out.println(element.toString());
    if (hasClass(element, "selectpicker"))
    {
      String js = "$(arguments[0]).selectpicker('val','" + value + "')";
      ((JavascriptExecutor)driver).executeScript(js, element);
    }
    else
    {
      Select select = new Select(element);
      select.selectByValue(value);
    }
  }

  /**
   * Executes javascript in browser
   * @param element
   * @param method
   * @param params
   */
  public void executeJquery(WebElement element, String method, String params)
  {
    String js = "$(arguments[0])." + method + "(" + params + ")";
    System.out.println("JavaScript to be executed: " + js);
    ((JavascriptExecutor)driver).executeScript(js, element);
  }

  /**
   * Check if an element has a class
   * @param element
   * @param className
   * @return
   */
  private boolean hasClass(WebElement element, String className)
  {
    if (element.getAttribute("class") != null)
      return element.getAttribute("class").contains(className);
    return false;
  }

  /**
   * Get the attribute value of an element
   * @param id
   * @param attribute
   * @return
   */
  public String getAttributeValueById(String id, String attribute)
  {
    WebElement element = driver.findElement(By.id(id));
    return element.getAttribute(attribute);
  }

  // Macro operations for job management

  /**
   * Perform an action (Start, Start minimal, Pause, Restart, Restart minimal, Abort) on a specified
   * job (English version).
   * @param jobID
   * @param action
   */
  public void performJobActionEN(String jobID, String action)
  {
    //Navigate to Status and Job management
    navigateTo("Manage jobs");
    waitForElementWithName("liststatuses");

    waitElementClickable(
      By.xpath(
        "//tr[@job-id="
          + jobID
          + "]//a[contains(@class,'btn') and text()='"
          + action
          + "']"))
      .click();
  }

  /**
   * Wait until the status of an job become as mentioned (English version)
   * @param jobID         is the jobID
   * @param jobStatus     is the desired job status (e.g. 'Done')
   * @param timeoutAmount is the maximum time until the status is expected
   * @throws Exception
   */
  public void waitForJobStatusEN(final String jobID, final String jobStatus, final int timeoutAmount) throws Exception
  {
    waitForJobStatus(jobID, jobStatus, timeoutAmount, "Manage jobs", "liststatuses", "Refresh");
  }

  /**
   * Wait until the status of an job become as mentioned (generic version)
   * @param jobID               is the jobID
   * @param jobStatus           is the desired job status (e.g. 'Done')
   * @param timeoutAmount       is the maximum time until the status is expected
   * @param manageJobsPage      is the 'manage jobs' page
   * @param listStatusesElement is the 'list statuses' element
   * @param refreshButton       is the 'Refresh" button
   * @throws Exception
   */
  public void waitForJobStatus(final String jobID, final String jobStatus, int timeoutAmount, final String manageJobsPage, final String listStatusesElement, final String refreshButton)
    throws Exception
  {
    //Navigate to Status and Job management
    navigateTo(manageJobsPage);
    waitForElementWithName(listStatusesElement);

    while (true)
    {
      if (!exists(By.xpath("//tr[@job-id='" + jobID + "']")))
      {
        throw new Exception("Job " + jobID + " not found");
      }
      if (exists(By.xpath("//tr[@job-id='" + jobID + "' and @job-status-name='" + jobStatus + "']")))
      {
        break;
      }
      if (timeoutAmount == 0)
      {
        throw new Exception("Timed out waiting for job " + jobID + " to acheive status '" + jobStatus + "'");
      }
      clickButton(refreshButton);
      waitForElementWithName(listStatusesElement);
      //Let us wait for a second.
      Thread.sleep(1000L);
      timeoutAmount--;
    }
  }

  /**
   * Obtain a given job's status (English version).
   * @param jobID is the job ID.
   * @return the job status, if found,
   */
  public String getJobStatusEN(final String jobID) throws Exception
  {
    return getJobStatus(jobID, "Manage jobs", "liststatuses");
  }

  /**
   * Obtain a given job's status (generic version).
   * @param jobID               is the job ID.
   * @param manageJobsPage      is the 'manage jobs' page
   * @param listStatusesElement is the 'list statuses' element
   * @return the job status, if found,
   */
  public String getJobStatus(final String jobID, final String manageJobsPage, final String listStatusesElement)
    throws Exception
  {
    //Navigate to Status and Job management
    navigateTo(manageJobsPage);
    waitForElementWithName(listStatusesElement);

    final WebElement element = driver.findElement(By.xpath("//tr[@job-id=" + jobID + "]"));
    if (element == null)
    {
      throw new Exception("Can't find job " + jobID);
    }
    return element.getAttribute("job-status-name");
  }

  /**
   * Wait for a specified job to go away after being deleted (English version).
   * @param jobID
   * @param timeoutAmount
   * @throws Exception
   */
  public void waitForJobDeleteEN(final String jobID, int timeoutAmount) throws Exception
  {
    waitForJobDelete(jobID, timeoutAmount, "Manage jobs", "liststatuses", "Refresh");
  }

  /**
   * Wait for a specified job to go away after being deleted (generic version).
   * @param jobID
   * @param timeoutAmount
   * @param manageJobsPage      is the 'manage jobs' page
   * @param listStatusesElement is the 'list statuses' element
   * @param refreshButton       is the 'Refresh" button
   * @throws Exception
   */
  public void waitForJobDelete(final String jobID, int timeoutAmount, final String manageJobsPage, final String listStatusesElement, final String refreshButton)
    throws Exception
  {
    navigateTo(manageJobsPage);
    waitForElementWithName(listStatusesElement);
    while (exists(By.xpath("//tr[@job-id=\"" + jobID + "\"]")))
    {
      if (timeoutAmount == 0)
      {
        throw new Exception("Timed out waiting for job " + jobID + " to go away");
      }
      clickButton(refreshButton);
      waitForElementWithName(listStatusesElement);
      //Let us wait for a second.
      Thread.sleep(1000L);
      timeoutAmount--;
    }
  }

  public boolean waitForAjaxAndDocumentReady()
  {
    return waitForAjaxAndDocumentReady(defaultTimeOutInSeconds);
  }

  public boolean waitForAjaxAndDocumentReady(long timeOutInSeconds)
  {
    WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);

    // wait for jQuery to load
    ExpectedCondition<Boolean> jQueryLoad =
      new ExpectedCondition<Boolean>()
      {
        @Override
        public Boolean apply(WebDriver driver)
        {
          try
          {
            return ((Long)
              ((JavascriptExecutor)getDriver()).executeScript("return jQuery.active")
              == 0);
          }
          catch (Exception e)
          {
            // no jQuery present
            return true;
          }
        }
      };

    // wait for Javascript to load
    ExpectedCondition<Boolean> jsLoad =
      new ExpectedCondition<Boolean>()
      {
        @Override
        public Boolean apply(WebDriver driver)
        {
          return ((JavascriptExecutor)getDriver())
            .executeScript("return document.readyState")
            .toString()
            .equals("complete");
        }
      };

    return wait.until(jQueryLoad) && wait.until(jsLoad);
  }

  public void waitUntilAnimationIsDone(final String selector)
  {
    waitUntilAnimationIsDone(selector, defaultTimeOutInSeconds);
  }

  public void waitUntilAnimationIsDone(final String selector, final long timeOutInSeconds)
  {
    WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);
    ExpectedCondition<Boolean> expectation =
      new ExpectedCondition<Boolean>()
      {
        @Override
        public Boolean apply(WebDriver driver)
        {
          String temp =
            ((JavascriptExecutor)driver)
              .executeScript("return jQuery('" + selector + "').is(':animated')")
              .toString();
          return temp.equalsIgnoreCase("false");
        }
      };

    try
    {
      wait.until(expectation);
    }
    catch (TimeoutException e)
    {
      throw new AssertionError("Element animation is not finished in time. selector: " + selector);
    }
  }

  /**
   * Get the source of the html document
   * @return
   */
  public String getRenderedSource()
  {
    return getRenderedSource(By.tagName("html"));
  }

  /**
   * Get the source of an element by find it in DOM
   * @param selector
   * @return
   */
  public String getRenderedSource(By selector)
  {
    return getRenderedSource(driver.findElement(selector));
  }

  /**
   * Get the source of an element
   * @param element
   * @return
   */
  public String getRenderedSource(WebElement element)
  {
    return (String)
      ((JavascriptExecutor)driver).executeScript("return arguments[0].innerHTML", element);
  }

  private long tick()
  {
    long TICKS_AT_EPOCH = 621355968000000000L;
    return System.currentTimeMillis() * 10000 + TICKS_AT_EPOCH;
  }

  /**
   * Clean up the files we created.
   */
  @After
  public void teardown() throws Exception
  {
    if (driver != null)
    {
      driver.close();
      driver.quit();
      driver = null;
      wait = null;
    }
  }
}
