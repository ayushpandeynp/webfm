# Author: Ayush Pandey (ayush.pandey@nyu.edu)
# This class manages the queue of screenshots to be taken using Selenium and ChromeDriver.
# We hardcode a specific chromedriver because during deployment at places with limited bandwidth, default chromedriver might auto update. Avoiding this is recommended.

from seleniumwire import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from PIL import Image, ImageFile
from utils import *
import time
import os
import json
from config import *
from logger import setupLogger
from Cache import Cache
import sqlite3

Image.ANTIALIAS = Image.LANCZOS
ImageFile.LOAD_TRUNCATED_IMAGES = True


class ScreenshotQueue:
    def __init__(self, manager) -> None:
        self.logger = setupLogger("ScreenshotQueue")
        self.queue = []

        os.makedirs("screenshots", exist_ok=True)

        self.cache = Cache()

        self.scrollQuota = 5

        self.db = sqlite3.connect("sonic.db", check_same_thread=False)

        self.manager = manager

        self.internalPages = []

    def setupDriver(self):
        mobile_emulation = {
            "deviceName": "iPhone 4"
        }

        chrome_options = webdriver.ChromeOptions()
        chrome_options.add_argument('ignore-certificate-errors')
        chrome_options.add_argument("--disable-notifications")
        chrome_options.add_argument("--disable-popup-window")
        chrome_options.add_experimental_option(
            "mobileEmulation", mobile_emulation)

        driver = webdriver.Chrome(options=chrome_options)
        driver.set_window_size(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)

        self.driver = driver

    def put(self, url, idle) -> str:
        filename = self.cache.getFilename(url)

        if filename:
            return filename, True

        filename = randomFileName(10)

        self.queue.append({
            'url': url,
            'filename': filename
        })

        self.cache.put(url, filename)

        return filename, False

    def getToday(self):
        return time.strftime("%Y-%m-%d")

    def size(self):
        return len(self.queue)

    def clear(self):
        self.queue = []

    def processScreenshots(self):
        while True:
            if self.queue != []:
                self.setupDriver()

                item = self.queue.pop(0)
                url = item['url']
                filename = item['filename'] + ".png"

                try:
                    driver = self.driver

                    driver.get(url)

                    shScript = ('return Math.max(document.body.scrollHeight, '
                                'document.documentElement.scrollHeight, '
                                'document.body.offsetHeight, '
                                'document.documentElement.offsetHeight, '
                                'document.documentElement.clientHeight);')
                    ihScript = 'return window.innerHeight'
                    iwScript = 'return window.innerWidth'

                    positionScript = """
                        var elements = document.querySelectorAll('*');
                        elements.forEach(function(element) {
                            if (window.getComputedStyle(element).position === 'fixed' || window.getComputedStyle(element).position === 'sticky') {
                                element.style.position = 'absolute';
                            }
                        });
                        """

                    try:
                        driver.execute_script(positionScript)
                    except:
                        pass

                    try:
                        scroll_height = driver.execute_script(shScript)
                    except:
                        scroll_height = VIEWPORT_HEIGHT

                    try:
                        inner_height = driver.execute_script(ihScript)
                    except:
                        inner_height = VIEWPORT_HEIGHT

                    inner_width = VIEWPORT_WIDTH

                    slices = []
                    offset = 0
                    total_size = 0

                    os.makedirs("tmp", exist_ok=True)

                    screenshot_path = "tmp/tmp.png"

                    def takeScreenshot():
                        driver.execute_script(f"window.scrollTo(0, {offset});")
                        time.sleep(1)

                        if (inner_height == scroll_height):
                            driver.set_window_size(
                                inner_width, scroll_height)

                        driver.save_screenshot(screenshot_path)
                        img = Image.open(screenshot_path)

                        new_width = inner_width
                        new_height = int(new_width * img.size[1] / img.size[0])
                        img = img.resize(
                            (new_width, new_height), Image.ANTIALIAS)

                        return img

                    viewport_shot = 0

                    while offset + inner_height < scroll_height and viewport_shot < self.scrollQuota:
                        img = takeScreenshot()

                        slices.append(img)
                        offset += inner_height
                        total_size += img.size[1]

                        os.remove(screenshot_path)

                        viewport_shot += 1

                    remaining_height = scroll_height - offset - viewport_shot * inner_height
                    if viewport_shot < self.scrollQuota:
                        if remaining_height > 0:
                            img = takeScreenshot()

                            img = img.crop(
                                (0, img.height - remaining_height, img.width, img.height))
                            slices.append(img)
                            total_size += remaining_height

                            os.remove(screenshot_path)

                    screenshot = Image.new(
                        'RGB', (slices[0].size[0], total_size))
                    offset = 0
                    for img in slices:
                        screenshot.paste(img, (0, offset))
                        offset += img.size[1]

                    os.makedirs("screenshots", exist_ok=True)

                    self.saveLinkBoundingBox(item["url"], item["filename"], total_size)
                    screenshot.save(f"screenshots/{filename}")
                    self.logger.info(f"Screenshot saved for {url}")
                except Exception as e:
                    self.logger.error(f"Screenshot failed for {url}")
                    self.db.execute(
                        "UPDATE requests SET completed_status = ? WHERE data = ?", (-1, url))
                    self.db.commit()
                    print(e)

                driver.quit()

    def saveLinkBoundingBox(self, url, filename, max_height):
        driver = self.driver

        links = driver.find_elements(By.TAG_NAME, 'a')
        link_bboxes = []
        href_data = {}
        for link in links:
            try:
                bbox = link.rect
                if (bbox["x"] <= 0 and bbox["y"] <= 0):
                    continue
                elif (bbox["width"] <= 0 and bbox["height"] <= 0):
                    continue
                elif (bbox["y"] > max_height):
                    continue

                href = link.get_attribute('href')
                if (not href):
                    continue

                def num(attr):
                    return int(attr) if attr else 0

                box = dict()

                box["w"] = num(link.rect.get("width")) - num(
                    link.get_attribute("marginRight")) - num(link.get_attribute("marginLeft"))
                box["h"] = num(link.rect.get("height")) - num(
                    link.get_attribute("marginBottom")) - num(link.get_attribute("marginTop"))

                box["x"] = num(link.rect.get(
                    "x")) + num(link.get_attribute("marginLeft"))
                box["y"] = num(link.rect.get(
                    "y")) + num(link.get_attribute("marginTop"))

                box["href"] = href
                box["area"] = box["w"] * box["h"]

                if href not in href_data:
                    href_data[href] = {
                        "area": 0,
                        "y": box["y"]
                    }

                href_data[href]["area"] += box["area"]
                href_data[href]["y"] = min(href_data[href]["y"], box["y"])

                link_bboxes.append(box)
            except:
                pass

        for href, data in href_data.items():
            data["score"] = 0.7 * data["area"] - 0.3 * data["y"]

        sorted_links = sorted(href_data.items(),
                           key=lambda x: x[1]["score"], reverse=True)
        
        if url not in self.internalPages:
            top3Links = [link[0]for link in sorted_links[:3]]
            self.manager.idlePages += top3Links
            self.internalPages += top3Links

        final_links = [
            {
                "x": link["x"],
                "y": link["y"],
                "w": link["w"],
                "h": link["h"],
                "href": link["href"]
            }
            for link in link_bboxes
            if link["href"] in [href for href, _ in sorted_links]
        ]

        with open(f"screenshots/{filename}.json", "w") as f:
            f.write(json.dumps(final_links).replace(r" ", ''))
