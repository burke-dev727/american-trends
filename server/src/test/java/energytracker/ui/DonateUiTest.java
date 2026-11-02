package energytracker.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Clicks the running site in Chrome. Not part of the regular test task. */
@Tag("ui")
class DonateUiTest {

    private WebDriver driver;

    @BeforeEach
    void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("ui.headless", "false"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1280,900");
        driver = new ChromeDriver(options);
    }

    @AfterEach
    void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void selectedAmountIsSentToPayPal() {
        String baseUrl = System.getProperty("ui.baseUrl", "http://localhost:5173");
        driver.get(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".donate-launcher"))).click();

        WebElement amounts = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".donate-amounts")));
        assertTrue(amounts.getText().contains("$5"));
        assertTrue(amounts.getText().contains("$10"));
        assertTrue(amounts.getText().contains("$25"));

        WebElement link = driver.findElement(By.cssSelector("a.donate-paypal"));
        amounts.findElement(By.xpath(".//button[normalize-space()='$25']")).click();
        wait.until(ExpectedConditions.attributeContains(link, "href", "amount=25"));

        String href = link.getAttribute("href");
        assertTrue(href.startsWith("https://www.paypal.com/donate/"), href);
        assertTrue(href.contains("amount=25"), href);
    }
}
