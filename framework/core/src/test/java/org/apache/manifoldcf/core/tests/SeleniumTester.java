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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SeleniumTester
{

  protected WebDriver driver = null;
  protected WebDriverWait wait = null;

  public enum BrowserType
  {
    CHROME
  };
  
  /** Constructor. Create a test sequence object. */
  public SeleniumTester() {}

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
      default:
        throw new IllegalArgumentException("Unknown browser type");
    }

    wait = new WebDriverWait(driver, 10);
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
    WebDriverWait wait = new WebDriverWait(driver, 1);
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
    WebDriverWait wait = new WebDriverWait(driver, 1);
    WebElement element =
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertThat(element.getText(), CoreMatchers.containsString(expected));
  }

  /**
   * Verify that we don't land in an error page
   */
  public void verifyThereIsNoError()
  {
    WebDriverWait wait = new WebDriverWait(driver,1);
    WebElement element =
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertNotEquals("Error!",element.getText());
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
    WebElement parent =
        ele.findElement(
            By.xpath("./ancestor::li[contains(concat(' ', @class, ' '), ' treeview ')][1]"));
    parent.click();

    if (!hasClass(parent, "active"))
    {
      executeJquery(parent, "addClass", "'active'");
    }

    //Wait until the menu is link is visible
    wait.until(ExpectedConditions.visibilityOf(ele)).click();

    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("#loader")));
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
    waitFor(By.id(id));
  }
  
  public void waitFor(By selector)
  {
    wait.until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  /**
   * Click a tab by it's name
   * @param tabName the name of the tab
   */
  public void clickTab(String tabName)
  {
    WebElement element =
        waitElementClickable(By.cssSelector("a[data-toggle=\"tab\"][alt=\"" + tabName + " tab\"]"));
    element.click();
    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
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
      wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
    }
  }

  /**
   * Clicks a button based on visible text, this type of button is created using anchor tag with .btn class
   * @param text
   */
  public void clickButton(String text)
  {
    WebElement element =
        waitElementClickable(
            By.xpath("//a[contains(@class,'btn') and contains(text(),'" + text + "')]"));
    element.click();

    if (!isAlertPresent())
    {
      wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
    }
  }

  /**
   * Click a button created using <input type="button"/>
   * @param buttonText
   * @param islegacy
   */
  public void clickButton(String buttonText, boolean islegacy)
  {
    if (!islegacy)
    {
      clickButton(buttonText);
    } else
    {
      waitFindElement(By.cssSelector("[type=\"button\"][value=\"" + buttonText + "\"]")).click();
    }
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
    } catch (TimeoutException eTO)
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
    context.findElement(selector).sendKeys(value);
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
      ((JavascriptExecutor) driver).executeScript(js, element);
    } else
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
    ((JavascriptExecutor) driver).executeScript(js, element);
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
   * Perform an action (Start, Start minimal, Pause, Restart, Restart minimal, Abort) on a specified job.
   * @param jobID
   * @param action
   */
  public void performJobAction(String jobID, String action)
  {
    //Navigate to Status and Job management
    navigateTo("Manage jobs");
    waitForElementWithName("liststatuses");

    waitElementClickable(By.xpath("//tr[@job-id=" + jobID + "]//a[contains(@class,'btn') and text()='" + action + "']")).click();
  }
  
  /**
   * Wait until the status of an job become as mentioned
   * @param jobID
   * @param jobStatus
   * @param timeoutAmount
   * @throws Exception
   */
  public String waitForJobStatus(String jobID, String jobStatus, int timeoutAmount) throws Exception
  {
    //Navigate to Status and Job management
    navigateTo("Manage jobs");
    waitForElementWithName("liststatuses");

    while (true)
    {
      if (!exists(By.xpath("//tr[@job-id='" + jobID + "']")))
      {
        throw new Exception("Job "+jobID+" not found");
      }
      if (exists(By.xpath("//tr[@job-id='" + jobID + "' and @job-status-name='" + jobStatus + "']")))
      {
        break;
      }
      if (timeoutAmount == 0)
      {
        throw new Exception("Timed out waiting for job " + jobID + " to acheive status '" + jobStatus + "'");
      }
      clickButton("Refresh");
      waitForElementWithName("liststatuses");
      //Let us wait for a second.
      Thread.sleep(1000L);
      timeoutAmount--;
    }
    return getJobStatus(jobID);
  }

  public String getJobStatus(String jobID)
  {
    WebElement element = driver.findElement(By.xpath("//tr[@job-id="+jobID+"]"));
    return element.getAttribute("job-status-name");
  }

  /**
   * Wait for a specified job to go away after being deleted.
   * @param jobID
   * @param timeoutAmount
   * @throws Exception
   */
  public void waitForJobDelete(final String jobID, int timeoutAmount) throws Exception
  {
    navigateTo("Manage jobs");
    waitForElementWithName("liststatuses");
    while (exists(By.xpath("//tr[@job-id=\"" + jobID + "\"]")))
    {
      if (timeoutAmount == 0)
      {
        throw new Exception("Timed out waiting for job "+jobID+" to go away");
      }
      clickButton("Refresh");
      waitForElementWithName("liststatuses");
      //Let us wait for a second.
      Thread.sleep(1000L);
      timeoutAmount--;
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
        ((JavascriptExecutor) driver).executeScript("return arguments[0].innerHTML", element);
  }
  
  private long tick()
  {
    long TICKS_AT_EPOCH = 621355968000000000L;
    return System.currentTimeMillis() * 10000 + TICKS_AT_EPOCH;
  }

  /** Clean up the files we created. */
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
