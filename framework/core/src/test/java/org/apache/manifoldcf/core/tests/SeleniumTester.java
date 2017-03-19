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

import org.apache.commons.io.FileUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;

public class SeleniumTester {

  protected WebDriver driver = null;
  protected WebDriverWait wait = null;

  public enum BrowserType {
    CHROME
  };
  
  /** Constructor. Create a test sequence object. */
  public SeleniumTester() {}

  /**
   * Set up for all tests. Basically this grabs the necessary stuff out of resources and writes it
   * to the current directory.
   */
  @Before
  public void setup() throws Exception {
    driver = null;
    wait = null;
  }

  public void start(final BrowserType browserType, final String language, final String startURL) {
    //Download Chrome Driver for Linux from here (https://chromedriver.storage.googleapis.com/index.html?path=2.28/)
    switch (browserType) {
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
  
  public WebDriver getDriver() {
    return driver;
  }

  public WebDriverWait getWait() {
    return wait;
  }

  public WebElement findElementById(String id) {
    return driver.findElement(By.id(id));
  }

  /*
  Verify that we land in a correct page based on display title.s
   */
  public void verifyHeader(String expected) {
    WebDriverWait wait = new WebDriverWait(driver, 1);
    WebElement element =
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertThat(element.getText(), CoreMatchers.is(CoreMatchers.equalTo(expected)));
  }

  public void verifyHeaderContains(String expected) {
    WebDriverWait wait = new WebDriverWait(driver, 1);
    WebElement element =
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("heading")));

    Assert.assertThat(element.getText(), CoreMatchers.containsString(expected));
  }

  public void gotoUrl(String url) {
    driver.get(url);
  }

  /*
  Click links from navigation sidebar.
   */
  public void navigateTo(String lintAlt) {
    //Identify the link
    WebElement ele =
        driver.findElement(
            By.cssSelector(".sidebar-menu .treeview-menu a[alt=\"" + lintAlt + "\"]"));

    //Expand the menu group, so that the element gets visible
    WebElement parent =
        ele.findElement(
            By.xpath("./ancestor::li[contains(concat(' ', @class, ' '), ' treeview ')][1]"));
    parent.click();

    if (!hasClass(parent, "active")) {
      executeJquery(parent, "addClass", "'active'");
    }

    //Wait until the menu is link is visible
    wait.until(ExpectedConditions.visibilityOf(ele)).click();

    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("#loader")));
  }

  /*
  Check if a element is present in DOM
   */
  public boolean exists(By selector) {
    return driver.findElements(selector).size() != 0;
  }

  public WebElement waitFindElement(By selector) {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
  }

  public WebElement waitElementClickable(By selector) {
    return wait.until(ExpectedConditions.elementToBeClickable(selector));
  }

  public WebElement waitUntilPresenceOfElementLocated(By selector) {
    return wait.until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  public void waitForElementWithName(String name) {
    waitFor(By.name(name));
  }
  
  public void waitFor(By selector) {
    wait.until(ExpectedConditions.elementToBeClickable(selector));
  }

  /*
  Click a tab
   */
  public void clickTab(String tabName) {
    WebElement element =
        waitElementClickable(By.cssSelector("a[data-toggle=\"tab\"][alt=\"" + tabName + " tab\"]"));
    element.click();
    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
  }

  /*
  Click a button based on title, button created using anchor tag and has title attribute set.
   */
  public void clickButtonByTitle(String title) {
    WebElement element =
        waitElementClickable(
            By.xpath(
                "//a[contains(@class,'btn') and contains(@data-original-title,'" + title + "')]"));
    element.click();

    if (!isAlertPresent()) {
      wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
    }
  }

  /*
  Clicks a button based on visible text, this type of button is created using anchor tag with .btn class
  */
  public void clickButton(String text) {
    WebElement element =
        waitElementClickable(
            By.xpath("//a[contains(@class,'btn') and contains(text(),'" + text + "')]"));
    element.click();

    if (!isAlertPresent()) {
      wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loader")));
    }
  }

  /*
  Click a button created using <input type="button"/>
   */
  public void clickButton(String buttonText, boolean islegacy) {
    if (!islegacy) {
      clickButton(buttonText);
    } else {
      waitFindElement(By.cssSelector("[type=\"button\"][value=\"" + buttonText + "\"]")).click();
    }
  }

  public boolean isAlertPresent() {
    boolean foundAlert = false;
    WebDriverWait wait = new WebDriverWait(driver, 0 /*timeout in seconds*/);
    try {
      wait.until(ExpectedConditions.alertIsPresent());
      foundAlert = true;
    } catch (TimeoutException eTO) {
      foundAlert = false;
    }
    return foundAlert;
  }

  public void acceptAlert() {
    wait.until(ExpectedConditions.alertIsPresent());
    Alert alert = driver.switchTo().alert();
    alert.accept();
  }

  public void setValue(String name, String value) {
    setValue(driver, name, value);
  }

  public void setValue(SearchContext context, String name, String value) {
    setValue(context, By.name(name), value);
  }

  public void setValue(SearchContext context, By selector, String value) {
    context.findElement(selector).sendKeys(value);
  }

  public void selectValue(String name, String value) {
    WebElement element = waitUntilPresenceOfElementLocated(By.name(name));
    System.out.println(element.toString());
    if (hasClass(element, "selectpicker")) {
      String js = "$(arguments[0]).selectpicker('val','" + value + "')";
      ((JavascriptExecutor) driver).executeScript(js, element);
    } else {
      Select select = new Select(element);
      select.selectByValue(value);
    }
  }

  public void executeJquery(WebElement element, String method, String params) {
    String js = "$(arguments[0])." + method + "(" + params + ")";
    System.out.println("JavaScript to be executed: " + js);
    ((JavascriptExecutor) driver).executeScript(js, element);
  }

  private boolean hasClass(WebElement element, String className) {
    if (element.getAttribute("class") != null)
      return element.getAttribute("class").contains(className);
    return false;
  }

  public String getAttributeValueById(String id, String attribute) {
    WebElement element = driver.findElement(By.id(id));
    return element.getAttribute(attribute);
  }

  //The below method will save the screenshot
  public void getscreenshot() {
    File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    try {
      FileUtils.copyFile(
          scrFile, new File("/home/kishore/temp/selenium-screenshots/" + tick() + ".png"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String getRenderedSource() {
    return getRenderedSource(By.tagName("html"));
  }

  public String getRenderedSource(By selector) {
    return getRenderedSource(driver.findElement(selector));
  }

  public String getRenderedSource(WebElement element) {
    return (String)
        ((JavascriptExecutor) driver).executeScript("return arguments[0].innerHTML", element);
  }

  private long tick() {
    long TICKS_AT_EPOCH = 621355968000000000L;
    return System.currentTimeMillis() * 10000 + TICKS_AT_EPOCH;
  }

  /** Clean up the files we created. */
  @After
  public void teardown() throws Exception {
    if (driver != null) {
      driver.close();
      driver.quit();
      driver = null;
      wait = null;
    }
  }
}